package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;

/**
 * Narrates what a fishing rod just reeled in - vanilla never says anything about it at all,
 * sighted or otherwise; the only feedback is the splash particle, the retrieve sound, and
 * whatever briefly flies out of the water toward the player.
 *
 * <p>There's no direct "you caught X" signal available here - loot resolution ({@code
 * FishingHook.retrieve}) only ever runs server-side, so the client never receives anything
 * that says why the hook came back. What it does see is inferred instead, the same way
 * {@link ArrowHitController} infers a hit from an arrow's disappearance rather than any
 * direct signal: {@link net.minecraft.world.entity.player.Player#fishing} tracks the local
 * player's own hook entity and is properly maintained client-side too (cleared via {@code
 * FishingHook#onClientRemoval} when the entity is removed), so watching it for a
 * non-null-to-null transition catches the exact moment a cast ends, for any reason - reeled
 * in empty, snagged on another entity, caught something, or simply despawned. A real catch
 * additionally spawns a brand new {@link ItemEntity} - the same one loot table result vanilla
 * itself would show tossed out of the water toward the player - so once the hook's gone, this
 * looks for one that's new enough to plausibly be that toss rather than some unrelated item
 * already lying around; if it finds one, that's the catch. Reeling in without a bite, or
 * snagging an entity instead of a fish, spawns no such item, so nothing gets narrated for
 * either.
 *
 * <p>The search is centered on the player, not the hook's own position - {@code retrieve}
 * gives the tossed item an initial velocity of roughly a tenth of the hook-to-player distance
 * per tick, which for anything past a short-range cast easily clears a tight radius around
 * the hook within a tick or two, so anchoring the search there instead of the player would
 * risk missing real catches at ordinary fishing range.
 */
public final class FishingCatchController {
	// How far from the player a candidate item can be and still count - generous, since the
	// item search is keyed on being brand new rather than being close, and a cast can land
	// well outside melee range.
	private static final double CATCH_RADIUS = 64.0;

	// How many ticks old a candidate item can be and still count as "just spawned by this
	// catch" rather than something that merely happened to be nearby already.
	private static final int MAX_CATCH_AGE_TICKS = 2;

	private static boolean wasFishing;

	private FishingCatchController() {
	}

	public static void reset() {
		wasFishing = false;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean fishing = player.fishing != null;
		if (fishing) {
			wasFishing = true;
			return;
		}
		if (wasFishing) {
			wasFishing = false;
			announceCatch(client, player);
		}
	}

	private static void announceCatch(Minecraft client, LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(CATCH_RADIUS);
		for (Entity entity : player.level().getEntities((Entity) null, box,
				e -> e instanceof ItemEntity item && item.tickCount <= MAX_CATCH_AGE_TICKS)) {
			MutableComponent description = ItemDescriptions.describe(((ItemEntity) entity).getItem(), player);
			client.getNarrator().saySystemNow(description);
		}
	}
}
