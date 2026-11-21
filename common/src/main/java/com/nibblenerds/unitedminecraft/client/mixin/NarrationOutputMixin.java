package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nibblenerds.unitedminecraft.client.speech.NarrationRepeat;

import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationThunk;

/**
 * Tells {@link NarrationRepeat} what kind of entry is being written, just before {@code
 * NarrationEntryMixin} sees it, so only title text is made to repeat.
 */
@Mixin(targets = "net.minecraft.client.gui.narration.ScreenNarrationCollector$Output")
public abstract class NarrationOutputMixin {
	@Inject(method = "add", at = @At("HEAD"))
	private void unitedMinecraft$noteEntryType(NarratedElementType type, NarrationThunk<?> contents, CallbackInfo ci) {
		NarrationRepeat.entryType(type);
	}
}
