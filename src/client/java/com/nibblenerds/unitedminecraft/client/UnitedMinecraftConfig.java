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

import net.fabricmc.loader.api.FabricLoader;

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

	/** Governs the audio cue for the weapon attack-strength meter refilling - see {@link CombatModeController#tickAttackCue}. */
	public enum CombatCueMode {
		OFF, COMBAT_MODE_ONLY, ALWAYS
	}

	private UnitedMinecraftConfig() {
	}

	private void sanitize() {
		durabilityWarningThreshold = Math.max(1, Math.min(50, durabilityWarningThreshold));
		durabilityCriticalThreshold = Math.max(1,
				Math.min(durabilityWarningThreshold, Math.min(50, durabilityCriticalThreshold)));
	}

	public static UnitedMinecraftConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("united_minecraft.json");
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
