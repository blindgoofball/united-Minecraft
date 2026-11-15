package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AbstractContainerScreenAccessorMixin}
 * to call {@code AbstractContainerScreen#slotClicked}, which is protected. A few screens
 * (the Crafter's slot lock) act on a click there rather than in the menu, so a click that
 * only goes to the menu and the server never reaches them.
 */
public interface SlotClickAccess {
	void unitedMinecraft$slotClicked(Slot slot, int slotId, int button, ContainerInput input);
}
