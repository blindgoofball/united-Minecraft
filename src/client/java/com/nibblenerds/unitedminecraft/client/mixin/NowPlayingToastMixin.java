package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Narrates "Now playing: &lt;song&gt;" - vanilla shows this purely visually. {@code
 * getNowPlayingString} is recomputed every render frame while the toast is visible, not
 * just once when a track starts, so this relies entirely on {@link
 * ToastNarrationController}'s same-text dedupe to avoid repeating it every frame.
 */
@Mixin(NowPlayingToast.class)
public class NowPlayingToastMixin {
	@Inject(method = "getNowPlayingString", at = @At("RETURN"))
	private static void unitedMinecraft$narrate(String songName, CallbackInfoReturnable<Component> cir) {
		ToastNarrationController.narrate(cir.getReturnValue());
	}
}
