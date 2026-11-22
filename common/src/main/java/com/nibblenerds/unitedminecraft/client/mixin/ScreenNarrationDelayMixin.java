package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.screens.Screen;

/**
 * Removes vanilla's own narration delay (200ms after a keyboard/mouse action, 750ms after
 * mouse movement) by making the delay it schedules with zero, so narration lands on "now"
 * instead of "now + delay". Everything else about scheduling - what triggered it, whether to
 * ignore the suppression window, the single check vanilla already runs every frame in {@code
 * Screen#handleDelayedNarration} - is vanilla's own, untouched.
 */
@Mixin(Screen.class)
public abstract class ScreenNarrationDelayMixin {
	@ModifyVariable(method = "scheduleNarration(JZLnet/minecraft/client/gui/narration/NarrationTrigger;)V",
			at = @At("HEAD"), argsOnly = true)
	private long unitedMinecraft$removeNarrationDelay(long delay) {
		return 0L;
	}
}
