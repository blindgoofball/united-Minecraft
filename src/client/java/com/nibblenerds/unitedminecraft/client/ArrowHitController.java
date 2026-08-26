package com.nibblenerds.unitedminecraft.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Always-on cue for whether a fired arrow actually landed a hit - a shot at real range
 * easily lands well outside both visual range and vanilla's own positional arrow-impact
 * sound, so there's currently no way to tell whether it connected at all.
 *
 * <p>The client never learns "this arrow just hit an entity" directly - hitting a living
 * target discards the arrow entity outright, the same client-visible signal as flying out
 * of tracked range or otherwise disappearing, and {@code AbstractArrow.isInGround()} isn't
 * accessible from here. So a hit is inferred instead: every arrow the player fires is
 * watched, and it counts as a hit if it disappears from the world while still actually
 * moving. An arrow that embeds in a block instead goes stationary first and stays that way
 * for a long time (until it despawns or is picked up), so requiring the disappearance to
 * happen while movement is still fresh - not after several ticks of sitting still - tells
 * the two cases apart without needing arrow-specific internals.
 *
 * <p>Played at the player's own position rather than the target's, deliberately breaking
 * from this mod's other positional cues (Hostile/Mining Radar, Fall Warning) - a shot
 * landing 50+ blocks out would be inaudible if it fell off with distance the normal way,
 * and the point here is a plain yes/no, not directional information the player already has
 * from having aimed the shot themselves.
 */
public final class ArrowHitController {
	// Comfortably past a fully-drawn bow's real flight range, so discovery never misses a
	// shot the player just fired.
	private static final double DISCOVERY_RANGE = 128.0;

	// Below this squared distance moved in a tick, an arrow counts as stopped rather than
	// still flying.
	private static final double STILL_EPSILON_SQ = 0.0025; // 0.05 blocks/tick

	// Once an arrow's been stationary this many ticks, it reads as embedded in a block (a
	// miss) - its eventual despawn/pickup no longer counts as a hit.
	private static final int STILL_TICKS_FOR_SETTLED = 2;

	private static final Map<Integer, Tracked> watched = new HashMap<>();

	private static final class Tracked {
		Vec3 lastPos;
		int stillTicks;
	}

	private ArrowHitController() {
	}

	public static void reset() {
		watched.clear();
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(DISCOVERY_RANGE);
		for (Entity entity : player.level().getEntities(player, box,
				e -> e instanceof AbstractArrow arrow && arrow.getOwner() == player)) {
			watched.computeIfAbsent(entity.getId(), id -> {
				Tracked tracked = new Tracked();
				tracked.lastPos = entity.position();
				return tracked;
			});
		}

		Iterator<Map.Entry<Integer, Tracked>> iterator = watched.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Tracked> entry = iterator.next();
			Tracked tracked = entry.getValue();
			Entity entity = player.level().getEntity(entry.getKey());
			if (entity == null) {
				double maxRangeSqr = (DISCOVERY_RANGE - 8.0) * (DISCOVERY_RANGE - 8.0);
				if (tracked.stillTicks < STILL_TICKS_FOR_SETTLED && tracked.lastPos.distanceToSqr(player.position()) < maxRangeSqr) {
					playHitCue(client, player);
				}
				iterator.remove();
				continue;
			}
			Vec3 pos = entity.position();
			tracked.stillTicks = pos.distanceToSqr(tracked.lastPos) < STILL_EPSILON_SQ ? tracked.stillTicks + 1 : 0;
			tracked.lastPos = pos;
		}
	}

	private static void playHitCue(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ARROW_HIT_PLAYER, SoundSource.MASTER,
				1.0f, 1.0f, random, player.getX(), player.getEyeY(), player.getZ()));
	}
}
