package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Plays an audio cue the moment the mount jump-strength meter (held Jump while riding a
 * horse/donkey/mule/llama/camel, released to leap - vanilla's only feedback for how charged it
 * is is a purely visual bar) reaches full charge - edge-triggered off {@link #jumpReadyLastTick},
 * the same pattern {@link CombatModeController#tickAttackCue} already uses for the weapon
 * attack-strength meter.
 *
 * <p>{@link LocalPlayer#getJumpRidingScale()} reads 0 whenever the player isn't currently
 * charging a jump (not riding a jump-capable mount, or not holding Jump), so this is safe to
 * poll unconditionally every tick without checking {@code getVehicle()} itself first.
 */
public final class MountJumpCueController {
	private static boolean jumpReadyLastTick;

	private MountJumpCueController() {
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().mountJumpCueEnabled) {
			// Resync so flipping the setting mid-charge doesn't fire a stale "just became ready"
			// cue for a charge that finished while untracked.
			jumpReadyLastTick = false;
			return;
		}
		boolean ready = player.getJumpRidingScale() >= 1.0f;
		if (ready && !jumpReadyLastTick) {
			playJumpReadyCue(client, player);
		}
		jumpReadyLastTick = ready;
	}

	private static void playJumpReadyCue(Minecraft client, LocalPlayer player) {
		RandomSource random = player.getRandom();
		Vec3 pos = player.position();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.MASTER, 0.6f, 1.6f, random, pos.x(), pos.y(), pos.z()));
	}

	/** Called when the player unloads, so a charge held across a world/session boundary doesn't leak in as a stale "already ready" state. */
	public static void reset() {
		jumpReadyLastTick = false;
	}
}
