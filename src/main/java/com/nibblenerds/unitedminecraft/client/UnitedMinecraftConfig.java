package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.nibblenerds.unitedminecraft.platform.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * United Minecraft's user-configurable settings, persisted as a single JSON file shared
 * across every world/server (unlike {@link MapMarkerController}'s per-world markers) -
 * these are player preferences, not something that varies by where you're playing.
 *
 * <p>Plain public fields rather than getters/setters - {@link SettingsScreen} mutates
 * {@link #get()} directly and every controller reads straight from it, matching how
 * {@link MapMarkerController}'s own {@code MapMarker} record is used elsewhere in this
 * codebase.
 */
public final class UnitedMinecraftConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static UnitedMinecraftConfig instance = new UnitedMinecraftConfig();

	public boolean hostileRadarEnabled = true;
	public double hostileRadarRange = 16.0;
	public boolean meleeRangeAlertEnabled = true;
	public boolean fallWarningEnabled = true;
	public double fallWarningThreshold = 3.0;
	public double fallWarningLookaheadSeconds = 1.0;
	public int miningRadarRange = 8;
	public int navRadarRange = 8;
	public double scannerRange = 32.0;
	public boolean buildModeActionNarrationEnabled = true;
	public CombatCueMode combatCueMode = CombatCueMode.COMBAT_MODE_ONLY;
	public boolean durabilityAwarenessEnabled = true;
	public int durabilityWarningThreshold = 25;
	public int durabilityCriticalThreshold = 10;
	public boolean toolHarvestWarningEnabled = true;
	public boolean scannerSkipEmptyCategories = false;
	public boolean scannerAutoLockAfterWalk = false;
	public boolean navRadarEnabled = false;
	public boolean miningRadarEnabled = false;
	public boolean autoCrosshairNarrationEnabled = false;
	public boolean preciseCoordinatesEnabled = false;
	public boolean mountJumpCueEnabled = true;
	public boolean mapBeaconEnabled = true;

	/** Governs the audio cue for the weapon attack-strength meter refilling - see {@link CombatModeController#tickAttackCue}. */
	public enum CombatCueMode {
		OFF, COMBAT_MODE_ONLY, ALWAYS
	}

	private UnitedMinecraftConfig() {
	}

	/**
	 * Forces every field back into the range {@link SettingsScreen}'s own control for it can
	 * produce. Only the two durability thresholds were checked before, so a hand-edited or
	 * partially-corrupt file could set a scannerRange of 10000 (a scan of billions of block
	 * positions, freezing the client outright), a negative fall-warning threshold, or a
	 * combatCueMode Gson couldn't parse - which it leaves null, silently disabling the attack cue
	 * with nothing said about why.
	 *
	 * <p>Anything actually out of range is logged rather than corrected silently: it means the
	 * file on disk disagreed with what the game will now do, which is worth being able to find
	 * out about from the log.
	 */
	private void sanitize() {
		hostileRadarRange = clamp("hostileRadarRange", hostileRadarRange, 4.0, 32.0);
		fallWarningThreshold = clamp("fallWarningThreshold", fallWarningThreshold, 1.0, 10.0);
		fallWarningLookaheadSeconds = clamp("fallWarningLookaheadSeconds", fallWarningLookaheadSeconds, 0.5, 3.0);
		miningRadarRange = (int) clamp("miningRadarRange", miningRadarRange, 4, 16);
		navRadarRange = (int) clamp("navRadarRange", navRadarRange, 4, 16);
		scannerRange = clamp("scannerRange", scannerRange, 8.0, 64.0);
		durabilityWarningThreshold = (int) clamp("durabilityWarningThreshold", durabilityWarningThreshold, 1, 50);
		// Deliberately capped by the warning threshold rather than a fixed 50: "critical" above
		// "getting low" would mean the critical warning always fired first and the other never.
		durabilityCriticalThreshold =
				(int) clamp("durabilityCriticalThreshold", durabilityCriticalThreshold, 1, durabilityWarningThreshold);
		if (combatCueMode == null) {
			LOGGER.warn("Setting combatCueMode was missing or not a recognised value, using {}", CombatCueMode.COMBAT_MODE_ONLY);
			combatCueMode = CombatCueMode.COMBAT_MODE_ONLY;
		}
	}

	private static double clamp(String name, double value, double min, double max) {
		if (Double.isNaN(value) || value < min || value > max) {
			double clamped = Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
			LOGGER.warn("Setting {} was {}, outside its range {} to {} - using {}", name, value, min, max, clamped);
			return clamped;
		}
		return value;
	}

	public static UnitedMinecraftConfig get() {
		return instance;
	}

	private static Path file() {
		return Platform.get().configDir().resolve("united_minecraft.json");
	}

	public static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			UnitedMinecraftConfig loaded = GSON.fromJson(reader, UnitedMinecraftConfig.class);
			if (loaded != null) {
				loaded.sanitize();
				instance = loaded;
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to load settings from {}", file, e);
		}
	}

	public static void save() {
		instance.sanitize();
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			LOGGER.warn("Failed to save settings to {}", file, e);
		}
	}
}
