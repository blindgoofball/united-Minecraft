package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Warns before walking into powder snow, gated by {@link UnitedMinecraftConfig#powderSnowWarningEnabled}:
 * powder snow has no collision, so a player walks straight into it, sinks, slows right down and starts
 * to freeze, and nothing about it can be felt coming. A sighted player sees the white block.
 *
 * <p>Looks a few blocks ahead along the way the player is actually moving and speaks once when powder
 * snow comes into the path, then stays quiet until the player has stopped or got clear of it. What
 * counts as walking into it is decided by {@link #entersPowderSnow}. Wearing leather boots is the one
 * thing that lets a player walk across the top of it rather than through it, so ground of powder snow
 * is not a warning for them - but a block of it at body height still is.
 *
 * <p>The fall warning ({@link FallWarningController}) already names powder snow as something a drop
 * would land in; this is for walking into it along level ground.
 */
public final class PowderSnowWarningController {
	private static final double MIN_HORIZONTAL_SPEED_SQR = 0.02 * 0.02;
	private static final double TICKS_PER_SECOND = 20.0;
	private static final double MIN_LOOKAHEAD = 1.5;
	private static final double MAX_LOOKAHEAD = 5.0;
	private static final double SAMPLE_STEP = 0.5;
	// Ticks without powder snow ahead before the next sighting counts as a new one.
	private static final int CLEAR_TICKS_TO_REARM = 10;

	private static boolean warned;
	private static int clearTicks;

	private PowderSnowWarningController() {
	}

	public static void reset() {
		warned = false;
		clearTicks = 0;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().powderSnowWarningEnabled || player.isSpectator() || player.isFallFlying()
				|| !player.onGround()) {
			reset();
			return;
		}
		Level level = player.level();
		double vx = player.getDeltaMovement().x;
		double vz = player.getDeltaMovement().z;
		double speedSqr = vx * vx + vz * vz;
		if (speedSqr < MIN_HORIZONTAL_SPEED_SQR || level.getBlockState(player.blockPosition()).is(Blocks.POWDER_SNOW)) {
			// Stopped, or already in it: nothing to walk into, and a later approach is a new one.
			reset();
			return;
		}

		double speed = Math.sqrt(speedSqr);
		double lookahead = Mth.clamp(speed * TICKS_PER_SECOND, MIN_LOOKAHEAD, MAX_LOOKAHEAD);
		boolean canWalkOnTop = PowderSnowBlock.canEntityWalkOnPowderSnow(player);
		int feetY = Mth.floor(player.getY());

		double found = -1.0;
		for (double d = SAMPLE_STEP; d <= lookahead + 1.0e-9; d += SAMPLE_STEP) {
			int x = Mth.floor(player.getX() + vx / speed * d);
			int z = Mth.floor(player.getZ() + vz / speed * d);
			BlockState feet = level.getBlockState(new BlockPos(x, feetY, z));
			BlockState head = level.getBlockState(new BlockPos(x, feetY + 1, z));
			BlockState ground = level.getBlockState(new BlockPos(x, feetY - 1, z));
			if (entersPowderSnow(feet.is(Blocks.POWDER_SNOW), head.is(Blocks.POWDER_SNOW), ground.is(Blocks.POWDER_SNOW),
					feet.getCollisionShape(level, new BlockPos(x, feetY, z)).isEmpty(), canWalkOnTop)) {
				found = d;
				break;
			}
			if (!feet.is(Blocks.POWDER_SNOW) && !feet.getCollisionShape(level, new BlockPos(x, feetY, z)).isEmpty()) {
				// A wall in the way: whatever lies past it cannot be walked into from here.
				break;
			}
		}

		if (found < 0.0) {
			if (++clearTicks >= CLEAR_TICKS_TO_REARM) {
				warned = false;
			}
			return;
		}
		clearTicks = 0;
		if (warned) {
			return;
		}
		warned = true;
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.MASTER,
				CueVolume.scale(1.0f), 0.7f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.powder_snow_ahead",
				Math.max(1, (int) Math.round(found))));
	}

	/**
	 * Whether stepping into a column would put the player in powder snow. A block of it at the feet or the
	 * head is walked into whatever is worn. Powder snow as the ground under open air is walked into too, as
	 * it has no collision to stand on, unless the player's boots let them stand on top of it.
	 */
	static boolean entersPowderSnow(boolean feetIsPowderSnow, boolean headIsPowderSnow, boolean groundIsPowderSnow,
			boolean feetCellIsEmpty, boolean canWalkOnTop) {
		if (feetIsPowderSnow || headIsPowderSnow) {
			return true;
		}
		return groundIsPowderSnow && feetCellIsEmpty && !canWalkOnTop;
	}
}
