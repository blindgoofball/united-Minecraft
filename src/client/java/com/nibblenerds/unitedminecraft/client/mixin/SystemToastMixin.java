package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates system toasts (low disk space, a chunk failing to save, world/pack access
 * failures, etc.) - vanilla shows these purely visually. Targets the private {@code
 * update(Component, Component)} rather than the public constructor, since both a brand
 * new toast <i>and</i> {@code addOrUpdate}'s in-place refresh of an existing one (a
 * persisting warning re-checked periodically) funnel through this one method - one
 * injection covers both, and {@link ToastNarrationController} skips the repeat if the
 * text hasn't actually changed.
 */
@Mixin(SystemToast.class)
public class SystemToastMixin {
	@Inject(method = "update(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V",
			at = @At("TAIL"))
	private void unitedMinecraft$narrate(Component title, Component description, CallbackInfo ci) {
		Component message = description.getString().isBlank() ? title : title.copy().append(". ").append(description);
		ToastNarrationController.narrate(message);
	}
}
