package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates a friend-came-online-style toast - vanilla shows these purely visually.
 * Neither constructor overload delegates to the other, so both need their own injection.
 */
@Mixin(FriendToast.class)
public class FriendToastMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/gui/Font;"
			+ "Lnet/minecraft/world/item/component/ResolvableProfile;"
			+ "Lnet/minecraft/network/chat/Component;)V",
			at = @At("TAIL"))
	private void unitedMinecraft$narrate(Font font, ResolvableProfile profile, Component message, CallbackInfo ci) {
		ToastNarrationController.narrate(message);
	}

	@Inject(method = "<init>(Lnet/minecraft/client/gui/Font;"
			+ "Lnet/minecraft/world/item/component/ResolvableProfile;"
			+ "Lnet/minecraft/network/chat/Component;J)V",
			at = @At("TAIL"))
	private void unitedMinecraft$narrateTimed(Font font, ResolvableProfile profile, Component message,
			long displayTimeMs, CallbackInfo ci) {
		ToastNarrationController.narrate(message);
	}
}
