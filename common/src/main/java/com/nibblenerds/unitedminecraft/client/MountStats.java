package com.nibblenerds.unitedminecraft.client;

/**
 * What a horse's speed and jump strength attributes mean in blocks - the numbers a sighted player
 * works out by watching it run and jump, and the only way to tell a good horse from a poor one.
 * Free of Minecraft types so it can be tested without the game.
 */
final class MountStats {
	/**
	 * Blocks per second a ridden mount covers on flat ground per point of its movement speed
	 * attribute - the same conversion that makes a walking player (0.1) cover 4.317 blocks a second.
	 */
	static final double BLOCKS_PER_SECOND_PER_SPEED = 43.17;

	// Vanilla's per-tick vertical drag on a falling or rising entity (LivingEntity#travel).
	private static final double VERTICAL_DRAG = 0.98;

	private MountStats() {
	}

	static double speedBlocksPerSecond(double movementSpeed) {
		return movementSpeed * BLOCKS_PER_SECOND_PER_SPEED;
	}

	/**
	 * The height in blocks a fully charged jump reaches. A horse's jump launches it at {@code
	 * jumpStrength} blocks per tick upward ({@code LivingEntity#getJumpPower}, with the charge at its
	 * full 1.0 and an ordinary block under it), and each tick after that it moves, then gravity pulls
	 * on its velocity and drag scales it. The peak is where that velocity runs out.
	 */
	static double jumpHeight(double jumpStrength, double gravity) {
		double height = 0.0;
		double velocity = jumpStrength;
		while (velocity > 0.0) {
			height += velocity;
			velocity = (velocity - gravity) * VERTICAL_DRAG;
		}
		return height;
	}
}
