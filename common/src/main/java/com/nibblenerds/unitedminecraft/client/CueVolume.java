package com.nibblenerds.unitedminecraft.client;

/**
 * The player's Audio Cue Volume setting, applied to every short sound cue this mod plays - radar
 * pings, fall warnings, arrival chimes, mode toggles and the rest - so they can be balanced
 * against the game's own sound. Wall Tones and Structure Voices are continuous or spoken rather
 * than cues, and keep their own volume settings instead.
 */
final class CueVolume {
	private CueVolume() {
	}

	/** {@code volume} as the cue was designed to sound, scaled by the setting. */
	static float scale(float volume) {
		return volume * UnitedMinecraftConfig.get().cueVolume / 100.0f;
	}
}
