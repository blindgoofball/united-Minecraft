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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

class KeybindConfigTest {
	@TempDir
	Path root;
	private Path file;

	@BeforeEach
	void setUp() throws IOException {
		TestPlatform.install(root);
		file = root.resolve("config").resolve("united_minecraft_keybinds.json");
		Files.createDirectories(file.getParent());
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			action.resetToDefault();
		}
		ClientKeyBindings.rebuildIndex();
	}

	private JsonObject savedBindings() throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		return root.getAsJsonObject("bindings");
	}

	@Test
	void savingDefaultsWritesNoBindings() throws IOException {
		KeybindConfig.save();
		assertEquals(0, savedBindings().size());
	}

	@Test
	void onlyChangedBindingsAreSaved() throws IOException {
		ClientKeyBindings.NARRATE_HEALTH.setCurrent(new Keybind(InputConstants.KEY_J, InputConstants.MOD_CONTROL));
		KeybindConfig.save();

		JsonObject bindings = savedBindings();
		assertEquals(1, bindings.size());
		JsonObject health = bindings.getAsJsonObject("narrate_health");
		assertEquals(InputConstants.KEY_J, health.get("key").getAsInt());
		assertEquals(InputConstants.MOD_CONTROL, health.get("modifiers").getAsInt());
	}

	@Test
	void aBindingSetBackToItsDefaultIsDroppedFromTheFile() throws IOException {
		ClientKeyBindings.NARRATE_HEALTH.setCurrent(new Keybind(InputConstants.KEY_J, 0));
		KeybindConfig.save();
		ClientKeyBindings.NARRATE_HEALTH.resetToDefault();
		KeybindConfig.save();
		assertEquals(0, savedBindings().size());
	}

	@Test
	void savedBindingsRoundTrip() {
		ClientKeyBindings.NARRATE_HEALTH.setCurrent(new Keybind(InputConstants.KEY_J, InputConstants.MOD_ALT));
		ClientKeyBindings.NARRATE_TIME.setCurrent(Keybind.UNBOUND);
		KeybindConfig.save();

		setUpDefaultsOnly();
		KeybindConfig.load();
		assertEquals(new Keybind(InputConstants.KEY_J, InputConstants.MOD_ALT), ClientKeyBindings.NARRATE_HEALTH.current());
		assertTrue(ClientKeyBindings.NARRATE_TIME.current().isUnbound());
		assertEquals(ClientKeyBindings.NARRATE_COORDINATES.default_(), ClientKeyBindings.NARRATE_COORDINATES.current());
	}

	@Test
	void anActionMissingFromTheFileKeepsItsDefault() throws IOException {
		Files.writeString(file, "{\"schemaVersion\": 2, \"bindings\": {}}");
		KeybindConfig.load();
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			assertEquals(action.default_(), action.current(), action.id());
		}
	}

	@Test
	void unknownActionsAreIgnored() throws IOException {
		Files.writeString(file, "{\"schemaVersion\": 2, \"bindings\": {\"no_such_action\": {\"key\": 5, \"modifiers\": 0}}}");
		KeybindConfig.load();
		assertEquals(ClientKeyBindings.NARRATE_HEALTH.default_(), ClientKeyBindings.NARRATE_HEALTH.current());
	}

	@Test
	void aPre263FileIsTranslatedFromGlfwCodes() throws IOException {
		// No schemaVersion: GLFW's J is 74 and its Shift bit is 1.
		Files.writeString(file, "{\"narrate_health\": {\"key\": 74, \"modifiers\": 1}, \"narrate_time\": {\"key\": -1, \"modifiers\": 0}}");
		KeybindConfig.load();

		assertEquals(new Keybind(InputConstants.KEY_J, InputConstants.MOD_SHIFT), ClientKeyBindings.NARRATE_HEALTH.current());
		assertTrue(ClientKeyBindings.NARRATE_TIME.current().isUnbound());
		assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().has("schemaVersion"),
				"the migrated file is written back in the current format");
	}

	@Test
	void anUnreadableFileIsSetAsideNotOverwritten() throws IOException {
		Files.writeString(file, "{ this is not json");
		KeybindConfig.load();

		assertFalse(Files.exists(file));
		try (Stream<Path> siblings = Files.list(file.getParent())) {
			assertTrue(siblings.anyMatch(path -> path.getFileName().toString().startsWith("united_minecraft_keybinds.json.corrupt-")));
		}
		assertEquals(ClientKeyBindings.NARRATE_HEALTH.default_(), ClientKeyBindings.NARRATE_HEALTH.current());
	}

	private static void setUpDefaultsOnly() {
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			action.resetToDefault();
		}
	}
}
