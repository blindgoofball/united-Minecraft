package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Toggleable "combat mode": continuously locks onto whichever hostile mob is nearest,
 * switching target the moment a closer one shows up, rather than sticking to one target
 * until it dies or is released like the Scanner's own lock-on does. Meant for an actual
 * fight against more than one attacker, where manually re-targeting between hits isn't
 * practical.
 *
 * <p>A candidate has to be meaningfully closer than the current target (see
 * {@link #SWITCH_MARGIN}) to actually steal the lock, not just momentarily nearer by a few
 * blocks - otherwise two similarly-distant mobs could make the lock flicker between them
 * every tick as they move.
 *
 * <p>Owns rotation entirely while active, the same as the Scanner's own lock-on and Build
 * Mode - so turning it on cancels either of those if they were running (Build Mode's own
 * toggle key is also blocked while this is active, so it can't be turned back on
 * underneath it - see {@link AccessibilityTickHandler}). Movement isn't touched at all -
 * you can still walk, strafe, and jump freely while your aim stays locked onto the
 * nearest threat.
 */
public final class CombatModeController {
	private static final double SCAN_RANGE = 32.0;
	private static final double SWITCH_MARGIN = 1.0;

	private static boolean enabled;
	private static Entity target;

	private CombatModeController() {
	}

	public static boolean isActive() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
		target = null;
	}

	public static void toggle(Minecraft client, LocalPlayer player) {
		enabled = !enabled;
		if (!enabled) {
			target = null;
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.combat_mode_off"));
			return;
		}

		if (BuildModeController.isActive()) {
			BuildModeController.toggle(client, player);
		}
		ScannerController.cancelLock();

		target = findNearestHostile(player, player.getEyePosition());
		Component message = Component.translatable("united_minecraft.narrate.combat_mode_on")
				.append(Component.literal(" "))
				.append(target != null
						? Component.translatable("united_minecraft.narrate.scanner_lock_started", target.getDisplayName())
						: Component.translatable("united_minecraft.narrate.scanner_empty"));
		client.getNarrator().saySystemNow(message);
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (target != null && (!target.isAlive() || target.level() != player.level())) {
			target = null;
		}

		Vec3 eye = player.getEyePosition();
		Entity nearest = findNearestHostile(player, eye);
		if (nearest != null && shouldSwitchTo(eye, nearest)) {
			target = nearest;
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.scanner_lock_started", target.getDisplayName()));
		}

		if (target != null) {
			CameraUtil.aimAtEntity(player, target);
		}
	}

	private static boolean shouldSwitchTo(Vec3 eye, Entity candidate) {
		if (target == null) {
			return true;
		}
		double margin = SWITCH_MARGIN * SWITCH_MARGIN;
		return distanceSq(eye, candidate) + margin < distanceSq(eye, target);
	}

	private static Entity findNearestHostile(LocalPlayer player, Vec3 eye) {
		AABB box = player.getBoundingBox().inflate(SCAN_RANGE);
		Entity nearest = null;
		double nearestDistSq = SCAN_RANGE * SCAN_RANGE;
		for (Entity entity : player.level().getEntities(player, box, e -> e.isAlive() && e instanceof Enemy)) {
			double distSq = distanceSq(eye, entity);
			if (distSq <= nearestDistSq) {
				nearest = entity;
				nearestDistSq = distSq;
			}
		}
		return nearest;
	}

	private static double distanceSq(Vec3 eye, Entity entity) {
		return eye.distanceToSqr(entity.getBoundingBox().getCenter());
	}
}
