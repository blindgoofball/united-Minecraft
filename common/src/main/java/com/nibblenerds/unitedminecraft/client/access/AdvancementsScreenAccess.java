package com.nibblenerds.unitedminecraft.client.access;

import java.util.Collection;

import net.minecraft.client.gui.screens.advancements.AdvancementTab;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AdvancementsScreenAccessorMixin}
 * to read the tabs {@code AdvancementsScreen} built and which one is showing - both private, and
 * only changed by vanilla through a right-click or a server update (see {@link
 * com.nibblenerds.unitedminecraft.client.AdvancementsController}).
 */
public interface AdvancementsScreenAccess {
	/** The tabs in the order vanilla lists them. */
	Collection<AdvancementTab> unitedMinecraft$getTabs();

	/** The tab currently showing, or null when there are no advancements at all. */
	AdvancementTab unitedMinecraft$getSelectedTab();
}
