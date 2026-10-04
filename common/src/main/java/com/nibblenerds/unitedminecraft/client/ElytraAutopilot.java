package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The flight law for {@link ElytraLandingController}'s automatic landing: given where the player is
 * and how it is moving, says which way to look next. Deliberately free of any Minecraft types, so
 * it can be run against vanilla's elytra physics without launching the game.
 *
 * <p>Elytra flight is fully determined by the player's velocity and look direction ({@code
 * LivingEntity#updateFallFlyingMovement}, mirrored in {@link ElytraPhysics}), so rather than chasing
 * a glide slope, this <em>predicts</em>: every tick it simulates the rest of the flight - turning
 * toward the target and easing to each of a range of candidate pitches - and picks the pitch whose
 * predicted touchdown lands closest to the target, among those that touch down gently. Gentle
 * matters because touching down while sinking slower than {@link #MAX_TOUCHDOWN_SINK} blocks/tick
 * does no fall damage at all (vanilla caps the accumulated fall distance at 1 in that case), and
 * level flight is the best glide there is (about 10 blocks forward per block lost), so no dramatic
 * flare is ever needed - only never arriving on a steep, fast dive.
 *
 * <p>Two phases:
 * <ul>
 * <li>{@link Phase#ORBIT}: no landing on the target is possible from here (even the steepest
 * allowed dive overshoots it, or the turn onto it can't be made in time). Fly a wide circle around
 * it, sinking at a moderate rate, until a landing on it becomes possible.</li>
 * <li>{@link Phase#APPROACH}: a landing on the target is predicted possible; fly the pitch that
 * lands on it, re-predicting every tick so any drift is corrected.</li>
 * </ul>
 */
final class ElytraAutopilot {
	enum Phase {
		ORBIT, APPROACH
	}

	/** Position, velocity (blocks per tick) and look angles in degrees (Minecraft convention: yaw 0 faces +Z, pitch positive looks down). */
	record State(double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch) {
	}

	/** The surface height at a column - what the predicted flight must stay above, and where it ends. */
	interface GroundProbe {
		double height(double x, double z);
	}

	/**
	 * The look to adopt next tick, which phase it came from, how far the predicted touchdown is from
	 * the target, the predicted flight path (x, y, z, every few ticks), and whether the circling
	 * path is predicted to run into terrain away from the target ({@code hazard}).
	 */
	record Command(float yaw, float pitch, Phase phase, double predictedMiss, List<double[]> path, boolean hazard) {
	}

	private record Prediction(double miss, double touchdownSink, double pitch, boolean landed, List<double[]> path) {
	}

	static final double ORBIT_RADIUS = 50.0;
	// Fall damage becomes possible once the touchdown sink rate (blocks/tick) reaches 0.5; keep clear of it.
	static final double MAX_TOUCHDOWN_SINK = 0.40;
	// A predicted landing this close to the target (blocks) is good enough to commit to the approach.
	static final double COMMIT_MISS = 2.5;
	// If a committed approach's best prediction drifts this far off, go back to circling.
	static final double ABANDON_MISS = 20.0;
	private static final double MAX_PITCH = 60.0;
	private static final double PITCH_STEP = 2.5;
	private static final float MAX_YAW_STEP = 5.0f;
	private static final float MAX_PITCH_STEP = 5.0f;
	private static final double ORBIT_SINK_PITCH = 30.0;
	private static final double LEAD_ANGLE = Math.toRadians(40.0);
	private static final int PATH_SAMPLE_TICKS = 4;
	private static final int MAX_PREDICT_TICKS = 1500;

	// Ticks of circling flown ahead to check for terrain, and how close to the target a ground contact still counts as the landing.
	private static final int ORBIT_LOOKAHEAD_TICKS = 80;
	private static final double LANDING_ZONE = 10.0;
	// How far above the spot's own ground a contact has to be to count as terrain in the way.
	private static final double TERRAIN_RISE = 1.0;

	private final double tx;
	private final double ty;
	private final double tz;
	private final GroundProbe ground;
	private Phase phase = Phase.ORBIT;
	private boolean started;

	/** Flat ground at the target's own height everywhere - what a test flight over an open plain sees. */
	ElytraAutopilot(double targetX, double targetGroundY, double targetZ) {
		this(targetX, targetGroundY, targetZ, (x, z) -> targetGroundY);
	}

	ElytraAutopilot(double targetX, double targetGroundY, double targetZ, GroundProbe ground) {
		this.tx = targetX;
		this.ty = targetGroundY;
		this.tz = targetZ;
		this.ground = ground;
	}

	Phase phase() {
		return phase;
	}

	Command step(State s) {
		Prediction best = bestApproach(s);
		boolean workable = best.landed() && best.touchdownSink() <= MAX_TOUCHDOWN_SINK;
		if (!started) {
			started = true;
			phase = workable && best.miss() <= COMMIT_MISS ? Phase.APPROACH : Phase.ORBIT;
		} else if (phase == Phase.ORBIT && workable && best.miss() <= COMMIT_MISS) {
			phase = Phase.APPROACH;
		} else if (phase == Phase.APPROACH && (!workable || best.miss() > ABANDON_MISS) && s.y() - ty > 8.0) {
			phase = Phase.ORBIT;
		}

		if (phase == Phase.APPROACH) {
			float yaw = steerYaw(s.yaw(), bearingTo(s.x(), s.z(), tx, tz));
			float pitch = s.pitch() + clamp((float) (best.pitch() - s.pitch()), MAX_PITCH_STEP);
			return new Command(yaw, pitch, phase, best.miss(), best.path(), false);
		}
		return orbit(s, best);
	}

	private Command orbit(State s, Prediction best) {
		double dx = tx - s.x();
		double dz = tz - s.z();
		double distance = Math.hypot(dx, dz);
		// A point on the circle a little ahead of where the player is on it - which also draws the
		// player out onto the circle from wherever it starts, inside or outside.
		double theta = distance < 1.0 ? Math.atan2(-s.vx(), s.vz()) + Math.PI : Math.atan2(-dx, dz) + Math.PI;
		double ahead = theta + LEAD_ANGLE;
		double aimX = tx - Math.sin(ahead) * ORBIT_RADIUS;
		double aimZ = tz + Math.cos(ahead) * ORBIT_RADIUS;
		float yaw = steerYaw(s.yaw(), bearingTo(s.x(), s.z(), aimX, aimZ));

		// Close to the ground with no way to reach the target: stop sinking steeply and just level
		// out into the best glide - landing nearby is better than diving into the ground.
		double pitchWanted = s.y() - ty < 6.0 ? 0.0 : ORBIT_SINK_PITCH;
		float pitch = s.pitch() + clamp((float) (pitchWanted - s.pitch()), MAX_PITCH_STEP);
		return new Command(yaw, pitch, phase, best.miss(), best.path(), orbitHazard(s));
	}

	/**
	 * Whether circling on from {@code s} is predicted to hit terrain somewhere other than the landing
	 * zone. Only ground standing higher than the spot counts: over ground no higher than the spot, a
	 * circle that runs low just levels out (see {@link #orbit}) into a gentle landing short of it,
	 * which is no reason to hand control back.
	 */
	private boolean orbitHazard(State s) {
		double x = s.x();
		double y = s.y();
		double z = s.z();
		double vx = s.vx();
		double vy = s.vy();
		double vz = s.vz();
		float yaw = s.yaw();
		float pitch = s.pitch();
		for (int t = 0; t < ORBIT_LOOKAHEAD_TICKS; t++) {
			double dx = tx - x;
			double dz = tz - z;
			double distance = Math.hypot(dx, dz);
			double theta = distance < 1.0 ? Math.atan2(-vx, vz) + Math.PI : Math.atan2(-dx, dz) + Math.PI;
			double ahead = theta + LEAD_ANGLE;
			yaw = steerYaw(yaw, bearingTo(x, z, tx - Math.sin(ahead) * ORBIT_RADIUS, tz + Math.cos(ahead) * ORBIT_RADIUS));
			double pitchWanted = y - ty < 6.0 ? 0.0 : ORBIT_SINK_PITCH;
			pitch += clamp((float) (pitchWanted - pitch), MAX_PITCH_STEP);
			double[] v = ElytraPhysics.step(vx, vy, vz, yaw, pitch);
			vx = v[0];
			vy = v[1];
			vz = v[2];
			x += vx;
			y += vy;
			z += vz;
			double surface = ground.height(x, z);
			if (y <= surface) {
				return surface > ty + TERRAIN_RISE && Math.hypot(x - tx, z - tz) > LANDING_ZONE;
			}
		}
		return false;
	}

	/** The candidate pitch whose predicted touchdown best serves the target: nearest to it, with a heavy penalty for a hard landing. */
	private Prediction bestApproach(State s) {
		Prediction best = null;
		double bestCost = Double.MAX_VALUE;
		for (double pitch = 0.0; pitch <= MAX_PITCH + 1e-9; pitch += PITCH_STEP) {
			Prediction p = predict(s, pitch);
			double cost = p.landed() ? p.miss() + Math.max(0.0, p.touchdownSink() - MAX_TOUCHDOWN_SINK) * 400.0 : 1e6;
			if (cost < bestCost) {
				bestCost = cost;
				best = p;
			}
		}
		return best;
	}

	/** Simulates the flight from {@code s}, steering toward the target and easing to {@code pitchTarget}, until the ground is reached. */
	private Prediction predict(State s, double pitchTarget) {
		double x = s.x();
		double y = s.y();
		double z = s.z();
		double vx = s.vx();
		double vy = s.vy();
		double vz = s.vz();
		float yaw = s.yaw();
		float pitch = s.pitch();
		List<double[]> path = new ArrayList<>();
		for (int t = 0; t < MAX_PREDICT_TICKS; t++) {
			yaw = steerYaw(yaw, bearingTo(x, z, tx, tz));
			pitch += clamp((float) (pitchTarget - pitch), MAX_PITCH_STEP);
			double[] v = ElytraPhysics.step(vx, vy, vz, yaw, pitch);
			vx = v[0];
			vy = v[1];
			vz = v[2];
			double prevX = x;
			double prevY = y;
			double prevZ = z;
			x += vx;
			y += vy;
			z += vz;
			if (t % PATH_SAMPLE_TICKS == 0) {
				path.add(new double[] {x, y, z});
			}
			double surface = ground.height(x, z);
			if (y <= surface) {
				double fraction = prevY <= surface || prevY == y ? 1.0 : (prevY - surface) / (prevY - y);
				double touchX = prevX + (x - prevX) * fraction;
				double touchZ = prevZ + (z - prevZ) * fraction;
				path.add(new double[] {touchX, surface, touchZ});
				return new Prediction(Math.hypot(touchX - tx, touchZ - tz), -vy, pitchTarget, true, path);
			}
		}
		return new Prediction(Double.MAX_VALUE, 0.0, pitchTarget, false, path);
	}

	private static float steerYaw(float yaw, float wanted) {
		return yaw + clamp(wrap(wanted - yaw), MAX_YAW_STEP);
	}

	private static float bearingTo(double fromX, double fromZ, double toX, double toZ) {
		return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
	}

	private static float wrap(float degrees) {
		float wrapped = degrees % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		} else if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	private static float clamp(float value, float limit) {
		return Math.max(-limit, Math.min(limit, value));
	}
}
