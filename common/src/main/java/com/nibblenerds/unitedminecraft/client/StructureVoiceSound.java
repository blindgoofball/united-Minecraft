package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * One spoken structure name. The direction is already baked into the stereo audio (see {@link
 * StructureVoiceAudio#variant}), so this is played centred on the listener with attenuation off
 * and never moves - only its pitch (height) and volume are set here.
 *
 * <p>Like {@link WallToneSound}, {@link #getOrResolve} builds the {@link Sound} directly and {@code
 * SoundBufferLibraryMixin} supplies its audio from {@link StructureVoiceAudio}.
 */
public final class StructureVoiceSound extends AbstractSoundInstance {
	public StructureVoiceSound(Identifier variant, float volume, float pitch, RandomSource random) {
		super(SoundEvent.createVariableRangeEvent(variant), SoundSource.MASTER, random);
		this.looping = false;
		this.delay = 0;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
		this.volume = volume;
		this.pitch = pitch;
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
		this.sound = new Sound(this.identifier, ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1,
				Sound.Type.FILE, false, false, 16);
		WeighedSoundEvents events = new WeighedSoundEvents(this.identifier, null);
		events.addSound(this.sound);
		return events;
	}
}
