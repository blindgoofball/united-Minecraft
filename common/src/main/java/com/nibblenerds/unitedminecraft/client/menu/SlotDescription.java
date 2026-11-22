package com.nibblenerds.unitedminecraft.client.menu;

import com.nibblenerds.unitedminecraft.client.BlockDescriptions;
import com.nibblenerds.unitedminecraft.client.keybind.ClientKeyBindings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Says what the block in the focused slot is made of, on {@link ClientKeyBindings#CONTAINER_DESCRIBE_SLOT}:
 * its grain, color and texture, as a sighted player would see them. The game gives only the item's
 * name. The descriptions themselves are written per material in {@link BlockDescriptions}.
 *
 * <p>Only acts when the focus path ends in a slot, so the key is left alone on a text field.
 */
public final class SlotDescription {
	private SlotDescription() {
	}

	/** @return {@code false} if the key was consumed, the polarity of every screen key hook. */
	public static boolean handleKey(AbstractContainerScreen<?> screen, KeyEvent event) {
		if (!ClientKeyBindings.CONTAINER_DESCRIBE_SLOT.current().matches(event)) {
			return true;
		}
		SlotWidget slot = SlotChangeNarration.focusedSlot(screen);
		if (slot == null) {
			return true;
		}
		Minecraft.getInstance().getNarrator().saySystemNow(BlockDescriptions.describe(slot.slot().getItem()));
		return false;
	}
}
