package com.nibblenerds.unitedminecraft.client.mixin;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

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
 *
 * <p>The same text is narrated again once the previous one has had time to fade off screen,
 * though - vanilla shows an overlay for {@link #OVERLAY_VISIBLE_MILLIS} after each send. A HUD
 * kept alive by resends never gets that far, so it still reads once; but a genuine repeat after
 * a gap (vanilla's own "You may not rest now, there are monsters nearby" on a second try at a
 * bed, a second "Chest is locked") was otherwise silent for the rest of the session.
 */
@Mixin(ChatListener.class)
public class ChatOverlayNarrationMixin {
	/** Vanilla's {@code Hud#setOverlayMessage} shows an overlay for 60 ticks. */
	@Unique
	private static final long OVERLAY_VISIBLE_MILLIS = 3000;

	@Unique
	private String unitedMinecraft$lastOverlayText;
	/** When any overlay was last received - each resend restarts vanilla's display timer. */
	@Unique
	private long unitedMinecraft$lastOverlayMillis;

	@Redirect(
			method = "handleOverlay",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/GameNarrator;saySystemQueued(Lnet/minecraft/network/chat/Component;)V"))
	private void unitedMinecraft$skipUnchangedOverlay(GameNarrator narrator, Component message) {
		String text = message.getString();
		long now = Util.getMillis();
		boolean stillShowing = now - this.unitedMinecraft$lastOverlayMillis < OVERLAY_VISIBLE_MILLIS;
		this.unitedMinecraft$lastOverlayMillis = now;
		if (!stillShowing || !text.equals(this.unitedMinecraft$lastOverlayText)) {
			this.unitedMinecraft$lastOverlayText = text;
			narrator.saySystemQueued(message);
		}
	}
}
