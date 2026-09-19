package com.nibblenerds.unitedminecraft.client.mixin;

import com.mojang.text2speech.Narrator;
import com.nibblenerds.unitedminecraft.client.speech.PrismNarrator;

import net.minecraft.client.GameNarrator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the {@link Narrator} that {@link GameNarrator} builds for itself with one
 * that prefers speaking through Prism's best available screen reader backend, so
 * screen reader users get their own screen reader's voice and review cursor
 * instead of going through SAPI/text2speech.
 */
@Mixin(GameNarrator.class)
public class GameNarratorMixin {
	@Redirect(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"))
	private Narrator usePrismNarrator() {
		return PrismNarrator.create(Narrator.getNarrator());
	}
}
