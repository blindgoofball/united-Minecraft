package com.nibblenerds.unitedminecraft.client.mixin;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Many servers (Hypixel's minigames/Skyblock included) drive a persistent action-bar HUD -
 * health, mana, cooldowns - by resending the same {@code overlay} system chat packet every
 * tick or two just to keep it on screen. Vanilla's {@link ChatListener#handleOverlay}
 * narrates every one of those resends unconditionally, which turns a static readout that
 * hasn't actually changed into a message repeated multiple times a second. This narrates
 * only when the plain text differs from the last overlay narrated, matching what a sighted
 * player would perceive as an update rather than a redraw.
 */
@Mixin(ChatListener.class)
public class ChatOverlayNarrationMixin {
	@Unique
	private String unitedMinecraft$lastOverlayText;

	@Redirect(
			method = "handleOverlay",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/GameNarrator;saySystemQueued(Lnet/minecraft/network/chat/Component;)V"))
	private void unitedMinecraft$skipUnchangedOverlay(GameNarrator narrator, Component message) {
		String text = message.getString();
		if (!text.equals(this.unitedMinecraft$lastOverlayText)) {
			this.unitedMinecraft$lastOverlayText = text;
			narrator.saySystemQueued(message);
		}
	}
}
