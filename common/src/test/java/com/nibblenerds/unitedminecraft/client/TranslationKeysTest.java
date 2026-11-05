package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every translation key written out in the code has to exist in {@code en_us.json}. A key that is
 * missing compiles, runs, and then shows the player the raw key ("united_minecraft.share_screen.
 * offer_entry") in place of the text, which nothing else would catch.
 *
 * <p>Only keys written out whole are checked. One built from a prefix and a suffix, such as a keybind
 * name or an alert, ends at the prefix in the source (with a dot or an underscore), and is skipped.
 */
class TranslationKeysTest {
	// A whole key in a string literal: the namespace, then at least two more dotted parts.
	private static final Pattern USED = Pattern.compile("\"(united_minecraft(?:\\.[a-z0-9_]+){2,})\"");
	private static final Pattern DEFINED = Pattern.compile("\"(united_minecraft(?:\\.[a-z0-9_]+)+)\"\\s*:");

	@Test
	void everyKeyTheCodeUsesIsInTheEnglishLanguageFile() throws IOException {
		Path common = commonModule();

		Set<String> defined = new HashSet<>();
		Matcher definedMatcher = DEFINED.matcher(Files.readString(
				common.resolve("src/main/resources/assets/united_minecraft/lang/en_us.json"), StandardCharsets.UTF_8));
		while (definedMatcher.find()) {
			defined.add(definedMatcher.group(1));
		}

		Map<String, String> missing = new TreeMap<>();
		try (Stream<Path> files = Files.walk(common.resolve("src/main/java"))) {
			for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
				Matcher used = USED.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (used.find()) {
					String key = used.group(1);
					// "...angle_mode_" is a prefix the code completes with a suffix, not a whole key.
					if (!key.endsWith("_") && !defined.contains(key)) {
						missing.putIfAbsent(used.group(1), file.getFileName().toString());
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "translation keys used in code but missing from en_us.json (key=file): " + missing);
	}

	/**
	 * The {@code common} module, whose location the build passes in. It fails rather than skips if
	 * that is missing: a check that quietly does nothing is worse than none.
	 */
	private static Path commonModule() {
		String dir = System.getProperty("united.commonDir");
		assertTrue(dir != null && Files.isDirectory(Path.of(dir, "src/main/java")),
				"the build should pass united.commonDir, the common module's folder (was: " + dir + ")");
		return Path.of(dir);
	}
}
