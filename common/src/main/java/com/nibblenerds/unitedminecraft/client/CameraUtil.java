package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/** Shared "snap the player's look direction at a point" math, used by build mode and the scanner. */
public final class CameraUtil {
	// Matches AbstractArrow's own per-tick physics (getDefaultGravity/getAirDrag) - position
	// advances by the current velocity, then velocity is scaled by drag and gravity is
	// subtracted from its vertical component, in that order, every tick.
	private static final double PROJECTILE_GRAVITY = 0.05;
	private static final double PROJECTILE_DRAG = 0.99;
	private static final int MAX_SIMULATION_TICKS = 140;
	private static final int BISECTION_STEPS = 40;

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

	/**
	 * Like {@link #aimAt}, but solves for the launch pitch a real arrow (at {@code projectileSpeed}
	 * blocks/tick, matching {@code BowItem}'s own draw-power formula) needs to actually arc onto
	 * the target, instead of just pointing straight at it - which under-shoots anything beyond
	 * point-blank range, since gravity pulls the real arrow down along the way.
	 */
	public static void aimBallisticAt(LocalPlayer player, Vec3 target, double projectileSpeed) {
		Vec3 eye = player.getEyePosition();
		double dx = target.x() - eye.x();
		double dy = target.y() - eye.y();
		double dz = target.z() - eye.z();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = horizontalDistance > 1.0e-3
				? solveBallisticPitch(horizontalDistance, dy, projectileSpeed)
				: (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));

		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setOldRot();
		player.setYHeadRot(yaw);
	}

	/**
	 * Aims at an entity the way any lock-on feature (Scanner, Combat Mode) wants to: a flat
	 * look normally, or - while a bow is being drawn, a crossbow is loaded, or a trident is
	 * being held back for a throw - a real ballistic arc instead, continuously matching the
	 * projectile's actual launch speed (for a bow, however far the draw has progressed).
	 */
	public static void aimAtEntity(LocalPlayer player, Entity target) {
		Vec3 center = target.getBoundingBox().getCenter();
		double speed = launchSpeed(player);
		if (speed > 0.0) {
			aimBallisticAt(player, center, speed);
		} else {
			aimAt(player, center);
		}
	}

	// Launch speeds in blocks/tick, read from the game: a trident is always thrown at 2.5
	// (TridentItem.PROJECTILE_SHOOT_POWER), a crossbow fires an arrow at 3.15 and a firework
	// rocket at 1.6 regardless of any charge.
	private static final double TRIDENT_SPEED = 2.5;
	private static final double CROSSBOW_ARROW_SPEED = 3.15;

	/**
	 * How fast the projectile the player is about to release would leave, or 0 when there is
	 * nothing to solve an arc for yet (plain aim is the right fallback then):
	 * <ul>
	 * <li>A bow scales with how far it is drawn. Below BowItem's own 0.1 firing threshold there
	 * is not enough draw speed to solve a sane arc from, and a barely-started draw would snap
	 * the pitch to a wild extreme.
	 * <li>A crossbow is a fixed speed once loaded and fires the instant it is used, so it counts
	 * whenever a loaded one is held. A firework rocket flies under its own rules, so it is left
	 * to plain aim.
	 * <li>A trident is thrown on release after being held back, so it counts while that hold is
	 * in progress. A Riptide trident launches the player instead of being thrown, so it never
	 * counts.
	 * </ul>
	 */
	private static double launchSpeed(LocalPlayer player) {
		float drawPower = drawingBowPower(player);
		if (drawPower >= 0.1f) {
			return drawPower * 3.0f;
		}
		if (player.isUsingItem() && player.getUseItem().getItem() instanceof TridentItem) {
			return EnchantmentHelper.getTridentSpinAttackStrength(player.getUseItem(), player) > 0.0f ? 0.0 : TRIDENT_SPEED;
		}
		ItemStack crossbow = loadedCrossbow(player);
		if (crossbow != null) {
			ChargedProjectiles loaded = crossbow.get(DataComponents.CHARGED_PROJECTILES);
			return loaded != null && !loaded.contains(Items.FIREWORK_ROCKET) ? CROSSBOW_ARROW_SPEED : 0.0;
		}
		return 0.0;
	}

	/**
	 * The loaded crossbow the player would fire, or null. The main hand's wins; an off-hand one
	 * only counts while the main hand is empty, so a loaded crossbow carried in the off-hand
	 * doesn't bend the aim while the player is fighting with a sword.
	 */
	private static ItemStack loadedCrossbow(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		if (main.isEmpty() && off.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(off)) {
			return off;
		}
		return null;
	}

	/** Current bow draw power (0..1, matching {@link BowItem#getPowerForTime}), or 0 if not drawing a bow. */
	private static float drawingBowPower(LocalPlayer player) {
		if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) {
			return 0.0f;
		}
		return BowItem.getPowerForTime(player.getTicksUsingItem());
	}

	/**
	 * Binary search for the launch pitch (degrees, vanilla convention: negative = up) whose
	 * simulated arc passes through {@code heightDiff} at {@code horizontalDistance}. A steeper
	 * (more negative/upward) pitch always arrives higher at that distance than a flatter one, so
	 * the simulated height is monotonic in pitch across a sane range, which is all bisection needs.
	 */
	private static float solveBallisticPitch(double horizontalDistance, double heightDiff, double speed) {
		double low = -80.0;
		double high = 80.0;
		boolean reachable = false;
		for (int i = 0; i < BISECTION_STEPS; i++) {
			double mid = (low + high) / 2.0;
			double h = heightAtDistance(mid, speed, horizontalDistance);
			if (Double.isFinite(h)) {
				reachable = true;
				if (h > heightDiff) {
					low = mid;
				} else {
					high = mid;
				}
			} else {
				// Steep angle couldn't cover the horizontal distance; try flatter angles
				high = mid;
			}
		}
		if (!reachable) {
			return (float) Math.toDegrees(Math.atan2(-heightDiff, horizontalDistance));
		}
		return (float) ((low + high) / 2.0);
	}

	/**
	 * Simulates a projectile launched at {@code pitchDegrees}/{@code speed} and returns its
	 * height once it's first travelled {@code horizontalDistance}, interpolated between the two
	 * surrounding ticks - or {@link Double#NEGATIVE_INFINITY} if it never covers that much
	 * ground (an overly steep angle, or too little draw speed).
	 */
	private static double heightAtDistance(double pitchDegrees, double speed, double horizontalDistance) {
		double pitch = Math.toRadians(pitchDegrees);
		double vy = -Math.sin(pitch) * speed;
		double vxz = Math.cos(pitch) * speed;
		double x = 0.0;
		double y = 0.0;
		for (int tick = 0; tick < MAX_SIMULATION_TICKS; tick++) {
			double nextX = x + vxz;
			double nextY = y + vy;
			if (nextX >= horizontalDistance) {
				double fraction = nextX > x ? (horizontalDistance - x) / (nextX - x) : 1.0;
				return y + (nextY - y) * fraction;
			}
			x = nextX;
			y = nextY;
			vxz *= PROJECTILE_DRAG;
			vy = vy * PROJECTILE_DRAG - PROJECTILE_GRAVITY;
		}
		return Double.NEGATIVE_INFINITY;
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

	// Beyond this many blocks of vertical separation, above/below is narrated alongside the
	// compass heading - shared by the Scanner and both radars (Mining, Hostile) rather than
	// each keeping its own copy of this threshold and wording.
	private static final double VERTICAL_DIRECTION_THRESHOLD = 5.0;

	/** Null when {@code to} is within the normal vertical range of {@code from} for a plain compass heading to suffice. */
	public static Component verticalDirectionTo(Vec3 from, Vec3 to) {
		double dy = to.y() - from.y();
		if (Math.abs(dy) <= VERTICAL_DIRECTION_THRESHOLD) {
			return null;
		}
		int blocks = (int) Math.round(Math.abs(dy));
		Component word = Component.translatable(dy > 0
				? "united_minecraft.direction.above"
				: "united_minecraft.direction.below");
		return Component.translatable("united_minecraft.narrate.scanner_vertical", word, blocks);
	}

	/**
	 * {@link #compassDirectionTo} plus {@link #verticalDirectionTo} folded in when it applies -
	 * the combined "direction" fragment {@link ScannerController}, {@link HostileRadarController},
	 * and {@link MiningRadarController} all narrate alongside a distance.
	 */
	public static Component fullDirectionTo(Vec3 from, Vec3 to) {
		Component direction = compassDirectionTo(from, to);
		Component vertical = verticalDirectionTo(from, to);
		return vertical == null ? direction : direction.copy().append(Component.literal(", ")).append(vertical);
	}
}
