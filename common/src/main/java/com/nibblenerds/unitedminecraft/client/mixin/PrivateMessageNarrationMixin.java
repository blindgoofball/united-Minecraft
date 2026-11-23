package com.nibblenerds.unitedminecraft.client.mixin;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla narrates a private message ({@code /msg}, {@code /tell}, {@code /w}, {@code /teammsg})
 * with the same "says" narration as ordinary chat, so it cannot be told from a public message by
 * ear. Narrating it with its on-screen decoration ("X whispers to you: ...", "You whisper to X:
 * ...", "[Team] ...") is how the chat screen already reads it.
 */
@Mixin(ChatListener.class)
public class PrivateMessageNarrationMixin {
	@Redirect(
			method = "narrateChatMessage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/chat/ChatType$Bound;decorateNarration(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"))
	private Component unitedMinecraft$narratePrivateMessageAsSuch(ChatType.Bound bound, Component message) {
		if (isPrivate(bound)) {
			return bound.decorate(message);
		}
		return bound.decorateNarration(message);
	}

	private static boolean isPrivate(ChatType.Bound bound) {
		return bound.chatType().is(ChatType.MSG_COMMAND_INCOMING)
				|| bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING)
				|| bound.chatType().is(ChatType.TEAM_MSG_COMMAND_INCOMING)
				|| bound.chatType().is(ChatType.TEAM_MSG_COMMAND_OUTGOING);
	}
}
