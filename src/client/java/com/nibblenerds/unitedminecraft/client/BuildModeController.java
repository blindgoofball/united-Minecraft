package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * "Build mode": a virtual cursor for exploring and targeting blocks without
 * needing to physically turn to face them.
 *
 * <p>While active, the arrow keys and Page Up/Down step a {@link BlockPos}
 * cursor instead of turning the camera, narrating the block, coordinates, and
 * whether it's placeable at each step. Movement is fixed to true compass
 * directions (Left/Right = west/east, Up/Down = north/south) regardless of
 * which way the player is currently facing - deliberately, not a bug: it's a
 * consistent, learnable mapping rather than one that flips depending on facing.
 *
 * <p>After every move, the player's actual look direction is snapped to aim
 * precisely at the cursor - not by faking interaction, but by pointing the real
 * camera there, so vanilla's own break/place logic (which just uses whatever
 * you're looking at) naturally targets the cursor for free.
 *
 * <p>If the cursor sits on a replaceable cell (air, tall grass, water, etc.),
 * aiming at its bare center wouldn't let you place into it - the raycast would
 * sail through and hit whatever's beyond. So in that case the aim point is
 * nudged to the sturdy face of whichever neighboring block is most "on the
 * player's side" of the cursor (using the same rule vanilla itself uses to
 * decide if a face supports placement), so the resulting hit face plants a
 * placed block exactly in the cursor cell. If no neighbor face qualifies, this
 * falls back to just looking at the cursor's center.
 */
public final class BuildModeController {
	// Kept in step with SurroundingsScanner's reach, since a cursor position
	// vanilla itself won't let you interact with isn't useful to aim at anyway.
	private static final double RANGE_BLOCKS = 6.0;
	private static final double FACE_EPSILON = 0.001;

	private static boolean active;
	private static BlockPos cursor;

	private BuildModeController() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void reset() {
		active = false;
		cursor = null;
	}

	public static void toggle(Minecraft client, LocalPlayer player) {
		active = !active;
		if (active) {
			cursor = player.blockPosition();
			aimAtCursor(player);
			Component message = Component.translatable("united_minecraft.narrate.build_mode_on")
					.append(Component.literal(" "))
					.append(describeCursor(player));
			client.getNarrator().saySystemNow(message);
		} else {
			cursor = null;
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_mode_off"));
		}
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean moved = false;
		if (ClientKeyBindings.LOOK_LEFT.consumeClick()) {
			moved |= tryMove(client, player, cursor.west());
		}
		if (ClientKeyBindings.LOOK_RIGHT.consumeClick()) {
			moved |= tryMove(client, player, cursor.east());
		}
		if (ClientKeyBindings.LOOK_UP.consumeClick()) {
			moved |= tryMove(client, player, cursor.north());
		}
		if (ClientKeyBindings.LOOK_DOWN.consumeClick()) {
			moved |= tryMove(client, player, cursor.south());
		}
		if (ClientKeyBindings.BUILD_CURSOR_RAISE.consumeClick()) {
			moved |= tryMove(client, player, cursor.above());
		}
		if (ClientKeyBindings.BUILD_CURSOR_LOWER.consumeClick()) {
			moved |= tryMove(client, player, cursor.below());
		}

		if (moved) {
			aimAtCursor(player);
			client.getNarrator().saySystemNow(describeCursor(player));
		}
	}

	private static boolean tryMove(Minecraft client, LocalPlayer player, BlockPos candidate) {
		if (player.getEyePosition().distanceTo(Vec3.atCenterOf(candidate)) > RANGE_BLOCKS) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.build_out_of_reach"));
			return false;
		}
		cursor = candidate;
		return true;
	}

	private static Component describeCursor(LocalPlayer player) {
		Level level = player.level();
		Component blockName = level.getBlockState(cursor).getBlock().getName();
		MutableComponent message = Component.translatable(
				"united_minecraft.narrate.build_cursor", blockName, cursor.getX(), cursor.getY(), cursor.getZ());
		if (isPlaceable(level, player)) {
			message = message.append(Component.literal(" ")).append(Component.translatable("united_minecraft.narrate.build_placeable"));
		}
		return message;
	}

	/** Would using a placeable item right now, aimed via {@link #aimAtCursor}, actually place a block at the cursor? */
	private static boolean isPlaceable(Level level, LocalPlayer player) {
		BlockState state = level.getBlockState(cursor);
		if (!state.canBeReplaced()) {
			return false;
		}
		if (player.getBoundingBox().intersects(cursor)) {
			return false;
		}
		return findSupportFace(level, cursor, player.getEyePosition()) != null;
	}

	private static void aimAtCursor(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 aimPoint = computeAimPoint(player.level(), eye);

		double dx = aimPoint.x() - eye.x();
		double dy = aimPoint.y() - eye.y();
		double dz = aimPoint.z() - eye.z();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		// Inverse of Entity.calculateViewVector: x = -sin(yaw)*cos(pitch), y = -sin(pitch), z = cos(yaw)*cos(pitch).
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
	}

	private static Vec3 computeAimPoint(Level level, Vec3 eye) {
		Vec3 center = Vec3.atCenterOf(cursor);
		BlockState state = level.getBlockState(cursor);
		if (!state.canBeReplaced()) {
			return center;
		}

		Direction face = findSupportFace(level, cursor, eye);
		if (face == null) {
			// No neighboring face to place against within reach; best effort, just look at the empty cell.
			return center;
		}
		return center.add(
				face.getStepX() * (0.5 - FACE_EPSILON),
				face.getStepY() * (0.5 - FACE_EPSILON),
				face.getStepZ() * (0.5 - FACE_EPSILON));
	}

	/**
	 * Finds the neighboring face of {@code pos} that a placement would rest against,
	 * preferring whichever sturdy neighbor is most "on the same side" as the player -
	 * both to feel natural and to reduce the chance of picking a face that's actually
	 * occluded from the player's real line of sight.
	 */
	private static Direction findSupportFace(Level level, BlockPos pos, Vec3 eye) {
		Vec3 towardEye = eye.subtract(Vec3.atCenterOf(pos));
		Direction bestFace = null;
		double bestAlignment = Double.NEGATIVE_INFINITY;
		for (Direction face : Direction.values()) {
			BlockPos neighborPos = pos.relative(face);
			BlockState neighborState = level.getBlockState(neighborPos);
			if (!neighborState.isFaceSturdy(level, neighborPos, face.getOpposite())) {
				continue;
			}
			double alignment = towardEye.x() * face.getStepX()
					+ towardEye.y() * face.getStepY()
					+ towardEye.z() * face.getStepZ();
			if (alignment > bestAlignment) {
				bestAlignment = alignment;
				bestFace = face;
			}
		}
		return bestFace;
	}
}
