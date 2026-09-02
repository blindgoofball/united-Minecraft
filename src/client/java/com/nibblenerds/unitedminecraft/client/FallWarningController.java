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
 * Always-on warning for a fall of more than {@code UnitedMinecraftConfig.fallWarningThreshold}
 * blocks (defaults to matching vanilla's own damage-free threshold, {@link
 * #SAFE_FALL_DISTANCE}, but is a separate, user-configurable value - see {@link
 * SettingsScreen} - since "worth a warning" and "will it actually hurt" are different
 * questions once someone wants a heads-up on shorter drops too) coming up in the direction
 * the player is actually moving (their current horizontal velocity, not just where they're
 * facing, since sprint-strafing can point those two different ways). Every fall past that
 * height gets a cue, not only damaging ones. How far ahead to project is itself
 * speed-based - faster travel looks further ahead, matching how much ground you'll actually
 * cover - scaled by {@code UnitedMinecraftConfig.fallWarningLookaheadSeconds} (also a
 * SettingsScreen slider; see {@link #MAX_LOOKAHEAD_PER_SECOND}), so someone who wants more
 * reaction time than the ~1-second default can have it without losing the speed-scaling
 * behavior itself:
 * a distinct sound and narration for "this will hurt" versus "this won't" (e.g. a cave lake
 * below), so a safe drop into water is useful information rather than a false alarm.
 *
 * <p>Runs during ordinary manual walking and Build Mode alike, same scope as {@link
 * MovementAssistController} and {@link NavRadarController} - auto-walk, scanner lock-on, and
 * combat mode all drive movement or rotation themselves, but Build Mode only repurposes arrow
 * keys for its virtual cursor and never touches player movement, so the player can still walk
 * (and fall) normally while it's active. Requires standing on solid ground before projecting
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
	private static final double TICKS_PER_SECOND = 20.0;
	// Floor stays fixed regardless of the reaction-time setting - even at a near-stationary
	// (but above MIN_HORIZONTAL_SPEED_SQR) creep, there still needs to be *some* lookahead to
	// warn at all. The ceiling instead scales with fallWarningLookaheadSeconds (see
	// UnitedMinecraftConfig, SettingsScreen) - it's what that setting is actually for, so
	// raising the reaction time has to raise the cap too, not just recompute the same 3-8 block
	// range for a slower walk.
	private static final double MIN_LOOKAHEAD = 3.0;
	private static final double MAX_LOOKAHEAD_PER_SECOND = 8.0;
	// Distance between samples along the lookahead line. Small enough that a staircase of
	// single-block steps gets a sample on each tread, so a walkable descent updates the
	// reference ground level gradually instead of being measured against the far endpoint.
	private static final double SAMPLE_STEP = 1.0;
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
		if (!UnitedMinecraftConfig.get().fallWarningEnabled) {
			return;
		}

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

		double lookaheadSeconds = UnitedMinecraftConfig.get().fallWarningLookaheadSeconds;
		double speed = Math.sqrt(speedSqr);
		double lookahead = Mth.clamp(
				speed * lookaheadSeconds * TICKS_PER_SECOND, MIN_LOOKAHEAD, MAX_LOOKAHEAD_PER_SECOND * lookaheadSeconds);
		double dirX = vx / speed;
		double dirZ = vz / speed;

		Level level = player.level();

		// Walk the lookahead line in small steps rather than jumping straight to the far
		// endpoint, tracking the ground level as it goes. A staircase the player can walk
		// down normally (each tread a safe-or-smaller drop) advances the reference ground
		// level a step at a time instead of being measured all at once against the distant
		// endpoint, which would otherwise read as one big unwalkable fall. Only a drop past
		// the threshold relative to the *nearest* ground below it triggers a warning.
		double referenceY = player.getY();
		double aheadX = player.getX();
		double aheadZ = player.getZ();
		double prevX = player.getX();
		double prevZ = player.getZ();
		BlockPos landing = null;
		double dropHeight = 0;
		boolean dangerous = false;

		for (double d = SAMPLE_STEP; d <= lookahead + 1.0e-9; d += SAMPLE_STEP) {
			double sampleDist = Math.min(d, lookahead);
			double x = player.getX() + dirX * sampleDist;
			double z = player.getZ() + dirZ * sampleDist;

			// Swept across the whole segment since the last sample, not just tested at this
			// single point - a wall no wider than one sample step could otherwise sit entirely
			// between two sample points and never register as a collision at either of them.
			if (!canOccupySwept(level, player, prevX, prevZ, x, z, referenceY)
					|| !canOccupySwept(level, player, prevX, prevZ, x, z, referenceY + 1.0)) {
				// Blocked by a wall before any fall is even reachable in a straight line.
				return;
			}
			prevX = x;
			prevZ = z;

			landing = scanForLanding(level, Mth.floor(x), Mth.floor(referenceY), Mth.floor(z));
			// No floor at all down to the bottom of the world - falling out of it entirely,
			// which always kills. Still worth a (damaging) warning rather than staying silent.
			dropHeight = landing != null ? referenceY - (landing.getY() + 1) : referenceY - level.getMinY();
			aheadX = x;
			aheadZ = z;

			if (dropHeight > UnitedMinecraftConfig.get().fallWarningThreshold) {
				dangerous = true;
				break;
			}

			if (landing == null) {
				// Fell out of the bottom of the world without ever exceeding the threshold
				// (i.e. the threshold is configured above the world depth) - nothing more to
				// scan for past this point.
				break;
			}

			referenceY = landing.getY() + 1;
			if (sampleDist >= lookahead) {
				break;
			}
		}

		if (!dangerous) {
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
		warn(client, player, new Vec3(aheadX, referenceY, aheadZ), dropHeight, damaging);
	}

	/**
	 * Whether the player's own bounding box would fit anywhere along the straight line from
	 * {@code (fromX, fromZ)} to {@code (toX, toZ)} at height {@code y}, without colliding with
	 * anything. Swept across the whole segment (via {@link AABB#expandTowards}, the same
	 * technique vanilla's own movement collision uses) rather than tested only at the
	 * endpoint, so a wall no wider than one sample step can't sit entirely between two sample
	 * points and slip through undetected.
	 */
	private static boolean canOccupySwept(Level level, LocalPlayer player, double fromX, double fromZ, double toX, double toZ, double y) {
		AABB box = player.getBoundingBox().move(fromX - player.getX(), y - player.getY(), fromZ - player.getZ());
		AABB swept = box.expandTowards(toX - fromX, 0, toZ - fromZ);
		return level.noCollision(player, swept);
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
