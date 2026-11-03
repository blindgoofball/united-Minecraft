package com.nibblenerds.unitedminecraft.client;

import net.minecraft.util.Mth;

/**
 * The arithmetic behind {@link BoatController}'s "turn the boat to this heading": pure, so it can be
 * tested against a model of the boat instead of the running game.
 *
 * <p>A boat does not turn at a set rate. Its turn speed is a stored value that each tick is first
 * multiplied by the water's drag ({@link #WATER_DRAG}), then nudged by one degree per tick for a held
 * left or right, and then added to its yaw (see {@code AbstractBoat#controlBoat}). Let go and it keeps
 * turning, coasting to a stop. Each degree of nudge therefore costs {@code 1 / (1 - drag)} - ten -
 * degrees of eventual turn, which is why boat steering feels so sensitive.
 */
final class BoatSteering {
	/** The per-tick drag on the boat's turn speed while it floats ({@code invFriction} in the vanilla boat). */
	static final double WATER_DRAG = 0.9;
	/** The least a heading can be off by: one nudge moves the resting heading by ten degrees, so ten degrees apart is as fine as it gets. */
	static final double RESOLUTION_DEGREES = 1.0 / (1.0 - WATER_DRAG);

	private BoatSteering() {
	}

	/**
	 * Which way to hold the steering for the next tick: -1 for left, 1 for right, 0 for neither.
	 *
	 * @param yaw the boat's yaw now
	 * @param turnSpeed how far the boat turned during the last tick, in degrees
	 * @param target the yaw to end up at
	 */
	static int decide(double yaw, double turnSpeed, double target) {
		double error = Mth.wrapDegrees(target - yaw);
		int best = 0;
		double bestMiss = Math.abs(error - restingTurn(turnSpeed, 0));
		for (int nudge = -1; nudge <= 1; nudge += 2) {
			double miss = Math.abs(error - restingTurn(turnSpeed, nudge));
			// Strictly better: with no nudge as good, leave the wheel alone.
			if (miss < bestMiss - 1.0e-9) {
				best = nudge;
				bestMiss = miss;
			}
		}
		return best;
	}

	/** How far the boat will still turn, from now until it stops, if it is nudged {@code nudge} this coming tick and then left alone. */
	static double restingTurn(double turnSpeed, int nudge) {
		return (turnSpeed * WATER_DRAG + nudge) / (1.0 - WATER_DRAG);
	}

	/** Whether the boat has stopped close enough to the target that steering is done. */
	static boolean settled(double yaw, double turnSpeed, double target) {
		return Math.abs(turnSpeed) < 0.15 && Math.abs(Mth.wrapDegrees(target - yaw)) <= RESOLUTION_DEGREES / 2.0 + 0.5;
	}
}
