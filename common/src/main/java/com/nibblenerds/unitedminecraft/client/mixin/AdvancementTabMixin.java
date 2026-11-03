package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Collection;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.nibblenerds.unitedminecraft.client.AdvancementsController;
import com.nibblenerds.unitedminecraft.client.access.AdvancementTabAccess;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;

/**
 * Two jobs on {@code AdvancementTab}: exposing its private widgets and scroll offset, and making
 * the advancement the keyboard has selected show vanilla's own hover box. Vanilla decides what
 * is "hovered" afresh every tick from the mouse position alone, and clears it when the mouse is
 * over nothing, so the selection is applied at the end of {@code tick} - only when the mouse
 * itself is over nothing, so a player who also uses the mouse still sees what they point at.
 */
@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin implements AdvancementTabAccess {
	@Shadow
	private Map<AdvancementHolder, AdvancementWidget> widgets;
	@Shadow
	private double scrollX;
	@Shadow
	private double scrollY;
	@Shadow
	private AdvancementWidget hovered;
	@Shadow
	private float fade;

	@Override
	public Collection<AdvancementWidget> unitedMinecraft$getWidgets() {
		return widgets.values();
	}

	@Override
	public double unitedMinecraft$getScrollX() {
		return scrollX;
	}

	@Override
	public double unitedMinecraft$getScrollY() {
		return scrollY;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void unitedMinecraft$showSelection(int relativeMouseX, int relativeMouseY, CallbackInfo ci) {
		if (hovered != null) {
			return;
		}
		AdvancementWidget selected = AdvancementsController.selectionIn((AdvancementTab) (Object) this);
		if (selected != null) {
			hovered = selected;
			// The same ceiling vanilla's own hover fade-in stops at.
			fade = 0.3F;
		}
	}
}
