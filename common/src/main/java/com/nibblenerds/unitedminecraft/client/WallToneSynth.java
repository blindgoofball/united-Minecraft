package com.nibblenerds.unitedminecraft.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Random;

import javax.sound.sampled.AudioFormat;

import com.mojang.blaze3d.audio.SoundBuffer;

import net.minecraft.resources.Identifier;

/**
 * Builds the wall tone loops as raw PCM at runtime instead of shipping {@code .ogg} files.
 * {@code SoundBufferLibraryMixin} hands these buffers to vanilla's own sound buffer cache
 * whenever {@link WallToneSound} asks for one of the {@link #PATH_PREFIX} paths, so the tones
 * still play through the normal sound engine (master volume, pausing, device switching,
 * Directional Audio/HRTF) without the mod needing an audio encoder at build time.
 *
 * <p>Two styles (see {@link UnitedMinecraftConfig.WallToneStyle}):
 * <ul>
 * <li>{@code TONES} - harmonic-rich pitched tones. Every frequency and pulse rate is a whole
 * number of cycles in the one-second loop, so OpenAL's loop point is seamless. Harmonic-rich
 * rather than pure sines because HRTF localization (front/back especially) depends on
 * high-frequency content; a bare sine tends to sound "inside your head".</li>
 * <li>{@code NOISE} - pink noise filtered to a smooth one-octave band per voice. Built directly
 * in the frequency domain (random phases, inverse FFT), which makes the loop exactly periodic -
 * no seam - and every voice gets its own seed, so two bands playing from opposite sides stay two
 * decorrelated sources instead of fusing into one centered image.</li>
 * </ul>
 * Obstacles in either style are the same sound pulsed about six times a second, so they're
 * told from a wall by rhythm alone.
 */
public final class WallToneSynth {
	/** Namespace every wall tone sound location lives under. */
	public static final String NAMESPACE = "united_minecraft";
	/** Resource path prefix (as passed to {@code SoundBufferLibrary#getCompleteBuffer}) owned by this class. */
	public static final String PATH_PREFIX = "sounds/wall_tone/";

	private static final int SAMPLE_RATE = 44100;
	private static final double HARMONIC_CEILING_HZ = 7000.0;
	private static final double TONE_PEAK = 0.45;

	/** Power of two for the FFT: about 1.49 seconds, long enough that the noise doesn't audibly repeat. */
	private static final int NOISE_LENGTH = 1 << 16;
	/** Gaussian width (in octaves) of each noise band - gives -3 dB at half an octave either side of center. */
	private static final double NOISE_BAND_SIGMA = 0.6;
	/**
	 * As loud as the peak limit allows. The tones measure about 0.32 RMS, but noise's much higher
	 * crest factor would clip long before reaching that, so noise sits roughly 4 dB lower on a
	 * meter - balance by ear with the volume setting.
	 */
	private static final double NOISE_RMS = 0.20;
	private static final double NOISE_PEAK_LIMIT = 0.95;

	private static final double PULSES_PER_SECOND = 6.0;

	private WallToneSynth() {
	}

	/** True only for a path naming a real style and {@link WallToneVoice} - anything else falls through to vanilla's normal file lookup. */
	public static boolean handles(Identifier path) {
		return parse(path) != null;
	}

	/** Must only be called for a path {@link #handles} accepts. */
	public static SoundBuffer create(Identifier path) {
		Request request = parse(path);
		boolean pulsed = request.voice().kind() == WallToneVoice.Kind.OBSTACLE;
		double[] wave = request.style() == UnitedMinecraftConfig.WallToneStyle.NOISE
				? noise(request.voice(), pulsed)
				: tone(request.voice(), pulsed);
		ByteBuffer data = ByteBuffer.allocateDirect(wave.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (double sample : wave) {
			data.putShort((short) Math.round(Math.max(-1.0, Math.min(1.0, sample)) * Short.MAX_VALUE));
		}
		data.flip();
		return new SoundBuffer(data, new AudioFormat(SAMPLE_RATE, 16, 1, true, false));
	}

	private static Request parse(Identifier path) {
		if (!path.getNamespace().equals(NAMESPACE) || !path.getPath().startsWith(PATH_PREFIX)) {
			return null;
		}
		String rest = path.getPath().substring(PATH_PREFIX.length());
		if (rest.endsWith(".ogg")) {
			rest = rest.substring(0, rest.length() - ".ogg".length());
		}
		int slash = rest.indexOf('/');
		if (slash < 0) {
			return null;
		}
		UnitedMinecraftConfig.WallToneStyle style = null;
		for (UnitedMinecraftConfig.WallToneStyle candidate : UnitedMinecraftConfig.WallToneStyle.values()) {
			if (candidate.name().toLowerCase(Locale.ROOT).equals(rest.substring(0, slash))) {
				style = candidate;
			}
		}
		WallToneVoice voice = WallToneVoice.byFileName(rest.substring(slash + 1));
		return style == null || voice == null ? null : new Request(style, voice);
	}

	/**
	 * Walls and ceilings: a steady, soft organ-like tone - every harmonic, falling off fast enough
	 * that it stays mellow rather than buzzy, since it may be heard for minutes at a time.
	 * Obstacles: odd harmonics only (a hollow, clarinet-like timbre), pulsed.
	 */
	private static double[] tone(WallToneVoice voice, boolean pulsed) {
		double[] wave = new double[SAMPLE_RATE];
		int step = pulsed ? 2 : 1;
		double rolloff = pulsed ? 1.2 : 1.7;
		int frequency = voice.frequency();
		for (int harmonic = 1; frequency * harmonic <= HARMONIC_CEILING_HZ; harmonic += step) {
			double amplitude = 1.0 / Math.pow(harmonic, rolloff);
			double omega = 2.0 * Math.PI * frequency * harmonic / SAMPLE_RATE;
			for (int i = 0; i < wave.length; i++) {
				wave[i] += amplitude * Math.sin(omega * i);
			}
		}
		if (pulsed) {
			applyPulses(wave);
		}
		scaleToPeak(wave, TONE_PEAK);
		return wave;
	}

	private static double[] noise(WallToneVoice voice, boolean pulsed) {
		int n = NOISE_LENGTH;
		double[] re = new double[n];
		double[] im = new double[n];
		Random random = new Random(0x5EEDL * 31 + voice.ordinal());
		double center = voice.noiseCenter();
		for (int k = 1; k < n / 2; k++) {
			double frequency = (double) k * SAMPLE_RATE / n;
			double octaves = Math.log(frequency / center) / Math.log(2.0);
			// Pink (-3 dB/octave) within a Gaussian-on-log-frequency band.
			double magnitude = Math.exp(-0.5 * (octaves / NOISE_BAND_SIGMA) * (octaves / NOISE_BAND_SIGMA)) / Math.sqrt(frequency);
			if (magnitude < 1.0e-7) {
				continue;
			}
			double phase = random.nextDouble() * 2.0 * Math.PI;
			re[k] = magnitude * Math.cos(phase);
			im[k] = magnitude * Math.sin(phase);
			// Hermitian mirror so the inverse transform is purely real.
			re[n - k] = re[k];
			im[n - k] = -im[k];
		}
		inverseFft(re, im);
		scaleToRms(re, NOISE_RMS);
		if (pulsed) {
			applyPulses(re);
		}
		return re;
	}

	/**
	 * Gates {@code wave} on and off a whole number of times per loop (so the loop stays seamless):
	 * on for the first 55% of each pulse with raised-cosine edges, silent for the rest.
	 */
	private static void applyPulses(double[] wave) {
		int pulsesPerLoop = (int) Math.round(PULSES_PER_SECOND * wave.length / SAMPLE_RATE);
		for (int i = 0; i < wave.length; i++) {
			double phase = (double) i * pulsesPerLoop / wave.length % 1.0;
			double envelope;
			if (phase < 0.08) {
				envelope = 0.5 - 0.5 * Math.cos(Math.PI * phase / 0.08);
			} else if (phase < 0.47) {
				envelope = 1.0;
			} else if (phase < 0.55) {
				envelope = 0.5 + 0.5 * Math.cos(Math.PI * (phase - 0.47) / 0.08);
			} else {
				envelope = 0.0;
			}
			wave[i] *= envelope;
		}
	}

	private static void scaleToPeak(double[] wave, double peak) {
		double max = 1.0e-12;
		for (double sample : wave) {
			max = Math.max(max, Math.abs(sample));
		}
		double scale = peak / max;
		for (int i = 0; i < wave.length; i++) {
			wave[i] *= scale;
		}
	}

	private static void scaleToRms(double[] wave, double rms) {
		double sum = 0.0;
		double max = 1.0e-12;
		for (double sample : wave) {
			sum += sample * sample;
			max = Math.max(max, Math.abs(sample));
		}
		double scale = rms / Math.max(1.0e-12, Math.sqrt(sum / wave.length));
		scale = Math.min(scale, NOISE_PEAK_LIMIT / max);
		for (int i = 0; i < wave.length; i++) {
			wave[i] *= scale;
		}
	}

	/** In-place iterative radix-2 inverse FFT (unscaled - {@link #scaleToRms} normalizes afterwards). {@code re.length} must be a power of two. */
	private static void inverseFft(double[] re, double[] im) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				double t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for (int length = 2; length <= n; length <<= 1) {
			double angle = 2.0 * Math.PI / length;
			double stepRe = Math.cos(angle);
			double stepIm = Math.sin(angle);
			for (int start = 0; start < n; start += length) {
				double wRe = 1.0;
				double wIm = 0.0;
				for (int k = 0; k < length / 2; k++) {
					int a = start + k;
					int b = a + length / 2;
					double tRe = re[b] * wRe - im[b] * wIm;
					double tIm = re[b] * wIm + im[b] * wRe;
					re[b] = re[a] - tRe;
					im[b] = im[a] - tIm;
					re[a] += tRe;
					im[a] += tIm;
					double nextRe = wRe * stepRe - wIm * stepIm;
					wIm = wRe * stepIm + wIm * stepRe;
					wRe = nextRe;
				}
			}
		}
	}

	private record Request(UnitedMinecraftConfig.WallToneStyle style, WallToneVoice voice) {
	}
}
