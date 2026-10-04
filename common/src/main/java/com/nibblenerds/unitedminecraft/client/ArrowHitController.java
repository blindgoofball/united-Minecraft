package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
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
 * accessible from here. So a hit is inferred in two steps. First, every arrow the player
 * fires is watched, and one that disappears while still actually moving is a candidate (an
 * arrow that embeds in a block goes stationary first and stays that way for a long time, so
 * requiring fresh movement rules it out). Second, a candidate only counts if something living
 * near where it was last seen was just hurt or has just died - the client gets that for any
 * damage, including the killing blow. Disappearing alone is not enough: an arrow flying past
 * the range the client tracks entities vanishes the same way, and that used to be reported
 * as a hit.
 *
 * <p>A hit that also kills its target gets its own cue (a low thud under a higher ding) instead of
 * the plain hit ding, so the two can be told apart by ear.
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

	// How far from its last seen position a hurt entity can be and still count as the arrow's
	// victim - a full-draw arrow covers about this much in the tick it lands.
	private static final double VICTIM_SEARCH_RADIUS = 4.0;

	// Ticks to wait after an arrow vanishes for the target's hurt/death to show up client-side.
	private static final int VERDICT_WAIT_TICKS = 3;

	// "Just hurt" and "just died", in ticks - an entity's hurt timer counts down from its full
	// duration and its death timer counts up from zero, so both are measured from those ends.
	private static final int RECENT_HURT_TICKS = 4;
	private static final int RECENT_DEATH_TICKS = 4;

	private static final Map<Integer, Tracked> watched = new HashMap<>();
	private static final List<Candidate> candidates = new ArrayList<>();

	// A trident that strikes something bounces off and stays in the world rather than vanishing
	// the way an arrow does, so for a trident the moment of impact is its speed collapsing
	// instead: from at least this many blocks/tick, down to under this fraction of its peak.
	private static final double TRIDENT_MIN_IMPACT_SPEED = 0.5;
	private static final double TRIDENT_IMPACT_SPEED_FRACTION = 0.5;

	private static final class Tracked {
		final boolean trident;
		Vec3 lastPos;
		int stillTicks;
		// Fastest per-tick movement seen since the last impact - what a trident's slowdown is
		// measured against.
		double peakSpeed;

		Tracked(boolean trident, Vec3 lastPos) {
			this.trident = trident;
			this.lastPos = lastPos;
		}
	}

	private static final class Candidate {
		final Vec3 lastPos;
		int ticksLeft = VERDICT_WAIT_TICKS;
		// A plain hurt was seen but not yet a death - held one more tick in case the death
		// event is still on its way, so a killing blow isn't announced as an ordinary hit.
		boolean sawHurt;

		Candidate(Vec3 lastPos) {
			this.lastPos = lastPos;
		}
	}

	private ArrowHitController() {
	}

	public static void reset() {
		watched.clear();
		candidates.clear();
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		AABB box = player.getBoundingBox().inflate(DISCOVERY_RANGE);
		for (Entity entity : player.level().getEntities(player, box,
				e -> e instanceof AbstractArrow arrow && arrow.getOwner() == player)) {
			watched.computeIfAbsent(entity.getId(), id -> {
				return new Tracked(entity instanceof ThrownTrident, entity.position());
			});
		}

		Iterator<Map.Entry<Integer, Tracked>> iterator = watched.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Tracked> entry = iterator.next();
			Tracked tracked = entry.getValue();
			Entity entity = player.level().getEntity(entry.getKey());
			if (entity == null) {
				// A trident that disappears has been picked up or recalled, never hit anything on
				// the way out - its impacts are caught by the speed check below instead.
				if (!tracked.trident && tracked.stillTicks < STILL_TICKS_FOR_SETTLED) {
					candidates.add(new Candidate(tracked.lastPos));
				}
				iterator.remove();
				continue;
			}
			Vec3 pos = entity.position();
			if (tracked.trident) {
				double speed = pos.distanceTo(tracked.lastPos);
				if (speed > tracked.peakSpeed) {
					tracked.peakSpeed = speed;
				} else if (tracked.peakSpeed >= TRIDENT_MIN_IMPACT_SPEED
						&& speed < tracked.peakSpeed * TRIDENT_IMPACT_SPEED_FRACTION) {
					candidates.add(new Candidate(pos));
					// Starts over, so a trident that comes back (Loyalty) and strikes again is
					// judged afresh instead of against its old peak.
					tracked.peakSpeed = 0.0;
				}
			}
			tracked.stillTicks = pos.distanceToSqr(tracked.lastPos) < STILL_EPSILON_SQ ? tracked.stillTicks + 1 : 0;
			tracked.lastPos = pos;
		}

		resolveCandidates(client, player);
	}

	private static void resolveCandidates(Minecraft client, LocalPlayer player) {
		Iterator<Candidate> iterator = candidates.iterator();
		while (iterator.hasNext()) {
			Candidate candidate = iterator.next();
			Outcome outcome = victimNear(player, candidate.lastPos);
			if (outcome == Outcome.KILL) {
				playKillCue(client, player);
				iterator.remove();
				continue;
			}
			if (outcome == Outcome.HURT) {
				if (candidate.sawHurt) {
					playHitCue(client, player);
					iterator.remove();
					continue;
				}
				candidate.sawHurt = true;
			}
			if (--candidate.ticksLeft <= 0) {
				if (candidate.sawHurt) {
					playHitCue(client, player);
				}
				iterator.remove();
			}
		}
	}

	private enum Outcome {
		NONE, HURT, KILL
	}

	/** The strongest thing that just happened to something living other than the player near {@code pos}: it died, it was hurt, or nothing. */
	private static Outcome victimNear(LocalPlayer player, Vec3 pos) {
		AABB box = new AABB(pos, pos).inflate(VICTIM_SEARCH_RADIUS);
		Outcome outcome = Outcome.NONE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, box, living -> living != player)) {
			if (living.isDeadOrDying() && living.deathTime <= RECENT_DEATH_TICKS) {
				return Outcome.KILL;
			}
			if (living.hurtTime > 0 && living.hurtTime > living.hurtDuration - RECENT_HURT_TICKS) {
				outcome = Outcome.HURT;
			}
		}
		return outcome;
	}

	/** The usual hit ding, lower and heavier, with a thud under it - a kill is meant to be told apart from a hit by ear alone. */
	private static void playKillCue(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.MASTER,
				CueVolume.scale(1.0f), 0.8f, random, player.getX(), player.getEyeY(), player.getZ()));
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ARROW_HIT_PLAYER, SoundSource.MASTER,
				CueVolume.scale(1.0f), 1.6f, random, player.getX(), player.getEyeY(), player.getZ()));
	}

	private static void playHitCue(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.ARROW_HIT_PLAYER, SoundSource.MASTER,
				CueVolume.scale(1.0f), 1.0f, random, player.getX(), player.getEyeY(), player.getZ()));
	}
}
