package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.BuildModeController;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla from aborting a Build Mode break. {@code continueAttack} calls {@code
 * stopDestroyBlock} on every tick the attack key isn't held, which Build Mode's own break key
 * never is - the abort packet that sends tells the server the break ended, and since the
 * repeating hit sound is now a server-driven level event (see {@code
 * ServerPlayerGameMode.tick}) rather than something the client plays itself, the server went
 * silent after the first tick of every break.
 */
@Mixin(Minecraft.class)
public class MinecraftContinueAttackMixin {
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$keepBuildModeBreak(boolean leftClick, CallbackInfo ci) {
		if (!leftClick && BuildModeController.isBreaking()) {
			ci.cancel();
		}
	}
}
