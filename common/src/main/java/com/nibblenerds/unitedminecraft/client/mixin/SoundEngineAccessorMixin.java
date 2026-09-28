package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Lets wall tones push a new volume straight to a playing channel. Vanilla only reads a
 * tickable sound's volume in its own sound tick, which runs <em>before</em> the end-of-tick
 * event where United Minecraft handles turning - so waiting for it would always leave the tones
 * a tick behind the way the player is facing. {@code calculateVolume} is invoked rather than
 * reimplemented so the master/category sliders still apply exactly as they do for every sound.
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessorMixin {
	@Accessor("instanceToChannel")
	Map<SoundInstance, ChannelAccess.ChannelHandle> unitedMinecraft$getInstanceToChannel();

	@Invoker("calculateVolume")
	float unitedMinecraft$calculateVolume(SoundInstance instance);
}
