package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ChatType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

/**
 * A short cue when another player's chat message arrives, gated by {@link
 * UnitedMinecraftConfig#chatSoundEnabled}. The message itself is read by the game's narrator
 * (or the screen reader), so this only marks that something was said. Fired from {@code
 * ChatMessageSoundMixin}, which sees player chat and disguised chat (/say, /me, server-formatted
 * chat) but not system messages such as join/leave notices and command feedback.
 */
public final class ChatMessageSound {
	/** Keeps a busy server's chat from turning the cue into a buzz. */
	private static final long MIN_INTERVAL_MS = 250;

	private static long lastPlayedMs;

	private ChatMessageSound() {
	}

	public static void onMessage(ChatType.Bound bound) {
		if (!UnitedMinecraftConfig.get().chatSoundEnabled) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || isOwnMessage(bound.name().getString(), player.getName().getString())) {
			return;
		}
		long now = Util.getMillis();
		if (now - lastPlayedMs < MIN_INTERVAL_MS) {
			return;
		}
		lastPlayedMs = now;
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundSource.MASTER,
				CueVolume.scale(0.5f), 1.8f, player.getRandom(), player.getX(), player.getY(), player.getZ()));
	}

	static boolean isOwnMessage(String senderName, String ownName) {
		return senderName.equals(ownName);
	}
}
