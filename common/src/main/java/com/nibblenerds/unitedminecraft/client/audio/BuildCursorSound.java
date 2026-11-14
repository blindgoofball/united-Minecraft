package com.nibblenerds.unitedminecraft.client.audio;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig.BuildCursorFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * 3D spatial audio cues for Build Mode's virtual cursor.
 *
 * <p>Plays material-specific step sounds when stepping over blocks, a custom licensed air whoosh
 * cue when over air / empty space, and fluid cues when over water or lava.
 *
 * <p>Sounds are positioned relative to the player's camera in the direction of the step so that
 * pressing Left always sounds in the left ear, Right in the right ear, Up in front, Down behind,
 * Page Up above, and Page Down below, regardless of where the cursor has traveled across the room.
 */
public final class BuildCursorSound {
	public static final SoundEvent AIR_SOUND =
			SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("united_minecraft", "build_cursor_air"));

	public static final double AUDIO_OFFSET_BLOCKS = 1.8;
	public static final float BASE_CUE_VOLUME = 2.5f;

	private BuildCursorSound() {
	}

	/**
	 * Plays the spatial audio cue for the cursor's current position and block.
	 *
	 * @param stepDirection the direction the cursor stepped (e.g. Left/Right/Up/Down/Ascend/Descend),
	 *                      or {@code null} if stationary (e.g. toggle on or recenter).
	 */
	public static void playCursorCue(Minecraft client, LocalPlayer player, BlockPos cursor, Direction stepDirection, Direction facing) {
		if (UnitedMinecraftConfig.get().buildCursorFeedback == BuildCursorFeedback.SPEAK_ONLY) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(cursor);
		Vec3 eye = player.getEyePosition();
		Vec3 audioPos = calculateAudioPosition(eye, facing, stepDirection, AUDIO_OFFSET_BLOCKS);
		RandomSource random = player.getRandom();

		SoundEvent sound;
		float volume;
		float pitchMultiplier = 1.0f;
		if (stepDirection == Direction.UP) {
			pitchMultiplier = 1.15f;
		} else if (stepDirection == Direction.DOWN) {
			pitchMultiplier = 0.85f;
		}

		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (fluid.is(FluidTags.LAVA)) {
				sound = SoundEvents.LAVA_POP;
				volume = CueVolume.scale(BASE_CUE_VOLUME);
			} else {
				sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
				volume = CueVolume.scale(BASE_CUE_VOLUME);
			}
		} else if (state.isAir()) {
			sound = AIR_SOUND;
			volume = CueVolume.scale(BASE_CUE_VOLUME);
		} else {
			SoundType soundType = state.getSoundType();
			sound = soundType.getStepSound();
			volume = CueVolume.scale(Math.max(1.8f, soundType.getVolume() * BASE_CUE_VOLUME));
			pitchMultiplier *= soundType.getPitch();
		}

		client.getSoundManager().play(new SimpleSoundInstance(
				sound,
				SoundSource.MASTER,
				volume,
				pitchMultiplier,
				random,
				audioPos.x(),
				audioPos.y(),
				audioPos.z()));
	}

	/**
	 * Computes the 3D position vector in world space for emitting the sound in OpenAL.
	 *
	 * <p>Positions the sound {@code offsetDistance} blocks from {@code eye} in the direction of
	 * the cursor step (or forward along {@code facing} if stationary).
	 */
	public static Vec3 calculateAudioPosition(Vec3 eye, Direction facing, Direction stepDirection, double offsetDistance) {
		if (stepDirection == null) {
			Vec3 forward = Vec3.atLowerCornerOf(facing.getUnitVec3i());
			return eye.add(forward.scale(offsetDistance));
		}
		Vec3 stepVec = Vec3.atLowerCornerOf(stepDirection.getUnitVec3i());
		return eye.add(stepVec.scale(offsetDistance));
	}

	/** Plays the block step sound cue for preview in {@link SoundGlossaryScreen}. */
	public static void previewBlock(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.STONE_STEP,
				SoundSource.MASTER,
				CueVolume.scale(BASE_CUE_VOLUME),
				1.0f,
				player.getRandom(),
				pos.x(),
				pos.y(),
				pos.z()));
	}

	/** Plays the air cue for preview in {@link SoundGlossaryScreen}. */
	public static void previewAir(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				AIR_SOUND,
				SoundSource.MASTER,
				CueVolume.scale(BASE_CUE_VOLUME),
				1.0f,
				player.getRandom(),
				pos.x(),
				pos.y(),
				pos.z()));
	}
}
