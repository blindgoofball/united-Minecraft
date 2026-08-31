package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.BossHealthOverlayAccess;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;

/**
 * Exposes {@code BossHealthOverlay}'s private {@code events} field - vanilla only renders the
 * active boss bars, with no public way to enumerate their names/progress on demand, which the
 * Narrate Boss Bars key needs.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayAccessorMixin implements BossHealthOverlayAccess {
	@Shadow
	private Map<UUID, LerpingBossEvent> events;

	@Override
	public Map<UUID, LerpingBossEvent> unitedMinecraft$getEvents() {
		return events;
	}
}
