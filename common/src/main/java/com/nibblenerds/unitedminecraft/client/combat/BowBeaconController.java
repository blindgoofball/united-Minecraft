package com.nibblenerds.unitedminecraft.client.combat;

import com.nibblenerds.unitedminecraft.client.CameraUtil;
import com.nibblenerds.unitedminecraft.client.scanner.ScannerController;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Optional audio guidance for ranged weapons, the bow counterpart of the Beacon style of Combat
 * Mode (see {@link CombatBeacon}, whose pings and lock tone it reuses). While a bow is drawn, a
 * crossbow is loaded, or a trident is held back, a ping plays from the target's direction -
 * faster as the player turns toward it, lower to look up and higher to look down - but pointed at
 * where the <em>arrow</em> has to go rather than at the target itself, so the pitch accounts for
 * the arc (which shifts as the bow is drawn further) and a moving target is led.
 *
 * <p>The solid lock tone means a real hit, not just good aim: the shot is simulated from the
 * player's actual look direction with the real launch speed, gravity and drag, against the target
 * moving along its current velocity, and the tone only plays if that arrow reaches the target
 * before anything solid stops it. That makes the lock honest about line of sight, about an
 * under-drawn bow that would fall short, and about an arc that clips a wall - none of which a
 * simple "pointing roughly at it" tolerance could know.
 *
 * <p>The target is the Scanner's locked entity if there is one, otherwise the hostile mob nearest
 * where the player is already aiming that can currently be seen. While this is switched on the
 * camera locks (the Scanner's and Combat Mode's Lock On) stop steering whenever a ranged weapon is
 * held, see {@link #pausesLock} - otherwise the camera would aim for the player and there would be
 * nothing left for the beacon to do.
 */
public final class BowBeaconController {
	private static final double SEARCH_RANGE = 64.0;
	private static final int MAX_FLIGHT_TICKS = 100;
	// A hit is predicted against the target's box grown by this much: the arrow itself has some
	// width, and aim by ear is not pixel-perfect. Leaving the lock needs more error than entering
	// it, so the tone does not stutter when the aim sits right on the edge.
	private static final double HIT_MARGIN_BLOCKS = 0.25;
	private static final double HIT_RELEASE_MARGIN_BLOCKS = 0.6;
	private static final double PROJECTILE_GRAVITY = 0.05;
	private static final double PROJECTILE_DRAG = 0.99;
	// How much of each tick's measured movement replaces the remembered velocity. Client-side
	// positions of other entities arrive in lerped steps, so a raw one-tick difference is jumpy.
	private static final double VELOCITY_SMOOTHING = 0.3;
	private static final double MAX_LEAD_TICKS = 40.0;

	private static boolean guiding;
	private static boolean onTarget;
	private static Entity velocityTarget;
	private static Vec3 smoothedVelocity = Vec3.ZERO;

	private BowBeaconController() {
	}

	/** The beacon is currently producing sound, so Combat Mode's own beacon must stay quiet. */
	static boolean isGuiding() {
		return guiding;
	}

	public static void reset() {
		guiding = false;
		onTarget = false;
		velocityTarget = null;
		smoothedVelocity = Vec3.ZERO;
	}

	/**
	 * True while the setting is on and a ranged weapon is in hand: the camera locks yield so the
	 * player can turn to the beacon. A trident only counts while it is being held back, since it is
	 * an ordinary melee weapon the rest of the time.
	 */
	public static boolean pausesLock() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !UnitedMinecraftConfig.get().bowBeaconEnabled) {
			return false;
		}
		return holdsBowOrCrossbow(player.getMainHandItem()) || holdsBowOrCrossbow(player.getOffhandItem())
				|| (player.isUsingItem() && player.getUseItem().getItem() instanceof TridentItem);
	}

	private static boolean holdsBowOrCrossbow(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	/** Bow being drawn (even before it can fire), loaded crossbow, or trident held back. */
	private static boolean isAiming(LocalPlayer player) {
		return CameraUtil.launchSpeed(player) > 0.0
				|| (player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem);
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().bowBeaconEnabled || !isAiming(player)) {
			if (guiding) {
				CombatBeacon.stop();
			}
			reset();
			return;
		}

		Entity target = pickTarget(player);
		if (target == null) {
			if (guiding) {
				CombatBeacon.stop();
			}
			guiding = false;
			onTarget = false;
			return;
		}
		guiding = true;

		// Below a bow's firing threshold there is no launch speed to solve an arc from; a flat
		// aim is the best guide until the draw gets there, and the lock is out of reach.
		double speed = CameraUtil.launchSpeed(player);
		Vec3 eye = player.getEyePosition();
		Vec3 velocity = smoothVelocity(target);
		Vec3 aimPoint = leadPoint(eye, target.getBoundingBox().getCenter(), velocity, speed);
		CameraUtil.BallisticAim aim = speed > 0.0
				? CameraUtil.ballisticAim(eye, aimPoint, speed)
				: new CameraUtil.BallisticAim((float) Math.toDegrees(Math.atan2(-(aimPoint.x - eye.x), aimPoint.z - eye.z)),
						(float) Math.toDegrees(Math.atan2(-(aimPoint.y - eye.y), Math.hypot(aimPoint.x - eye.x, aimPoint.z - eye.z))), false);

		double yawError = Mth.wrapDegrees(aim.yaw() - player.getYRot());
		double pitchError = aim.pitch() - player.getXRot();
		onTarget = speed > 0.0 && wouldHit(player, target, velocity, speed, onTarget ? HIT_RELEASE_MARGIN_BLOCKS : HIT_MARGIN_BLOCKS);

		CombatBeacon.guide(client, player, eye, aimPoint.x - eye.x, aimPoint.z - eye.z, yawError, pitchError, onTarget);
	}

	/** The Scanner's locked entity if it is still around, otherwise the visible hostile nearest the player's aim. */
	private static Entity pickTarget(LocalPlayer player) {
		Entity locked = ScannerController.lockedTarget();
		if (locked != null && locked.isAlive() && locked.level() == player.level()) {
			return locked;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB search = player.getBoundingBox().inflate(SEARCH_RANGE);
		Entity best = null;
		double bestDot = -2.0;
		for (Entity entity : player.level().getEntities(player, search,
				e -> e.isAlive() && e instanceof Enemy && !CombatModeController.isCalmNeutral(e))) {
			Vec3 center = entity.getBoundingBox().getCenter();
			Vec3 toward = center.subtract(eye);
			double distance = toward.length();
			if (distance > SEARCH_RANGE || distance < 1.0e-3) {
				continue;
			}
			double dot = toward.scale(1.0 / distance).dot(look);
			if (dot > bestDot && canSee(player.level(), player, eye, center)) {
				best = entity;
				bestDot = dot;
			}
		}
		return best;
	}

	private static boolean canSee(Level level, Entity viewer, Vec3 eye, Vec3 point) {
		return level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer))
				.getType() == HitResult.Type.MISS;
	}

	/** The target's movement per tick, smoothed - zero for the first tick a target is seen. */
	private static Vec3 smoothVelocity(Entity target) {
		Vec3 measured = target.position().subtract(target.oldPosition());
		if (target != velocityTarget) {
			velocityTarget = target;
			smoothedVelocity = Vec3.ZERO;
			return smoothedVelocity;
		}
		smoothedVelocity = smoothedVelocity.scale(1.0 - VELOCITY_SMOOTHING).add(measured.scale(VELOCITY_SMOOTHING));
		return smoothedVelocity;
	}

	/** Where the target will be about when the arrow gets there: its centre plus its velocity times a rough flight time. */
	private static Vec3 leadPoint(Vec3 eye, Vec3 center, Vec3 velocity, double speed) {
		if (speed <= 0.0) {
			return center;
		}
		double ticks = Math.min(center.distanceTo(eye) / speed, MAX_LEAD_TICKS);
		return center.add(velocity.scale(ticks));
	}

	/**
	 * Flies an arrow from the player's real look direction and says whether it reaches the target
	 * before a block stops it. The target is moved along its velocity each tick alongside the
	 * arrow, and its box grown by {@code margin}.
	 */
	private static boolean wouldHit(LocalPlayer player, Entity target, Vec3 velocity, double speed, double margin) {
		Level level = player.level();
		Vec3 position = player.getEyePosition();
		Vec3 motion = player.getLookAngle().scale(speed);
		AABB box = target.getBoundingBox().inflate(margin);
		for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
			Vec3 next = position.add(motion);
			AABB movedBox = box.move(velocity.scale(tick + 1));
			Optional<Vec3> entityHit = movedBox.clip(position, next);
			BlockHitResult blockHit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			boolean blocked = blockHit.getType() != HitResult.Type.MISS;
			if (entityHit.isPresent()) {
				// Hit the target unless a block is in the way first on this same step.
				return !blocked || position.distanceToSqr(entityHit.get()) <= position.distanceToSqr(blockHit.getLocation());
			}
			if (blocked) {
				return false;
			}
			position = next;
			motion = new Vec3(motion.x * PROJECTILE_DRAG, motion.y * PROJECTILE_DRAG - PROJECTILE_GRAVITY, motion.z * PROJECTILE_DRAG);
		}
		return false;
	}
}
