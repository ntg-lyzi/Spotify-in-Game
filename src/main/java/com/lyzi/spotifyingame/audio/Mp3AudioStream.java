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
 */
public class Mp3AudioStream implements AudioStream {

	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNELS = 2;
	private static final int MAX_CONSECUTIVE_ERRORS = 50;

	private final AudioFormat format;
	private final Bitstream bitstream;
	private final Decoder decoder;
	private final InputStream sourceStream;
	private final String debugName;

	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private byte[] pendingBytes = new byte[0];
	private int pendingOffset = 0;
	private boolean sourceExhausted = false;
	private int consecutiveErrors = 0;
	private int totalFramesDecoded = 0;
	private boolean loggedFirstError = false;

	public volatile boolean finished = false;

	public Mp3AudioStream(InputStream mp3InputStream) throws IOException {
		this(mp3InputStream, "unknown");
	}

	public Mp3AudioStream(InputStream mp3InputStream, String debugName) throws IOException {
		this.debugName = debugName;
		InputStream skippedTag = skipId3v2(new BufferedInputStream(mp3InputStream, 8192));
		this.sourceStream = skippedTag;
		this.bitstream = new Bitstream(skippedTag);
		this.decoder = new Decoder();
		this.format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
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

	@Override
	public AudioFormat getFormat() {
		return format;
	}

	@Override
	public ByteBuffer read(int size) throws IOException {
		fill(size);

		int available = pendingBytes.length - pendingOffset;
		if (available <= 0) {
			finished = true;
			SpotifyInGame.LOGGER.info("[{}] Stream ended after decoding {} frames", debugName, totalFramesDecoded);
			throw new EOFException("End of MP3 stream");
		}

		int toReturn = Math.min(size, available);
		ByteBuffer buffer = ByteBuffer.allocateDirect(toReturn).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(pendingBytes, pendingOffset, toReturn);
		pendingOffset += toReturn;
		buffer.flip();

		if (sourceExhausted && (pendingBytes.length - pendingOffset) <= 0) {
			finished = true;
		}

		return buffer;
	}

	private void fill(int needed) {
		int haveLeft = pendingBytes.length - pendingOffset;
		if (haveLeft >= needed) {
			return;
		}

		if (pendingOffset > 0) {
			byte[] rest = new byte[haveLeft];
			System.arraycopy(pendingBytes, pendingOffset, rest, 0, haveLeft);
			pending.reset();
			pending.write(rest, 0, rest.length);
			pendingBytes = pending.toByteArray();
			pendingOffset = 0;
		}

		while (!sourceExhausted && (pendingBytes.length - pendingOffset) < needed) {
			try {
				Header header = bitstream.readFrame();
				if (header == null) {
					SpotifyInGame.LOGGER.info("[{}] readFrame() returned null (natural end) after {} frames", debugName, totalFramesDecoded);
					sourceExhausted = true;
					break;
				}

				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
				appendPcm(output);
				bitstream.closeFrame();
				consecutiveErrors = 0;
				totalFramesDecoded++;
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
					SpotifyInGame.LOGGER.warn("[{}] Giving up after {} consecutive decode errors (decoded {} good frames total)",
							debugName, consecutiveErrors, totalFramesDecoded);
					sourceExhausted = true;
					break;
				}
			}
		}
	}

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
		try {
			bitstream.close();
		} catch (Exception ignored) {
		}
		sourceStream.close();
	}
}
