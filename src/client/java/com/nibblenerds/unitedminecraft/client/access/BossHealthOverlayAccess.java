package com.nibblenerds.unitedminecraft.client.access;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.components.LerpingBossEvent;

/**
 * Duck interface implemented by {@link
 * com.nibblenerds.unitedminecraft.client.mixin.BossHealthOverlayAccessorMixin} to expose {@code
 * BossHealthOverlay}'s private active-boss-bar map, since the compiler has no way to know about
 * members a mixin injects into a vanilla class otherwise.
 */
public interface BossHealthOverlayAccess {
	/** Every boss bar currently active on the HUD, keyed by its network UUID. */
	Map<UUID, LerpingBossEvent> unitedMinecraft$getEvents();
}
