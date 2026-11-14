package com.nibblenerds.unitedminecraft.client.audio;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig.BuildCursorFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * 3D spatial audio cues for Build Mode's virtual cursor.
 *
 * <p>Positions sounds in real 3D space with expanded lateral separation and acoustic depth cues,
 * mirroring the audio architecture of {@code audio_minecraft}:
 * <ul>
 *   <li><b>True 3D Spatial Panning with Expanded Lateral Spread:</b> Lateral offsets are scaled
 *       by {@link #LATERAL_SPREAD_FACTOR} so every single block step left or right creates an
 *       immediate, unmistakable stereo separation in headphones rather than slowly drifting.</li>
 *   <li><b>Acoustic Behind Cue:</b> When the cursor is positioned behind the listener, a subtle
 *       pitch drop and gain reduction provide immediate front vs. back distinction without
 *       requiring baked audio filter assets.</li>
 *   <li><b>Camera Distance Falloff:</b> Uses a gentle inverse-distance attenuation curve
 *       (-4 dB at 5 blocks) so distant blocks stay clearly audible across the full build area while
 *       providing precise near-vs-far depth perception.</li>
 *   <li><b>Elevation Pitch Shift:</b> Vertical cursor position shifts playback pitch continuously
 *       (up to +4 semitones straight above, -4 semitones straight below).</li>
 *   <li><b>Distinct Material Cues:</b> Crisp footstep step sounds for solid blocks, a short
 *       synthesized air cue ({@link BuildCursorAirSynth}) for empty air, and fluid cues for water and lava.</li>
 * </ul>
 */
public final class BuildCursorSound {
	public static final SoundEvent AIR_SOUND =
			SoundEvent.createVariableRangeEvent(BuildCursorAirSynth.SOUND_ID);

	public static final float BASE_CUE_VOLUME = 3.0f;
	public static final double CAMERA_ROLLOFF_FACTOR = 0.15;
	public static final double REFERENCE_DISTANCE = 1.0;
	public static final double LATERAL_SPREAD_FACTOR = 2.2;

	public record Cue(SoundEvent sound, boolean isAir, float baseVolume, float basePitch) {
	}

	private BuildCursorSound() {
	}

	/**
	 * Selects the sound, volume, and pitch multiplier for the block, fluid, or air at the cursor.
	 */
	public static Cue chooseCue(BlockState state, boolean isBehind) {
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			boolean isLava = fluid.is(FluidTags.LAVA) || fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA;
			SoundEvent sound = isLava
					? SoundEvents.LAVA_POP
					: SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
			float basePitch = isBehind ? 0.85f : 1.0f;
			return new Cue(sound, false, BASE_CUE_VOLUME, basePitch);
		} else if (state.isAir()) {
			float basePitch = isBehind ? 0.82f : 1.0f;
			return new Cue(AIR_SOUND, true, BASE_CUE_VOLUME, basePitch);
		} else {
			SoundType soundType = state.getSoundType();
			float baseVolume = Math.max(2.5f, soundType.getVolume() * BASE_CUE_VOLUME);
			float basePitch = isBehind ? soundType.getPitch() * 0.82f : soundType.getPitch();
			return new Cue(soundType.getStepSound(), false, baseVolume, basePitch);
		}
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

		// Single yaw source for both front/behind detection and behind gain attenuation
		Direction fwd = facing != null ? facing : Direction.NORTH;
		float yaw = fwd.toYRot();
		boolean isBehind = isBehind(eye, yaw, cursor);
		float behindGain = calculateBehindGain(eye, yaw, audioPos);

		Cue cue = chooseCue(state, isBehind);

		float finalVolume = CueVolume.scale(cue.baseVolume()) * distanceGain * behindGain;
		float finalPitch = cue.basePitch() * elevationPitch;

		playCueInstance(client, cue.sound(), cue.isAir(), audioPos, finalVolume, finalPitch, random);
	}

	/**
	 * Whether {@code cursor} is behind the player relative to {@code yaw}.
	 */
	public static boolean isBehind(Vec3 eye, float yaw, BlockPos cursor) {
		double yawRad = Math.toRadians(yaw);
		double fwdX = -Math.sin(yawRad);
		double fwdZ = Math.cos(yawRad);
		Vec3 center = Vec3.atCenterOf(cursor);
		double ahead = (center.x() - eye.x()) * fwdX + (center.z() - eye.z()) * fwdZ;
		return ahead < -0.15;
	}

	/**
	 * Computes the 3D position vector in world space for emitting the sound in OpenAL.
	 *
	 * <p>Expands the lateral offset by {@link #LATERAL_SPREAD_FACTOR} so stepping left/right
	 * immediately creates distinct stereo separation across the headphones.
	 */
	public static Vec3 calculateAudioPosition(Vec3 eye, BlockPos cursor, Direction facing) {
		Direction fwd = facing != null ? facing : Direction.NORTH;
		float yaw = fwd.toYRot();
		double yawRad = Math.toRadians(yaw);
		double fwdX = -Math.sin(yawRad);
		double fwdZ = Math.cos(yawRad);
		double rightX = -fwdZ;
		double rightZ = fwdX;

		Vec3 center = Vec3.atCenterOf(cursor);
		double dx = center.x() - eye.x();
		double dy = center.y() - eye.y();
		double dz = center.z() - eye.z();

		double ahead = dx * fwdX + dz * fwdZ;
		double lateral = dx * rightX + dz * rightZ;

		// Expand lateral separation so 1 block step gives immediate stereo pan
		double effectiveLateral = lateral * LATERAL_SPREAD_FACTOR;

		// If degenerate near head, ensure minimum forward offset
		double spreadDistSq = ahead * ahead + effectiveLateral * effectiveLateral + dy * dy;
		if (spreadDistSq < 0.09) {
			return eye.add(fwdX * 0.5, 0, fwdZ * 0.5);
		}

		return new Vec3(
				eye.x() + fwdX * ahead + rightX * effectiveLateral,
				eye.y() + dy,
				eye.z() + fwdZ * ahead + rightZ * effectiveLateral
		);
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

	/**
	 * Shared helper to play a cue instance, routing air cues through {@link BuildCursorAirSound}
	 * and vanilla SoundEvents through {@link SimpleSoundInstance}.
	 */
	private static void playCueInstance(Minecraft client, SoundEvent sound, boolean isAir, Vec3 pos, float volume, float pitch, RandomSource random) {
		SoundInstance instance = isAir
				? new BuildCursorAirSound(pos.x(), pos.y(), pos.z(), volume, pitch, random)
				: new SimpleSoundInstance(
						sound.location(),
						SoundSource.MASTER,
						volume,
						pitch,
						random,
						false,
						0,
						SoundInstance.Attenuation.NONE,
						pos.x(),
						pos.y(),
						pos.z(),
						false);
		client.getSoundManager().play(instance);
	}

	/** Plays the block step sound cue for preview in {@link com.nibblenerds.unitedminecraft.client.SoundGlossaryScreen}. */
	public static void previewBlock(Minecraft client, LocalPlayer player) {
		playCueInstance(client, SoundEvents.STONE_STEP, false, player.getEyePosition(), CueVolume.scale(BASE_CUE_VOLUME), 1.0f, player.getRandom());
	}

	/** Plays the air cue for preview in {@link com.nibblenerds.unitedminecraft.client.SoundGlossaryScreen}. */
	public static void previewAir(Minecraft client, LocalPlayer player) {
		playCueInstance(client, null, true, player.getEyePosition(), CueVolume.scale(BASE_CUE_VOLUME), 1.0f, player.getRandom());
	}
}
