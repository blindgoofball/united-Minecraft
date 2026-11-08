package com.nibblenerds.unitedminecraft.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * The Combat Beacon's solid "you are on target" tone: a looping, head-relative voice straight
 * ahead of the listener with attenuation off, so it always sounds centered and at the same level
 * for as long as the lock holds. Like {@link WallToneSound}, there is no {@code sounds.json} entry
 * behind it - {@link #getOrResolve} builds the {@link Sound} directly and the buffer mixin answers
 * with PCM from {@link CombatLockSynth}.
 */
public final class CombatLockSound extends AbstractTickableSoundInstance {
	private CombatLockSound(RandomSource random, float volume, boolean looping) {
		super(SoundEvent.createVariableRangeEvent(CombatLockSynth.SOUND_ID), SoundSource.MASTER, random);
		this.looping = looping;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = 0.0;
		this.y = 0.0;
		this.z = -1.0;
		this.volume = volume;
	}

	/** The lock tone held for as long as the lock lasts. */
	public static CombatLockSound live(RandomSource random, float volume) {
		return new CombatLockSound(random, volume, true);
	}

	/**
	 * A single pass for the sound glossary. Plays its loop just once rather than being stopped by a
	 * tick countdown: the glossary is usually opened from a pausing screen, and a paused game never
	 * ticks sounds.
	 */
	public static CombatLockSound preview(RandomSource random, float volume) {
		return new CombatLockSound(random, volume, false);
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

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
	}
}
