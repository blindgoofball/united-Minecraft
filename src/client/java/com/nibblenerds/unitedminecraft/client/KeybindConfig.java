package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists user-rebound {@link Keybind}s for every {@link ClientKeyBindings} action, in its
 * own file separate from {@link UnitedMinecraftConfig} - independently reset/reloadable, and a
 * different kind of setting (a chord, not a toggle/slider). Mirrors that class's own
 * load/save/best-effort-on-failure shape.
 */
public final class KeybindConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/keybinds");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type STORED_TYPE = new TypeToken<Map<String, StoredKeybind>>() {
	}.getType();

	private record StoredKeybind(int key, int modifiers) {
	}

	private KeybindConfig() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("united_minecraft_keybinds.json");
	}

	/** Must run after {@link ClientKeyBindings#register} has populated every action's default. */
	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Map<String, StoredKeybind> stored = GSON.fromJson(reader, STORED_TYPE);
				if (stored != null) {
					for (Map.Entry<String, StoredKeybind> entry : stored.entrySet()) {
						KeybindAction action = ClientKeyBindings.byId(entry.getKey());
						// Unknown ids (a stale entry after a mod update removed/renamed an
						// action) are dropped silently rather than kept around inert.
						if (action != null) {
							StoredKeybind keybind = entry.getValue();
							action.setCurrent(new Keybind(keybind.key(), keybind.modifiers()));
						}
					}
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("Failed to load keybindings from {}", file, e);
			}
		}
		ClientKeyBindings.rebuildIndex();
	}

	public static void save() {
		Map<String, StoredKeybind> stored = new LinkedHashMap<>();
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			Keybind keybind = action.current();
			stored.put(action.id(), new StoredKeybind(keybind.key(), keybind.modifiers()));
		}
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(stored, STORED_TYPE, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			LOGGER.warn("Failed to save keybindings to {}", file, e);
		}
	}
}
