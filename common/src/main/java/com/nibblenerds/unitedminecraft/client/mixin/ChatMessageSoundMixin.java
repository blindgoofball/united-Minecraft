package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ChatMessageSound;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code narrateChatMessage} runs once for every player or disguised chat message that is
 * actually shown, whatever the narrator mode, so it is where the arrival cue belongs.
 */
@Mixin(ChatListener.class)
public class ChatMessageSoundMixin {
	@Inject(method = "narrateChatMessage", at = @At("HEAD"))
	private void unitedMinecraft$playChatSound(ChatType.Bound bound, Component message, CallbackInfo ci) {
		ChatMessageSound.onMessage(bound);
	}
}
