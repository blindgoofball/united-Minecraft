package com.nibblenerds.unitedminecraft.client.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BuildCursorSoundTest {
	private static final double EPSILON = 1.0e-5;

	@Test
	void audioPositionAtEyeReturnsEye() {
		Vec3 eye = new Vec3(10.0, 64.0, -10.0);
		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, eye, 3.5);
		assertEquals(eye.x, result.x, EPSILON);
		assertEquals(eye.y, result.y, EPSILON);
		assertEquals(eye.z, result.z, EPSILON);
	}

	@Test
	void audioPositionWithinMaxDistanceReturnsExactTarget() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Vec3 target = new Vec3(1.0, 65.0, -1.0);
		assertTrue(target.distanceTo(eye) <= 3.5);

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, target, 3.5);
		assertEquals(target.x, result.x, EPSILON);
		assertEquals(target.y, result.y, EPSILON);
		assertEquals(target.z, result.z, EPSILON);
	}

	@Test
	void audioPositionBeyondMaxDistanceClampsDistanceAlongRay() {
		Vec3 eye = new Vec3(0.0, 64.0, 0.0);
		Vec3 target = new Vec3(20.0, 64.0, 0.0); // 20 blocks east

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, target, 3.5);
		assertEquals(3.5, result.x, EPSILON);
		assertEquals(64.0, result.y, EPSILON);
		assertEquals(0.0, result.z, EPSILON);
		assertEquals(3.5, result.distanceTo(eye), EPSILON);
	}

	@Test
	void audioPositionElevatedClampsWithCorrectElevation() {
		Vec3 eye = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(0.0, 10.0, 0.0); // 10 blocks straight up

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, target, 3.5);
		assertEquals(0.0, result.x, EPSILON);
		assertEquals(3.5, result.y, EPSILON);
		assertEquals(0.0, result.z, EPSILON);
	}

	@Test
	void audioPositionDiagonalPreservesUnitVector() {
		Vec3 eye = new Vec3(0.0, 0.0, 0.0);
		Vec3 target = new Vec3(-10.0, 10.0, -10.0);

		Vec3 result = BuildCursorSound.calculateAudioPosition(eye, target, 3.5);
		assertEquals(3.5, result.distanceTo(eye), EPSILON);

		Vec3 expectedDirection = target.normalize();
		Vec3 actualDirection = result.normalize();
		assertEquals(expectedDirection.x, actualDirection.x, EPSILON);
		assertEquals(expectedDirection.y, actualDirection.y, EPSILON);
		assertEquals(expectedDirection.z, actualDirection.z, EPSILON);
	}
}
