package com.nibblenerds.unitedminecraft.client;

/**
 * A copy of vanilla's elytra velocity update ({@code LivingEntity#updateFallFlyingMovement}, with the
 * default gravity of 0.08), kept free of Minecraft types so {@link ElytraAutopilot} can predict a
 * flight without an entity. Any change vanilla makes to elytra flight needs mirroring here - the
 * autopilot's predictions are only as good as this matches.
 */
final class ElytraPhysics {
	private static final double GRAVITY = 0.08;

	private ElytraPhysics() {
	}

	/** One tick of velocity {@code (vx, vy, vz)} flying with the given look; returns the new velocity. */
	static double[] step(double vx, double vy, double vz, float yawDegrees, float pitchDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		float leanAngle = pitchDegrees * (float) (Math.PI / 180.0);
		double cosPitch = Math.cos(Math.toRadians(pitchDegrees));
		double lookX = -Math.sin(yaw) * cosPitch;
		double lookZ = Math.cos(yaw) * cosPitch;
		double lookHorLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
		double moveHorLength = Math.sqrt(vx * vx + vz * vz);
		double liftForce = Math.cos(leanAngle) * Math.cos(leanAngle);

		vy += GRAVITY * (-1.0 + liftForce * 0.75);
		if (vy < 0.0 && lookHorLength > 0.0) {
			double convert = vy * -0.1 * liftForce;
			vx += lookX * convert / lookHorLength;
			vy += convert;
			vz += lookZ * convert / lookHorLength;
		}
		if (leanAngle < 0.0F && lookHorLength > 0.0) {
			double convert = moveHorLength * -Math.sin(leanAngle) * 0.04;
			vx += -lookX * convert / lookHorLength;
			vy += convert * 3.2;
			vz += -lookZ * convert / lookHorLength;
		}
		if (lookHorLength > 0.0) {
			vx += (lookX / lookHorLength * moveHorLength - vx) * 0.1;
			vz += (lookZ / lookHorLength * moveHorLength - vz) * 0.1;
		}
		return new double[] {vx * 0.99F, vy * 0.98F, vz * 0.99F};
	}
}
