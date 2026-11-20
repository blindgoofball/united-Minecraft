package com.nibblenerds.unitedminecraft.client;

/**
 * Clamping that keeps the old {@code Mth.clamp} behavior: an inverted range (min greater than max,
 * which empty lists produce) yields max rather than throwing, as {@link Math#clamp} would.
 */
public final class MathUtil {
	private MathUtil() {
	}

	public static int clamp(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}

	public static float clamp(float value, float min, float max) {
		return value < min ? min : Math.min(value, max);
	}

	public static double clamp(double value, double min, double max) {
		return value < min ? min : Math.min(value, max);
	}
}
