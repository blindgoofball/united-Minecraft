package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Flies {@link ElytraAutopilot} the way {@link ElytraLandingController} does, against {@link
 * ElytraPhysics} instead of the game: each tick the autopilot picks a look from the current state,
 * the look is applied, and the next tick's movement is vanilla's elytra update with that look.
 */
final class ElytraFlight {
	/** Five minutes of flight - far longer than any landing should take. */
	private static final int MAX_TICKS = 20 * 60 * 5;

	/**
	 * Where and how a flight met the ground, and what happened on the way. {@code handedBack} is
	 * set when the controller would have given control back for a predicted collision - the
	 * flight stops there, so {@code x}/{@code z}/{@code sink} describe where it was at that point.
	 */
	record Landing(double x, double z, double sink, int ticks, List<ElytraAutopilot.Phase> phases, boolean handedBack) {
		double missFrom(double tx, double tz) {
			return Math.hypot(x - tx, z - tz);
		}
	}

	private ElytraFlight() {
	}

	/** A glide starting at {@code (x, y, z)}, heading along {@code yaw} at {@code speed} blocks/tick, sinking gently. */
	static ElytraAutopilot.State gliding(double x, double y, double z, float yaw, double speed) {
		double radians = Math.toRadians(yaw);
		return new ElytraAutopilot.State(x, y, z, -Math.sin(radians) * speed, -0.1, Math.cos(radians) * speed, yaw, 10.0f);
	}

	static Landing fly(ElytraAutopilot pilot, ElytraAutopilot.State start, ElytraAutopilot.GroundProbe ground) {
		ElytraAutopilot.State s = start;
		List<ElytraAutopilot.Phase> phases = new ArrayList<>();
		int hazardTicks = 0;
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			ElytraAutopilot.Command command = pilot.step(s);
			if (phases.isEmpty() || phases.get(phases.size() - 1) != command.phase()) {
				phases.add(command.phase());
			}
			// The same hand-back rule ElytraLandingController applies.
			hazardTicks = command.hazard() ? hazardTicks + 1 : 0;
			if (hazardTicks >= ElytraLandingController.HAZARD_TICKS_TO_ABORT) {
				return new Landing(s.x(), s.z(), -s.vy(), tick, phases, true);
			}

			double[] v = ElytraPhysics.step(s.vx(), s.vy(), s.vz(), command.yaw(), command.pitch());
			double x = s.x() + v[0];
			double y = s.y() + v[1];
			double z = s.z() + v[2];
			double surface = ground.height(x, z);
			if (y <= surface) {
				// Where between the last two positions the feet crossed the surface.
				double fraction = s.y() <= surface || s.y() == y ? 1.0 : (s.y() - surface) / (s.y() - y);
				return new Landing(s.x() + (x - s.x()) * fraction, s.z() + (z - s.z()) * fraction, -v[1], tick, phases, false);
			}
			s = new ElytraAutopilot.State(x, y, z, v[0], v[1], v[2], command.yaw(), command.pitch());
		}
		throw new AssertionError("still flying after " + MAX_TICKS + " ticks");
	}
}
