package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.BeaconScreenAccess;

import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

/**
 * Exposes {@code BeaconScreen}'s private, not-yet-confirmed {@code primary}/{@code secondary}
 * effect fields - the buttons the vanilla screen builds for effect selection are themselves
 * private inner classes with no public way to reach the choice they're driving, so {@link
 * com.nibblenerds.unitedminecraft.client.MenuAccessibilityController}'s Beacon Effects section
 * reads and writes these two fields directly instead, exactly like the vanilla buttons do.
 */
@Mixin(BeaconScreen.class)
public abstract class BeaconScreenAccessorMixin implements BeaconScreenAccess {
	@Shadow
	private Holder<MobEffect> primary;
	@Shadow
	private Holder<MobEffect> secondary;

	@Override
	public Holder<MobEffect> unitedMinecraft$getPrimary() {
		return primary;
	}

	@Override
	public Holder<MobEffect> unitedMinecraft$getSecondary() {
		return secondary;
	}

	@Override
	public void unitedMinecraft$setPrimary(Holder<MobEffect> primary) {
		this.primary = primary;
	}

	@Override
	public void unitedMinecraft$setSecondary(Holder<MobEffect> secondary) {
		this.secondary = secondary;
	}
}
