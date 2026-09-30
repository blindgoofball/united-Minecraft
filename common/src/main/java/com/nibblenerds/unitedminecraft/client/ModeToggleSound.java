package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The short audio cue that accompanies toggling Build Mode or Combat Mode on or off, gated by
 * {@link UnitedMinecraftConfig#modeToggleSoundsEnabled}. Each mode gets its own note-block
 * instrument so they're told apart by ear, and on/off are the same instrument pitched up vs. down
 * so "entering" and "leaving" are too. Instruments are chosen to stay clear of the ones other cues
 * in this mod already use (chime, bass, xylophone, didgeridoo, bell, hat, pling).
 */
final class ModeToggleSound {
	private static final float PITCH_ON = 2.0f;
	private static final float PITCH_OFF = 1.0f;

	private ModeToggleSound() {
	}

	static void playBuildMode(Minecraft client, LocalPlayer player, boolean on) {
		play(client, player, SoundEvents.NOTE_BLOCK_BIT.value(), on);
	}

	static void playCombatMode(Minecraft client, LocalPlayer player, boolean on) {
		play(client, player, SoundEvents.NOTE_BLOCK_GUITAR.value(), on);
	}

	private static void play(Minecraft client, LocalPlayer player, SoundEvent sound, boolean on) {
		if (!UnitedMinecraftConfig.get().modeToggleSoundsEnabled) {
			return;
		}
		client.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.MASTER, 0.8f,
				on ? PITCH_ON : PITCH_OFF, player.getRandom(), player.getX(), player.getY(), player.getZ()));
	}
}
