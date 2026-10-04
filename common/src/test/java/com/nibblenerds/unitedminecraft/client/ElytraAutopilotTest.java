package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Flies the elytra landing autopilot against the copy of vanilla's flight physics it predicts
 * with ({@link ElytraFlight}), from starts the landing controller would actually accept.
 *
 * <p>Two standards, both from the game rather than chosen here:
 * <ul>
 * <li><b>Gentle:</b> vanilla caps fall distance at 1 block while the player sinks slower than
 * 0.5 blocks/tick ({@code Entity#checkFallDistanceAccumulation}), well under the 3-block safe fall
 * distance - so a touchdown slower than that does no damage at all.</li>
 * <li><b>On the spot:</b> within {@link ElytraAutopilot#COMMIT_MISS}, the miss the autopilot itself
 * counts as a landing on the target.</li>
 * </ul>
 *
 * <p>The target is the centre of block (0, 64, 0) on flat ground; each start is {@code distance}
 * blocks due north of it and {@code height} above, heading along {@code yaw} (0 faces the target,
 * 180 faces away) at {@code speed} blocks/tick.
 */
class ElytraAutopilotTest {
	/** Vanilla stops capping fall distance once sinking this fast or faster - see the class doc. */
	private static final double DAMAGING_SINK = 0.5;
	private static final double TX = 0.5;
	private static final double TY = 64.0;
	private static final double TZ = 0.5;
	private static final ElytraAutopilot.GroundProbe FLAT = (x, z) -> TY;

	private static ElytraAutopilot.State start(double distance, double height, float yaw, double speed) {
		return ElytraFlight.gliding(TX, TY + height, TZ - distance, yaw, speed);
	}

	/** The same two checks ElytraLandingController makes before it takes over. */
	private static boolean controllerWouldStart(double distance, double height, float yaw, double speed) {
		if (!ElytraLandingController.inGlideRange(height, distance)) {
			return false;
		}
		ElytraAutopilot.Command first = new ElytraAutopilot(TX, TY, TZ).step(start(distance, height, yaw, speed));
		return first.phase() == ElytraAutopilot.Phase.APPROACH || height >= ElytraLandingController.MIN_START_HEIGHT_TO_CIRCLE;
	}

	private static void assertLandsOnTheSpotGently(double distance, double height, float yaw, double speed) {
		ElytraFlight.Landing landing = ElytraFlight.fly(new ElytraAutopilot(TX, TY, TZ), start(distance, height, yaw, speed), FLAT);
		String flight = String.format("from %.0f out, %.0f up, yaw %.0f, speed %.1f: %s", distance, height, yaw, speed, landing);
		assertFalse(landing.handedBack(), "gave up over open, flat ground " + flight);
		assertTrue(landing.sink() < DAMAGING_SINK, "landed hard enough to hurt " + flight);
		assertTrue(landing.missFrom(TX, TZ) <= ElytraAutopilot.COMMIT_MISS, "missed the spot " + flight);
	}

	/** A spread of what the controller takes on: straight in, side-on and turning back; low, high and very high; slow and fast. */
	static Stream<Arguments> representativeStarts() {
		return Stream.of(
				Arguments.of(0, 30, 0f, 1.2),
				Arguments.of(0, 100, 90f, 1.2),
				Arguments.of(10, 10, 90f, 1.2),
				Arguments.of(30, 15, 45f, 1.2),
				Arguments.of(30, 30, 180f, 2.5),
				Arguments.of(60, 20, 0f, 1.2),
				Arguments.of(60, 60, 90f, 0.4),
				Arguments.of(60, 100, 180f, 2.5),
				Arguments.of(90, 20, 315f, 2.5),
				Arguments.of(90, 30, 0f, 1.2),
				Arguments.of(90, 45, 180f, 1.2),
				Arguments.of(120, 30, 90f, 1.2),
				Arguments.of(120, 60, 180f, 0.4),
				Arguments.of(120, 150, 270f, 2.5));
	}

	@ParameterizedTest(name = "{0} out, {1} up, yaw {2}, speed {3}")
	@MethodSource("representativeStarts")
	void landsOnTheSpotGently(double distance, double height, float yaw, double speed) {
		assertTrue(controllerWouldStart(distance, height, yaw, speed), "not a start the controller would take on");
		assertLandsOnTheSpotGently(distance, height, yaw, speed);
	}

	@Test
	void givesControlBackRatherThanCirclingIntoAWall() {
		// A wall 40-45 blocks around the spot, taller than the start: the circle the autopilot flies
		// to lose height (50 blocks out) runs straight through it.
		ElytraAutopilot.GroundProbe walled = (x, z) -> {
			double r = Math.hypot(x - TX, z - TZ);
			return r >= 40 && r <= 45 ? TY + 80 : TY;
		};
		ElytraFlight.Landing landing = ElytraFlight.fly(new ElytraAutopilot(TX, TY, TZ, walled), start(20, 40, 180f, 1.2), walled);
		assertTrue(landing.handedBack(), "flew on toward the wall: " + landing);
	}

	/**
	 * Every combination of distance, height, heading and speed the controller would take on - over
	 * 900 flights, a couple of minutes. Not part of the normal build; run it with {@code ./gradlew
	 * :fabric:elytraSweep} after changing the autopilot or {@link ElytraPhysics}.
	 */
	@Test
	@Tag("sweep")
	void sweep() {
		List<String> failures = new ArrayList<>();
		for (double distance : new double[] {0, 10, 30, 60, 90, 120}) {
			for (double height : new double[] {6, 10, 15, 20, 30, 45, 60, 100, 150}) {
				for (float yaw = 0; yaw < 360; yaw += 45) {
					for (double speed : new double[] {0.4, 1.2, 2.5}) {
						if (!controllerWouldStart(distance, height, yaw, speed)) {
							continue;
						}
						try {
							assertLandsOnTheSpotGently(distance, height, yaw, speed);
						} catch (AssertionError e) {
							failures.add(e.getMessage());
						}
					}
				}
			}
		}
		assertTrue(failures.isEmpty(), failures.size() + " flights failed:\n" + String.join("\n", failures));
	}
}
