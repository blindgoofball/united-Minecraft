package com.nibblenerds.unitedminecraft.client.audio;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig.BuildCursorFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 3D spatial audio cues for Build Mode's virtual cursor.
 *
 * <p>Positions sounds in real 3D space at the cursor's world position, mirroring the audio
 * architecture of {@code audio_minecraft}:
 * <ul>
 *   <li><b>True 3D Spatial Panning:</b> Sounds originate from the cursor block itself, so moving
 *       right pans progressively further right, stepping left brings it back toward center, and
 *       moving behind places the sound in back.</li>
 *   <li><b>Camera Distance Falloff:</b> Uses the gentle inverse-distance attenuation curve
 *       (-4 dB at 5 blocks) so distant blocks stay clearly audible across the full build area while
 *       providing precise near-vs-far depth perception.</li>
 *   <li><b>Elevation Pitch Shift:</b> Vertical cursor position shifts playback pitch continuously
 *       (up to +4 semitones straight above, -4 semitones straight below).</li>
 *   <li><b>Behind Attenuation Cue:</b> Sounds located behind the listener receive a subtle gain reduction
 *       for unmistakable front/back disambiguation on headphones.</li>
 *   <li><b>Distinct Material Cues:</b> Crisp footstep step sounds for solid blocks, punchy CC0 synth
 *       whooshes for empty air, and fluid cues for water and lava.</li>
 * </ul>
 */
public final class BuildCursorSound {
	public static final SoundEvent AIR_SOUND =
			SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("united_minecraft", "build_cursor_air"));

	public static final float BASE_CUE_VOLUME = 3.0f;
	public static final double CAMERA_ROLLOFF_FACTOR = 0.15;
	public static final double REFERENCE_DISTANCE = 1.0;

	private BuildCursorSound() {
	}

	/**
	 * Plays the spatial audio cue for the cursor's current position and block.
	 */
	public static void playCursorCue(Minecraft client, LocalPlayer player, BlockPos cursor, Direction stepDirection, Direction facing) {
		if (UnitedMinecraftConfig.get().buildCursorFeedback == BuildCursorFeedback.SPEAK_ONLY) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(cursor);
		Vec3 eye = player.getEyePosition();
		Vec3 audioPos = calculateAudioPosition(eye, cursor, facing);
		RandomSource random = player.getRandom();

		double distance = eye.distanceTo(audioPos);
		float distanceGain = calculateDistanceGain(distance);
		float elevationPitch = calculateElevationPitch(eye, audioPos);
		float behindGain = calculateBehindGain(eye, player.getYRot(), audioPos);

		SoundEvent sound;
		float baseVolume;
		float basePitch = 1.0f;

		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (fluid.is(FluidTags.LAVA)) {
				sound = SoundEvents.LAVA_POP;
			} else {
				sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
			}
			baseVolume = BASE_CUE_VOLUME;
		} else if (state.isAir()) {
			sound = AIR_SOUND;
			baseVolume = BASE_CUE_VOLUME;
		} else {
			SoundType soundType = state.getSoundType();
			sound = soundType.getStepSound();
			baseVolume = Math.max(2.5f, soundType.getVolume() * BASE_CUE_VOLUME);
			basePitch = soundType.getPitch();
		}

		float finalVolume = CueVolume.scale(baseVolume) * distanceGain * behindGain;
		float finalPitch = basePitch * elevationPitch;

		client.getSoundManager().play(new SimpleSoundInstance(
				sound.location(),
				SoundSource.MASTER,
				finalVolume,
				finalPitch,
				random,
				false,
				0,
				SoundInstance.Attenuation.NONE,
				audioPos.x(),
				audioPos.y(),
				audioPos.z(),
				false
		));
	}

	/**
	 * Computes the 3D position vector in world space for emitting the sound in OpenAL.
	 *
	 * <p>Returns the center of the {@code cursor} block. If the cursor is positioned directly
	 * at the player's head (distance &lt; 0.3 blocks), pushes the sound slightly forward along
	 * {@code facing} to prevent degenerate panning at distance zero.
	 */
	public static Vec3 calculateAudioPosition(Vec3 eye, BlockPos cursor, Direction facing) {
		Vec3 center = Vec3.atCenterOf(cursor);
		if (eye.distanceTo(center) < 0.3) {
			Direction fwd = facing != null ? facing : Direction.NORTH;
			Vec3 forwardVec = Vec3.atLowerCornerOf(fwd.getUnitVec3i());
			return eye.add(forwardVec.scale(0.5));
		}
		return center;
	}

	/**
	 * Gentle camera distance attenuation: inside reach it stays clearly audible, with just enough
	 * fall-off (about -4 dB at 5 blocks, -7.4 dB at 10 blocks) to tell near from far.
	 */
	public static float calculateDistanceGain(double distance) {
		if (distance <= REFERENCE_DISTANCE) {
			return 1.0f;
		}
		return (float) (REFERENCE_DISTANCE / (REFERENCE_DISTANCE + CAMERA_ROLLOFF_FACTOR * (distance - REFERENCE_DISTANCE)));
	}

	/**
	 * Continuous elevation pitch cue: up to +4 semitones straight above, -4 semitones straight below.
	 */
	public static float calculateElevationPitch(Vec3 eye, Vec3 targetPos) {
		double distance = eye.distanceTo(targetPos);
		if (distance < 0.3) {
			return 1.0f;
		}
		double dy = targetPos.y() - eye.y();
		double upRatio = Mth.clamp(dy / distance, -1.0, 1.0);
		double semitones = 4.0 * upRatio;
		return (float) Math.pow(2.0, semitones / 12.0);
	}

	/**
	 * Behind cue: applies subtle gain reduction (up to 30% quieter) when target is behind player.
	 */
	public static float calculateBehindGain(Vec3 eye, float yaw, Vec3 targetPos) {
		Vec3 delta = targetPos.subtract(eye);
		double distance = delta.length();
		if (distance < 0.3) {
			return 1.0f;
		}
		double yawRad = Math.toRadians(yaw);
		double fwdX = -Math.sin(yawRad);
		double fwdZ = Math.cos(yawRad);
		double ahead = delta.x() * fwdX + delta.z() * fwdZ;
		double aheadRatio = ahead / distance;
		double behind = Mth.clamp((-aheadRatio - 0.15) / 0.85, 0.0, 1.0);
		return (float) (1.0 - 0.3 * behind);
	}

	/** Plays the block step sound cue for preview in {@link com.nibblenerds.unitedminecraft.client.SoundGlossaryScreen}. */
	public static void previewBlock(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.getEyePosition();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.STONE_STEP.location(),
				SoundSource.MASTER,
				CueVolume.scale(BASE_CUE_VOLUME),
				1.0f,
				player.getRandom(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				pos.x(),
				pos.y(),
				pos.z(),
				false));
	}

	/** Plays the air cue for preview in {@link com.nibblenerds.unitedminecraft.client.SoundGlossaryScreen}. */
	public static void previewAir(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.getEyePosition();
		client.getSoundManager().play(new SimpleSoundInstance(
				AIR_SOUND.location(),
				SoundSource.MASTER,
				CueVolume.scale(BASE_CUE_VOLUME),
				1.0f,
				player.getRandom(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				pos.x(),
				pos.y(),
				pos.z(),
				false));
	}
}
