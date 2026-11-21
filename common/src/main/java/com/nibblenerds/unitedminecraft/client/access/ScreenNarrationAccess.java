package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.client.gui.narration.NarrationTrigger;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.ScreenNarrationAccessorMixin}
 * to reach {@code Screen}'s private three-argument {@code scheduleNarration}. The public one has
 * no way to say what triggered the narration, and always says "system".
 */
public interface ScreenNarrationAccess {
	void unitedMinecraft$scheduleNarration(long delay, boolean ignoreSuppression, NarrationTrigger trigger);
}
