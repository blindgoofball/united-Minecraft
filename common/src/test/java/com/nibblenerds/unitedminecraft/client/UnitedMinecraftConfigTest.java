package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnitedMinecraftConfigTest {
	@TempDir
	Path root;
	private Path file;

	@BeforeEach
	void setUp() throws IOException {
		TestPlatform.install(root);
		file = root.resolve("config").resolve("united_minecraft.json");
		Files.createDirectories(file.getParent());
	}

	@Test
	void outOfRangeValuesAreClampedOnLoad() throws IOException {
		Files.writeString(file, """
				{"scannerRange": 10000, "cueVolume": 0, "hostileRadarRange": -3,
				 "durabilityWarningThreshold": 20, "durabilityCriticalThreshold": 40}""");
		UnitedMinecraftConfig.load();
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();

		assertEquals(64.0, config.scannerRange);
		assertEquals(5, config.cueVolume);
		assertEquals(4.0, config.hostileRadarRange);
		assertEquals(20, config.durabilityCriticalThreshold, "critical can never sit above the warning threshold");
	}

	@Test
	void unknownChoicesFallBackToTheirDefault() throws IOException {
		Files.writeString(file, "{\"combatCueMode\": \"SOMETIMES\", \"wallToneStyle\": \"BAGPIPES\", \"structureVoiceMuted\": null}");
		UnitedMinecraftConfig.load();
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();

		assertEquals(UnitedMinecraftConfig.CombatCueMode.COMBAT_MODE_ONLY, config.combatCueMode);
		assertEquals(UnitedMinecraftConfig.WallToneStyle.TONES, config.wallToneStyle);
		assertTrue(config.structureVoiceMuted.isEmpty());
	}

	@Test
	void settingsMissingFromTheFileKeepTheirDefaults() throws IOException {
		Files.writeString(file, "{\"scannerRange\": 48}");
		UnitedMinecraftConfig.load();
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();

		assertEquals(48.0, config.scannerRange);
		assertEquals(100, config.cueVolume);
		assertTrue(config.titleNarrationEnabled);
	}

	@Test
	void savedSettingsRoundTrip() throws IOException {
		Files.writeString(file, "{}");
		UnitedMinecraftConfig.load();
		UnitedMinecraftConfig.get().cueVolume = 35;
		UnitedMinecraftConfig.get().titleNarrationEnabled = false;
		UnitedMinecraftConfig.save();

		UnitedMinecraftConfig.get().cueVolume = 100;
		UnitedMinecraftConfig.load();
		assertEquals(35, UnitedMinecraftConfig.get().cueVolume);
		assertFalse(UnitedMinecraftConfig.get().titleNarrationEnabled);
	}

	@Test
	void anUnreadableFileIsSetAsideNotOverwritten() throws IOException {
		Files.writeString(file, "{ \"scannerRange\": ");
		UnitedMinecraftConfig.load();

		assertFalse(Files.exists(file));
		try (Stream<Path> siblings = Files.list(file.getParent())) {
			assertTrue(siblings.anyMatch(path -> path.getFileName().toString().startsWith("united_minecraft.json.corrupt-")));
		}
	}
}
