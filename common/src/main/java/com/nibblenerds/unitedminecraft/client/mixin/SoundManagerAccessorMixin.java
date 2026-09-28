package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

/** Reaches the {@link SoundEngine} behind {@code SoundManager} - see {@link SoundEngineAccessorMixin} for why. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessorMixin {
	@Accessor("soundEngine")
	SoundEngine unitedMinecraft$getSoundEngine();
}
