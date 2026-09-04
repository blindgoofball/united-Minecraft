package com.nibblenerds.unitedminecraft.client;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * Rebind screen for every {@link ClientKeyBindings} action - one {@link ObjectSelectionList}
 * row per action (matching {@link SoundGlossaryScreen}'s pattern, not {@link SettingsScreen}'s
 * hand-rolled rows, since a flat scrolling list is the more compact, more vanilla-like fit for
 * this many entries), reached from {@link SettingsScreen}.
 *
 * <p>Capture works by committing on <b>release</b> of whichever key was pressed most recently,
 * not on press - see {@link #keyPressed} and {@link #keyReleased}. That's what lets a bare key
 * (including a bare modifier, like {@link ClientKeyBindings#BUILD_PLACE}'s default of Right
 * Ctrl) be bound on purpose while a chord like Ctrl+G still captures correctly, with no
 * allow-list or special-casing needed: pressing Ctrl sets it as the pending key; pressing G
 * while Ctrl's still down overwrites the pending key to G (with Ctrl's bit still in its
 * modifiers); releasing G (the common case) commits {@code (G, CTRL)}. Releasing Ctrl first
 * instead just doesn't match the pending key and is ignored, still listening for G's release.
 *
 * <p>Escape and Backspace are reserved control keys during listening, handled on press rather
 * than through that pending/release flow - neither can be bound to an action, same as vanilla's
 * own Controls screen reserving Escape.
 */
public final class KeybindScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int TOP_MARGIN = 32;
	private static final int BOTTOM_MARGIN = 36;

	private KeybindList list;

	private KeybindAction listeningAction;
	private Integer pendingKey;
	private int pendingModifiers;

	private KeybindAction conflictEditing;
	private Keybind conflictCandidate;
	private KeybindAction conflictOther;

	public KeybindScreen() {
		super(Component.translatable("united_minecraft.keybind_screen.title"));
	}

	@Override
	protected void init() {
		int top = TOP_MARGIN;
		int bottom = this.height - BOTTOM_MARGIN;
		list = new KeybindList(this.minecraft, this.font, this.width, Math.max(bottom - top, ROW_HEIGHT), top, ROW_HEIGHT);
		addRenderableWidget(list);

		int buttonWidth = 150;
		int spacing = 8;
		int y = this.height - BOTTOM_MARGIN + 8;
		int totalWidth = buttonWidth * 3 + spacing * 2;
		int x = this.width / 2 - totalWidth / 2;
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.keybind_screen.reset"),
				button -> resetSelected())
				.bounds(x, y, buttonWidth, ROW_HEIGHT)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.keybind_screen.reset_all"),
				button -> resetAll())
				.bounds(x + buttonWidth + spacing, y, buttonWidth, ROW_HEIGHT)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("united_minecraft.settings_screen.done"),
				button -> onClose())
				.bounds(x + (buttonWidth + spacing) * 2, y, buttonWidth, ROW_HEIGHT)
				.build());
	}

	@Override
	public void onClose() {
		KeybindConfig.save();
		this.minecraft.gui.setScreen(new SettingsScreen());
	}

	private void resetSelected() {
		KeybindList.Entry entry = list.getSelected();
		if (entry == null || entry.action() == null) {
			return;
		}
		entry.action().resetToDefault();
		applyChange();
		minecraft.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.keybind_screen.reset_done", actionLabel(entry.action())));
	}

	private void resetAll() {
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			action.resetToDefault();
		}
		applyChange();
		minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.keybind_screen.reset_all_done"));
	}

	private void applyChange() {
		ClientKeyBindings.rebuildIndex();
		KeybindConfig.save();
	}

	private void startListening(KeybindAction action) {
		listeningAction = action;
		pendingKey = null;
		pendingModifiers = 0;
		minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.keybind_screen.listening"));
	}

	private void cancelListening() {
		listeningAction = null;
		pendingKey = null;
		minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.keybind_screen.cancelled"));
	}

	private void commitUnbind() {
		KeybindAction action = listeningAction;
		listeningAction = null;
		pendingKey = null;
		action.setCurrent(Keybind.UNBOUND);
		applyChange();
		minecraft.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.keybind_screen.unbound_done", actionLabel(action)));
	}

	private void commitCandidate(Keybind candidate) {
		KeybindAction action = listeningAction;
		listeningAction = null;
		pendingKey = null;

		KeybindAction conflicting = findConflict(action, candidate);
		if (conflicting == null) {
			applyCandidate(action, candidate);
			return;
		}
		conflictEditing = action;
		conflictCandidate = candidate;
		conflictOther = conflicting;
		minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.keybind_screen.conflict",
				actionLabel(conflicting), candidate.describe()));
	}

	private void confirmConflict() {
		KeybindAction action = conflictEditing;
		Keybind candidate = conflictCandidate;
		KeybindAction other = conflictOther;
		conflictEditing = null;
		conflictCandidate = null;
		conflictOther = null;
		other.setCurrent(Keybind.UNBOUND);
		applyCandidate(action, candidate);
	}

	private void cancelConflict() {
		conflictEditing = null;
		conflictCandidate = null;
		conflictOther = null;
		minecraft.getNarrator().saySystemNow(Component.translatable("united_minecraft.keybind_screen.cancelled"));
	}

	private void applyCandidate(KeybindAction action, Keybind candidate) {
		action.setCurrent(candidate);
		applyChange();
		minecraft.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.keybind_screen.bound", actionLabel(action), candidate.describe()));
	}

	/**
	 * Only actions that could ever both actually be reachable at the same time as {@code
	 * editing}'s really conflict. {@link KeybindContext#canOverlap} handles that at the
	 * tick-loop-mode level; within two {@link KeybindContext#CONTAINER_SCREEN} actions specifically,
	 * {@link ContainerScope} refines it further - e.g. {@code recipe_book_search} and {@code
	 * container_describe_slot} share a context but can never both fire (Recipe Book search vs.
	 * ordinary slot navigation are different, mutually exclusive screen sections), so scoping
	 * keeps that from being flagged as a false-positive conflict. See {@link ContainerScope}'s
	 * own doc for why {@code null} scope still conflicts with everything.
	 */
	private static KeybindAction findConflict(KeybindAction editing, Keybind candidate) {
		if (candidate.isUnbound()) {
			return null;
		}
		for (KeybindAction other : ClientKeyBindings.allActions()) {
			if (other != editing && other.current().equals(candidate)
					&& KeybindContext.canOverlap(other.context(), editing.context())
					&& scopesCanOverlap(other, editing)) {
				return other;
			}
		}
		return null;
	}

	/** {@code null} scope means "reachable from more than one place, treat as universally conflicting" - see {@link ContainerScope}. */
	private static boolean scopesCanOverlap(KeybindAction a, KeybindAction b) {
		return a.scope() == null || b.scope() == null || a.scope() == b.scope();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (conflictEditing != null) {
			if (event.isEscape()) {
				cancelConflict();
			} else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				confirmConflict();
			}
			return true;
		}
		if (listeningAction != null) {
			if (event.isEscape()) {
				cancelListening();
			} else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
				commitUnbind();
			} else {
				pendingKey = event.key();
				pendingModifiers = event.modifiers();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (listeningAction != null && pendingKey != null && event.key() == pendingKey) {
			commitCandidate(new Keybind(pendingKey, pendingModifiers));
			return true;
		}
		return super.keyReleased(event);
	}

	private static Component actionLabel(KeybindAction action) {
		return Component.translatable("united_minecraft.keybind." + action.id());
	}

	private final class KeybindList extends ObjectSelectionList<KeybindList.Entry> {
		private final Font font;

		KeybindList(Minecraft minecraft, Font font, int width, int height, int y0, int itemHeight) {
			super(minecraft, width, height, y0, itemHeight);
			this.font = font;
			// Grouped by category rather than raw declaration order - ClientKeyBindings' fields
			// aren't declared in contiguous category blocks (e.g. RESET_ROTATION_TO_NORTH sits
			// between two NARRATION actions but is itself MOVEMENT_AND_MODES), so without this
			// sort a category could get more than one header scattered through the list. A
			// stable sort keeps each category's own declaration order intact.
			KeybindAction[] actions = ClientKeyBindings.allActions();
			Arrays.sort(actions, Comparator.comparingInt(action -> action.category().ordinal()));
			KeybindCategory lastCategory = null;
			for (KeybindAction action : actions) {
				if (action.category() != lastCategory) {
					lastCategory = action.category();
					addEntry(new Entry(lastCategory));
				}
				addEntry(new Entry(action));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(360, this.width - 20);
		}

		/**
		 * One row - either a rebindable {@link #action}, or (when {@code action} is {@code null})
		 * a category header, purely a visual/narration divider between {@link KeybindCategory}
		 * groups with no rebind behavior of its own.
		 */
		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final KeybindAction action;
			private final KeybindCategory header;

			Entry(KeybindAction action) {
				this.action = action;
				this.header = null;
			}

			Entry(KeybindCategory header) {
				this.action = null;
				this.header = header;
			}

			KeybindAction action() {
				return action;
			}

			private Component label() {
				if (action == null) {
					return header.label();
				}
				if (KeybindScreen.this.listeningAction == action) {
					return Component.translatable("united_minecraft.keybind_screen.rebind_prompt", actionLabel(action));
				}
				if (KeybindScreen.this.conflictEditing == action) {
					return Component.translatable("united_minecraft.keybind_screen.conflict_prompt",
							actionLabel(action), KeybindScreen.this.conflictCandidate.describe());
				}
				return Component.translatable(
						"united_minecraft.keybind_screen.row", actionLabel(action), action.current().describe());
			}

			@Override
			public void extractContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovering, float partialTick) {
				int textX = getContentX() + (action == null ? 0 : 2);
				int textY = getContentY() + (getContentHeight() - font.lineHeight) / 2;
				String text = label().getString();
				guiGraphics.text(font, font.plainSubstrByWidth(text, getContentWidth() - 4), textX, textY, -1);
			}

			@Override
			public Component getNarration() {
				return label();
			}

			@Override
			public boolean keyPressed(KeyEvent event) {
				if (action != null && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
					KeybindScreen.this.startListening(action);
					return true;
				}
				return false;
			}
		}
	}
}
