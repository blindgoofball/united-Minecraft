package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
 *
 * <p>Ctrl+Page Up/Down and Ctrl+Enter add keyboard access to whatever a sighted player would
 * normally reach by clicking chat text directly - a link, a {@code run_command}/{@code
 * suggest_command} span, a copy-to-clipboard prompt, and so on. Ctrl+Page Up/Down cycles which
 * clickable span in the currently focused message (see {@link #unitedMinecraft$historyIndex}) is
 * selected, narrating its text; Ctrl+Enter activates it via the same private {@code
 * handleComponentClicked} vanilla's own {@code mouseClicked} calls, so it gets vanilla's own
 * per-{@link ClickEvent} behavior for free - including the "Are you sure you want to open this
 * link" confirmation screen for URLs, rather than this mod opening one unprompted.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@Shadow
	private boolean handleComponentClicked(Style style, boolean insertion) {
		throw new AssertionError();
	}

	@Unique
	private int unitedMinecraft$historyIndex;

	// Which clickable span within the currently focused message Ctrl+Page Up/Down has selected -
	// see #unitedMinecraft$cycleLink and #unitedMinecraft$activateFocusedLink.
	@Unique
	private int unitedMinecraft$linkIndex;

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
		unitedMinecraft$linkIndex = 0;
		Minecraft.getInstance().gui.hud.getChat().resetChatScroll();
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$browseChatHistory(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		int key = event.key();
		if (event.hasControlDown() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
			unitedMinecraft$activateFocusedLink();
			cir.setReturnValue(true);
			return;
		}
		if (event.hasControlDown() && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
			unitedMinecraft$cycleLink(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
			cir.setReturnValue(true);
			return;
		}
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
		unitedMinecraft$linkIndex = 0;

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

	/**
	 * Runs whichever clickable span {@link #unitedMinecraft$linkIndex} currently points at in the
	 * focused history message, via the same {@link #handleComponentClicked} vanilla's own click
	 * handling uses - so a URL still gets vanilla's "Are you sure?" confirmation screen, a {@code
	 * run_command} span still runs the command, and so on, with no separate handling needed here
	 * per {@link ClickEvent} type.
	 */
	@Unique
	private void unitedMinecraft$activateFocusedLink() {
		List<ChatScreenMixin.ChatLink> links = unitedMinecraft$focusedLinks();
		if (links == null) {
			return;
		}
		if (links.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.chat_no_link"));
			return;
		}
		int index = Mth.clamp(unitedMinecraft$linkIndex, 0, links.size() - 1);
		handleComponentClicked(links.get(index).style(), false);
	}

	/** Moves {@link #unitedMinecraft$linkIndex} to the next/previous clickable span, wrapping, and narrates it. */
	@Unique
	private void unitedMinecraft$cycleLink(int direction) {
		List<ChatScreenMixin.ChatLink> links = unitedMinecraft$focusedLinks();
		if (links == null) {
			return;
		}
		if (links.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.chat_no_link"));
			return;
		}
		unitedMinecraft$linkIndex = Math.floorMod(unitedMinecraft$linkIndex + direction, links.size());
		ChatScreenMixin.ChatLink link = links.get(unitedMinecraft$linkIndex);

		MutableComponent narration = link.text().isBlank()
				? Component.translatable("united_minecraft.narrate.chat_link_unnamed")
				: Component.literal(link.text());
		narration = narration.append(Component.literal(", ")).append(
				Component.translatable("united_minecraft.narrate.chat_link_position", unitedMinecraft$linkIndex + 1, links.size()));
		Minecraft.getInstance().getNarrator().saySystemNow(narration);
	}

	/** The focused history message's clickable spans, or null (already narrated) if there's no history to focus at all. */
	@Unique
	private List<ChatScreenMixin.ChatLink> unitedMinecraft$focusedLinks() {
		List<GuiMessage> messages = ((ChatComponentAccess) Minecraft.getInstance().gui.hud.getChat()).unitedMinecraft$getAllMessages();
		if (messages.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.chat_history_empty"));
			return null;
		}
		int index = Mth.clamp(unitedMinecraft$historyIndex, 0, messages.size() - 1);
		return unitedMinecraft$collectLinks(messages.get(index).content());
	}

	/**
	 * Every distinct {@link ClickEvent}-bearing {@link Style} in {@code content}, in reading
	 * order, paired with the plain text it covers - adjacent runs sharing the identical {@link
	 * ClickEvent} (as a single link's text is usually split into per-format-change chunks by
	 * {@link Component#visit(net.minecraft.network.chat.FormattedText.StyledContentConsumer,
	 * Style)}) are merged into one entry rather than counted as separate links.
	 */
	@Unique
	private static List<ChatScreenMixin.ChatLink> unitedMinecraft$collectLinks(Component content) {
		List<ChatScreenMixin.ChatLink> links = new ArrayList<>();
		content.visit((style, text) -> {
			ClickEvent click = style.getClickEvent();
			if (click != null) {
				if (!links.isEmpty() && click.equals(links.get(links.size() - 1).style().getClickEvent())) {
					ChatScreenMixin.ChatLink previous = links.remove(links.size() - 1);
					links.add(new ChatScreenMixin.ChatLink(previous.style(), previous.text() + text));
				} else {
					links.add(new ChatScreenMixin.ChatLink(style, text));
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return links;
	}

	@Unique
	private record ChatLink(Style style, String text) {
	}
}
