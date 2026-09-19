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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import com.mojang.blaze3d.platform.InputConstants;

import com.nibblenerds.unitedminecraft.platform.Platform;

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

	/**
	 * Bumped whenever the on-disk format's {@code key}/{@code modifiers} ints stop meaning what
	 * they used to. A file with no {@code schemaVersion} field at all predates this field's
	 * introduction - written back when {@code key}/{@code modifiers} were raw GLFW keysym codes
	 * and GLFW_MOD_* bits, before Minecraft 26.3 replaced GLFW with SDL and changed both
	 * {@link InputConstants}' key numbering (GLFW keysym -&gt; SDL scancode - e.g. GLFW's
	 * {@code KEY_C == 67} collides with this version's {@code InputConstants.KEY_F10 == 67}) and
	 * its modifier bitmask (GLFW_MOD_SHIFT/CONTROL/ALT/SUPER were {@code 1/2/4/8}; this version's
	 * {@link InputConstants#MOD_SHIFT}/{@link InputConstants#MOD_CONTROL}/
	 * {@link InputConstants#MOD_ALT}/{@link InputConstants#MOD_SUPER} are {@code 3/192/768/3072}).
	 * Loading such a file without translating it silently reinterprets every saved chord as a
	 * different, unrelated one - see {@link #migrateLegacyKey} and {@link #migrateLegacyModifiers}.
	 */
	private static final int CURRENT_SCHEMA_VERSION = 2;
	private static final String SCHEMA_VERSION_FIELD = "schemaVersion";
	private static final String BINDINGS_FIELD = "bindings";

	/**
	 * GLFW keysym (pre-26.3) -&gt; this version's {@link InputConstants} key code, for every key
	 * a saved chord could plausibly use. Deliberately excludes keys with no confident equivalent
	 * (GLFW's {@code WORLD_1}/{@code WORLD_2}, {@code F25}, {@code MENU}, and the numpad
	 * decimal/divide/subtract keys, which don't obviously collapse onto any single {@code
	 * InputConstants} constant) - {@link #migrateLegacyKey} leaves those actions at their default
	 * instead of guessing.
	 */
	private static final Map<Integer, Integer> LEGACY_KEY_MAP = buildLegacyKeyMap();

	private static Map<Integer, Integer> buildLegacyKeyMap() {
		Map<Integer, Integer> map = new LinkedHashMap<>();
		map.put(32, InputConstants.KEY_SPACE);
		map.put(39, InputConstants.KEY_APOSTROPHE);
		map.put(44, InputConstants.KEY_COMMA);
		map.put(45, InputConstants.KEY_MINUS);
		map.put(46, InputConstants.KEY_PERIOD);
		map.put(47, InputConstants.KEY_SLASH);
		for (int digit = 0; digit <= 9; digit++) {
			map.put(48 + digit, switch (digit) {
				case 0 -> InputConstants.KEY_0;
				case 1 -> InputConstants.KEY_1;
				case 2 -> InputConstants.KEY_2;
				case 3 -> InputConstants.KEY_3;
				case 4 -> InputConstants.KEY_4;
				case 5 -> InputConstants.KEY_5;
				case 6 -> InputConstants.KEY_6;
				case 7 -> InputConstants.KEY_7;
				case 8 -> InputConstants.KEY_8;
				default -> InputConstants.KEY_9;
			});
		}
		map.put(59, InputConstants.KEY_SEMICOLON);
		map.put(61, InputConstants.KEY_EQUALS);
		int[] letterKeys = {
				InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_D,
				InputConstants.KEY_E, InputConstants.KEY_F, InputConstants.KEY_G, InputConstants.KEY_H,
				InputConstants.KEY_I, InputConstants.KEY_J, InputConstants.KEY_K, InputConstants.KEY_L,
				InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O, InputConstants.KEY_P,
				InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S, InputConstants.KEY_T,
				InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W, InputConstants.KEY_X,
				InputConstants.KEY_Y, InputConstants.KEY_Z };
		for (int letter = 0; letter < letterKeys.length; letter++) {
			map.put(65 + letter, letterKeys[letter]);
		}
		map.put(91, InputConstants.KEY_LBRACKET);
		map.put(92, InputConstants.KEY_BACKSLASH);
		map.put(93, InputConstants.KEY_RBRACKET);
		map.put(96, InputConstants.KEY_GRAVE);
		map.put(256, InputConstants.KEY_ESCAPE);
		map.put(257, InputConstants.KEY_RETURN);
		map.put(258, InputConstants.KEY_TAB);
		map.put(259, InputConstants.KEY_BACKSPACE);
		map.put(260, InputConstants.KEY_INSERT);
		map.put(261, InputConstants.KEY_DELETE);
		map.put(262, InputConstants.KEY_RIGHT);
		map.put(263, InputConstants.KEY_LEFT);
		map.put(264, InputConstants.KEY_DOWN);
		map.put(265, InputConstants.KEY_UP);
		map.put(266, InputConstants.KEY_PAGEUP);
		map.put(267, InputConstants.KEY_PAGEDOWN);
		map.put(268, InputConstants.KEY_HOME);
		map.put(269, InputConstants.KEY_END);
		map.put(280, InputConstants.KEY_CAPSLOCK);
		map.put(281, InputConstants.KEY_SCROLLLOCK);
		map.put(282, InputConstants.KEY_NUMLOCK);
		map.put(283, InputConstants.KEY_PRINTSCREEN);
		map.put(284, InputConstants.KEY_PAUSE);
		int[] functionKeys = {
				InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3, InputConstants.KEY_F4,
				InputConstants.KEY_F5, InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8,
				InputConstants.KEY_F9, InputConstants.KEY_F10, InputConstants.KEY_F11, InputConstants.KEY_F12,
				InputConstants.KEY_F13, InputConstants.KEY_F14, InputConstants.KEY_F15, InputConstants.KEY_F16,
				InputConstants.KEY_F17, InputConstants.KEY_F18, InputConstants.KEY_F19, InputConstants.KEY_F20,
				InputConstants.KEY_F21, InputConstants.KEY_F22, InputConstants.KEY_F23, InputConstants.KEY_F24 };
		for (int fkey = 0; fkey < functionKeys.length; fkey++) {
			map.put(290 + fkey, functionKeys[fkey]);
		}
		int[] numpadDigitKeys = {
				InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
				InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5,
				InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8,
				InputConstants.KEY_NUMPAD9 };
		for (int numpadDigit = 0; numpadDigit < numpadDigitKeys.length; numpadDigit++) {
			map.put(320 + numpadDigit, numpadDigitKeys[numpadDigit]);
		}
		map.put(335, InputConstants.KEY_NUMPADENTER);
		map.put(336, InputConstants.KEY_NUMPADEQUALS);
		map.put(340, InputConstants.KEY_LSHIFT);
		map.put(341, InputConstants.KEY_LCONTROL);
		map.put(342, InputConstants.KEY_LALT);
		map.put(343, InputConstants.KEY_LGUI);
		map.put(344, InputConstants.KEY_RSHIFT);
		map.put(345, InputConstants.KEY_RCONTROL);
		map.put(346, InputConstants.KEY_RALT);
		map.put(347, InputConstants.KEY_RGUI);
		return Map.copyOf(map);
	}

	/** GLFW_MOD_* bit (pre-26.3) -&gt; this version's {@link InputConstants} MOD_* value. */
	private static final int[][] LEGACY_MOD_BITS = {
			{ 0x0001, InputConstants.MOD_SHIFT },
			{ 0x0002, InputConstants.MOD_CONTROL },
			{ 0x0004, InputConstants.MOD_ALT },
			{ 0x0008, InputConstants.MOD_SUPER },
			{ 0x0010, InputConstants.MOD_CAPS_LOCK },
			{ 0x0020, InputConstants.MOD_NUM_LOCK },
	};

	private record StoredKeybind(int key, int modifiers) {
	}

	private KeybindConfig() {
	}

	private static Path file() {
		return Platform.get().configDir().resolve("united_minecraft_keybinds.json");
	}

	/** Null if {@code legacyKey} has no confident {@link InputConstants} equivalent - see {@link #LEGACY_KEY_MAP}. */
	private static Integer migrateLegacyKey(int legacyKey) {
		return LEGACY_KEY_MAP.get(legacyKey);
	}

	private static int migrateLegacyModifiers(int legacyModifiers) {
		int migrated = 0;
		for (int[] bit : LEGACY_MOD_BITS) {
			if ((legacyModifiers & bit[0]) != 0) {
				migrated |= bit[1];
			}
		}
		return migrated;
	}

	/** Must run after {@link ClientKeyBindings#register} has populated every action's default. */
	public static void load() {
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root != null && root.isJsonObject()) {
					JsonObject rootObject = root.getAsJsonObject();
					// A file with no schemaVersion field predates that field - see the constant's
					// own doc for why that means its key/modifiers ints need translating rather
					// than applying as-is.
					boolean legacy = !rootObject.has(SCHEMA_VERSION_FIELD);
					JsonElement bindingsElement = legacy ? rootObject : rootObject.get(BINDINGS_FIELD);
					Map<String, StoredKeybind> stored =
							bindingsElement == null ? null : GSON.fromJson(bindingsElement, STORED_TYPE);
					if (stored != null) {
						for (Map.Entry<String, StoredKeybind> entry : stored.entrySet()) {
							KeybindAction action = ClientKeyBindings.byId(entry.getKey());
							// Unknown ids (a stale entry after a mod update removed/renamed an
							// action) are dropped silently rather than kept around inert.
							if (action == null) {
								continue;
							}
							StoredKeybind keybind = entry.getValue();
							if (legacy) {
								if (keybind.key() < 0) {
									action.setCurrent(Keybind.UNBOUND);
									continue;
								}
								Integer migratedKey = migrateLegacyKey(keybind.key());
								if (migratedKey == null) {
									LOGGER.warn(
											"Could not migrate legacy keybinding for '{}' (unrecognized key code {}) - leaving it at its default",
											entry.getKey(), keybind.key());
									continue;
								}
								action.setCurrent(new Keybind(migratedKey, migrateLegacyModifiers(keybind.modifiers())));
							} else {
								action.setCurrent(new Keybind(keybind.key(), keybind.modifiers()));
							}
						}
					}
					if (legacy) {
						LOGGER.info("Migrated {} from the pre-26.3 GLFW key scheme", file);
						// Persist the migrated result immediately rather than leaving the file in
						// the legacy schema and re-deriving it from scratch every future launch.
						save();
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
		JsonObject root = new JsonObject();
		root.addProperty(SCHEMA_VERSION_FIELD, CURRENT_SCHEMA_VERSION);
		root.add(BINDINGS_FIELD, GSON.toJsonTree(stored, STORED_TYPE));
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			// Best-effort - losing the ability to persist shouldn't crash the game, and there's
			// nowhere better than the log to report a disk-write failure to.
			LOGGER.warn("Failed to save keybindings to {}", file, e);
		}
	}
}
