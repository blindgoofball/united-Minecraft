package com.nibblenerds.unitedminecraft.client;

import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.AABB;

/**
 * Announces "Tamed &lt;mob&gt;" the moment a nearby animal you own finishes taming. Vanilla's
 * only feedback for a successful tame attempt is heart particles (smoke on a failed roll) plus a
 * silent server-side flag flip - useless without sight, and the roll itself isn't client-visible
 * at all, so this can't predict success the way {@link AnimalFeedingController} predicts a feed.
 *
 * <p>Detected by edge-triggering off a per-entity last-known-tamed map instead of listening for
 * the taming particle event directly, so {@code isTame()}/{@code isTamed()} - the same check
 * {@link ScannerController#tameStatusFragments} already uses as the single source of truth for
 * what "tamed" means across the two unrelated hierarchies ({@link TamableAnimal} and
 * {@link AbstractHorse} share no common tamed/owned supertype) - stays authoritative here too.
 *
 * <p>{@code lastTamed} is a {@link WeakHashMap} keyed by entity identity so unloaded entities are
 * simply forgotten rather than leaked, and only entities within {@link #RANGE} are ever visited
 * (unlike {@link ScannerController}'s configurable range, fixed here - this isn't a Scanner
 * category the player chooses to check, just passive awareness of what's right around them).
 */
public final class TameNotificationController {
	private static final double RANGE = 16.0;

	private static final WeakHashMap<Entity, Boolean> lastTamed = new WeakHashMap<>();

	private TameNotificationController() {
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(RANGE);
		for (Entity entity : player.level().getEntities(player, box, TameNotificationController::isTameable)) {
			boolean tamedNow = isTamed(entity);
			Boolean tamedBefore = lastTamed.put(entity, tamedNow);
			// Only narrates a genuine false-to-true transition, never the first sighting of an
			// already-tamed animal (tamedBefore == null) - otherwise every pet you already own
			// would announce itself as freshly tamed the moment it first comes into range.
			if (tamedNow && Boolean.FALSE.equals(tamedBefore) && isOwnedByPlayer(entity, player)) {
				client.getNarrator().saySystemNow(
						Component.translatable("united_minecraft.narrate.mob_tamed_notification", entity.getDisplayName()));
			}
		}
	}

	private static boolean isTameable(Entity entity) {
		return entity instanceof TamableAnimal || entity instanceof AbstractHorse;
	}

	private static boolean isTamed(Entity entity) {
		if (entity instanceof TamableAnimal tamable) {
			return tamable.isTame();
		}
		if (entity instanceof AbstractHorse horse) {
			return horse.isTamed();
		}
		return false;
	}

	/**
	 * {@link AbstractHorse}'s {@code owner} field is never networked to the client - confirmed
	 * via its {@code defineSynchedData} bytecode, which only registers the tame-flag byte as an
	 * {@code EntityDataAccessor}, unlike {@link TamableAnimal}'s explicitly synced owner UUID -
	 * so {@code getOwner()} always reads {@code null} here regardless of who actually tamed it.
	 * Being the horse's current rider the instant it flips tamed is the only client-visible
	 * substitute: vanilla keeps you mounted exactly when a bucking-phase tame roll finally
	 * succeeds, so this can't false-negative on your own successful tame or false-positive on
	 * someone else's horse you aren't riding.
	 */
	private static boolean isOwnedByPlayer(Entity entity, LocalPlayer player) {
		if (entity instanceof AbstractHorse) {
			return player.getVehicle() == entity;
		}
		return entity instanceof OwnableEntity ownable && player.equals(ownable.getOwner());
	}

	/** Called when the player unloads, so a pet tamed in a previous session doesn't look like a fresh transition here. */
	public static void reset() {
		lastTamed.clear();
	}
}
