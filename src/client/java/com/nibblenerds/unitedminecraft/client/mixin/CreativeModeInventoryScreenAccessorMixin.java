package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.CreativeModeInventoryScreenAccess;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Exposes {@code CreativeModeInventoryScreen}'s private {@code selectedTab} field,
 * {@code selectTab(...)} method, and its Search tab's private {@code searchBox}. Vanilla only
 * offers mouse clicks on custom-rendered tab sprites to change or even query the current tab -
 * there's no other way in, keyboard or otherwise - so
 * {@link com.nibblenerds.unitedminecraft.client.CreativeInventoryController} needs this to
 * build tab cycling, and needs the search box itself to give it real keyboard focus (see that
 * class's Section.SEARCH handling).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenAccessorMixin implements CreativeModeInventoryScreenAccess {
	@Shadow
	private static CreativeModeTab selectedTab;

	@Shadow
	private EditBox searchBox;

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

	@Override
	public EditBox unitedMinecraft$getSearchBox() {
		return searchBox;
	}
}
