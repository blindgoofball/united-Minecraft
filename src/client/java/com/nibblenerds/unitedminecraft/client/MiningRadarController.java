package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Toggleable passive alert for ore exposed nearby while mining: every {@link #SCAN_INTERVAL_TICKS}
 * ticks, checks a small radius around the player for valuable ore that's newly exposed (see
 * {@link OreDetection#isExposed}) and hasn't already been alerted this session, then plays a
 * short positional "found something" chime plus full narration (name, distance, direction) -
 * the sound for an immediate, hard-to-miss cue that something's there, the narration for what
 * and where. Deliberately not x-ray, same as the scanner's Ores category, which shares the
 * exposure logic - this only ever notices ore a real player could already see through some gap.
 *
 * <p>Polling rather than hooking the actual break event: this project stays mixin-free, and a
 * short poll interval over a small radius is cheap while still feeling immediate.
 *
 * <p>A single scan can turn up several ore blocks at once (breaking into a small vein exposes
 * multiple faces together) - alerting all of them in the same tick would just have each new
 * narration cut the previous one off ({@code saySystemNow} interrupts). Newly found ore is
 * queued and drained one at a time, spaced a few ticks apart, instead.
 */
public final class MiningRadarController {
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int ALERT_INTERVAL_TICKS = 5;

	private static int ticksUntilScan;
	private static int ticksUntilNextAlert;
	private static final Set<BlockPos> alerted = new HashSet<>();
	private static final Deque<BlockPos> pending = new ArrayDeque<>();

	private MiningRadarController() {
	}

	/** Persisted in {@link UnitedMinecraftConfig} - stays on/off across world/session boundaries, same as Nav Radar. */
	public static boolean isEnabled() {
		return UnitedMinecraftConfig.get().miningRadarEnabled;
	}

	public static void toggle(Minecraft client) {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		config.miningRadarEnabled = !config.miningRadarEnabled;
		UnitedMinecraftConfig.save();
		ticksUntilScan = 0;
		pending.clear();
		client.getNarrator().saySystemNow(Component.translatable(config.miningRadarEnabled
				? "united_minecraft.narrate.mining_radar_on"
				: "united_minecraft.narrate.mining_radar_off"));
	}

	/** Clears per-session scan state only - {@link #isEnabled()} is a persistent preference, not session state. */
	public static void reset() {
		ticksUntilScan = 0;
		ticksUntilNextAlert = 0;
		alerted.clear();
		pending.clear();
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!isEnabled()) {
			return;
		}

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
	}

	private static void scan(LocalPlayer player) {
		Level level = player.level();
		BlockPos center = player.blockPosition();
		Vec3 eye = player.getEyePosition();
		int radius = UnitedMinecraftConfig.get().miningRadarRange;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (alerted.contains(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!OreDetection.isValuableOre(state) || !OreDetection.isExposed(level, pos, eye)) {
				continue;
			}
			BlockPos immutable = pos.immutable();
			alerted.add(immutable);
			pending.add(immutable);
		}
	}

	private static void alert(Minecraft client, LocalPlayer player, BlockPos pos) {
		Level level = player.level();
		BlockState state = level.getBlockState(pos);
		if (!OreDetection.isValuableOre(state)) {
			// Mined away (by the player or otherwise) before its turn in the queue came up.
			return;
		}

		RandomSource random = player.getRandom();
		Vec3 center = Vec3.atCenterOf(pos);
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 1.6f, random, center.x(), center.y(), center.z()));

		int distance = (int) Math.round(player.getEyePosition().distanceTo(center));
		Component direction = CameraUtil.compassDirectionTo(player.position(), center);
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.scanner_item", state.getBlock().getName(), distance, direction));
	}
}
