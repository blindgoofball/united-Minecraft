package com.nibblenerds.unitedminecraft.client.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Spatial sound instance for Build Mode cursor air cues. Like {@link CombatLockSound} and
 * {@link WallToneSound}, there is no {@code sounds.json} entry - {@link #getOrResolve} builds the
 * {@link Sound} directly and {@code SoundBufferLibraryMixin} supplies raw PCM from {@link BuildCursorAirSynth}.
 */
public final class BuildCursorAirSound extends AbstractSoundInstance {
	public BuildCursorAirSound(double x, double y, double z, float volume, float pitch, RandomSource random) {
		super(SoundEvent.createVariableRangeEvent(BuildCursorAirSynth.SOUND_ID), SoundSource.MASTER, random);
		this.looping = false;
		this.delay = 0;
		this.relative = false;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = x;
		this.y = y;
		this.z = z;
		this.volume = volume;
		this.pitch = pitch;
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
		if (this.soundEvent == null) {
			this.sound = new Sound(this.identifier, ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1,
					Sound.Type.FILE, false, false, 16);
			this.soundEvent = new WeighedSoundEvents(this.identifier, null);
			this.soundEvent.addSound(this.sound);
		}
		return this.soundEvent;
	}
}
