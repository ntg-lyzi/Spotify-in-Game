package com.lyzi.spotifyingame.audio;

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
 * Two important robustness fixes baked in here:
 * 1. Skips leading ID3v2 tags (common on real-world mp3s, often containing embedded
 *    album art) — JLayer's frame sync can choke on these otherwise, causing the whole
 *    stream to look "finished" after only a few seconds.
 * 2. A single bad/unsupported frame no longer kills the whole stream — we skip past
 *    isolated decode errors and keep going, only giving up after many in a row.
 */
public class Mp3AudioStream implements AudioStream {

	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNELS = 2;
	private static final int MAX_CONSECUTIVE_ERRORS = 30;

	private final AudioFormat format;
	private final Bitstream bitstream;
	private final Decoder decoder;
	private final InputStream sourceStream;

	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private byte[] pendingBytes = new byte[0];
	private int pendingOffset = 0;
	private boolean sourceExhausted = false;
	private int consecutiveErrors = 0;

	public volatile boolean finished = false;

	public Mp3AudioStream(InputStream mp3InputStream) throws IOException {
		InputStream skippedTag = skipId3v2(new BufferedInputStream(mp3InputStream, 8192));
		this.sourceStream = skippedTag;
		this.bitstream = new Bitstream(skippedTag);
		this.decoder = new Decoder();
		this.format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
	}

	/** Skips a leading ID3v2 tag block if present, so JLayer starts right at the first real mp3 frame. */
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
					sourceExhausted = true;
					break;
				}

				SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
				appendPcm(output);
				bitstream.closeFrame();
				consecutiveErrors = 0;
			} catch (Exception decodeError) {
				// Skip past a bad/unsupported frame instead of giving up on the whole track.
				consecutiveErrors++;
				try {
					bitstream.closeFrame();
				} catch (Exception ignored) {
				}
				if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
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
