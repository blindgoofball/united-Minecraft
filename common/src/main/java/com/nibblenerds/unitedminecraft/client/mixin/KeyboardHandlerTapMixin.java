package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.client.ClientKeyBindings;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Tells {@link ClientKeyBindings} about every key press the moment it happens, so a tap too quick
 * for its once-a-tick polling still registers - see {@link ClientKeyBindings#recordKeyPress}.
 * Observes only; vanilla's own handling of the key is untouched.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerTapMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void unitedMinecraft$recordKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		// Presses only - a held key's auto-repeat is still the same press.
		if (action == InputConstants.PRESS && handle == minecraft.getWindow().handle()) {
			ClientKeyBindings.recordKeyPress(event.key(), minecraft.gui.screen() != null);
		}
	}
}
