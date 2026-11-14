package com.nibblenerds.unitedminecraft.client.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BuildCursorSoundTest {
	private static final double EPSILON = 1.0e-4;

	@Test
	void audioPositionAppliesLateralSpread() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		// 5 blocks North (-Z), 1 block East (+X). Block center is (1.5, 64.5, -4.5)
		BlockPos cursor = new BlockPos(1, 64, -5);
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, cursor, Direction.NORTH);

		// lateral offset 1.5 scaled by 2.2 = 3.3
		assertEquals(3.3, result.x, EPSILON);
		assertEquals(64.5, result.y, EPSILON);
		assertEquals(-4.5, result.z, EPSILON);

		// Angle from nose: atan(3.3 / 4.5) ~= 36.25 degrees - immediate distinct stereo pan
		double angleDegrees = Math.toDegrees(Math.atan2(result.x, -result.z));
		assertTrue(angleDegrees > 30.0 && angleDegrees < 45.0, "Expected >30 degrees lateral pan, was: " + angleDegrees);
	}

	@Test
	void audioPositionOffsetsAheadWhenAtEyeLevel() {
		Vec3 eye = new Vec3(0.5, 64.5, 0.5);
		BlockPos cursor = new BlockPos(0, 64, 0); // Center is (0.5, 64.5, 0.5), dist = 0
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, cursor, Direction.NORTH);

		// Offsets 0.5 blocks north (-Z)
		assertEquals(0.5, result.x, EPSILON);
		assertEquals(64.5, result.y, EPSILON);
		assertEquals(0.0, result.z, EPSILON);
	}

	@Test
	void distanceGainMatchesAudioMinecraftCameraCurve() {
		// Matches audio_minecraft's camera_distance() (rolloff 0.15, ref 1.0)
		assertEquals(1.0f, BuildCursorSound.calculateDistanceGain(0.5), (float) EPSILON);
		assertEquals(1.0f, BuildCursorSound.calculateDistanceGain(1.0), (float) EPSILON);

		// -4 dB at 5 blocks: 1.0 / (1.0 + 0.15 * 4.0) = 1.0 / 1.6 = 0.625
		assertEquals(0.625f, BuildCursorSound.calculateDistanceGain(5.0), (float) EPSILON);

		// At 10 blocks: 1.0 / (1.0 + 0.15 * 9.0) = 1.0 / 2.35 = 0.4255
		assertEquals(1.0f / 2.35f, BuildCursorSound.calculateDistanceGain(10.0), (float) EPSILON);

		// At 20 blocks: 1.0 / (1.0 + 0.15 * 19.0) = 1.0 / 3.85 = 0.2597
		assertEquals(1.0f / 3.85f, BuildCursorSound.calculateDistanceGain(20.0), (float) EPSILON);

		// Monotonically decreases
		assertTrue(BuildCursorSound.calculateDistanceGain(1.0) > BuildCursorSound.calculateDistanceGain(5.0));
		assertTrue(BuildCursorSound.calculateDistanceGain(5.0) > BuildCursorSound.calculateDistanceGain(10.0));
		assertTrue(BuildCursorSound.calculateDistanceGain(10.0) > BuildCursorSound.calculateDistanceGain(20.0));
	}

	@Test
	void elevationPitchShiftsSmoothlyWithHeight() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);

		// Eye level -> pitch 1.0
		Vec3 eyeLevel = new Vec3(5.0, 64.0, 0.0);
		assertEquals(1.0f, BuildCursorSound.calculateElevationPitch(eye, eyeLevel), (float) EPSILON);

		// Straight above -> +4 semitones (2^(4/12) ~= 1.2599)
		Vec3 straightAbove = new Vec3(0.0, 68.0, 0.0);
		float expectedHigh = (float) Math.pow(2.0, 4.0 / 12.0);
		assertEquals(expectedHigh, BuildCursorSound.calculateElevationPitch(eye, straightAbove), (float) EPSILON);

		// Straight below -> -4 semitones (2^(-4/12) ~= 0.7937)
		Vec3 straightBelow = new Vec3(0.0, 60.0, 0.0);
		float expectedLow = (float) Math.pow(2.0, -4.0 / 12.0);
		assertEquals(expectedLow, BuildCursorSound.calculateElevationPitch(eye, straightBelow), (float) EPSILON);
	}

	@Test
	void behindGainAttenuatesSoundsInBack() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		float yawNorth = 180.0f; // In Minecraft, yaw 180 is North (-Z)

		// Straight ahead (North: -Z) -> 1.0 gain
		Vec3 ahead = new Vec3(0.0, 64.0, -5.0);
		assertEquals(1.0f, BuildCursorSound.calculateBehindGain(eye, yawNorth, ahead), (float) EPSILON);

		// To the side (East: +X) -> 1.0 gain
		Vec3 side = new Vec3(5.0, 64.0, 0.0);
		assertEquals(1.0f, BuildCursorSound.calculateBehindGain(eye, yawNorth, side), (float) EPSILON);

		// Straight behind (South: +Z) -> 0.7 gain (-30% volume, matching audio_minecraft)
		Vec3 behind = new Vec3(0.0, 64.0, 5.0);
		assertEquals(0.7f, BuildCursorSound.calculateBehindGain(eye, yawNorth, behind), (float) EPSILON);
	}

	@Test
	void panningProgressionAcrossTiles() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);

		// Facing North: Right is East (+X)
		BlockPos tile1Right = new BlockPos(1, 64, -2);
		BlockPos tile5Right = new BlockPos(5, 64, -2);
		BlockPos tile20Right = new BlockPos(20, 64, -2);

		Vec3 pos1 = BuildCursorSound.calculateAudioPosition(eye, tile1Right, Direction.NORTH);
		Vec3 pos5 = BuildCursorSound.calculateAudioPosition(eye, tile5Right, Direction.NORTH);
		Vec3 pos20 = BuildCursorSound.calculateAudioPosition(eye, tile20Right, Direction.NORTH);

		// Sound X moves further to the right with each tile
		assertTrue(pos1.x < pos5.x);
		assertTrue(pos5.x < pos20.x);

		// Stepping left brings X back toward 0
		BlockPos tile0 = new BlockPos(0, 64, -2);
		Vec3 pos0 = BuildCursorSound.calculateAudioPosition(eye, tile0, Direction.NORTH);
		assertEquals(0.5 * 2.2, pos0.x, EPSILON); // center of block 0 is 0.5 scaled

		// Stepping further left moves into negative X
		BlockPos tile1Left = new BlockPos(-1, 64, -2);
		Vec3 posLeft = BuildCursorSound.calculateAudioPosition(eye, tile1Left, Direction.NORTH);
		assertTrue(posLeft.x < 0.0);
		assertTrue(posLeft.x < pos0.x);
	}
}
