package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.components.inventory.SlotWidget;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AbstractContainerScreenAccessorMixin}
 * to call {@code AbstractContainerScreen#slotClicked}, which is protected. A click is made through
 * the screen rather than sent to the server directly so that a screen that handles one itself - the
 * Creative inventory, dropping an item - does, exactly as it does for a mouse click.
 */
public interface SlotClickAccess {
	void unitedMinecraft$slotClicked(SlotWidget slot, int slotId, int button, ContainerInput input);
}
