package com.nibblenerds.unitedminecraft.client.combat;

import com.nibblenerds.unitedminecraft.client.MapBeaconController;
import com.nibblenerds.unitedminecraft.client.audio.CombatLockSound;
import com.nibblenerds.unitedminecraft.client.audio.CueVolume;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The audio half of {@link UnitedMinecraftConfig.CombatStyle#BEACON} Combat Mode: instead of
 * taking over the camera, the nearest hostile becomes a sound the player turns toward.
 *
 * <ul>
 * <li>A ping is placed a fixed short distance from the player along the real horizontal bearing
 * to the target (the trick {@link MapBeaconController} uses), so Minecraft's own panning says
 * which side it is on. It repeats faster the closer the player's facing is to the target's
 * bearing - slow when facing away, fastest when nearly on it - which also tells ahead from
 * behind without any extra cue.</li>
 * <li>The ping's pitch carries the vertical: neutral when level with the target, lower when
 * the player needs to look up, higher when down.</li>
 * <li>Once the player's aim is on the target in both axes, the pings give way to a solid tone
 * ({@link CombatLockSound}) that holds until the aim drifts off again.</li>
 * </ul>
 */
public final class CombatBeacon {
	private static final double PING_OFFSET_BLOCKS = 2.0;
	private static final int PING_INTERVAL_MIN_TICKS = 2;
	private static final int PING_INTERVAL_MAX_TICKS = 20;
	/** Vertical error that moves the ping one octave. */
	private static final double DEGREES_PER_OCTAVE = 60.0;
	private static final double LOCK_TOLERANCE_MIN_DEGREES = 3.0;
	private static final double LOCK_TOLERANCE_MAX_DEGREES = 12.0;
	/** Leaving the lock takes this much more error than entering it, so the tone doesn't stutter at the edge. */
	private static final double LOCK_RELEASE_FACTOR = 1.5;
	private static final float PING_VOLUME = 0.8f;
	private static final float LOCK_VOLUME = 0.35f;

	private static int ticksUntilPing;
	// Hysteresis state for Combat Mode's own aim tolerance; the bow beacon keeps its own.
	private static boolean locked;
	private static CombatLockSound lockSound;

	private CombatBeacon() {
	}

	/** Silences the beacon entirely - Combat Mode off, switched to Lock On, a screen opened, or the world left. */
	public static void stop() {
		locked = false;
		ticksUntilPing = 0;
		if (lockSound != null) {
			Minecraft.getInstance().getSoundManager().stop(lockSound);
			lockSound = null;
		}
	}

	static void tick(Minecraft client, LocalPlayer player, Entity target) {
		if (target == null) {
			stop();
			return;
		}
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		Vec3 aim = box.getCenter();
		double dx = aim.x - eye.x;
		double dz = aim.z - eye.z;
		double dy = aim.y - eye.y;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		double yawError = yawErrorDegrees(dx, dz, player.getYRot());
		double pitchError = pitchErrorDegrees(dy, horizontal, player.getXRot());

		double distance = Math.sqrt(horizontal * horizontal + dy * dy);
		double yawTolerance = toleranceDegrees(box.getXsize() / 2.0, distance);
		double pitchTolerance = toleranceDegrees(box.getYsize() / 2.0, distance);
		locked = isLocked(locked, Math.abs(yawError), Math.abs(pitchError), yawTolerance, pitchTolerance);
		guide(client, player, eye, dx, dz, yawError, pitchError, locked);
	}

	/**
	 * The sound for one tick of guidance, given how far off the player's aim is and whether it counts
	 * as {@code onTarget}: the solid lock tone if so, otherwise a ping on the cadence {@code yawError} sets.
	 * Shared with {@link BowBeaconController}, which decides {@code onTarget} by simulating the shot.
	 *
	 * @param dx the horizontal offset to the aim point, which places the ping (only its direction matters)
	 * @param dz as {@code dx}
	 */
	static void guide(Minecraft client, LocalPlayer player, Vec3 eye, double dx, double dz,
			double yawError, double pitchError, boolean onTarget) {
		if (onTarget) {
			ticksUntilPing = 0;
			holdLockTone(client, player);
			return;
		}
		releaseLockTone(client);

		if (--ticksUntilPing > 0) {
			return;
		}
		ticksUntilPing = pingIntervalTicks(Math.abs(yawError));
		playPing(client, player, eye, dx, dz, pitchError);
	}

	private static void playPing(Minecraft client, LocalPlayer player, Vec3 eye, double dx, double dz, double pitchError) {
		double length = Math.sqrt(dx * dx + dz * dz);
		double ux;
		double uz;
		if (length < 0.1) {
			// Straight above or below: no horizontal bearing, so put it dead ahead.
			double yawRad = Math.toRadians(player.getYRot());
			ux = -Math.sin(yawRad);
			uz = Math.cos(yawRad);
		} else {
			ux = dx / length;
			uz = dz / length;
		}
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_COW_BELL.value(), SoundSource.MASTER,
				CueVolume.scale(PING_VOLUME), pingPitch(pitchError), player.getRandom(),
				eye.x + ux * PING_OFFSET_BLOCKS, eye.y, eye.z + uz * PING_OFFSET_BLOCKS));
	}

	private static void holdLockTone(Minecraft client, LocalPlayer player) {
		SoundManager sounds = client.getSoundManager();
		if (lockSound != null && !sounds.isActive(lockSound)) {
			// Stopped out from under us (sound device change, resource reload, stopAll).
			sounds.stop(lockSound);
			lockSound = null;
		}
		if (lockSound == null) {
			lockSound = CombatLockSound.live(player.getRandom(), CueVolume.scale(LOCK_VOLUME));
			sounds.play(lockSound);
		}
	}

	private static void releaseLockTone(Minecraft client) {
		if (lockSound != null) {
			client.getSoundManager().stop(lockSound);
			lockSound = null;
		}
	}

	/** Plays the lock tone once for the sound glossary. */
	public static void previewLock(Minecraft client, LocalPlayer player) {
		client.getSoundManager().play(CombatLockSound.preview(player.getRandom(), CueVolume.scale(LOCK_VOLUME)));
	}

	/** Plays a ping at the given vertical error for the sound glossary. */
	public static void previewPing(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_COW_BELL.value(), SoundSource.MASTER,
				CueVolume.scale(PING_VOLUME), 1.0f, player.getRandom(), pos.x(), pos.y(), pos.z()));
	}

	// --- Pure math, split out so it can be tested without a running game ---------------------

	/**
	 * Signed horizontal angle from where the player faces to the target, in degrees, in (-180, 180].
	 * Minecraft's yaw is 0 = south and increases toward the west, so the direction to a target at
	 * offset ({@code dx}, {@code dz}) has yaw {@code atan2(-dx, dz)}. A target straight above or
	 * below ({@code dx}, {@code dz} both ~0) has no bearing, so counts as dead ahead.
	 */
	static double yawErrorDegrees(double dx, double dz, float playerYaw) {
		if (dx * dx + dz * dz < 0.01) {
			return 0.0;
		}
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		return Mth.wrapDegrees(targetYaw - playerYaw);
	}

	/**
	 * Signed vertical angle the player still has to turn, in degrees: negative means look up,
	 * positive means look down (Minecraft's pitch is positive looking down).
	 */
	static double pitchErrorDegrees(double dy, double horizontal, float playerPitch) {
		double targetPitch = -Math.toDegrees(Math.atan2(dy, Math.max(horizontal, 1.0e-6)));
		return targetPitch - playerPitch;
	}

	/** Ticks between pings: {@link #PING_INTERVAL_MIN_TICKS} when on the bearing, {@link #PING_INTERVAL_MAX_TICKS} when facing directly away. */
	static int pingIntervalTicks(double absYawError) {
		double fraction = Math.min(Math.max(absYawError / 180.0, 0.0), 1.0);
		return PING_INTERVAL_MIN_TICKS + (int) Math.round((PING_INTERVAL_MAX_TICKS - PING_INTERVAL_MIN_TICKS) * fraction);
	}

	/** Sound pitch for a vertical error: 1.0 when level, an octave lower per 60 degrees to look up, higher to look down; clamped to what the engine allows. */
	static float pingPitch(double pitchError) {
		double pitch = Math.pow(2.0, pitchError / DEGREES_PER_OCTAVE);
		return (float) Math.min(Math.max(pitch, 0.5), 2.0);
	}

	/** How far off, in degrees, the aim can be and still count as on a target of the given half-size at the given distance. */
	static double toleranceDegrees(double halfExtent, double distance) {
		double degrees = Math.toDegrees(Math.atan2(halfExtent, Math.max(distance, 1.0)));
		return Math.min(Math.max(degrees, LOCK_TOLERANCE_MIN_DEGREES), LOCK_TOLERANCE_MAX_DEGREES);
	}

	/** Enters the lock inside the tolerance on both axes, and only leaves it once outside {@link #LOCK_RELEASE_FACTOR} times that. */
	static boolean isLocked(boolean wasLocked, double absYawError, double absPitchError, double yawTolerance, double pitchTolerance) {
		double factor = wasLocked ? LOCK_RELEASE_FACTOR : 1.0;
		return absYawError <= yawTolerance * factor && absPitchError <= pitchTolerance * factor;
	}
}
