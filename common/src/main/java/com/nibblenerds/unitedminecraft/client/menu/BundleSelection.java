package com.nibblenerds.unitedminecraft.client.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import com.nibblenerds.unitedminecraft.client.ItemDescriptions;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;

/**
 * Steps the selected item of a bundle in the focused slot, the keyboard counterpart of scrolling
 * the mouse wheel over it: {@link ClientKeyBindings#CONTAINER_PAGE_NEXT} and {@link
 * ClientKeyBindings#CONTAINER_PAGE_PREV}, Page Down and Page Up by default. The selected item is
 * the one a right-click, or Ctrl+Enter, takes out, and the one the player hears named.
 *
 * <p>Done exactly as vanilla's own mouse handling does it: the selection is updated on the local
 * stack and reported to the server in a select-bundle-item packet. The game has no keyboard way to
 * do this.
 *
 * <p>Not in the Creative inventory: its slots stand in for the player's own, so the index the server
 * needs is not the one the screen has.
 */
public final class BundleSelection {
	private BundleSelection() {
	}

	/** @return {@code false} if the key was consumed, the polarity of every screen key hook. */
	public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		int step;
		if (ClientKeyBindings.CONTAINER_PAGE_NEXT.current().matches(event)) {
			step = 1;
		} else if (ClientKeyBindings.CONTAINER_PAGE_PREV.current().matches(event)) {
			step = -1;
		} else {
			return true;
		}
		if (screen instanceof CreativeModeInventoryScreen) {
			return true;
		}
		SlotWidget widget = SlotChangeNarration.focusedSlot(screen);
		Minecraft client = Minecraft.getInstance();
		if (widget == null || client.player == null || client.getConnection() == null) {
			return true;
		}

		Slot slot = widget.slot();
		ItemStack stack = slot.getItem();
		if (!stack.has(DataComponents.BUNDLE_CONTENTS)) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.bundle_not_a_bundle"));
			return false;
		}
		int selectable = BundleItem.getNumberOfItemsToShow(stack);
		if (selectable == 0) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.bundle_empty_short"));
			return false;
		}
		int current = BundleItem.getSelectedItemIndex(stack);
		int next = nextIndex(current, step, selectable);
		if (next != current) {
			BundleItem.toggleSelectedItem(stack, next);
			client.getConnection().send(new ServerboundSelectBundleItemPacket(slot.index, next));
		}
		ItemStackTemplate selected = BundleItem.getSelectedItem(stack);
		if (selected != null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.bundle_selected",
					ItemDescriptions.describe(selected.create(), client.player, false, false),
					BundleItem.getSelectedItemIndex(stack) + 1, selectable));
		}
		return false;
	}

	/**
	 * The index {@code step} places on from {@code current}, wrapping. Nothing selected yet
	 * ({@code current} below zero) starts at the first for a step forward and the last for a step back.
	 */
	static int nextIndex(int current, int step, int selectable) {
		return current < 0 ? (step > 0 ? 0 : selectable - 1) : Math.floorMod(current + step, selectable);
	}
}
