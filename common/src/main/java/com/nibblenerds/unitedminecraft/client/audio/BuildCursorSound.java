package com.nibblenerds.unitedminecraft.client.audio;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
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
 * <p>Plays material-specific hit sounds when stepping over blocks, a distinct percussion tick
 * when over air / empty space, and fluid sounds when over water or lava. Sounds are positioned
 * in 3D relative to the player's eye so stereo panning and elevation indicate the cursor's
 * spatial position, with distance clamped so sounds remain clearly audible even when the cursor
 * roams far away.
 */
public final class BuildCursorSound {
	public static final double MAX_AUDIO_OFFSET_BLOCKS = 3.5;
	public static final float AIR_VOLUME = 0.5f;
	public static final float AIR_PITCH = 1.6f;
	public static final float FLUID_VOLUME = 0.6f;
	public static final float SOLID_VOLUME_SCALE = 0.8f;

	private BuildCursorSound() {
	}

	/**
	 * Plays the spatial audio cue for the cursor's current position and block.
	 */
	public static void playCursorCue(Minecraft client, LocalPlayer player, BlockPos cursor) {
		if (!UnitedMinecraftConfig.get().buildModeSoundCuesEnabled) {
			return;
		}
		Level level = player.level();
		BlockState state = level.getBlockState(cursor);
		Vec3 eye = player.getEyePosition();
		Vec3 target = Vec3.atCenterOf(cursor);
		Vec3 audioPos = calculateAudioPosition(eye, target, MAX_AUDIO_OFFSET_BLOCKS);
		RandomSource random = player.getRandom();

		SoundEvent sound;
		float volume;
		float pitch;

		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (fluid.is(FluidTags.LAVA)) {
				sound = SoundEvents.LAVA_POP;
				volume = FLUID_VOLUME;
				pitch = 1.2f;
			} else {
				sound = SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON;
				volume = FLUID_VOLUME;
				pitch = 1.2f;
			}
		} else if (state.isAir()) {
			sound = SoundEvents.NOTE_BLOCK_HAT.value();
			volume = AIR_VOLUME;
			pitch = AIR_PITCH;
		} else {
			SoundType soundType = state.getSoundType();
			sound = soundType.getHitSound();
			volume = Math.max(0.4f, soundType.getVolume() * SOLID_VOLUME_SCALE);
			pitch = soundType.getPitch();
		}

		client.getSoundManager().play(new SimpleSoundInstance(
				sound,
				SoundSource.MASTER,
				CueVolume.scale(volume),
				pitch,
				random,
				audioPos.x(),
				audioPos.y(),
				audioPos.z()));
	}

	/**
	 * Computes the 3D position vector where the sound instance should be emitted in OpenAL.
	 *
	 * <p>Clamps the distance along the ray from {@code eye} to {@code targetCenter} to {@code maxDistance}
	 * so directional stereo panning and elevation are preserved without letting the sound attenuate into
	 * silence when the cursor is far from the player.
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

	/** Plays the block hit sound cue for preview in {@link SoundGlossaryScreen}. */
	public static void previewBlock(Minecraft client, LocalPlayer player) {
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.STONE_HIT,
				SoundSource.MASTER,
				CueVolume.scale(SOLID_VOLUME_SCALE),
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
				SoundEvents.NOTE_BLOCK_HAT.value(),
				SoundSource.MASTER,
				CueVolume.scale(AIR_VOLUME),
				AIR_PITCH,
				player.getRandom(),
				pos.x(),
				pos.y(),
				pos.z()));
	}
}
