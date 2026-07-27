package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * Removes vanilla's own narration delay (200ms after a keyboard/mouse action, 750ms after
 * mouse movement) by having the schedule land on "now" instead of "now + delay" - narration
 * still goes through the exact same single scheduled check vanilla already runs every frame
 * ({@code Screen#handleDelayedNarration}), it just doesn't wait to become due.
 *
 * <p>Deliberately not a second, separate narration trigger (an earlier version of this mod
 * tried firing narration immediately on keypress, alongside vanilla's own delayed cycle - see
 * {@link ScreenNarratedWidgetMixin}'s javadoc) - that drove the same shared per-screen
 * narration state from two independent call sites and caused stale/doubled speech. This keeps
 * exactly one driver, just faster.
 */
@Mixin(Screen.class)
public abstract class ScreenNarrationDelayMixin {
	@Shadow
	private long nextNarrationTime;
	@Shadow
	private long narrationSuppressTime;

	@Inject(method = "scheduleNarration", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$removeNarrationDelay(long delay, boolean ignoreSuppression, CallbackInfo ci) {
		this.nextNarrationTime = Util.getMillis();
		if (ignoreSuppression) {
			this.narrationSuppressTime = Long.MIN_VALUE;
		}
		ci.cancel();
	}
}
