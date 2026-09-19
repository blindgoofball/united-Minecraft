package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nibblenerds.unitedminecraft.client.duck.MultilineTextFieldExt;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;

/**
 * Vanilla speaks "label. {full page text}" again every time the book-and-quill page editor's
 * value changes (see {@code updateWidgetNarration}), which combined with the deltas {@link
 * MultilineTextFieldMixin} narrates would mean every edit - and every page switch, see {@link
 * BookEditScreenMixin} - gets spoken twice. Skip the full re-read exactly once per change;
 * narration triggered by anything else (gaining focus) narrates normally. Mirrors {@link
 * EditBoxMixin}'s own version of this same fix.
 */
@Mixin(MultiLineEditBox.class)
public abstract class MultiLineEditBoxMixin {
	@Shadow
	@Final
	private MultilineTextField textField;

	@Inject(method = "updateWidgetNarration", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$skipRedundantFullNarration(NarrationElementOutput output, CallbackInfo ci) {
		if (((MultilineTextFieldExt) textField).unitedMinecraft$consumeEditedFlag()) {
			ci.cancel();
		}
	}
}
