cat > src/main/java/com/lyzi/spotifyingame/audio/Mp3AudioStream.java << 'ENDOFFILE'
package com.lyzi.spotifyingame.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sound.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Mp3AudioStream implements AudioStream {

	private static final int SAMPLE_RATE = 44100;
	private static final int CHANNELS = 2;

	private final AudioFormat format;
	private final Bitstream bitstream;
	private final Decoder decoder;
	private final InputStream sourceStream;

	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private byte[] pendingBytes = new byte[0];
	private int pendingOffset = 0;
	private boolean sourceExhausted = false;

	public Mp3AudioStream(InputStream mp3InputStream) {
		this.sourceStream = mp3InputStream;
		this.bitstream = new Bitstream(mp3InputStream);
		this.decoder = new Decoder();
		this.format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
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
			throw new EOFException("End of MP3 stream");
		}

		int toReturn = Math.min(size, available);
		ByteBuffer buffer = ByteBuffer.allocate(toReturn).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(pendingBytes, pendingOffset, toReturn);
		pendingOffset += toReturn;
		buffer.flip();
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
			} catch (Exception decodeError) {
				sourceExhausted = true;
				break;
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
ENDOFFILE
