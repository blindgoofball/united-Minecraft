package com.nibblenerds.unitedminecraft.client.access;

import java.util.List;

import net.minecraft.client.multiplayer.chat.GuiMessage;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.ChatComponentAccessorMixin}
 * to expose {@code ChatComponent}'s private message lists, since the compiler has no way to
 * know about members a mixin injects into a vanilla class otherwise.
 */
public interface ChatComponentAccess {
	/** Every received chat entry, index 0 = most recently added, capped at 100. */
	List<GuiMessage> unitedMinecraft$getAllMessages();

	/**
	 * Every entry above, split into individually word-wrapped render lines, index 0 = the
	 * bottommost (most recent) rendered line. A single multi-line entry occupies a
	 * contiguous run here; {@link GuiMessage.Line#parent()} identifies which entry a line
	 * belongs to.
	 */
	List<GuiMessage.Line> unitedMinecraft$getTrimmedMessages();
}
