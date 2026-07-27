package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Shared "snap the player's look direction at a point" math, used by build mode and the scanner. */
public final class CameraUtil {
	// Ordered every 45 degrees starting at compass bearing 0 (north), matching the same
	// north=0 convention AccessibilityTickHandler's bearing narration uses.
	private static final String[] COMPASS_DIRECTION_KEYS = {
			"united_minecraft.direction.north",
			"united_minecraft.direction.northeast",
			"united_minecraft.direction.east",
			"united_minecraft.direction.southeast",
			"united_minecraft.direction.south",
			"united_minecraft.direction.southwest",
			"united_minecraft.direction.west",
			"united_minecraft.direction.northwest",
	};

	private CameraUtil() {
	}

	public static void aimAt(LocalPlayer player, Vec3 target) {
		Vec3 eye = player.getEyePosition();
		double dx = target.x() - eye.x();
		double dy = target.y() - eye.y();
		double dz = target.z() - eye.z();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		// Inverse of Entity.calculateViewVector: x = -sin(yaw)*cos(pitch), y = -sin(pitch), z = cos(yaw)*cos(pitch).
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
	}

	/** The compass direction (north/southeast/etc.) of {@code to} as seen from {@code from}, ignoring height. */
	public static Component compassDirectionTo(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		// Same yaw formula aimAt uses, then the same yaw-to-compass-bearing shift
		// AccessibilityTickHandler.narrateBearing uses (Minecraft yaw 0 = south).
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		int bearing = Math.floorMod(Math.round(yaw) + 180, 360);
		int octant = Math.floorMod(Math.round(bearing / 45.0f), 8);
		return Component.translatable(COMPASS_DIRECTION_KEYS[octant]);
	}
}
