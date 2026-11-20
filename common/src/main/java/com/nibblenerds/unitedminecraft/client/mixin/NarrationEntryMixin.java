package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nibblenerds.unitedminecraft.client.speech.NarrationRepeat;

import net.minecraft.client.gui.narration.NarrationThunk;

/** Clears the already-narrated mark while {@link NarrationRepeat} is on, so identical text is said again. */
@Mixin(targets = "net.minecraft.client.gui.narration.ScreenNarrationCollector$NarrationEntry")
public abstract class NarrationEntryMixin {
	@Shadow
	private boolean alreadyNarrated;

	@Inject(method = "update", at = @At("RETURN"))
	private void unitedMinecraft$repeatWhenFocusMoved(int generation, NarrationThunk<?> contents,
			CallbackInfoReturnable<Object> cir) {
		if (NarrationRepeat.isForcing()) {
			alreadyNarrated = false;
		}
	}
}
