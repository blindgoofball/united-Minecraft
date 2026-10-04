package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.HudTitleAccess;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

/**
 * Exposes {@code Hud}'s private title state. Vanilla draws server titles and subtitles (the
 * {@code /title} command, minigame countdowns, region names) purely visually and never narrates
 * them.
 */
@Mixin(Hud.class)
public abstract class HudTitleAccessorMixin implements HudTitleAccess {
	@Shadow
	private int titleTime;
	@Shadow
	private Component title;
	@Shadow
	private Component subtitle;

	@Override
	public Component unitedMinecraft$getVisibleTitle() {
		return titleTime > 0 ? title : null;
	}

	@Override
	public Component unitedMinecraft$getSubtitle() {
		return subtitle;
	}
}
