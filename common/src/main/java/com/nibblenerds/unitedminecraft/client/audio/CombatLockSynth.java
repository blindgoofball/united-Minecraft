package com.nibblenerds.unitedminecraft.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

import com.mojang.blaze3d.audio.SoundBuffer;

import net.minecraft.resources.Identifier;

/**
 * Builds the Combat Beacon's lock tone as raw PCM at runtime, the same way {@link WallToneSynth}
 * does for the wall tones (and handed to vanilla's buffer cache by the same mixin): a steady,
 * slightly bright tone that is clearly not any of the wall tones or the pings. The frequency is a
 * whole number of cycles in the one-second loop, so the loop point is seamless.
 */
public final class CombatLockSynth {
	/** The sound location {@link CombatLockSound} plays. */
	static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath(WallToneSynth.NAMESPACE, "combat_lock");
	/** What vanilla asks the buffer cache for: {@code sounds/<path>.ogg}. */
	private static final String BUFFER_PATH = "sounds/combat_lock.ogg";

	private static final int SAMPLE_RATE = 44100;
	// F sharp 5 (739.99 Hz, rounded to a whole 740 so the one-second loop is seamless): the pitch the
	// cow bell pings play at when you are level with the target, so the lock sounds like the pings
	// settling onto their own note.
	private static final int FREQUENCY_HZ = 740;
	// Fundamental plus a few quickly-falling harmonics: bright enough to localize, mellow enough
	// to be heard for as long as a fight lasts.
	private static final double[] HARMONICS = {1.0, 0.30, 0.12, 0.05};
	private static final double PEAK = 0.4;

	private CombatLockSynth() {
	}

	public static boolean handles(Identifier path) {
		return path.getNamespace().equals(WallToneSynth.NAMESPACE) && path.getPath().equals(BUFFER_PATH);
	}

	public static SoundBuffer create(Identifier path) {
		double[] wave = new double[SAMPLE_RATE];
		double total = 0.0;
		for (double amplitude : HARMONICS) {
			total += amplitude;
		}
		for (int h = 0; h < HARMONICS.length; h++) {
			double omega = 2.0 * Math.PI * FREQUENCY_HZ * (h + 1) / SAMPLE_RATE;
			for (int i = 0; i < wave.length; i++) {
				wave[i] += HARMONICS[h] * Math.sin(omega * i);
			}
		}
		ByteBuffer data = ByteBuffer.allocateDirect(wave.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (double sample : wave) {
			data.putShort((short) Math.round(Math.max(-1.0, Math.min(1.0, sample / total * PEAK)) * Short.MAX_VALUE));
		}
		data.flip();
		return new SoundBuffer(data, new AudioFormat(SAMPLE_RATE, 16, 1, true, false));
	}
}
