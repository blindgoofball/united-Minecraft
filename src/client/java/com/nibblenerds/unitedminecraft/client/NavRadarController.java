package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Toggleable audio "radar": a single straight ray each for front, right, and left, relative
 * to facing snapped to the nearest cardinal (behind is deliberately not scanned). Each ray
 * is a genuine geometric raycast ({@link Level#clip}) against the world's actual collision
 * shapes. A hit's height above the player's feet, compared against their own step-up and
 * jump reach, classifies it open/jumpable/blocked; wall cues reuse the hit block's own
 * {@code SoundType} place sound (so a stone wall and a wood wall already sound different,
 * free, and a placed block's own sound reads as more solid/distinct than its hit sound),
 * pitched up for jumpable vs blocked, while an open direction plays a short chime positioned
 * that way.
 *
 * <p>State is tracked per absolute compass bearing (0/90/270), not per direction index -
 * tracking by index would make every direction look "changed" on every 90-degree turn,
 * since the index-to-bearing mapping rotates with facing. Cues are narrated only on a state
 * change, staggered a few ticks apart so several simultaneous changes trickle out one at a
 * time instead of overlapping, and a per-bearing cooldown stops a direction flickering
 * across a classification boundary from cuing repeatedly.
 */
public final class NavRadarController {
	private static final int[] DIRECTION_OFFSETS = {0, 90, 270};
	private static final double PROBE_HEIGHT = 0.1;
	private static final double JUMP_HEIGHT = 1.25;

	private static final int PLAY_INTERVAL_TICKS = 3;
	private static final int BEARING_COOLDOWN_TICKS = 10;

	private static final float JUMPABLE_PITCH = 2.0f;
	private static final float BLOCKED_PITCH = 1.5f;

	private static boolean enabled = false;
	private static int ticks = 0;
	private static int lastGlobalPlayTick = -PLAY_INTERVAL_TICKS;

	private static final Map<Integer, ObstacleState> lastState = new HashMap<>();
	private static final Map<Integer, Integer> lastPlayedTick = new HashMap<>();
	private static final List<PendingCue> pending = new ArrayList<>();

	private NavRadarController() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle(Minecraft client) {
		enabled = !enabled;
		if (!enabled) {
			reset();
		}
		client.getNarrator().saySystemNow(Component.translatable(enabled
				? "united_minecraft.narrate.nav_radar_on"
				: "united_minecraft.narrate.nav_radar_off"));
	}

	public static void reset() {
		lastState.clear();
		lastPlayedTick.clear();
		pending.clear();
		ticks = 0;
		lastGlobalPlayTick = -PLAY_INTERVAL_TICKS;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!enabled) {
			return;
		}
		ticks++;

		Level level = player.level();
		int playerBearing = Math.floorMod(Math.round(player.getYRot()) + 180, 360);
		int facing = Math.floorMod(Math.round(playerBearing / 90.0f) * 90, 360);

		for (int offset : DIRECTION_OFFSETS) {
			int bearing = Math.floorMod(facing + offset, 360);
			ScanResult result = probe(level, player, bearing);

			if (lastState.get(bearing) == result.state()) {
				continue;
			}
			lastState.put(bearing, result.state());

			Integer lastPlayedAt = lastPlayedTick.get(bearing);
			if (lastPlayedAt != null && ticks - lastPlayedAt < BEARING_COOLDOWN_TICKS) {
				continue;
			}
			pending.removeIf(cue -> cue.center() == bearing);
			pending.add(new PendingCue(bearing, result));
		}

		if (!pending.isEmpty() && ticks - lastGlobalPlayTick >= PLAY_INTERVAL_TICKS) {
			int index = 0;
			for (int i = 0; i < pending.size(); i++) {
				if (pending.get(i).result().state() == ObstacleState.OPEN) {
					index = i;
					break;
				}
			}
			PendingCue cue = pending.remove(index);
			playCue(client, player, cue.result());
			lastPlayedTick.put(cue.center(), ticks);
			lastGlobalPlayTick = ticks;
		}
	}

	private static ScanResult probe(Level level, LocalPlayer player, double bearingDeg) {
		double rad = Math.toRadians(bearingDeg);
		double dirX = Math.sin(rad);
		double dirZ = -Math.cos(rad);

		// Just above the feet, not at eye height - a low fence or wall should still be
		// found even though a person could see clean over the top of it.
		int radarRange = UnitedMinecraftConfig.get().navRadarRange;
		double rayY = player.getY() + PROBE_HEIGHT;
		Vec3 from = new Vec3(player.getX(), rayY, player.getZ());
		Vec3 to = new Vec3(player.getX() + dirX * radarRange, rayY, player.getZ() + dirZ * radarRange);

		ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
		BlockHitResult hit = level.clip(context);
		if (hit.getType() == HitResult.Type.MISS) {
			return new ScanResult(ObstacleState.OPEN, bearingDeg, radarRange, null);
		}

		BlockPos hitPos = hit.getBlockPos();
		BlockState hitState = level.getBlockState(hitPos);
		double top = hitPos.getY() + hitState.getCollisionShape(level, hitPos).bounds().maxY;
		double rise = top - player.getY();
		double distance = from.distanceTo(hit.getLocation());

		ObstacleState state = rise <= player.maxUpStep()
				? ObstacleState.OPEN
				: rise <= JUMP_HEIGHT ? ObstacleState.JUMPABLE : ObstacleState.BLOCKED;
		return new ScanResult(state, bearingDeg, distance, hitState);
	}

	private static void playCue(Minecraft client, LocalPlayer player, ScanResult result) {
		double rad = Math.toRadians(result.bearing());
		double dirX = Math.sin(rad);
		double dirZ = -Math.cos(rad);
		RandomSource random = player.getRandom();

		if (result.state() == ObstacleState.OPEN) {
			double x = player.getX() + dirX;
			double z = player.getZ() + dirZ;
			client.getSoundManager().play(new SimpleSoundInstance(
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6f, 1.0f, random, x, player.getY(), z));
			return;
		}

		SoundEvent sound = result.hitBlock().getSoundType().getPlaceSound();
		float pitch = result.state() == ObstacleState.JUMPABLE ? JUMPABLE_PITCH : BLOCKED_PITCH;
		double distance = Math.min(result.distance(), UnitedMinecraftConfig.get().navRadarRange);
		double x = player.getX() + dirX * distance;
		double z = player.getZ() + dirZ * distance;
		client.getSoundManager().play(new SimpleSoundInstance(
				sound, SoundSource.MASTER, 1.0f, pitch, random, x, player.getY(), z));
	}

	private enum ObstacleState {
		OPEN, JUMPABLE, BLOCKED
	}

	private record ScanResult(ObstacleState state, double bearing, double distance, BlockState hitBlock) {
	}

	private record PendingCue(int center, ScanResult result) {
	}
}
