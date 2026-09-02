package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Always-on passive warning for a hostile mob that's gotten close and has a clear line of
 * sight to you - the closest client-visible proxy for "something has targeted you", since
 * vanilla never actually syncs a mob's AI target to the client (only the server's
 * {@code Mob.target} field knows that). A nearby hostile mob that can genuinely see you is
 * either already attacking or about to be, so it's used here as a stand-in.
 *
 * <p>Not toggleable - this is a safety cue, not an exploration tool, so unlike the other
 * radars it always runs. Each mob re-alerts at most once per {@link #RE_ALERT_COOLDOWN_TICKS}
 * (rather than the mining radar's "alert once ever" for ore) since a hostile mob sticking
 * around nearby is worth an occasional reminder, not just a one-shot notice - and simple
 * line-of-sight flicker (a passing leaf, a door swinging) shouldn't be able to spam it either.
 *
 * <p>Separately, while any visible hostile is within actual melee reach ({@link
 * LocalPlayer#entityInteractionRange()}), a plain sound-only pulse repeats on {@link
 * #MELEE_ALERT_INTERVAL_TICKS} - deliberately no narration, since at that range the player
 * already knows roughly where the threat is and needs a fast, unobtrusive "still in range"
 * cue rather than another spoken sentence competing with combat.
 */
public final class HostileRadarController {
	private static final int SCAN_INTERVAL_TICKS = 5;
	private static final int ALERT_INTERVAL_TICKS = 5;
	private static final int RE_ALERT_COOLDOWN_TICKS = 100;
	private static final int MELEE_ALERT_INTERVAL_TICKS = 10;

	private static int ticksUntilScan;
	private static int ticksUntilNextAlert;
	private static int ticksUntilMeleeAlert;
	private static int ticks;
	private static boolean meleeThreatPresent;
	private static final Map<Integer, Integer> lastAlertTick = new HashMap<>();
	private static final Deque<Entity> pending = new ArrayDeque<>();

	private HostileRadarController() {
	}

	public static void reset() {
		ticksUntilScan = 0;
		ticksUntilNextAlert = 0;
		ticksUntilMeleeAlert = 0;
		ticks = 0;
		meleeThreatPresent = false;
		lastAlertTick.clear();
		pending.clear();
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().hostileRadarEnabled) {
			pending.clear();
			meleeThreatPresent = false;
			return;
		}

		ticks++;

		if (ticksUntilScan <= 0) {
			ticksUntilScan = SCAN_INTERVAL_TICKS;
			scan(player);
		} else {
			ticksUntilScan--;
		}

		if (!pending.isEmpty() && ticksUntilNextAlert <= 0) {
			ticksUntilNextAlert = ALERT_INTERVAL_TICKS;
			alert(client, player, pending.poll());
		} else if (ticksUntilNextAlert > 0) {
			ticksUntilNextAlert--;
		}

		if (UnitedMinecraftConfig.get().meleeRangeAlertEnabled && meleeThreatPresent) {
			if (ticksUntilMeleeAlert <= 0) {
				ticksUntilMeleeAlert = MELEE_ALERT_INTERVAL_TICKS;
				playMeleeAlert(client, player);
			} else {
				ticksUntilMeleeAlert--;
			}
		} else {
			ticksUntilMeleeAlert = 0;
		}
	}

	private static void scan(LocalPlayer player) {
		// lastAlertTick otherwise only ever grows - an entity ID it's tracking never gets
		// removed just because that mob wandered off, died, or unloaded. Once an entry's
		// cooldown has fully elapsed it's dead weight either way (see the check just below,
		// which already treats it as "eligible to re-alert" past this point), so purge it here
		// rather than let the map accumulate one entry per hostile mob ever encountered all
		// session.
		lastAlertTick.entrySet().removeIf(entry -> ticks - entry.getValue() >= RE_ALERT_COOLDOWN_TICKS);

		double range = UnitedMinecraftConfig.get().hostileRadarRange;
		double meleeRange = player.entityInteractionRange();
		Vec3 eye = player.getEyePosition();
		AABB box = player.getBoundingBox().inflate(range);
		meleeThreatPresent = false;
		for (Entity entity : player.level().getEntities(player, box, e -> e.isAlive() && e instanceof Enemy)) {
			double distSq = eye.distanceToSqr(entity.getEyePosition());

			if (distSq <= meleeRange * meleeRange && hasLineOfSight(player.level(), eye, entity.getEyePosition())) {
				meleeThreatPresent = true;
			}

			Integer alertedAt = lastAlertTick.get(entity.getId());
			if (alertedAt != null && ticks - alertedAt < RE_ALERT_COOLDOWN_TICKS) {
				continue;
			}
			if (distSq > range * range || pending.contains(entity)) {
				continue;
			}
			if (hasLineOfSight(player.level(), eye, entity.getEyePosition())) {
				pending.add(entity);
			}
		}
	}

	private static void alert(Minecraft client, LocalPlayer player, Entity entity) {
		lastAlertTick.put(entity.getId(), ticks);
		if (!entity.isAlive() || entity.level() != player.level()) {
			return;
		}

		Vec3 pos = entity.position();
		RandomSource random = player.getRandom();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.0f, random, pos.x(), pos.y(), pos.z()));

		int distance = (int) Math.round(player.getEyePosition().distanceTo(entity.position()));
		Component direction = CameraUtil.fullDirectionTo(player.position(), pos);
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.scanner_item", entity.getDisplayName(), distance, direction));
	}

	private static void playMeleeAlert(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.6f, 1.4f, random, pos.x(), pos.y(), pos.z()));
	}

	private static boolean hasLineOfSight(Level level, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(
				from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}
}
