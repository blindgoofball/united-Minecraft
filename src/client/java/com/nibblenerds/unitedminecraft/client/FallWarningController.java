package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Always-on warning for a fall of more than {@link #SAFE_FALL_DISTANCE} blocks - vanilla's own
 * damage-free threshold - coming up in the direction the player is actually moving (their
 * current horizontal velocity, not just where they're facing, since sprint-strafing can point
 * those two different ways). Every fall past that height gets a cue, not only damaging ones:
 * a distinct sound and narration for "this will hurt" versus "this won't" (e.g. a cave lake
 * below), so a safe drop into water is useful information rather than a false alarm.
 *
 * <p>Only runs during ordinary manual walking, same scope as {@link MovementAssistController}
 * and {@link NavRadarController} - auto-walk, build mode, scanner lock-on, and combat mode all
 * drive movement or rotation themselves. Requires standing on solid ground before projecting
 * ahead (so it fires once, proactively, before you actually step off an edge) and is skipped
 * entirely in Creative/Spectator, while gliding, and while Slow Falling or Levitation is active,
 * since none of those can take fall damage in the first place - though the last two still get no
 * warning at all rather than a "safe" one, since the whole point is knowing what's coming up.
 *
 * <p>The landing spot's block matters for whether a fall actually damages: water negates it
 * entirely, a slime block always negates it (bouncing or not), a hay block reduces the final
 * damage to a fifth, and a bed halves the effective fall distance before the 3-block threshold
 * is even subtracted - all mirrored here from vanilla's own {@code fallOn} overrides so the
 * "will this hurt" call matches what actually happens on landing.
 */
public final class FallWarningController {
	/** Matches {@code Entity.BASE_SAFE_FALL_DISTANCE} - falls at or under this never damage. */
	private static final double SAFE_FALL_DISTANCE = 3.0;

	private static final double MIN_HORIZONTAL_SPEED_SQR = 0.02 * 0.02;
	// ~1 second of travel at current speed, so there's actually time to react and stop.
	private static final double LOOKAHEAD_TICKS = 20.0;
	private static final double MIN_LOOKAHEAD = 3.0;
	private static final double MAX_LOOKAHEAD = 8.0;
	private static final int RE_WARN_COOLDOWN_TICKS = 40;

	private static int ticks;
	private static BlockPos lastWarnedColumn;
	private static int lastWarnTick = -RE_WARN_COOLDOWN_TICKS;

	private FallWarningController() {
	}

	public static void reset() {
		ticks = 0;
		lastWarnedColumn = null;
		lastWarnTick = -RE_WARN_COOLDOWN_TICKS;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		ticks++;

		if (!player.onGround() || player.isSpectator() || player.getAbilities().invulnerable
				|| player.isFallFlying() || player.hasEffect(MobEffects.SLOW_FALLING) || player.hasEffect(MobEffects.LEVITATION)) {
			return;
		}

		double vx = player.getDeltaMovement().x;
		double vz = player.getDeltaMovement().z;
		double speedSqr = vx * vx + vz * vz;
		if (speedSqr < MIN_HORIZONTAL_SPEED_SQR) {
			// Stationary - clear so re-approaching the same edge later warns again instead of
			// staying silent because of the cooldown below.
			lastWarnedColumn = null;
			return;
		}

		double speed = Math.sqrt(speedSqr);
		double lookahead = Mth.clamp(speed * LOOKAHEAD_TICKS, MIN_LOOKAHEAD, MAX_LOOKAHEAD);
		double dirX = vx / speed;
		double dirZ = vz / speed;

		Level level = player.level();
		double aheadX = player.getX() + dirX * lookahead;
		double aheadZ = player.getZ() + dirZ * lookahead;

		if (!canOccupy(level, player, aheadX, player.getY(), aheadZ)
				|| !canOccupy(level, player, aheadX, player.getY() + 1.0, aheadZ)) {
			// Blocked by a wall before any fall is even reachable in a straight line.
			return;
		}

		BlockPos landing = scanForLanding(level, Mth.floor(aheadX), Mth.floor(player.getY()), Mth.floor(aheadZ));
		// No floor at all down to the bottom of the world - falling out of it entirely, which
		// always kills. Still worth a (damaging) warning rather than staying silent.
		double dropHeight = landing != null ? player.getY() - (landing.getY() + 1) : player.getY() - level.getMinY();
		if (dropHeight <= SAFE_FALL_DISTANCE) {
			return;
		}

		BlockPos column = new BlockPos(Mth.floor(aheadX), 0, Mth.floor(aheadZ));
		if (column.equals(lastWarnedColumn) && ticks - lastWarnTick < RE_WARN_COOLDOWN_TICKS) {
			return;
		}
		lastWarnedColumn = column;
		lastWarnTick = ticks;

		boolean damaging = landing == null || wouldDamage(player, level.getBlockState(landing), dropHeight);
		// Played at the edge itself, not the landing spot below - for anything but a short
		// drop, the landing spot is far enough away (or, for a void fall, doesn't exist) that
		// positional attenuation would make it inaudible right when it matters most.
		warn(client, player, new Vec3(aheadX, player.getY(), aheadZ), dropHeight, damaging);
	}

	/** Whether the player's own bounding box would fit at this position without colliding with anything. */
	private static boolean canOccupy(Level level, LocalPlayer player, double x, double y, double z) {
		AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
		return level.noCollision(player, box);
	}

	/** First solid or fluid block found scanning straight down from {@code startY} to the bottom of the world, or null if none. */
	private static BlockPos scanForLanding(Level level, int x, int startY, int z) {
		int minY = level.getMinY();
		BlockPos pos = new BlockPos(x, startY, z);
		while (pos.getY() >= minY) {
			BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) {
				return pos;
			}
			pos = pos.below();
		}
		return null;
	}

	/** Mirrors vanilla's own fall-damage mitigations for the landing spot to decide whether this specific fall actually hurts. */
	private static boolean wouldDamage(LocalPlayer player, BlockState landingState, double dropHeight) {
		if (landingState.getFluidState().is(FluidTags.WATER)) {
			return false;
		}

		Block block = landingState.getBlock();
		if (block instanceof SlimeBlock) {
			return false;
		}

		double effectiveDrop = dropHeight;
		float damageModifier = 1.0f;
		if (block instanceof BedBlock) {
			// BedBlock#fallOn halves the fall distance itself before the safe-distance subtraction.
			effectiveDrop *= 0.5;
		} else if (block instanceof HayBlock) {
			// HayBlock#fallOn instead reduces the final damage to a fifth.
			damageModifier = 0.2f;
		}

		int damage = (int) Math.floor((effectiveDrop - SAFE_FALL_DISTANCE) * damageModifier);
		return damage > 0;
	}

	private static void warn(Minecraft client, LocalPlayer player, Vec3 pos, double dropHeight, boolean damaging) {
		RandomSource random = player.getRandom();
		// Note block tones (even at high volume) read as quiet/soft - an anvil landing is
		// inherently loud and unmistakably "bad", a clear contrast against the bright pling
		// for a safe drop.
		client.getSoundManager().play(damaging
				? new SimpleSoundInstance(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 1.0f, 0.8f, random, pos.x(), pos.y(), pos.z())
				: new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1.5f, 1.3f, random, pos.x(), pos.y(), pos.z()));

		int blocks = (int) Math.round(dropHeight);
		client.getNarrator().saySystemNow(Component.translatable(damaging
				? "united_minecraft.narrate.fall_warning_damaging"
				: "united_minecraft.narrate.fall_warning_safe", blocks));
	}
}
