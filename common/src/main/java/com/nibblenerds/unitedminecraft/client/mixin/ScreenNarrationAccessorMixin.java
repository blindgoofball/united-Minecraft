package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.nibblenerds.unitedminecraft.client.access.ScreenNarrationAccess;

import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.screens.Screen;

/** Exposes {@code Screen#scheduleNarration(long, boolean, NarrationTrigger)} - see {@link ScreenNarrationAccess}. */
@Mixin(Screen.class)
public abstract class ScreenNarrationAccessorMixin implements ScreenNarrationAccess {
	@Invoker("scheduleNarration")
	@Override
	public abstract void unitedMinecraft$scheduleNarration(long delay, boolean ignoreSuppression, NarrationTrigger trigger);
}
