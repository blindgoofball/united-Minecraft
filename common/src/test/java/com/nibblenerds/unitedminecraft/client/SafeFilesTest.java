package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SafeFilesTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(SafeFilesTest.class);

	@TempDir
	Path root;

	private List<String> fileNames(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(path -> path.getFileName().toString()).sorted().toList();
		}
	}

	@Test
	void writesANewFileCreatingItsFolder() throws IOException {
		Path file = root.resolve("nested").resolve("data.json");
		SafeFiles.writeAtomically(file, writer -> writer.write("hello"));
		assertEquals("hello", Files.readString(file));
	}

	@Test
	void replacesAnExistingFileWhole() throws IOException {
		Path file = root.resolve("data.json");
		Files.writeString(file, "a much longer original text");
		SafeFiles.writeAtomically(file, writer -> writer.write("short"));
		assertEquals("short", Files.readString(file));
		assertEquals(List.of("data.json"), fileNames(root), "no temporary file is left behind");
	}

	@Test
	void aFailedWriteLeavesTheOriginalUntouched() throws IOException {
		Path file = root.resolve("data.json");
		Files.writeString(file, "original");
		assertThrows(IOException.class, () -> SafeFiles.writeAtomically(file, writer -> {
			writer.write("half of the new");
			throw new IOException("disk full");
		}));
		assertEquals("original", Files.readString(file));
		assertEquals(List.of("data.json"), fileNames(root), "no temporary file is left behind");
	}

	@Test
	void anUnreadableFileIsMovedAsideWithItsContents() throws IOException {
		Path file = root.resolve("data.json");
		Files.writeString(file, "{ broken");
		SafeFiles.preserveUnreadable(file, LOGGER);

		assertFalse(Files.exists(file));
		List<String> names = fileNames(root);
		assertEquals(1, names.size());
		assertTrue(names.get(0).startsWith("data.json.corrupt-"), names.get(0));
		assertEquals("{ broken", Files.readString(root.resolve(names.get(0))));
	}
}
