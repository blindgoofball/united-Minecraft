package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/** {@link StructureVoiceAudio#parseWav} - reads what the system's text-to-speech wrote for a structure voice. */
class WavParserTest {
	private static final int PCM = 1;
	private static final int FLOAT = 3;

	/** A RIFF/WAVE file, built chunk by chunk. */
	private static final class Wav {
		private final ByteArrayOutputStream chunks = new ByteArrayOutputStream();
		private int riffSize = -1;

		Wav fmt(int format, int channels, int sampleRate, int bits) {
			ByteBuffer body = le(16);
			body.putShort((short) format).putShort((short) channels).putInt(sampleRate)
					.putInt(sampleRate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
			return chunk("fmt ", body.array(), 16);
		}

		Wav data(short... samples) {
			return data(samples.length * 2, samples);
		}

		/** A data chunk whose declared size is {@code declaredSize}, whatever it really holds. */
		Wav data(int declaredSize, short... samples) {
			ByteBuffer body = le(samples.length * 2);
			for (short sample : samples) {
				body.putShort(sample);
			}
			return chunk("data", body.array(), declaredSize);
		}

		Wav chunk(String id, byte[] body, int declaredSize) {
			ByteBuffer header = le(8);
			header.put(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(declaredSize);
			chunks.writeBytes(header.array());
			chunks.writeBytes(body);
			return this;
		}

		Wav riffSize(int size) {
			riffSize = size;
			return this;
		}

		byte[] bytes() {
			byte[] body = chunks.toByteArray();
			ByteBuffer file = le(12 + body.length);
			file.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
					.putInt(riffSize >= 0 ? riffSize : 4 + body.length)
					.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
					.put(body);
			return file.array();
		}

		private static ByteBuffer le(int size) {
			return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
		}
	}

	private static StructureVoiceAudio.Pcm parse(byte[] bytes) {
		return StructureVoiceAudio.parseWav(bytes).orElseThrow();
	}

	@Test
	void readsMono16BitPcm() {
		StructureVoiceAudio.Pcm pcm = parse(new Wav().fmt(PCM, 1, 22050, 16).data((short) 0, (short) 16384, (short) -32768).bytes());
		assertEquals(1, pcm.channels());
		assertEquals(22050, pcm.sampleRate());
		assertArrayEquals(new float[] {0.0f, 0.5f, -1.0f}, pcm.samples());
	}

	@Test
	void keepsStereoInterleaved() {
		StructureVoiceAudio.Pcm pcm = parse(new Wav().fmt(PCM, 2, 44100, 16).data((short) 100, (short) -100).bytes());
		assertEquals(2, pcm.channels());
		assertEquals(2, pcm.samples().length);
	}

	@Test
	void toleratesEspeaksUnfilledSizesWhenStreaming() {
		// espeak writing to stdout can't seek back, so both sizes are left at their maximum.
		byte[] wav = new Wav().fmt(PCM, 1, 22050, 16).data(0xFFFFFFFF, (short) 1, (short) 2, (short) 3).riffSize(0xFFFFFFFF).bytes();
		assertEquals(3, parse(wav).samples().length);
	}

	@Test
	void skipsUnknownChunksIncludingOddSizedOnes() {
		byte[] wav = new Wav()
				.chunk("LIST", new byte[] {1, 2, 3, 0}, 3) // odd size, padded to an even boundary
				.fmt(PCM, 1, 16000, 16)
				.chunk("fact", new byte[4], 4)
				.data((short) 7)
				.bytes();
		assertEquals(1, parse(wav).samples().length);
	}

	@Test
	void rejectsWhatItCannotPlay() {
		assertTrue(StructureVoiceAudio.parseWav(new Wav().fmt(PCM, 1, 22050, 8).data((short) 1).bytes()).isEmpty(), "8-bit");
		assertTrue(StructureVoiceAudio.parseWav(new Wav().fmt(FLOAT, 1, 22050, 16).data((short) 1).bytes()).isEmpty(), "float");
		assertTrue(StructureVoiceAudio.parseWav(new Wav().data((short) 1).fmt(PCM, 1, 22050, 16).bytes()).isEmpty(),
				"data before its format");
		assertTrue(StructureVoiceAudio.parseWav(new Wav().fmt(PCM, 1, 22050, 16).data().bytes()).isEmpty(), "no samples");
	}

	@Test
	void rejectsSomethingThatIsNotAWav() {
		assertTrue(StructureVoiceAudio.parseWav(new byte[0]).isEmpty());
		assertTrue(StructureVoiceAudio.parseWav("RIFF".getBytes()).isEmpty());
		assertTrue(StructureVoiceAudio.parseWav("not a wav file at all".getBytes()).isEmpty());
	}

	@Test
	void stopsAtTheEndOfATruncatedFile() {
		byte[] full = new Wav().fmt(PCM, 1, 22050, 16).data((short) 1, (short) 2, (short) 3, (short) 4).bytes();
		byte[] truncated = java.util.Arrays.copyOf(full, full.length - 3);
		assertEquals(2, parse(truncated).samples().length);
	}
}
