package com.nibblenerds.unitedminecraft.client.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BuildCursorSoundTest {
	private static final double EPSILON = 1.0e-5;
	private static final double DISTANCE = 1.8;

	@Test
	void stationaryAudioPositionFacesDeadAhead() {
		Vec3 eye = new Vec3(10.0, 64.0, -10.0);
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, Direction.NORTH, null, DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z - DISTANCE, result.z, EPSILON); // North is -Z
	}

	@Test
	void facingNorthSteppingLeftPansToLeftEar() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Direction facing = Direction.NORTH;
		Direction stepLeft = facing.getCounterClockWise(); // West (-X)

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, facing, stepLeft, DISTANCE);
		assertEquals(eye.x - DISTANCE, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z, result.z, EPSILON);
	}

	@Test
	void facingNorthSteppingRightPansToRightEar() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Direction facing = Direction.NORTH;
		Direction stepRight = facing.getClockWise(); // East (+X)

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, facing, stepRight, DISTANCE);
		assertEquals(eye.x + DISTANCE, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z, result.z, EPSILON);
	}

	@Test
	void facingNorthSteppingForwardPositionsAhead() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Direction facing = Direction.NORTH;

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, facing, facing, DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z - DISTANCE, result.z, EPSILON);
	}

	@Test
	void facingNorthSteppingBackwardPositionsBehind() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Direction facing = Direction.NORTH;

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, facing, facing.getOpposite(), DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z + DISTANCE, result.z, EPSILON);
	}

	@Test
	void steppingUpPositionsAbove() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, Direction.NORTH, Direction.UP, DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y + DISTANCE, result.y, EPSILON);
		assertEquals(eye.z, result.z, EPSILON);
	}

	@Test
	void steppingDownPositionsBelow() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, Direction.NORTH, Direction.DOWN, DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y - DISTANCE, result.y, EPSILON);
		assertEquals(eye.z, result.z, EPSILON);
	}

	@Test
	void facingWestSteppingLeftPansSouth() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Direction facing = Direction.WEST; // -X
		Direction stepLeft = facing.getCounterClockWise(); // South (+Z)

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, facing, stepLeft, DISTANCE);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z + DISTANCE, result.z, EPSILON);
	}
}
