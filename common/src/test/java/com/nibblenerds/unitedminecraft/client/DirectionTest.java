package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;

/**
 * The conversions between Minecraft's yaw (0 = south, increasing toward west) and the compass
 * directions and bearings the mod narrates.
 */
class DirectionTest {
	private static final double TOLERANCE = 1.0e-4;

	private static String key(Component component) {
		return ((TranslatableContents) component.getContents()).getKey().replace("united_minecraft.direction.", "");
	}

	@Nested
	class FacingAndBearing {
		@ParameterizedTest(name = "yaw {0} faces {1}")
		@CsvSource({
				"0, south", "45, southwest", "90, west", "135, northwest", "180, north",
				"-90, east", "270, east", "-45, southeast",
				"22.4, south", "22.6, southwest", "359, south", "720, south", "-180, north"})
		void facingOctant(float yaw, String direction) {
			String[] octants = {"south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast"};
			assertEquals(direction, octants[AccessibilityTickHandler.facingOctant(yaw)]);
		}

		@ParameterizedTest(name = "yaw {0} is bearing {1}")
		@CsvSource({"180, 0", "-90, 90", "270, 90", "0, 180", "90, 270", "-180, 0", "540, 0", "179.6, 0", "45, 225"})
		void compassBearing(float yaw, int bearing) {
			assertEquals(bearing, AccessibilityTickHandler.compassBearing(yaw));
		}
	}

	@Nested
	class CompassDirectionTo {
		@ParameterizedTest(name = "({0}, {1}) is {2}")
		@CsvSource({
				"0, -10, north", "10, -10, northeast", "10, 0, east", "10, 10, southeast",
				"0, 10, south", "-10, 10, southwest", "-10, 0, west", "-10, -10, northwest"})
		void eightWay(double dx, double dz, String direction) {
			Vec3 from = new Vec3(100.5, 64, -20.5);
			assertEquals(direction, key(CameraUtil.compassDirectionTo(from, from.add(dx, 0, dz))));
		}

		@Test
		void heightIsIgnored() {
			assertEquals("east", key(CameraUtil.compassDirectionTo(Vec3.ZERO, new Vec3(10, 200, 0))));
		}
	}

	/** {@link StructureVoiceController#direction} - where a structure's voice is placed in your ears. */
	@Nested
	class StructureVoiceDirection {
		private static final Vec3 EYE = new Vec3(0, 64, 0);
		private static final float FACING_SOUTH = 0;
		private static final float LEVEL = 0;

		private StructureVoiceController.Direction at(float yaw, float pitch, double dx, double dy, double dz) {
			return StructureVoiceController.direction(yaw, pitch, EYE, EYE.add(dx, dy, dz));
		}

		@Test
		void straightAheadIsCentredAndUnmuffled() {
			StructureVoiceController.Direction direction = at(FACING_SOUTH, LEVEL, 0, 0, 20);
			assertEquals(0, direction.pan(), TOLERANCE);
			assertEquals(0, direction.behind(), TOLERANCE);
			assertEquals(0, direction.height(), TOLERANCE);
		}

		@Test
		void facingSouthWestIsOnYourRight() {
			assertEquals(1, at(FACING_SOUTH, LEVEL, -20, 0, 0).pan(), TOLERANCE);
			assertEquals(-1, at(FACING_SOUTH, LEVEL, 20, 0, 0).pan(), TOLERANCE);
		}

		@Test
		void sidesFollowWhereYouAreFacing() {
			float facingNorth = 180;
			assertEquals(1, at(facingNorth, LEVEL, 20, 0, 0).pan(), TOLERANCE, "east is on your right facing north");
		}

		@Test
		void halfwayToTheSideIsHalfPanned() {
			assertEquals(0.5, at(FACING_SOUTH, LEVEL, -20, 0, 20).pan(), TOLERANCE);
		}

		@Test
		void directlyBehindIsFullyMuffled() {
			assertEquals(1, at(FACING_SOUTH, LEVEL, 0, 0, -20).behind(), TOLERANCE);
			assertEquals(0, at(FACING_SOUTH, LEVEL, -20, 0, 0).behind(), TOLERANCE, "exactly to the side is not behind");
		}

		@Test
		void heightIsRelativeToWhereYouAreLooking() {
			assertEquals(1, at(FACING_SOUTH, LEVEL, 0, 20, 20).height(), TOLERANCE, "45 degrees up");
			assertEquals(-1, at(FACING_SOUTH, LEVEL, 0, -20, 20).height(), TOLERANCE, "45 degrees down");
			float lookingDown45 = 45;
			assertEquals(1, at(FACING_SOUTH, lookingDown45, 0, 0, 20).height(), TOLERANCE,
					"level ground is above a view tilted down");
		}

		@Test
		void standingInsideItIsCentred() {
			StructureVoiceController.Direction direction = at(FACING_SOUTH, LEVEL, 0.3, 0.2, -0.4);
			assertEquals(0, direction.pan(), TOLERANCE);
			assertEquals(0, direction.behind(), TOLERANCE);
			assertEquals(0, direction.height(), TOLERANCE);
		}
	}
}
