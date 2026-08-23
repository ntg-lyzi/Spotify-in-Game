package com.lyzi.spotifyingame.audio;

import com.lyzi.spotifyingame.SpotifyInGame;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sound.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Streams a .mp3 file as raw 16-bit PCM into Minecraft's sound engine (via LWJGL/OpenAL),
 * decoding with JLayer (pure Java, no native libs — safe on PojavLauncher/Android too).
 *
 * IMPORTANT (performance): actual MP3 decoding happens on a dedicated background
 * thread, continuously filling a small ahead-of-time buffer. read() — which may be
 * called from a game/render-adjacent thread — just copies already-decoded bytes out
 * of that buffer, so decode CPU work never blocks/stutters the game itself.
 */
public class Mp3AudioStream implements AudioStream {

	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNELS = 2;
	private static final int BYTES_PER_MS = (SAMPLE_RATE * CHANNELS * 2) / 1000;
	private static final int MAX_CONSECUTIVE_ERRORS = 50;

	// How far ahead (in bytes of PCM) the background thread is allowed to decode
	// before pausing — roughly 4 seconds of buffered audio.
	private static final int MAX_BUFFERED_BYTES = SAMPLE_RATE * CHANNELS * 2 * 4;

	private final AudioFormat format;
	private final Bitstream bitstream;
	private final Decoder decoder;
	private final InputStream sourceStream;
	private final String debugName;
	private final long baseMillis;

	private final Object bufferLock = new Object();
	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private byte[] pendingBytes = new byte[0];
	private int pendingOffset = 0;
	private boolean sourceExhausted = false;
	private volatile boolean stopped = false;
	private int consecutiveErrors = 0;
	private int totalFramesDecoded = 0;
	private boolean loggedFirstError = false;
	private long bytesServed = 0;

	private Thread decoderThread;

	public volatile boolean finished = false;

	public Mp3AudioStream(InputStream mp3InputStream, String debugName, long startAtMillis) throws IOException {
		this.debugName = debugName;
		this.baseMillis = Math.max(0, startAtMillis);
		InputStream skippedTag = skipId3v2(new BufferedInputStream(mp3InputStream, 8192));
		this.sourceStream = skippedTag;
		this.bitstream = new Bitstream(skippedTag);
		this.decoder = new Decoder();
		this.format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);

		if (baseMillis > 0) {
			// Fast-forward-decode (synchronously, on this thread, before streaming
			// begins) up to the resume point. Decoding is far faster than real-time
			// so this is a brief blip, not a stutter.
			skipAheadBytes(baseMillis * BYTES_PER_MS);
		}

		decoderThread = new Thread(this::decodeLoop, "SpotifyInGame-Decoder-" + debugName);
		decoderThread.setDaemon(true);
		decoderThread.start();
	}

	private static InputStream skipId3v2(InputStream in) throws IOException {
		PushbackInputStream pin = new PushbackInputStream(in, 10);
		byte[] header = new byte[10];
		int read = pin.read(header);
		if (read == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
			int size = ((header[6] & 0x7F) << 21) | ((header[7] & 0x7F) << 14)
					| ((header[8] & 0x7F) << 7) | (header[9] & 0x7F);
			long toSkip = size;
			while (toSkip > 0) {
				long skipped = pin.skip(toSkip);
				if (skipped <= 0) break;
				toSkip -= skipped;
			}
		} else if (read > 0) {
			pin.unread(header, 0, read);
		}
		return pin;
	}

	/** Synchronous decode-and-discard used only once, at startup, to resume mid-track. */
	private void skipAheadBytes(long bytesToSkip) {
		long skipped = 0;
		while (skipped < bytesToSkip && !sourceExhausted) {
			decodeOneFrameSync();
			int avail = pendingBytes.length - pendingOffset;
			if (avail <= 0) break;
			long toDiscard = Math.min(avail, bytesToSkip - skipped);
			pendingOffset += (int) toDiscard;
			skipped += toDiscard;
		}
	}

	/** Decodes a handful of frames synchronously (used only during the initial resume skip). */
	private void decodeOneFrameSync() {
		for (int i = 0; i < 8 && !sourceExhausted; i++) {
			try {
				Header header = bitstream.readFrame();
				if (header == null) {
					sourceExhausted = true;
					return;
				}
				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
				appendPcm(output);
				bitstream.closeFrame();
				totalFramesDecoded++;
			} catch (Exception e) {
				try {
					bitstream.closeFrame();
				} catch (Exception ignored) {
				}
			}
		}
	}

	/** Runs on the background decoder thread for the lifetime of this stream. */
	private void decodeLoop() {
		while (!stopped && !sourceExhausted) {
			try {
				Header header = bitstream.readFrame();
				if (header == null) {
					synchronized (bufferLock) {
						sourceExhausted = true;
						bufferLock.notifyAll();
					}
					break;
				}

				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
				bitstream.closeFrame();
				consecutiveErrors = 0;
				totalFramesDecoded++;

				synchronized (bufferLock) {
					appendPcm(output);
					bufferLock.notifyAll();

					while (!stopped && (pendingBytes.length - pendingOffset) > MAX_BUFFERED_BYTES) {
						try {
							bufferLock.wait(200);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							return;
						}
					}
				}
			} catch (Exception decodeError) {
				consecutiveErrors++;
				if (!loggedFirstError) {
					loggedFirstError = true;
					SpotifyInGame.LOGGER.warn("[{}] Frame decode error after {} good frames: {}: {}",
							debugName, totalFramesDecoded, decodeError.getClass().getSimpleName(), decodeError.getMessage());
				}
				try {
					bitstream.closeFrame();
				} catch (Exception ignored) {
				}
				if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
					synchronized (bufferLock) {
						sourceExhausted = true;
						bufferLock.notifyAll();
					}
					break;
				}
			}
		}
	}

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int size) throws IOException {
		byte[] snapshot;
		int toReturn;

		synchronized (bufferLock) {
			long deadline = System.currentTimeMillis() + 3000;
			while ((pendingBytes.length - pendingOffset) < size && !sourceExhausted && System.currentTimeMillis() < deadline) {
				try {
					bufferLock.wait(50);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}

			int available = pendingBytes.length - pendingOffset;
			if (available <= 0) {
				finished = true;
				SpotifyInGame.LOGGER.info("[{}] Stream ended after decoding {} frames", debugName, totalFramesDecoded);
				throw new EOFException("End of MP3 stream");
			}

			toReturn = Math.min(size, available);
			snapshot = pendingBytes;
			int offset = pendingOffset;

			ByteBuffer buffer = ByteBuffer.allocateDirect(toReturn).order(ByteOrder.LITTLE_ENDIAN);
			buffer.put(snapshot, offset, toReturn);
			pendingOffset += toReturn;
			bytesServed += toReturn;
			buffer.flip();

			// Periodically compact the consumed prefix so the buffer doesn't grow unbounded.
			if (pendingOffset > 65536) {
				byte[] rest = new byte[pendingBytes.length - pendingOffset];
				System.arraycopy(pendingBytes, pendingOffset, rest, 0, rest.length);
				pending.reset();
				pending.write(rest, 0, rest.length);
				pendingBytes = pending.toByteArray();
				pendingOffset = 0;
			}

			bufferLock.notifyAll();

			if (sourceExhausted && (pendingBytes.length - pendingOffset) <= 0) {
				finished = true;
			}

			return buffer;
		}
	}

	public long getElapsedMillis() {
		return baseMillis + (bytesServed / BYTES_PER_MS);
	}

	/** Must be called while holding bufferLock when invoked from decodeLoop(); safe standalone during sync skip-ahead. */
	private void appendPcm(SampleBuffer sample) {
		short[] pcm = sample.getBuffer();
		int len = sample.getBufferLength();
		int srcChannels = sample.getChannelCount();

		ByteBuffer out = ByteBuffer.allocate(len * 2 * (srcChannels == 1 ? 2 : 1))
				.order(ByteOrder.LITTLE_ENDIAN);

		if (srcChannels == CHANNELS) {
			for (int i = 0; i < len; i++) {
				out.putShort(pcm[i]);
			}
		} else if (srcChannels == 1) {
			for (int i = 0; i < len; i++) {
				out.putShort(pcm[i]);
				out.putShort(pcm[i]);
			}
		} else {
			for (int i = 0; i < len; i++) {
				out.putShort(pcm[i]);
			}
		}

		pending.write(out.array(), 0, out.position());
		pendingBytes = pending.toByteArray();
	}

	public void close() throws IOException {
		stopped = true;
		synchronized (bufferLock) {
			bufferLock.notifyAll();
		}
		if (decoderThread != null) {
			try {
				decoderThread.join(500);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
		try {
			bitstream.close();
		} catch (Exception ignored) {
		}
		sourceStream.close();
	}
}
