package com.nibblenerds.unitedminecraft.client;

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
 * One looping wall tone voice. Played head-relative (see {@link WallToneVoice} for the
 * coordinate space) with attenuation off, so its position only ever conveys direction and
 * {@link WallToneController} alone decides loudness from the measured wall distance.
 *
 * <p>No smoothing: the controller sets the volume outright every tick and pushes it straight
 * to the playing channel, matching Grimdark and Wrath Access, which both apply wall distance to
 * volume directly for responsiveness. OpenAL itself ramps gain changes across its own mix
 * update, which is what keeps a 20-per-second volume step from clicking.
 *
 * <p>There's no {@code sounds.json} entry behind these: {@link #getOrResolve} builds the {@link
 * Sound} directly, and {@code SoundBufferLibraryMixin} answers the buffer request with PCM
 * from {@link WallToneSynth}.
 */
public final class WallToneSound extends AbstractTickableSoundInstance {
	private final WallToneVoice voice;
	private final UnitedMinecraftConfig.WallToneStyle style;

	private WallToneSound(WallToneVoice voice, UnitedMinecraftConfig.WallToneStyle style, RandomSource random,
			float volume, boolean looping) {
		super(SoundEvent.createVariableRangeEvent(voice.identifier(style)), SoundSource.MASTER, random);
		this.voice = voice;
		this.style = style;
		this.looping = looping;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = voice.earX();
		this.y = voice.earY();
		this.z = voice.earZ();
		this.volume = volume;
	}

	/** A controller-driven looping voice, starting at {@code volume}. */
	public static WallToneSound live(WallToneVoice voice, UnitedMinecraftConfig.WallToneStyle style, RandomSource random, float volume) {
		return new WallToneSound(voice, style, random, volume, true);
	}

	/**
	 * A one-shot sample of a voice for the sound glossary. Plays its loop just once rather than
	 * being stopped by a tick countdown: the glossary is usually opened from a pausing screen,
	 * and a paused game never ticks sounds, so a tick-driven stop would never fire.
	 */
	public static WallToneSound preview(WallToneVoice voice, UnitedMinecraftConfig.WallToneStyle style, RandomSource random, float volume) {
		return new WallToneSound(voice, style, random, volume, false);
	}

	public WallToneVoice voice() {
		return voice;
	}

	public UnitedMinecraftConfig.WallToneStyle style() {
		return style;
	}

	public void setVolume(float volume) {
		this.volume = volume;
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

	/** Nothing to do - the controller drives volume directly (see the class doc). */
	@Override
	public void tick() {
	}
}
