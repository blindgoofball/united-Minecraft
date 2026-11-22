package com.nibblenerds.unitedminecraft.client.menu;

import com.nibblenerds.unitedminecraft.client.ItemDescriptions;
import com.nibblenerds.unitedminecraft.client.access.SlotClickAccess;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * Drops what is on the cursor into the world: all of it on {@link ClientKeyBindings#CONTAINER_DROP_CARRIED},
 * one item on {@link ClientKeyBindings#CONTAINER_DROP_CARRIED_ONE} - the left and right click a mouse
 * makes outside the window, which the game has no key for.
 *
 * <p>Sent through the screen as a click on no slot, the way the mouse does it, so a screen that
 * handles one itself (the Creative inventory) does. Only acts when focus is on a slot, so
 * Backspace is left alone in a text field.
 */
public final class DropCarried {
	/** The slot id the game uses for a click outside the window. */
	private static final int OUTSIDE = -999;

	private DropCarried() {
	}

	/** @return {@code false} if the key was consumed, the polarity of every screen key hook. */
	public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		int button;
		if (ClientKeyBindings.CONTAINER_DROP_CARRIED.current().matches(event)) {
			button = 0;
		} else if (ClientKeyBindings.CONTAINER_DROP_CARRIED_ONE.current().matches(event)) {
			button = 1;
		} else {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		if (SlotChangeNarration.focusedSlot(screen) == null || client.player == null) {
			return true;
		}

		ItemStack carried = screen.getMenu().getCarried();
		if (carried.isEmpty()) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.menu_nothing_carried"));
			return false;
		}
		Component dropped = ItemDescriptions.describe(button == 0 ? carried : carried.copyWithCount(1), client.player);
		((SlotClickAccess) screen).unitedMinecraft$slotClicked(null, OUTSIDE, button, ContainerInput.PICKUP);
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.menu_item_dropped", dropped));
		return false;
	}
}
