package com.nibblenerds.unitedminecraft.client.audio;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig.BuildCursorFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
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
 * <p>Plays material-specific footstep/step sounds when stepping over blocks, a custom licensed air
 * whoosh cue when over air / empty space, and fluid cues when over water or lava. Sounds are
 * positioned directly at the cursor's 3D coordinates so Minecraft's internal OpenAL audio handler
 * performs accurate spatial panning, elevation, and direction calculation.
 */
public final class BuildCursorSound {
	public static final SoundEvent AIR_SOUND =
			SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("united_minecraft", "build_cursor_air"));

	public static final double MAX_AUDIO_OFFSET_BLOCKS = 2.0;
	public static final float BASE_CUE_VOLUME = 2.5f;

	private BuildCursorSound() {
	}

	/**
	 * Plays the spatial audio cue for the cursor's current position and block.
	 */
	public static void playCursorCue(Minecraft client, LocalPlayer player, BlockPos cursor) {
		if (UnitedMinecraftConfig.get().buildCursorFeedback == BuildCursorFeedback.SPEAK_ONLY) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(cursor);
		Vec3 target = Vec3.atCenterOf(cursor);
		RandomSource random = player.getRandom();

		SoundEvent sound;
		float volume;
		float pitch;

		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (fluid.is(FluidTags.LAVA)) {
				sound = SoundEvents.LAVA_POP;
				volume = CueVolume.scale(BASE_CUE_VOLUME);
				pitch = 1.2f;
			} else {
				sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
				volume = CueVolume.scale(BASE_CUE_VOLUME);
				pitch = 1.2f;
			}
		} else if (state.isAir()) {
			sound = AIR_SOUND;
			volume = CueVolume.scale(BASE_CUE_VOLUME);
			pitch = 1.0f;
		} else {
			SoundType soundType = state.getSoundType();
			sound = soundType.getStepSound();
			volume = CueVolume.scale(Math.max(1.5f, soundType.getVolume() * BASE_CUE_VOLUME));
			pitch = soundType.getPitch();
		}

		client.getSoundManager().play(new SimpleSoundInstance(
				sound,
				SoundSource.MASTER,
				volume,
				pitch,
				random,
				target.x(),
				target.y(),
				target.z()));
	}

	/**
	 * Computes the 3D position vector where a sound instance is emitted in OpenAL.
	 * Kept as a pure mathematical helper for testing ray projections.
	 */
	public static Vec3 calculateAudioPosition(Vec3 eye, Vec3 targetCenter, double maxDistance) {
		Vec3 offset = targetCenter.subtract(eye);
		double distance = offset.length();
		if (distance < 0.1) {
			return eye;
		}
		if (distance <= maxDistance) {
			return targetCenter;
		}
		return eye.add(offset.scale(maxDistance / distance));
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
