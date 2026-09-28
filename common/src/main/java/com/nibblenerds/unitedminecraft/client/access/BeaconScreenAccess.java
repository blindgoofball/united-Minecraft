package com.nibblenerds.unitedminecraft.client.access;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * Duck interface implemented by {@link com.nibblenerds.unitedminecraft.client.mixin.BeaconScreenAccessorMixin}
 * to read and drive {@code BeaconScreen}'s private, not-yet-confirmed primary/secondary effect
 * choice (either may be null, meaning "none chosen yet") - the same fields its own on-screen
 * buttons read and write, and the only place that choice lives before the player presses
 * Confirm (see {@link com.nibblenerds.unitedminecraft.client.MenuAccessibilityController}'s
 * Beacon Effects section).
 */
public interface BeaconScreenAccess {
	Holder<MobEffect> unitedMinecraft$getPrimary();

	Holder<MobEffect> unitedMinecraft$getSecondary();

	void unitedMinecraft$setPrimary(Holder<MobEffect> primary);

	void unitedMinecraft$setSecondary(Holder<MobEffect> secondary);
}
