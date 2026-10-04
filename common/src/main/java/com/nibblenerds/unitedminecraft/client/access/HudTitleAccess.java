package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.network.chat.Component;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.HudTitleAccessorMixin}
 * to expose the title and subtitle {@code Hud} is currently showing - see {@link
 * com.nibblenerds.unitedminecraft.client.TitleNarrationController}.
 */
public interface HudTitleAccess {
	/** The title on screen, or null when none is showing (never set, or it has faded out). */
	Component unitedMinecraft$getVisibleTitle();

	/** The subtitle under the title; only meaningful while a title is visible. */
	Component unitedMinecraft$getSubtitle();
}
