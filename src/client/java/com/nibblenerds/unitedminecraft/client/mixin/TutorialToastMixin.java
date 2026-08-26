package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates first-launch tutorial hints (move with WASD, open your inventory, etc.) -
 * vanilla shows these purely visually. Neither constructor overload delegates to the
 * other, so both need their own injection.
 */
@Mixin(TutorialToast.class)
public class TutorialToastMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/gui/Font;"
			+ "Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;"
			+ "Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;Z)V",
			at = @At("TAIL"))
	private void unitedMinecraft$narrate(Font font, TutorialToast.Icons icon, Component title, Component description,
			boolean progressable, CallbackInfo ci) {
		narrate(title, description);
	}

	@Inject(method = "<init>(Lnet/minecraft/client/gui/Font;"
			+ "Lnet/minecraft/client/gui/components/toasts/TutorialToast$Icons;"
			+ "Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;ZI)V",
			at = @At("TAIL"))
	private void unitedMinecraft$narrateTimed(Font font, TutorialToast.Icons icon, Component title, Component description,
			boolean progressable, int timeToDisplayMs, CallbackInfo ci) {
		narrate(title, description);
	}

	private static void narrate(Component title, Component description) {
		Component message = description.getString().isBlank() ? title : title.copy().append(". ").append(description);
		ToastNarrationController.narrate(message);
	}
}
