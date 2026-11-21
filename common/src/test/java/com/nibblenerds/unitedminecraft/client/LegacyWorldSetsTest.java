package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyWorldSetsTest {
	private Path config;

	@BeforeEach
	void setUp(@TempDir Path root) {
		TestPlatform.install(root);
		config = root.resolve("config").resolve("united_minecraft");
	}

	private Path fileIn(String subdirectory, String worldId) {
		return config.resolve(subdirectory).resolve(WorldScopedStore.fileNameFor(worldId));
	}

	private void save(String subdirectory, String worldId, String json) throws IOException {
		Path file = fileIn(subdirectory, worldId);
		Files.createDirectories(file.getParent());
		Files.writeString(file, json);
	}

	@Test
	void findsTheSetsForAnAddressAndReadsTheirMarkers() throws IOException {
		save("markers", "mp_play.example.com_1a2b", "[{\"name\":\"Home\"},{\"name\":\"Mine\"}]");
		save("named_blocks", "mp_play.example.com_1a2b", "[]");
		save("markers", "mp_play.example.com_ff", "[{\"name\":\"Base\"}]");

		List<LegacyWorldSets.LegacySet> sets = LegacyWorldSets.find("play.example.com");

		assertEquals(2, sets.size());
		LegacyWorldSets.LegacySet first = sets.stream().filter(s -> s.rawId().endsWith("_1a2b")).findFirst().orElseThrow();
		assertEquals(2, first.markerCount());
		assertEquals(List.of("Home", "Mine"), first.markerNames());
	}

	@Test
	void aFileFromAnotherAddressThatCleansUpTheSameIsNotTaken() throws IOException {
		// "1.2.3.4:25565" and "1-2-3-4-25565" both clean up to mp_1_2_3_4_25565; only the hash tells them apart.
		save("markers", "mp_1-2-3-4-25565_abc", "[{\"name\":\"Elsewhere\"}]");
		save("markers", "mp_1.2.3.4:25565_def", "[{\"name\":\"Here\"}]");

		List<LegacyWorldSets.LegacySet> sets = LegacyWorldSets.find("1.2.3.4:25565");

		assertEquals(1, sets.size());
		assertEquals("mp_1.2.3.4:25565_def", sets.get(0).rawId());
	}

	@Test
	void anAddressWithNoSetsHasNone() throws IOException {
		save("markers", "mp_other.example.com_1", "[]");
		assertTrue(LegacyWorldSets.find("play.example.com").isEmpty());
	}

	@Test
	void claimingMovesEveryStoresFileToTheNewWorld() throws IOException {
		save("markers", "mp_play.example.com_1a2b", "[{\"name\":\"Home\"}]");
		save("named_blocks", "mp_play.example.com_1a2b", "[]");
		LegacyWorldSets.LegacySet set = LegacyWorldSets.find("play.example.com").get(0);

		LegacyWorldSets.claim(set, "mp_world_x");

		assertTrue(Files.exists(fileIn("markers", "mp_world_x")));
		assertTrue(Files.exists(fileIn("named_blocks", "mp_world_x")));
		assertFalse(Files.exists(fileIn("markers", "mp_play.example.com_1a2b")));
		assertTrue(LegacyWorldSets.find("play.example.com").isEmpty());
	}

	@Test
	void claimingNeverOverwritesWhatTheWorldAlreadyHas() throws IOException {
		save("markers", "mp_play.example.com_1a2b", "[{\"name\":\"Old\"}]");
		save("markers", "mp_world_x", "[{\"name\":\"New\"}]");
		LegacyWorldSets.claim(LegacyWorldSets.find("play.example.com").get(0), "mp_world_x");
		assertEquals("[{\"name\":\"New\"}]", Files.readString(fileIn("markers", "mp_world_x")));
	}

	@Test
	void startingFreshMarksTheWorldAsHavingData() {
		assertFalse(LegacyWorldSets.hasData("mp_world_y"));
		LegacyWorldSets.startFresh("mp_world_y");
		assertTrue(LegacyWorldSets.hasData("mp_world_y"));
	}
}
