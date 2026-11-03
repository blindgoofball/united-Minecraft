package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.client.access.AdvancementTabAccess;
import com.nibblenerds.unitedminecraft.client.access.AdvancementWidgetAccess;
import com.nibblenerds.unitedminecraft.client.access.AdvancementsScreenAccess;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Keyboard navigation of the advancements screen, which vanilla draws by hand - the tabs and the
 * nodes are not widgets, so nothing on it can take focus or be narrated except the Done button.
 * This treats each tab as a tree view: Up/Down walk it top to bottom with every branch open,
 * Right enters a node's first child, Left returns to its parent, Home/End jump to the ends, and
 * Tab/Shift+Tab change tab. Each stop narrates the advancement the way vanilla's hover box
 * shows it - title, whether it is done, progress, description - and the screen scrolls to it and
 * shows that same hover box (see {@link com.nibblenerds.unitedminecraft.client.mixin.AdvancementTabMixin}).
 *
 * <p>The arrows and Tab reuse the container-menu actions, so a rebind there carries over. The
 * selection is remembered by advancement id, not by widget, because vanilla throws every tab and
 * widget away and rebuilds them whenever the server sends an update.
 *
 * <p>An advancement that is hidden and not yet earned is left out entirely, exactly as vanilla
 * leaves its node undrawn and unhoverable; its visible descendants, if any, hang from the nearest
 * visible ancestor.
 */
public final class AdvancementsController {
	private static final Keybind HOME = new Keybind(InputConstants.KEY_HOME, 0);
	private static final Keybind END = new Keybind(InputConstants.KEY_END, 0);

	/** The advancement the keyboard is on, or null before the first move on this tab. */
	private static @Nullable Identifier selectedId;
	private static @Nullable AdvancementsScreen announcedScreen;

	private AdvancementsController() {
	}

	/** One stop in a tab's list. */
	private record Entry(AdvancementWidget widget, int depth) {
	}

	static void onScreenInit(AdvancementsScreen screen) {
		// A same-instance re-init (a window resize) must not announce the tab all over again.
		if (screen == announcedScreen) {
			return;
		}
		announcedScreen = screen;
		AdvancementTab tab = selectedTab(screen);
		if (tab != null) {
			// Queued, so it follows the screen's own title instead of cutting it off.
			Minecraft.getInstance().getNarrator().saySystemQueued(describeTab(tab));
		}
	}

	static void onScreenRemoved() {
		selectedId = null;
		announcedScreen = null;
	}

	/**
	 * @return {@code true} to let vanilla handle the key, {@code false} if consumed - the same
	 *         polarity as {@link MenuAccessibilityController#handleKey}
	 */
	static boolean handleKey(AdvancementsScreen screen, KeyEvent event) {
		if (ClientKeyBindings.CONTAINER_SWITCH_SECTION_NEXT.current().matches(event)) {
			switchTab(screen, 1);
			return false;
		}
		if (ClientKeyBindings.CONTAINER_SWITCH_SECTION_PREV.current().matches(event)) {
			switchTab(screen, -1);
			return false;
		}

		AdvancementTab tab = selectedTab(screen);
		if (tab == null) {
			return true;
		}
		List<Entry> entries = entries(tab);
		if (entries.isEmpty()) {
			return true;
		}
		int[] depths = entries.stream().mapToInt(Entry::depth).toArray();
		int pos = positionOf(entries, selectedId);

		if (ClientKeyBindings.CONTAINER_NAV_DOWN.current().matches(event)) {
			if (pos == entries.size() - 1) {
				say(Component.translatable("united_minecraft.narrate.advancements_end"));
			} else {
				select(tab, entries, pos + 1);
			}
		} else if (ClientKeyBindings.CONTAINER_NAV_UP.current().matches(event)) {
			if (pos <= 0) {
				// Before anything is selected, Up lands on the first entry rather than refusing.
				if (pos < 0) {
					select(tab, entries, 0);
				} else {
					say(Component.translatable("united_minecraft.narrate.advancements_start"));
				}
			} else {
				select(tab, entries, pos - 1);
			}
		} else if (ClientKeyBindings.CONTAINER_NAV_RIGHT.current().matches(event)) {
			int child = pos < 0 ? 0 : AdvancementTreeOrder.firstChildPosition(depths, pos);
			if (child < 0) {
				say(Component.translatable("united_minecraft.narrate.advancements_no_children"));
			} else {
				select(tab, entries, child);
			}
		} else if (ClientKeyBindings.CONTAINER_NAV_LEFT.current().matches(event)) {
			int parent = pos < 0 ? 0 : AdvancementTreeOrder.parentPosition(depths, pos);
			if (parent < 0) {
				say(Component.translatable("united_minecraft.narrate.advancements_top"));
			} else {
				select(tab, entries, parent);
			}
		} else if (HOME.matches(event)) {
			select(tab, entries, 0);
		} else if (END.matches(event)) {
			select(tab, entries, entries.size() - 1);
		} else {
			return true;
		}
		return false;
	}

	/**
	 * The selected advancement's widget if it lives in {@code tab} and the screen is open, for the
	 * tab's tick to show as hovered.
	 */
	public static @Nullable AdvancementWidget selectionIn(AdvancementTab tab) {
		if (selectedId == null || !(Minecraft.getInstance().gui.screen() instanceof AdvancementsScreen screen)
				|| selectedTab(screen) != tab) {
			return null;
		}
		for (AdvancementWidget widget : ((AdvancementTabAccess) tab).unitedMinecraft$getWidgets()) {
			if (widget.getAdvancement().id().equals(selectedId)) {
				return widget;
			}
		}
		return null;
	}

	private static @Nullable AdvancementTab selectedTab(AdvancementsScreen screen) {
		return ((AdvancementsScreenAccess) screen).unitedMinecraft$getSelectedTab();
	}

	private static void switchTab(AdvancementsScreen screen, int step) {
		List<AdvancementTab> tabs = new ArrayList<>(((AdvancementsScreenAccess) screen).unitedMinecraft$getTabs());
		AdvancementTab current = selectedTab(screen);
		ClientAdvancements advancements = Minecraft.getInstance().getConnection() == null
				? null : Minecraft.getInstance().getConnection().getAdvancements();
		if (tabs.isEmpty() || current == null || advancements == null) {
			return;
		}
		if (tabs.size() == 1) {
			say(describeTab(current));
			return;
		}
		int next = Math.floorMod(tabs.indexOf(current) + step, tabs.size());
		selectedId = null;
		// Vanilla's own right-click path: tells the server which tab is open and calls back into
		// the screen to change its selection, synchronously.
		advancements.setSelectedTab(tabs.get(next).getRootAdvancement(), true);
		AdvancementTab now = selectedTab(screen);
		if (now != null) {
			say(describeTab(now));
		}
	}

	private static void select(AdvancementTab tab, List<Entry> entries, int pos) {
		AdvancementWidget widget = entries.get(pos).widget();
		selectedId = widget.getAdvancement().id();
		scrollTo(tab, widget);
		say(describe(widget, pos + 1, entries.size()));
	}

	/** Centres {@code widget} in the tab's 234 x 113 view; the tab's own clamp keeps it inside the tree. */
	private static void scrollTo(AdvancementTab tab, AdvancementWidget widget) {
		AdvancementTabAccess access = (AdvancementTabAccess) tab;
		double targetX = 117 - (widget.getX() + 13);
		double targetY = 56 - (widget.getY() + 13);
		tab.scroll(targetX - access.unitedMinecraft$getScrollX(), targetY - access.unitedMinecraft$getScrollY());
	}

	private static void say(Component message) {
		Minecraft.getInstance().getNarrator().saySystemNow(message);
	}

	private static boolean isDone(AdvancementWidget widget) {
		AdvancementProgress progress = ((AdvancementWidgetAccess) widget).unitedMinecraft$getProgress();
		return progress != null && progress.isDone();
	}

	/** Vanilla's own rule for whether a node is drawn and can be hovered. */
	private static boolean isVisible(AdvancementWidget widget) {
		return !widget.getDisplay().hidden() || isDone(widget);
	}

	private static int positionOf(List<Entry> entries, @Nullable Identifier id) {
		if (id == null) {
			return -1;
		}
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).widget().getAdvancement().id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private static List<Entry> entries(AdvancementTab tab) {
		List<AdvancementWidget> visible = new ArrayList<>();
		for (AdvancementWidget widget : ((AdvancementTabAccess) tab).unitedMinecraft$getWidgets()) {
			if (isVisible(widget)) {
				visible.add(widget);
			}
		}
		Map<AdvancementWidget, Integer> indexOf = new IdentityHashMap<>();
		for (int i = 0; i < visible.size(); i++) {
			indexOf.put(visible.get(i), i);
		}
		int[] parent = new int[visible.size()];
		int[] key = new int[visible.size()];
		for (int i = 0; i < parent.length; i++) {
			AdvancementWidget ancestor = ((AdvancementWidgetAccess) visible.get(i)).unitedMinecraft$getParent();
			// A hidden, unearned ancestor is skipped, so the child hangs from the next one up.
			while (ancestor != null && !indexOf.containsKey(ancestor)) {
				ancestor = ((AdvancementWidgetAccess) ancestor).unitedMinecraft$getParent();
			}
			parent[i] = ancestor == null ? -1 : indexOf.get(ancestor);
			key[i] = visible.get(i).getY();
		}
		AdvancementTreeOrder.Order order = AdvancementTreeOrder.preorder(parent, key);
		List<Entry> entries = new ArrayList<>(order.size());
		for (int i = 0; i < order.size(); i++) {
			entries.add(new Entry(visible.get(order.indices()[i]), order.depths()[i]));
		}
		return entries;
	}

	private static Component describeTab(AdvancementTab tab) {
		List<Entry> entries = entries(tab);
		int done = (int) entries.stream().filter(e -> isDone(e.widget())).count();
		return Component.translatable("united_minecraft.narrate.advancements_tab", tab.getTitle(), done, entries.size());
	}

	private static Component describe(AdvancementWidget widget, int position, int total) {
		DisplayInfo display = widget.getDisplay();
		AdvancementProgress progress = ((AdvancementWidgetAccess) widget).unitedMinecraft$getProgress();
		Component title = switch (display.type()) {
			case CHALLENGE -> Component.translatable("united_minecraft.narrate.advancement_titled",
					display.title(), Component.translatable("united_minecraft.narrate.advancement_type.challenge"));
			case GOAL -> Component.translatable("united_minecraft.narrate.advancement_titled",
					display.title(), Component.translatable("united_minecraft.narrate.advancement_type.goal"));
			case TASK -> display.title();
		};
		Component progressText = progress == null ? null : progress.getProgressText();
		Component status;
		if (isDone(widget)) {
			status = Component.translatable("united_minecraft.narrate.advancement_completed");
		} else if (progressText != null) {
			status = Component.translatable("united_minecraft.narrate.advancement_in_progress", progressText);
		} else {
			status = Component.translatable("united_minecraft.narrate.advancement_not_completed");
		}
		return Component.translatable("united_minecraft.narrate.advancement", title, status, display.description(),
				position, total);
	}
}
