package com.nibblenerds.unitedminecraft.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import javax.sound.sampled.AudioFormat;

import com.mojang.blaze3d.audio.SoundBuffer;

import net.minecraft.resources.Identifier;

/**
 * Builds the Build Mode air cursor cue as raw PCM at runtime, the same way {@link WallToneSynth}
 * and {@link CombatLockSynth} do (served to vanilla's sound buffer cache via {@code SoundBufferLibraryMixin}):
 * a short, soft airy tone and filtered noise burst that is distinct from solid block footsteps and fluids,
 * without requiring bundled {@code .ogg} sound files.
 */
public final class BuildCursorAirSynth {
	public static final String NAMESPACE = "united_minecraft";
	public static final String PATH_PREFIX = "sounds/build_cursor/";
	public static final String BUFFER_PATH = "sounds/build_cursor/air.ogg";

	/** The sound location played by {@link BuildCursorAirSound}. */
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(NAMESPACE, "build_cursor/air");

	private static final int SAMPLE_RATE = 44100;
	// 50 milliseconds: snappy enough to follow rapid cursor movement without sluggish overlap.
	private static final int SAMPLE_COUNT = 2205;
	private static final double ATTACK_MS = 4.0;
	private static final double PEAK = 0.40;

	private BuildCursorAirSynth() {
	}

	public static boolean handles(Identifier path) {
		return path.getNamespace().equals(NAMESPACE)
				&& (path.getPath().startsWith(PATH_PREFIX) || path.getPath().equals("sounds/build_cursor_air.ogg"));
	}

	public static SoundBuffer create(Identifier path) {
		double[] wave = new double[SAMPLE_COUNT];
		Random random = new Random(0x4218);

		// Generate noise filtered by a gentle low-pass filter (~1000 Hz cutoff) for an airy texture
		double dt = 1.0 / SAMPLE_RATE;
		double rc = 1.0 / (2.0 * Math.PI * 1000.0);
		double alpha = dt / (rc + dt);
		double filteredNoise = 0.0;

		int attackSamples = (int) Math.round(ATTACK_MS * SAMPLE_RATE / 1000.0);
		int decaySamples = SAMPLE_COUNT - attackSamples;

		for (int i = 0; i < SAMPLE_COUNT; i++) {
			double rawNoise = random.nextDouble() * 2.0 - 1.0;
			filteredNoise += alpha * (rawNoise - filteredNoise);

			// Soft 380 Hz tone mixed in to provide a clear pitch body for OpenAL elevation/spatialization
			double tone = Math.sin(2.0 * Math.PI * 380.0 * i / SAMPLE_RATE);

			// Envelope: fast raised-cosine attack followed by a smooth cosine-squared decay
			double envelope;
			if (i < attackSamples) {
				envelope = 0.5 - 0.5 * Math.cos(Math.PI * i / attackSamples);
			} else {
				double decayRatio = (double) (i - attackSamples) / decaySamples;
				double decayCos = Math.cos(Math.PI * 0.5 * decayRatio);
				envelope = decayCos * decayCos;
			}

			wave[i] = (0.65 * filteredNoise + 0.35 * tone) * envelope;
		}

		// Find peak and scale to target peak amplitude
		double max = 1.0e-12;
		for (double sample : wave) {
			max = Math.max(max, Math.abs(sample));
		}
		double scale = PEAK / max;

		ByteBuffer data = ByteBuffer.allocateDirect(wave.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (double sample : wave) {
			data.putShort((short) Math.round(Math.max(-1.0, Math.min(1.0, sample * scale)) * Short.MAX_VALUE));
		}
		data.flip();
		return new SoundBuffer(data, new AudioFormat(SAMPLE_RATE, 16, 1, true, false));
	}
}
