package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.List;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.nibblenerds.unitedminecraft.client.access.ChatComponentAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

/**
 * {@code ChatScreen} narrates its input box's entire current message every time the narration
 * state refreshes (see {@code updateNarrationState}), independent of and in addition to {@link
 * EditBoxMixin}'s own per-keystroke narration - so without this, every character typed in chat
 * gets spoken twice: once as the delta, once as the whole message read from the start. Suppressed
 * here since {@link EditBoxMixin} already covers typing. Recalling a past message with Up/Down
 * still gets a full read, since that's a real full-content change worth announcing in full.
 *
 * <p>Also repurposes Page Up/Down (vanilla scrolls the chat log by a whole page of lines, silently)
 * into narrated, one-message-at-a-time chat history browsing: Page Down moves to a more recent
 * message, Page Up to an older one, and Shift with either jumps straight to the newest or oldest
 * message. The overlay's own scroll position is kept in sync with whichever message is focused
 * (via {@link ChatComponentAccess}, since vanilla only exposes relative, line-count scrolling and
 * has no notion of "the message currently at index N") so the visible text still matches what's
 * being read. The chat log always starts back at the most recent message when the screen opens,
 * regardless of where it was left scrolled to previously.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@Unique
	private int unitedMinecraft$historyIndex;

	@Redirect(
			method = "updateNarrationState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;"))
	private String unitedMinecraft$suppressPerKeystrokeNarration(EditBox instance) {
		return "";
	}

	@Inject(method = "moveInHistory", at = @At("TAIL"))
	private void unitedMinecraft$narrateRecalledMessage(int direction, CallbackInfo ci) {
		String recalled = input.getValue();
		if (!recalled.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(recalled);
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void unitedMinecraft$resetChatHistoryOnOpen(CallbackInfo ci) {
		unitedMinecraft$historyIndex = 0;
		Minecraft.getInstance().gui.hud.getChat().resetChatScroll();
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$browseChatHistory(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		int key = event.key();
		if (key != GLFW.GLFW_KEY_PAGE_UP && key != GLFW.GLFW_KEY_PAGE_DOWN) {
			return;
		}

		ChatComponent chat = Minecraft.getInstance().gui.hud.getChat();
		List<GuiMessage> messages = ((ChatComponentAccess) chat).unitedMinecraft$getAllMessages();
		if (messages.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.chat_history_empty"));
			cir.setReturnValue(true);
			return;
		}

		boolean older = key == GLFW.GLFW_KEY_PAGE_UP;
		int lastIndex = messages.size() - 1;
		int next;
		if (event.hasShiftDown()) {
			next = older ? lastIndex : 0;
		} else {
			next = Mth.clamp(unitedMinecraft$historyIndex + (older ? 1 : -1), 0, lastIndex);
			if (next == unitedMinecraft$historyIndex) {
				// Already at the newest/oldest message - nothing to move to, but still swallow
				// the key so vanilla's own whole-page scroll doesn't also fire underneath us.
				cir.setReturnValue(true);
				return;
			}
		}
		unitedMinecraft$historyIndex = next;

		GuiMessage message = messages.get(next);
		unitedMinecraft$scrollToMessage(chat, message);

		MutableComponent narration = message.content().copy();
		if (messages.size() > 1) {
			narration = narration.append(Component.literal(", ")).append(
					Component.translatable("united_minecraft.narrate.chat_history_position", next + 1, messages.size()));
		}
		Minecraft.getInstance().getNarrator().saySystemNow(narration);
		cir.setReturnValue(true);
	}

	/**
	 * Scrolls the chat overlay so the given entry's text is at the top of the visible page.
	 * {@code trimmedMessages} is already word-wrapped index-0-is-newest-line order - the first
	 * line in it belonging to {@code target} is exactly how many lines of newer messages sit
	 * above it, which is what {@code ChatComponent}'s own line-count-based scroll amount expects.
	 */
	@Unique
	private void unitedMinecraft$scrollToMessage(ChatComponent chat, GuiMessage target) {
		List<GuiMessage.Line> lines = ((ChatComponentAccess) chat).unitedMinecraft$getTrimmedMessages();
		int offset = 0;
		for (GuiMessage.Line line : lines) {
			if (line.parent() == target) {
				break;
			}
			offset++;
		}
		chat.resetChatScroll();
		chat.scrollChat(offset);
	}
}
