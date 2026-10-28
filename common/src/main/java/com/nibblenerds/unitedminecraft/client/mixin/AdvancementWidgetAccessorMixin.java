package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.AdvancementWidgetAccess;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;

/** Exposes the progress and visible parent of an advancement node - see {@link AdvancementWidgetAccess}. */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetAccessorMixin implements AdvancementWidgetAccess {
	@Shadow
	private AdvancementProgress progress;
	@Shadow
	private AdvancementWidget parent;

	@Override
	public AdvancementProgress unitedMinecraft$getProgress() {
		return progress;
	}

	@Override
	public AdvancementWidget unitedMinecraft$getParent() {
		return parent;
	}
}
