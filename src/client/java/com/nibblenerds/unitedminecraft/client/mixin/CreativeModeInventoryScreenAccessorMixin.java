package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Exposes {@code CreativeModeInventoryScreen}'s private {@code selectedTab} field and
 * {@code selectTab(...)} method. Vanilla only offers mouse clicks on custom-rendered tab
 * sprites to change or even query the current tab - there's no other way in, keyboard or
 * otherwise - so {@link com.nibblenerds.unitedminecraft.client.CreativeInventoryController}
 * needs this to build tab cycling.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenAccessorMixin implements CreativeModeInventoryScreenAccess {
	@Shadow
	private static CreativeModeTab selectedTab;

	@Shadow
	protected abstract void selectTab(CreativeModeTab tab);

	@Override
	public CreativeModeTab unitedMinecraft$getSelectedTab() {
		return selectedTab;
	}

	@Override
	public void unitedMinecraft$selectTab(CreativeModeTab tab) {
		this.selectTab(tab);
	}
}
