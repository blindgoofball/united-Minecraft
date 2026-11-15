package com.nibblenerds.unitedminecraft.client.autowalk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Finds the button that opens an iron door, which nothing opens by hand. A door is powered when
 * any block touching either of its two halves is (the cell above the top half and below the
 * bottom one included), and a button powers a block two ways: it powers a cell it is simply in
 * (a button beside or above the door), and it powers the solid block it is stuck to, which in turn
 * powers the door if that block touches it (a button on the wall next to the door, or on the
 * lintel above it). Both are found here, whether or not the button is pressed at the moment.
 */
final class ButtonDoors {
	private ButtonDoors() {
	}

	/** A door nothing opens by hand: the kind a button can open. */
	static boolean isHandlessDoor(BlockState state) {
		return state.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand();
	}

	/** The lower half of the door at {@code pos}, whichever half that is. */
	static BlockPos lowerHalf(BlockState state, BlockPos pos) {
		return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
	}

	/** The way a closed door faces, which is the line a player walks through it along. */
	static Direction passage(BlockState door) {
		return door.getValue(DoorBlock.FACING);
	}

	/**
	 * Which side of the door whose lower half is {@code lower} the point {@code (x, z)} is on: the
	 * door's facing direction or its opposite, or null if it is level with the door.
	 */
	static Direction sideOf(BlockState door, BlockPos lower, double x, double z) {
		Direction facing = passage(door);
		double along = (x - (lower.getX() + 0.5)) * facing.getStepX() + (z - (lower.getZ() + 0.5)) * facing.getStepZ();
		if (Math.abs(along) < 0.5) {
			return null;
		}
		return along > 0 ? facing : facing.getOpposite();
	}

	/**
	 * The first button that would power the door whose lower half is {@code lower}, or null if there is
	 * none. With {@code side}, only a button that can be pressed standing on that side of the door:
	 * one on the other side is behind the wall. A button level with the door (on top of the lintel,
	 * say) is reachable from either.
	 */
	static BlockPos buttonFor(BlockGetter level, BlockPos lower, Direction side) {
		Direction facing = passage(level.getBlockState(lower));
		BlockPos upper = lower.above();
		for (BlockPos half : new BlockPos[] {lower, upper}) {
			for (Direction around : Direction.values()) {
				BlockPos neighbour = half.relative(around);
				if (neighbour.equals(lower) || neighbour.equals(upper)) {
					continue;
				}
				BlockState state = level.getBlockState(neighbour);
				if (state.getBlock() instanceof ButtonBlock && onSide(neighbour, lower, facing, side)) {
					return neighbour;
				}
				if (!state.isRedstoneConductor(level, neighbour)) {
					continue;
				}
				for (Direction face : Direction.values()) {
					BlockPos candidate = neighbour.relative(face);
					BlockState button = level.getBlockState(candidate);
					if (button.getBlock() instanceof ButtonBlock && candidate.relative(attachedTo(button)).equals(neighbour)
							&& onSide(candidate, lower, facing, side)) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	/** Whether the button at {@code button} is on {@code side} of the door (or level with it, or {@code side} is null). */
	private static boolean onSide(BlockPos button, BlockPos lower, Direction facing, Direction side) {
		if (side == null) {
			return true;
		}
		int along = (button.getX() - lower.getX()) * facing.getStepX() + (button.getZ() - lower.getZ()) * facing.getStepZ();
		return along == 0 || (along > 0) == (side == facing);
	}

	/** The side of a button that the block it is stuck to is on. */
	private static Direction attachedTo(BlockState button) {
		return switch (button.getValue(BlockStateProperties.ATTACH_FACE)) {
			case FLOOR -> Direction.DOWN;
			case CEILING -> Direction.UP;
			case WALL -> button.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
		};
	}
}
