package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared "snap the player's look direction at a point" math, used by build mode and the scanner. */
public final class CameraUtil {
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
}
