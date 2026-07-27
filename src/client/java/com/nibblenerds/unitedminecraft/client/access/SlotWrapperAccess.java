package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.world.inventory.Slot;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.CreativeSlotWrapperAccessorMixin}
 * to expose the real {@link Slot} that {@code CreativeModeInventoryScreen$SlotWrapper} wraps.
 */
public interface SlotWrapperAccess {
	Slot unitedMinecraft$getTarget();
}
