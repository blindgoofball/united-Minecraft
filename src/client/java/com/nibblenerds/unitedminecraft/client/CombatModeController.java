package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
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
	// Starts true so a fresh world/session doesn't immediately narrate a false "ready" cue -
	// same reasoning as this mod's other edge-triggered ambient narration.
	private static boolean attackReadyLastTick = true;

	private CombatModeController() {
	}

	public static boolean isActive() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
		target = null;
		attackReadyLastTick = true;
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
		// Also releases an Enderman target that's calmed back down since being locked onto -
		// no sense continuing to stare (and risk re-provoking it) once it's not actually mad
		// anymore.
		if (target != null && (!target.isAlive() || target.level() != player.level() || isCalmEnderman(target))) {
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

	/**
	 * Plays an audio cue the moment the weapon's attack-strength meter (the cooldown that
	 * scales down damage on an early swing) refills to full - edge-triggered off {@link
	 * #attackReadyLastTick}, same pattern as this mod's other ambient narration. Runs every
	 * tick regardless of whether Combat Mode is active, since {@link
	 * UnitedMinecraftConfig.CombatCueMode#ALWAYS} needs to track it outside Combat Mode too.
	 */
	public static void tickAttackCue(Minecraft client, LocalPlayer player) {
		UnitedMinecraftConfig.CombatCueMode mode = UnitedMinecraftConfig.get().combatCueMode;
		boolean shouldTrack = mode == UnitedMinecraftConfig.CombatCueMode.ALWAYS
				|| (mode == UnitedMinecraftConfig.CombatCueMode.COMBAT_MODE_ONLY && enabled);
		if (!shouldTrack) {
			// Resync so flipping the setting/entering Combat Mode mid-cooldown doesn't fire a
			// stale "just became ready" cue for a refill that already happened while untracked.
			attackReadyLastTick = true;
			return;
		}
		boolean ready = player.getAttackStrengthScale(1.0f) >= 1.0f;
		if (ready && !attackReadyLastTick) {
			playAttackReadyCue(client, player);
		}
		attackReadyLastTick = ready;
	}

	private static void playAttackReadyCue(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), SoundSource.MASTER, 0.5f, 1.4f, random, pos.x(), pos.y(), pos.z()));
	}

	private static boolean shouldSwitchTo(Vec3 eye, Entity candidate) {
		if (target == null) {
			return true;
		}
		return Math.sqrt(distanceSq(eye, candidate)) + SWITCH_MARGIN < Math.sqrt(distanceSq(eye, target));
	}

	private static Entity findNearestHostile(LocalPlayer player, Vec3 eye) {
		AABB box = player.getBoundingBox().inflate(SCAN_RANGE);
		Entity nearest = null;
		double nearestDistSq = SCAN_RANGE * SCAN_RANGE;
		for (Entity entity : player.level().getEntities(player, box, e -> e.isAlive() && e instanceof Enemy && !isCalmEnderman(e))) {
			double distSq = distanceSq(eye, entity);
			if (distSq <= nearestDistSq) {
				nearest = entity;
				nearestDistSq = distSq;
			}
		}
		return nearest;
	}

	/**
	 * True for an Enderman that hasn't actually turned hostile yet - {@code isCreepy()} is
	 * vanilla's own synced "screaming/angry" flag, the same one that drives its eye-glow and
	 * scream sound, so it's already available client-side with no server cooperation needed.
	 * Locking onto (and thus staring at) a calm Enderman is exactly what provokes it in the
	 * first place, so Combat Mode should never volunteer one as a target on its own - if it's
	 * already angry at you for some other reason, though, there's no more provoking left to do.
	 */
	private static boolean isCalmEnderman(Entity entity) {
		return entity instanceof EnderMan enderMan && !enderMan.isCreepy();
	}

	private static double distanceSq(Vec3 eye, Entity entity) {
		return eye.distanceToSqr(entity.getBoundingBox().getCenter());
	}
}
