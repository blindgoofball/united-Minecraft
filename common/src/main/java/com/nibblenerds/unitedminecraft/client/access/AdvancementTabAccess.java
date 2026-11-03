package com.nibblenerds.unitedminecraft.client.access;

import java.util.Collection;

import net.minecraft.client.gui.screens.advancements.AdvancementWidget;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AdvancementTabMixin}
 * to reach a tab's private widgets and scroll offset, which {@link
 * com.nibblenerds.unitedminecraft.client.AdvancementsController} needs to list the advancements
 * and to bring the focused one into view.
 */
public interface AdvancementTabAccess {
	Collection<AdvancementWidget> unitedMinecraft$getWidgets();

	double unitedMinecraft$getScrollX();

	double unitedMinecraft$getScrollY();
}
