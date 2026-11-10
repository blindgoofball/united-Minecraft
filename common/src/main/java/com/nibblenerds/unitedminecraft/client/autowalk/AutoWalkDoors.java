package com.nibblenerds.unitedminecraft.client.autowalk;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The doors of an {@link AutoWalkController} walk, when {@link UnitedMinecraftConfig#autoWalkOpenDoors}
 * lets the route go through them (see {@link AutoWalkNodeEvaluator}): what a villager does, since the
 * pathfinder only plans a route through a closed door and leaves opening it to the walker.
 *
 * <p>A closed door a couple of blocks ahead on the route is opened with the same click a player would
 * make, and the walk waits for it to swing open. Each door opened this way is remembered, and closed
 * again once the player is a little way past it; a door that was already open is never touched, so the
 * player leaves things as they found them. Doors still open when the walk ends are closed too, if the
 * player has got through them.
 */
public final class AutoWalkDoors {
	enum Gate {
		/** Nothing to wait for - keep walking. */
		CLEAR,
		/** A door ahead is being opened - stand still until it has. */
		WAIT,
		/** A door will not open - give up. */
		FAILED
	}

	// How close to a closed door on the route to be before opening it.
	private static final double OPEN_DISTANCE = 2.0;
	// How far past a door the player must be before it is closed behind them, mid-walk and at the end.
	static final double CLOSE_BEHIND_DISTANCE = 1.3;
	static final double CLOSE_AT_END_DISTANCE = 0.7;
	// A door further away than this cannot be reached to close.
	private static final double REACH = 4.0;
	private static final int RETRY_TICKS = 8;
	// Opens tried on one door before it counts as stuck - a server that keeps refusing (a protected
	// area, say) would otherwise be clicked at forever.
	private static final int MAX_ATTEMPTS = 3;

	/** A door this walk opened, and the way the player was heading as they opened it. */
	private record Opened(BlockPos pos, double dirX, double dirZ) {
	}

	private static final List<Opened> OPENED = new ArrayList<>();
	private static final Map<BlockPos, Integer> ATTEMPTS = new HashMap<>();
	private static int sinceAttempt;

	private AutoWalkDoors() {
	}

	static void reset() {
		OPENED.clear();
		ATTEMPTS.clear();
		sinceAttempt = 0;
	}

	/** Opens the closed door just ahead on the route, if there is one, and says whether the walk has to wait for it. */
	static Gate approach(Minecraft client, LocalPlayer player, Path path) {
		Level level = player.level();
		int next = path.getNextNodeIndex();
		for (int i = next; i < Math.min(path.getNodeCount(), next + 2); i++) {
			BlockPos door = closedDoorAt(level, path.getNodePos(i));
			if (door == null) {
				continue;
			}
			double dx = door.getX() + 0.5 - player.getX();
			double dz = door.getZ() + 0.5 - player.getZ();
			double distance = Math.hypot(dx, dz);
			if (distance > OPEN_DISTANCE) {
				return Gate.CLEAR;
			}
			int attempts = ATTEMPTS.getOrDefault(door, 0);
			if (++sinceAttempt >= RETRY_TICKS || attempts == 0) {
				if (attempts >= MAX_ATTEMPTS) {
					return Gate.FAILED;
				}
				ATTEMPTS.put(door, attempts + 1);
				sinceAttempt = 0;
				toggle(client, player, door);
				if (OPENED.stream().noneMatch(opened -> opened.pos().equals(door))) {
					OPENED.add(new Opened(door, dx / distance, dz / distance));
				}
			}
			return Gate.WAIT;
		}
		return Gate.CLEAR;
	}

	/** Closes every door this walk opened that the player has now got {@code distance} blocks past. */
	static void closeBehind(Minecraft client, LocalPlayer player, double distance) {
		Level level = player.level();
		Iterator<Opened> doors = OPENED.iterator();
		while (doors.hasNext()) {
			Opened opened = doors.next();
			BlockState state = level.getBlockState(opened.pos());
			if (!(state.getBlock() instanceof DoorBlock) || !state.getValue(DoorBlock.OPEN)) {
				// Gone, or somebody else already shut it.
				doors.remove();
				continue;
			}
			double along = distancePast(player.getX(), player.getZ(), opened.pos(), opened.dirX(), opened.dirZ());
			if (along < distance) {
				continue;
			}
			if (player.position().distanceTo(Vec3.atCenterOf(opened.pos())) <= REACH) {
				toggle(client, player, opened.pos());
			}
			doors.remove();
		}
	}

	/** Called as a walk ends: closes what can be, and forgets the rest. */
	static void finish(Minecraft client, LocalPlayer player) {
		closeBehind(client, player, CLOSE_AT_END_DISTANCE);
		reset();
	}

	/** How far past the door, along the way the player was heading as they opened it, the point {@code (x, z)} is. */
	static double distancePast(double x, double z, BlockPos door, double dirX, double dirZ) {
		return (x - (door.getX() + 0.5)) * dirX + (z - (door.getZ() + 0.5)) * dirZ;
	}

	/** The lower half of the closed, hand-openable door at {@code pos}, or null if it is anything else. */
	private static BlockPos closedDoorAt(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock door) || state.getValue(DoorBlock.OPEN) || !door.type().canOpenByHand()) {
			return null;
		}
		return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	/** Opens or closes a door with the same click a player would make. */
	private static void toggle(Minecraft client, LocalPlayer player, BlockPos door) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(door), Direction.UP, door, false);
		InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		if (result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
	}
}
