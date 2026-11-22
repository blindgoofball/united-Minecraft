package com.nibblenerds.unitedminecraft.client.menu;

import com.nibblenerds.unitedminecraft.client.speech.KeyboardNarration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/**
 * Says the focused slot again when what is in it changes while it stays focused - a count going
 * up or down, a stack picked up or placed, the item swapped. Vanilla narrates a slot when focus
 * moves onto it but nothing prompts it to speak again when something else (a click, a quick-move,
 * the server) changes the stack under the player's focus.
 *
 * <p>This only asks vanilla to narrate; it goes through {@link KeyboardNarration#schedule}, the
 * same single path every other narration uses, and the collector speaks it because the slot's
 * text now differs.
 */
public final class SlotChangeNarration {
	private static Screen lastScreen;
	private static SlotWidget lastSlot;
	private static ItemStack lastStack = ItemStack.EMPTY;

	private SlotChangeNarration() {
	}

	public static void tick(Minecraft client) {
		Screen screen = client.gui.screen();
		SlotWidget slot = screen == null ? null : focusedSlot(screen);
		if (slot == null) {
			reset();
			return;
		}
		ItemStack now = slot.slot().getItem();
		if (screen == lastScreen && slot == lastSlot && !ItemStack.matches(now, lastStack)) {
			KeyboardNarration.schedule(screen);
		}
		lastScreen = screen;
		lastSlot = slot;
		lastStack = now.copy();
	}

	/**
	 * Takes the focused slot as it is now as the one nothing has changed from, for a change the
	 * player made and has already heard about - stepping a bundle's selection, which changes the
	 * stack but is announced on its own.
	 */
	public static void acknowledge(Screen screen) {
		SlotWidget slot = focusedSlot(screen);
		if (slot != null && screen == lastScreen && slot == lastSlot) {
			lastStack = slot.slot().getItem().copy();
		}
	}

	public static void reset() {
		lastScreen = null;
		lastSlot = null;
		lastStack = ItemStack.EMPTY;
	}

	/** The slot widget at the end of the screen's focus path, if the path ends in one. */
	static SlotWidget focusedSlot(Screen screen) {
		GuiEventListener current = screen;
		while (current instanceof ContainerEventHandler handler) {
			GuiEventListener next = handler.getFocused();
			if (next == null) {
				return null;
			}
			current = next;
		}
		return current instanceof SlotWidget slot ? slot : null;
	}
}
