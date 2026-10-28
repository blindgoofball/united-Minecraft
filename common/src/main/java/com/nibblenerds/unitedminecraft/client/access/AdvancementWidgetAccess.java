package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.AdvancementWidgetAccessorMixin}
 * to read the private progress and visible-parent link of an advancement node - the two things a
 * keyboard player needs that vanilla only ever draws (see {@link
 * com.nibblenerds.unitedminecraft.client.AdvancementsController}).
 */
public interface AdvancementWidgetAccess {
	/** Null until the server has sent progress for this advancement. */
	AdvancementProgress unitedMinecraft$getProgress();

	/** The nearest ancestor that has a display, or null for a tab's root. */
	AdvancementWidget unitedMinecraft$getParent();
}
