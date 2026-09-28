package com.nibblenerds.unitedminecraft.client;

import java.util.Locale;

import net.minecraft.resources.Identifier;

/**
 * Every looping voice {@link WallToneController} can play, with its pitch and where it sits
 * around the listener's head.
 *
 * <p>Pitches form a C major chord (behind C4, left E4, right G4, ahead C5, ceiling E5) so an
 * enclosed room - where every voice plays at once - sounds consonant rather than grating.
 * Each direction gets its own pitch rather than relying on position alone: HRTF front/back
 * confusion is common, and left and right in particular must differ - two identical tones at
 * equal volume from opposite sides fuse into one centered tone, which is exactly the
 * corridor case wall tones exist for.
 *
 * <p>The {@link UnitedMinecraftConfig.WallToneStyle#NOISE noise} style keeps the same ordering
 * (behind lowest, ahead highest) as band centers for filtered pink noise instead - see {@link
 * WallToneSynth}.
 *
 * <p>{@link #earX}/{@link #earY}/{@link #earZ} are in OpenAL listener space (the sound is
 * played head-relative): -Z is straight ahead, +X right, +Y up. A voice therefore stays
 * put relative to the player's ears while turning, the same way the probe directions rotate
 * with facing.
 */
public enum WallToneVoice {
	WALL_AHEAD(Kind.WALL, Direction.AHEAD, 523, 2000),
	WALL_BEHIND(Kind.WALL, Direction.BEHIND, 262, 400),
	WALL_LEFT(Kind.WALL, Direction.LEFT, 330, 800),
	WALL_RIGHT(Kind.WALL, Direction.RIGHT, 392, 1200),
	OBSTACLE_AHEAD(Kind.OBSTACLE, Direction.AHEAD, 523, 2000),
	OBSTACLE_BEHIND(Kind.OBSTACLE, Direction.BEHIND, 262, 400),
	OBSTACLE_LEFT(Kind.OBSTACLE, Direction.LEFT, 330, 800),
	OBSTACLE_RIGHT(Kind.OBSTACLE, Direction.RIGHT, 392, 1200),
	CEILING(Kind.CEILING, Direction.UP, 659, 3200);

	/** How far from the listener each voice is placed - close enough to be clearly directional, never so close it's "inside the head". */
	private static final double EAR_DISTANCE = 2.0;

	public enum Kind {
		WALL, OBSTACLE, CEILING
	}

	public enum Direction {
		AHEAD(0, 0, -1), BEHIND(0, 0, 1), LEFT(-1, 0, 0), RIGHT(1, 0, 0), UP(0, 1, 0);

		final int x;
		final int y;
		final int z;

		Direction(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private final Kind kind;
	private final Direction direction;
	private final int frequency;
	private final int noiseCenter;
	private final String fileName;

	WallToneVoice(Kind kind, Direction direction, int frequency, int noiseCenter) {
		this.kind = kind;
		this.direction = direction;
		this.frequency = frequency;
		this.noiseCenter = noiseCenter;
		this.fileName = name().toLowerCase(Locale.ROOT);
	}

	public Kind kind() {
		return kind;
	}

	public Direction direction() {
		return direction;
	}

	public int frequency() {
		return frequency;
	}

	/** Center of this voice's band in the noise style, in Hz. */
	public int noiseCenter() {
		return noiseCenter;
	}

	public String fileName() {
		return fileName;
	}

	/** The sound location {@link WallToneSound} plays - resolves to {@code sounds/wall_tone/<style>/<name>.ogg}, which {@link WallToneSynth} supplies. */
	public Identifier identifier(UnitedMinecraftConfig.WallToneStyle style) {
		return Identifier.fromNamespaceAndPath(WallToneSynth.NAMESPACE,
				"wall_tone/" + style.name().toLowerCase(Locale.ROOT) + "/" + fileName);
	}

	public double earX() {
		return direction.x * EAR_DISTANCE;
	}

	public double earY() {
		return direction.y * EAR_DISTANCE;
	}

	public double earZ() {
		return direction.z * EAR_DISTANCE;
	}

	public static WallToneVoice byFileName(String name) {
		for (WallToneVoice voice : values()) {
			if (voice.fileName.equals(name)) {
				return voice;
			}
		}
		return null;
	}

	/** The wall or obstacle voice for a horizontal direction. */
	public static WallToneVoice of(Kind kind, Direction direction) {
		for (WallToneVoice voice : values()) {
			if (voice.kind == kind && voice.direction == direction) {
				return voice;
			}
		}
		throw new IllegalArgumentException(kind + " " + direction);
	}
}
