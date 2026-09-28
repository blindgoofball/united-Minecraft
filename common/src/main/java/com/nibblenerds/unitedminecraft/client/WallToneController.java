package com.nibblenerds.unitedminecraft.client;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import com.nibblenerds.unitedminecraft.client.mixin.SoundEngineAccessorMixin;
import com.nibblenerds.unitedminecraft.client.mixin.SoundManagerAccessorMixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wall tones, in the style of Wrath Access (https://github.com/bradjrenshaw/wotr-access) and
 * Grimdark (https://github.com/ahicks92/grimdark): a continuous looping tone for each of
 * ahead, behind, left and right (relative to facing), each one louder the closer a wall is in
 * that direction and silent when nothing is within range. Walking down a corridor you hear both
 * side tones steady; a side tone dropping out means an opening, a doorway, or a side passage.
 *
 * <p>Minecraft is 3D rather than top-down, so "is there a wall" is decided by height, using
 * three horizontal rays per direction against real collision shapes:
 * <ul>
 * <li>head ({@link #HEAD_CLEARANCE} below the top of the player's hitbox) and waist
 * ({@link #WAIST_PROBE}) - anything they hit is a <b>wall</b>, something you can't pass;</li>
 * <li>ankle (just above step-up height, so slabs/carpets/paths are ignored) - something only
 * this ray hits is an <b>obstacle</b>: a one-block rise you could jump onto, played on a
 * separate pulsing tone set (see {@link WallToneSynth}) unless {@link
 * UnitedMinecraftConfig#wallToneObstaclesEnabled} is off, in which case it counts as a wall. An
 * ankle hit too tall to jump (fence, wall block) is always a wall.</li>
 * </ul>
 * An optional ceiling tone above the head does the same thing vertically, which is what tells a
 * tunnel from a cavern from the open sky.
 *
 * <p>Responsiveness over smoothing, as in Grimdark and Wrath Access: volumes are applied
 * directly with no glide, and pushed straight to the playing channel (see {@link
 * #setVolumeNow}). {@code AccessibilityTickHandler} calls this last, after every turn and
 * rotation-owning mode has run, so a snap turn is heard the same tick it happens.
 *
 * <p>Voices are only started once something is in range and released after a couple of seconds
 * of silence, so open ground costs no sound channels.
 */
public final class WallToneController {
	private static final double WAIST_PROBE = 1.1;
	private static final double HEAD_CLEARANCE = 0.2;
	private static final double ANKLE_ABOVE_STEP = 0.05;
	/** Same jump reach {@link NavRadarController} classifies against. */
	private static final double JUMP_HEIGHT = 1.25;
	private static final double SAME_FACE_TOLERANCE = 0.05;
	/** Below this hitbox height (swimming, crawling) there's no meaningful head/ankle split - one ray, all walls. */
	private static final double LOW_POSE_HEIGHT = 1.0;
	private static final float CEILING_GAIN = 0.6f;
	private static final int SILENT_TICKS_BEFORE_RELEASE = 40;
	private static final float SILENT = 0.001f;

	private static final WallToneVoice.Direction[] HORIZONTAL = {
			WallToneVoice.Direction.AHEAD, WallToneVoice.Direction.BEHIND,
			WallToneVoice.Direction.LEFT, WallToneVoice.Direction.RIGHT,
	};

	private static final Map<WallToneVoice, WallToneSound> playing = new EnumMap<>(WallToneVoice.class);
	private static final Map<WallToneVoice, Integer> silentTicks = new EnumMap<>(WallToneVoice.class);
	private static final float[] targets = new float[WallToneVoice.values().length];

	private WallToneController() {
	}

	/** Persisted in {@link UnitedMinecraftConfig}, like the radars. */
	public static boolean isEnabled() {
		return UnitedMinecraftConfig.get().wallTonesEnabled;
	}

	public static void toggle(Minecraft client) {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		config.wallTonesEnabled = !config.wallTonesEnabled;
		UnitedMinecraftConfig.save();
		client.getNarrator().saySystemNow(Component.translatable(config.wallTonesEnabled
				? "united_minecraft.narrate.wall_tones_on"
				: "united_minecraft.narrate.wall_tones_off"));
	}

	/** Stops every voice - used when leaving a world. */
	public static void reset() {
		if (!playing.isEmpty()) {
			SoundManager sounds = Minecraft.getInstance().getSoundManager();
			for (WallToneSound sound : playing.values()) {
				sounds.stop(sound);
			}
		}
		playing.clear();
		silentTicks.clear();
	}

	/**
	 * @param active false while something should silence the tones without turning them off
	 *        (a screen is open) - voices go quiet and resume by themselves afterwards.
	 */
	public static void tick(Minecraft client, LocalPlayer player, boolean active) {
		Arrays.fill(targets, 0.0f);
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		if (config.wallTonesEnabled && active && player.isAlive()) {
			computeTargets(player, config);
		}
		if (!config.wallTonesEnabled && playing.isEmpty()) {
			return;
		}
		for (WallToneVoice voice : WallToneVoice.values()) {
			apply(client, player, voice, targets[voice.ordinal()], config.wallToneStyle);
		}
	}

	private static void computeTargets(LocalPlayer player, UnitedMinecraftConfig config) {
		double range = config.wallToneRange;
		float gain = config.wallToneVolume / 100.0f;
		Level level = player.level();

		double yaw = Math.toRadians(player.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);

		for (WallToneVoice.Direction direction : HORIZONTAL) {
			double dx;
			double dz;
			switch (direction) {
				case AHEAD -> {
					dx = forwardX;
					dz = forwardZ;
				}
				case BEHIND -> {
					dx = -forwardX;
					dz = -forwardZ;
				}
				case LEFT -> {
					dx = forwardZ;
					dz = -forwardX;
				}
				default -> {
					dx = -forwardZ;
					dz = forwardX;
				}
			}
			Probe probe = probe(level, player, dx, dz, range);
			double wall = probe.wall();
			double obstacle = probe.obstacle();
			if (obstacle < wall && !config.wallToneObstaclesEnabled) {
				wall = obstacle;
			}
			targets[WallToneVoice.of(WallToneVoice.Kind.WALL, direction).ordinal()] = loudness(wall, range) * gain;
			if (obstacle < wall) {
				targets[WallToneVoice.of(WallToneVoice.Kind.OBSTACLE, direction).ordinal()] = loudness(obstacle, range) * gain;
			}
		}

		if (config.wallToneCeilingEnabled) {
			Vec3 top = new Vec3(player.getX(), player.getY() + player.getBbHeight(), player.getZ());
			double distance = cast(level, player, top, top.add(0, range, 0));
			targets[WallToneVoice.CEILING.ordinal()] = loudness(distance, range) * gain * CEILING_GAIN;
		}
	}

	/** Horizontal distances from the player's center to the nearest wall and nearest (jumpable) obstacle, infinite when clear. */
	private static Probe probe(Level level, LocalPlayer player, double dx, double dz, double range) {
		double feet = player.getY();
		double height = player.getBbHeight();

		if (height < LOW_POSE_HEIGHT) {
			return new Probe(castHorizontal(level, player, feet + height / 2.0, dx, dz, range), Double.POSITIVE_INFINITY);
		}

		double head = feet + height - HEAD_CLEARANCE;
		double waist = feet + Math.min(WAIST_PROBE, height - HEAD_CLEARANCE);
		double ankle = feet + player.maxUpStep() + ANKLE_ABOVE_STEP;

		double wall = Math.min(
				castHorizontal(level, player, head, dx, dz, range),
				castHorizontal(level, player, waist, dx, dz, range));

		Vec3 from = new Vec3(player.getX(), ankle, player.getZ());
		BlockHitResult low = clip(level, player, from, from.add(dx * range, 0, dz * range));
		if (low.getType() == HitResult.Type.MISS) {
			return new Probe(wall, Double.POSITIVE_INFINITY);
		}
		double lowDistance = horizontalDistance(from, low.getLocation());
		// Same face the waist/head rays already hit (an ordinary two-high wall) - not a separate obstacle.
		if (lowDistance >= wall - SAME_FACE_TOLERANCE) {
			return new Probe(wall, Double.POSITIVE_INFINITY);
		}
		double rise = topOf(level, low.getBlockPos()) - feet;
		if (rise > JUMP_HEIGHT) {
			return new Probe(lowDistance, Double.POSITIVE_INFINITY);
		}
		return new Probe(wall, lowDistance);
	}

	/** Top of the collision shape at {@code pos}, continuing into the block above if the shape reaches the top of its own block. */
	private static double topOf(Level level, BlockPos pos) {
		double top = pos.getY();
		BlockPos cursor = pos;
		for (int i = 0; i < 3; i++) {
			BlockState state = level.getBlockState(cursor);
			VoxelShape shape = state.getCollisionShape(level, cursor);
			if (shape.isEmpty()) {
				break;
			}
			double max = shape.max(Direction.Axis.Y);
			top = cursor.getY() + max;
			if (max < 1.0) {
				break;
			}
			cursor = cursor.above();
		}
		return top;
	}

	private static double castHorizontal(Level level, LocalPlayer player, double y, double dx, double dz, double range) {
		Vec3 from = new Vec3(player.getX(), y, player.getZ());
		return cast(level, player, from, from.add(dx * range, 0, dz * range));
	}

	private static double cast(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
		BlockHitResult hit = clip(level, player, from, to);
		return hit.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceTo(hit.getLocation());
	}

	private static BlockHitResult clip(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
	}

	private static double horizontalDistance(Vec3 from, Vec3 to) {
		double x = to.x() - from.x();
		double z = to.z() - from.z();
		return Math.sqrt(x * x + z * z);
	}

	/** 0 at or beyond range, rising to 1 against the wall - squared so it bites close in (same curve Wrath Access uses). */
	private static float loudness(double distance, double range) {
		if (!(distance < range)) {
			return 0.0f;
		}
		double t = 1.0 - distance / range;
		return (float) (t * t);
	}

	private static void apply(Minecraft client, LocalPlayer player, WallToneVoice voice, float target,
			UnitedMinecraftConfig.WallToneStyle style) {
		SoundManager sounds = client.getSoundManager();
		WallToneSound sound = playing.get(voice);
		if (sound != null && (!sounds.isActive(sound) || sound.style() != style)) {
			// Stopped out from under us (sound device change, resource reload, stopAll), or the
			// style setting changed - forget it so it's restarted below if it's still wanted.
			sounds.stop(sound);
			playing.remove(voice);
			sound = null;
		}

		if (target > SILENT) {
			if (sound == null) {
				// Starts straight at its real volume: tone loops begin at a zero crossing and pulsed
				// loops on a silent edge, and a noise onset is already click-like, so a fade-in
				// would only add latency.
				sound = WallToneSound.live(voice, style, player.getRandom(), target);
				sounds.play(sound);
				playing.put(voice, sound);
			} else {
				setVolumeNow(sounds, sound, target);
			}
			silentTicks.put(voice, 0);
		} else if (sound != null) {
			setVolumeNow(sounds, sound, 0.0f);
			int silent = silentTicks.getOrDefault(voice, 0) + 1;
			if (silent >= SILENT_TICKS_BEFORE_RELEASE) {
				sounds.stop(sound);
				playing.remove(voice);
				silentTicks.remove(voice);
			} else {
				silentTicks.put(voice, silent);
			}
		}
	}

	/**
	 * Sets the voice's volume and pushes it to its playing channel now, rather than waiting for
	 * vanilla's own sound tick - which runs before this (end-of-tick) update, so it would only
	 * pick the change up a whole tick later. See {@link SoundEngineAccessorMixin}.
	 */
	private static void setVolumeNow(SoundManager sounds, WallToneSound sound, float volume) {
		sound.setVolume(volume);
		SoundEngine engine = ((SoundManagerAccessorMixin) sounds).unitedMinecraft$getSoundEngine();
		SoundEngineAccessorMixin access = (SoundEngineAccessorMixin) engine;
		ChannelAccess.ChannelHandle handle = access.unitedMinecraft$getInstanceToChannel().get(sound);
		if (handle != null) {
			float gain = access.unitedMinecraft$calculateVolume(sound);
			handle.execute(channel -> channel.setVolume(gain));
		}
	}

	/** Plays a short sample of one voice at a comfortable fixed volume, in the current style, for the sound glossary. */
	public static void preview(Minecraft client, LocalPlayer player, WallToneVoice voice) {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		float gain = config.wallToneVolume / 100.0f;
		client.getSoundManager().play(WallToneSound.preview(voice, config.wallToneStyle, player.getRandom(), 0.6f * gain));
	}

	private record Probe(double wall, double obstacle) {
	}
}
