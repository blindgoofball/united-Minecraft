package com.nibblenerds.unitedminecraft.client.travel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Flies the autopilot to a platform hanging in the void (the End's spawn platform is five blocks square):
 * every start the controller would take on must land on it, not clip its edge and glide on past. A sweep
 * - run with {@code ./gradlew :fabric:elytraSweep}, a minute or so.
 */
class ElytraPlatformSweepTest {
	private static final double TX = 0.5;
	private static final double TY = 64.0;
	private static final double TZ = 0.5;

	@Test
	@Tag("sweep")
	void landsOnPlatformsInTheVoid() {
		for (double half : new double[] {2.5, 4.5, 8.5}) {
			ElytraAutopilot.GroundProbe platform = (x, z) -> Math.abs(x - TX) <= half && Math.abs(z - TZ) <= half ? TY : TY - 60;
			int ok = 0;
			int total = 0;
			StringBuilder bad = new StringBuilder();
			for (double distance : new double[] {20, 60, 100}) {
				for (double height : new double[] {30, 60, 100}) {
					for (float yaw = 0; yaw < 360; yaw += 45) {
						for (double speed : new double[] {1.2, 2.5}) {
							ElytraAutopilot.State start = ElytraFlight.gliding(TX, TY + height, TZ - distance, yaw, speed);
							if (!ElytraLandingController.inGlideRange(height, distance)) {
								continue;
							}
							total++;
							ElytraFlight.Landing l = ElytraFlight.fly(new ElytraAutopilot(TX, TY, TZ, platform), start, platform);
							boolean landed = !l.handedBack() && platform.height(l.x(), l.z()) == TY && l.sink() < 0.5;
							if (landed) {
								ok++;
							} else if (bad.length() < 600) {
								bad.append(String.format("%n  d%.0f h%.0f yaw%.0f v%.1f -> x%.1f z%.1f sink%.2f hb=%s", distance, height, yaw, speed, l.x(), l.z(), l.sink(), l.handedBack()));
							}
						}
					}
				}
			}
			assertEquals(total, ok, "platform " + (half * 2 + 1) + " blocks square: only " + ok + " of " + total + " landed on it" + bad);
		}
	}
}
