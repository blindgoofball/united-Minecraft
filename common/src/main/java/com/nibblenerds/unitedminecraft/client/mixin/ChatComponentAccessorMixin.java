package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.ChatComponentAccess;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/**
 * Exposes {@code ChatComponent}'s private {@code allMessages}/{@code trimmedMessages} fields
 * - vanilla only renders them, with no public way to read a past chat entry's content or find
 * where it sits in the wrapped-line list, which {@link
 * com.nibblenerds.unitedminecraft.client.mixin.ChatScreenMixin}'s Page Up/Down chat-history
 * cycling needs both for.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentAccessorMixin implements ChatComponentAccess {
	@Shadow
	private List<GuiMessage> allMessages;

	@Shadow
	private List<GuiMessage.Line> trimmedMessages;

	@Override
	public List<GuiMessage> unitedMinecraft$getAllMessages() {
		return allMessages;
	}

	@Override
	public List<GuiMessage.Line> unitedMinecraft$getTrimmedMessages() {
		return trimmedMessages;
	}
}
