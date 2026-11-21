package com.nibblenerds.unitedminecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldIdRelayTest {
	@Test
	void aNewWorldGetsAnIdThatIsKept(@TempDir Path dir) {
		Path file = dir.resolve(WorldIdRelay.FILE_NAME);
		UUID first = WorldIdRelay.readOrCreate(file);
		assertTrue(Files.exists(file));
		assertEquals(first, WorldIdRelay.readOrCreate(file));
	}

	@Test
	void twoWorldsGetDifferentIds(@TempDir Path dir) {
		assertNotEquals(
				WorldIdRelay.readOrCreate(dir.resolve("a.txt")),
				WorldIdRelay.readOrCreate(dir.resolve("b.txt")));
	}

	@Test
	void aFileThatIsNotAnIdIsSetAsideAndReplaced(@TempDir Path dir) throws IOException {
		Path file = dir.resolve(WorldIdRelay.FILE_NAME);
		Files.writeString(file, "not an id");
		UUID id = WorldIdRelay.readOrCreate(file);
		assertEquals(id, WorldIdRelay.readOrCreate(file));
		try (var files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().contains("corrupt")));
		}
	}
}
