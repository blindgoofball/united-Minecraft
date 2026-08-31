package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.CreativeModeInventoryScreenAccessorMixin}
 * to expose vanilla's private tab-selection state and Search-tab {@link EditBox}, since the
 * compiler has no way to know about members a mixin injects into a vanilla class otherwise.
 */
public interface CreativeModeInventoryScreenAccess {
	CreativeModeTab unitedMinecraft$getSelectedTab();

	void unitedMinecraft$selectTab(CreativeModeTab tab);

	EditBox unitedMinecraft$getSearchBox();
}
