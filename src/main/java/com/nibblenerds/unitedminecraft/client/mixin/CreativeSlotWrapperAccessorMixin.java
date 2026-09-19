package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.SlotWrapperAccess;

import net.minecraft.world.inventory.Slot;

/**
 * Exposes the real {@link Slot} that {@code CreativeModeInventoryScreen$SlotWrapper} wraps
 * when the Creative screen's Inventory tab shows the player's real inventory. The wrapper
 * itself reports the wrong container-local slot index - it's built with its position in the
 * creative menu's own slot list (0-44) rather than the wrapped slot's real position in the
 * player's {@code Inventory} container (0-8 hotbar, 9-35 main inventory, 36+ armor/offhand) -
 * which breaks {@link com.nibblenerds.unitedminecraft.client.MenuAccessibilityController}'s
 * hotbar/inventory/equipment detection. Reading through to the real target slot's own
 * {@code getContainerSlot()} sidesteps that entirely.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public abstract class CreativeSlotWrapperAccessorMixin implements SlotWrapperAccess {
	@Shadow
	@Final
	private Slot target;

	@Override
	public Slot unitedMinecraft$getTarget() {
		return this.target;
	}
}
