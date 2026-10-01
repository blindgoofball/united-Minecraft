package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the camera's pitch on whatever block a repetitive building or digging job needs next,
 * so the player only has to keep yaw square and hold the attack or use key. Cycled with {@link
 * ClientKeyBindings#ANGLE_ASSIST_CYCLE}: off, bridge, tunnel, staircase.
 *
 * <p>Only pitch is ever touched - yaw stays entirely the player's, so it must be squared to a
 * compass direction (Alt+arrow snap-turns do that) for the aim to line up. Every tick the mode
 * works out which block comes next and tilts the crosshair onto it. Because the target is
 * always the first block of the pattern still standing, a fast pickaxe that breaks a block
 * the instant it is hit just moves the crosshair on to the next one in the pattern, never to
 * something beyond it - which is how holding the attack key used to dig extra.
 *
 * <ul>
 * <li><b>Tunnel</b>: a two-high tunnel straight ahead, taking the head-height block and then
 * the foot-height block at each distance, nearest first.
 * <li><b>Staircase</b>: a one-down-per-one-forward staircase. Each step clears three blocks
 * in its column - one above and one at foot height of the position being stepped from, plus
 * the one stepped down onto - because the player has to walk horizontally into that column
 * (two blocks tall) before dropping into it.
 * <li><b>Bridge</b>: aims at the side of the block being bridged out from. That face points
 * toward the void, so it can only be seen from beyond the edge looking back at it: face the
 * block and back up until you are over the edge. A forward-facing player looking down at the
 * edge hits the top of the block first, never the side.
 * </ul>
 *
 * <p>Only active during normal camera control, like {@link OreMiningAssist} - modes that own
 * rotation take precedence.
 */
public final class AngleAssistController {
	private enum Mode {
		OFF, BRIDGE, TUNNEL, STAIRS
	}

	private enum BridgeState {
		NONE, NO_SUPPORT, TOO_FAR, READY
	}

	// How far ahead of the player the patterns are searched - a little past reach, so the
	// crosshair is already on the next block when the player steps forward.
	private static final int TUNNEL_DEPTH = 6;
	private static final int STAIR_STEPS = 4;
	// A target closer than this along the line of sight can't be aimed at sensibly.
	private static final double MIN_AIM_DISTANCE = 0.05;
	// How high up the bridged-from block's side the crosshair is aimed.
	private static final double BRIDGE_FACE_HEIGHT = 0.8;

	// How far from its anchor a staircase can drift before it starts over from where it is.
	private static final int STAIR_ANCHOR_DRIFT = 2;
	// A target this far behind the eye along the line of sight is still straight below the player.
	private static final double STRAIGHT_DOWN_REACH = 0.45;

	private static Mode mode = Mode.OFF;
	private static BridgeState lastBridgeState = BridgeState.NONE;
	// Where the current staircase step began and which way it faces - see #stairBase.
	private static BlockPos stairAnchor;
	private static Direction stairFacing;
	// The block being worked on, kept until it is gone - see #aimAtPattern.
	private static BlockPos latchedTarget;
	private static Direction latchedFacing;

	private AngleAssistController() {
	}

	public static void reset() {
		mode = Mode.OFF;
		lastBridgeState = BridgeState.NONE;
		stairAnchor = null;
		latchedTarget = null;
	}

	public static void cycle(Minecraft client) {
		Mode[] modes = Mode.values();
		mode = modes[(mode.ordinal() + 1) % modes.length];
		lastBridgeState = BridgeState.NONE;
		stairAnchor = null;
		latchedTarget = null;
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.angle_mode_" + mode.name().toLowerCase(java.util.Locale.ROOT)));
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		switch (mode) {
			case TUNNEL -> aimAtPattern(client, player, BlockPos.containing(player.position()), TUNNEL_DEPTH,
					AngleAssistController::tunnelOffsets);
			case STAIRS -> aimAtPattern(client, player, stairBase(player), STAIR_STEPS,
					AngleAssistController::stairOffsets);
			case BRIDGE -> aimAtBridge(client, player);
			case OFF -> {
			}
		}
	}

	/** Height offsets from the player's feet cell to clear at {@code distance} blocks ahead, in the order they should be broken. */
	private interface Pattern {
		int[] offsets(int distance);
	}

	private static int[] tunnelOffsets(int distance) {
		return new int[] {1, 0};
	}

	private static int[] stairOffsets(int distance) {
		return new int[] {2 - distance, 1 - distance, -distance};
	}

	/**
	 * The cell a staircase's step pattern is measured from. Measuring from the player's current
	 * cell every tick breaks down mid-step: walking forward after the two upper blocks are dug but
	 * before the lower one is moves the player's own cell forward, which puts that undug block
	 * directly underfoot where the pattern (which starts one block ahead) never looks, so the
	 * staircase would start over in front of them. So the pattern stays anchored where the step
	 * began until the player has actually dropped below it, wandered off, or turned around.
	 */
	private static BlockPos stairBase(LocalPlayer player) {
		BlockPos feet = BlockPos.containing(player.position());
		Direction facing = Direction.fromYRot(player.getYRot());
		if (stairAnchor == null || facing != stairFacing || feet.getY() != stairAnchor.getY()
				|| Math.abs(feet.getX() - stairAnchor.getX()) > STAIR_ANCHOR_DRIFT
				|| Math.abs(feet.getZ() - stairAnchor.getZ()) > STAIR_ANCHOR_DRIFT) {
			stairAnchor = feet;
			stairFacing = facing;
		}
		return stairAnchor;
	}

	/**
	 * Aims at the next block of the pattern. Two things keep the crosshair from wandering while
	 * the player holds forward and the attack key, because moving the crosshair off a block that
	 * is part-way through breaking throws its progress away and starts it over:
	 * <ul>
	 * <li>The block being worked on is latched for as long as it is within reach and still
	 * standing, rather than re-picked from the pattern every tick as the player's position
	 * shifts.
	 * <li>If the crosshair is already on that block, the pitch is left alone entirely instead of
	 * being nudged by the fraction of a degree walking forward changes it by.
	 * </ul>
	 */
	private static void aimAtPattern(Minecraft client, LocalPlayer player, BlockPos feet, int depth, Pattern pattern) {
		Level level = player.level();
		Direction facing = Direction.fromYRot(player.getYRot());

		BlockPos target = null;
		if (latchedTarget != null && latchedFacing == facing && needsBreaking(level, latchedTarget)
				&& player.getEyePosition().distanceTo(Vec3.atCenterOf(latchedTarget)) <= player.blockInteractionRange()) {
			target = latchedTarget;
		}
		if (target == null) {
			target = nextInPattern(level, feet, facing, depth, pattern);
		}
		latchedTarget = target;
		latchedFacing = facing;
		if (target == null) {
			return;
		}

		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& hit.getBlockPos().equals(target)) {
			return;
		}
		if (!aimPitchAt(player, Vec3.atCenterOf(target), true)) {
			latchedTarget = null;
		}
	}

	private static BlockPos nextInPattern(Level level, BlockPos feet, Direction facing, int depth, Pattern pattern) {
		for (int distance = 1; distance <= depth; distance++) {
			for (int offset : pattern.offsets(distance)) {
				BlockPos cell = feet.relative(facing, distance).above(offset);
				if (needsBreaking(level, cell)) {
					return cell;
				}
			}
		}
		return null;
	}

	private static boolean needsBreaking(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		// Fluids can't be broken, and unbreakable blocks (bedrock) would pin the aim there for
		// good - either way there is nothing for the crosshair to do about them.
		return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0f;
	}

	private static void aimAtBridge(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		Direction facing = Direction.fromYRot(player.getYRot());
		BlockPos below = BlockPos.containing(player.position()).below();

		BlockPos support = null;
		for (int i = 0; i <= 2; i++) {
			BlockPos candidate = below.relative(facing, i);
			BlockState state = level.getBlockState(candidate);
			if (!state.isAir() && !state.canBeReplaced()) {
				support = candidate;
				break;
			}
		}
		if (support == null) {
			announceBridge(client, BridgeState.NO_SUPPORT);
			return;
		}

		// The side of the support facing the player, i.e. the side the next block goes against.
		Vec3 face = Vec3.atCenterOf(support).add(-facing.getStepX() * 0.5, 0.0, -facing.getStepZ() * 0.5)
				.with(Direction.Axis.Y, support.getY() + BRIDGE_FACE_HEIGHT);
		if (!aimPitchAt(player, face, false)) {
			announceBridge(client, BridgeState.TOO_FAR);
			return;
		}
		announceBridge(client, BridgeState.READY);
	}

	private static void announceBridge(Minecraft client, BridgeState state) {
		if (state == lastBridgeState) {
			return;
		}
		lastBridgeState = state;
		client.getNarrator().saySystemNow(Component.translatable(switch (state) {
			case NO_SUPPORT -> "united_minecraft.narrate.angle_bridge_no_support";
			case TOO_FAR -> "united_minecraft.narrate.angle_bridge_back_up";
			default -> "united_minecraft.narrate.angle_bridge_ready";
		}));
	}

	/**
	 * Sets only the pitch that makes the crosshair pass through {@code target}, measured along
	 * the player's current yaw. When the target is not ahead of the player along that line this
	 * returns false and changes nothing - except with {@code allowStraightDown}, where a target
	 * that is right below the player is aimed at by looking straight down.
	 */
	private static boolean aimPitchAt(LocalPlayer player, Vec3 target, boolean allowStraightDown) {
		Vec3 eye = player.getEyePosition();
		double yawRadians = Math.toRadians(player.getYRot());
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double ahead = (target.x() - eye.x()) * forwardX + (target.z() - eye.z()) * forwardZ;
		if (ahead < MIN_AIM_DISTANCE) {
			if (allowStraightDown && ahead > -STRAIGHT_DOWN_REACH && target.y() < eye.y()) {
				player.setXRot(90.0f);
				player.setOldRot();
				return true;
			}
			return false;
		}
		float pitch = (float) Math.toDegrees(Math.atan2(-(target.y() - eye.y()), ahead));
		player.setXRot(Math.clamp(pitch, -90.0f, 90.0f));
		player.setOldRot();
		return true;
	}
}
