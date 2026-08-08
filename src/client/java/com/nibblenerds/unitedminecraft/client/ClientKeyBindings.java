package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.nibblenerds.unitedminecraft.UnitedMinecraft;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for United Minecraft's accessibility features.
 *
 * <p>{@code C} defaults to unbound during normal gameplay - vanilla only wires it
 * up to "Save Toolbar Activator" while the creative inventory screen is open, so
 * there's no practical clash with narrating coordinates in-game (Minecraft's
 * controls menu may still show them as a cosmetic duplicate, since it compares
 * raw keys across contexts).
 *
 * <p>The arrow keys are entirely unbound by default in vanilla.
 *
 * <p><b>Never call {@code KeyMapping.consumeClick()} on any keybinding declared here -
 * call {@link #pressed} instead.</b> {@code consumeClick()} drains a click queue that
 * keeps piling up for as long as nothing reads it, which is exactly what happens every
 * time this mod's own guard conditions skip a key's handling for a tick (a mode that
 * blocks it, a menu that's open, a different mode's tick() running instead of this one)
 * - the queued click doesn't vanish, it just fires the next time that code path happens
 * to run again, often well after the key was actually released (e.g. toggling Build Mode
 * on right after leaving Combat Mode, with no Build Mode key pressed in between). {@link
 * #pressed} sidesteps the queue entirely via {@link #updateAll}, the same {@code isDown()}
 * plus tracked-held-state approach this mod already used piecemeal for a few keys
 * (movement/look, Build Mode's cursor) before this was generalized to every keybinding.
 */
public final class ClientKeyBindings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(UnitedMinecraft.id("keys"));

	public static final KeyMapping NARRATE_COORDINATES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_coordinates", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

	public static final KeyMapping NARRATE_HEALTH = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_health", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

	public static final KeyMapping NARRATE_BEARING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_bearing", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));

	public static final KeyMapping SCAN_SURROUNDINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scan_surroundings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));

	public static final KeyMapping NARRATE_TIME = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.narrate_time", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

	/** Alt instead opens a name prompt for the Scanner's currently focused block item (see {@link ScannerController#nameFocusedItem}). */
	public static final KeyMapping PLACE_MARKER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.place_marker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY));

	public static final KeyMapping TOGGLE_BUILD_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_build_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY));

	public static final KeyMapping BUILD_PLACE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_place", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));
	public static final KeyMapping BUILD_BREAK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_break", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

	public static final KeyMapping BUILD_WALK_TO_CURSOR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_walk_to_cursor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

	/** Cycles Build Mode's placement facing forward; Alt reverses it. */
	public static final KeyMapping BUILD_CYCLE_FACING = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.build_cycle_facing", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

	public static final KeyMapping TOGGLE_NAV_RADAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_nav_radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY));

	public static final KeyMapping TOGGLE_MINING_RADAR = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_mining_radar", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));

	public static final KeyMapping TOGGLE_COMBAT_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.toggle_combat_mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

	/** Narrates the nearest reachable way out of water; Alt instead swims there automatically. */
	public static final KeyMapping WATER_ESCAPE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.water_escape", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));

	/** Clears the recorded cave trail and starts recording fresh from the current position. */
	public static final KeyMapping MARK_TRAIL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.mark_trail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));

	/** Narrates the way back along the recorded trail; Alt instead walks it automatically. */
	public static final KeyMapping RETRACE_TRAIL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.retrace_trail", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY));


	/**
	 * The arrow keys are dual-purpose: continuous camera turning normally (see
	 * {@code handleCameraLook}), but discrete one-block cursor steps while build
	 * mode is active (see {@link BuildModeController}).
	 *
	 * <p>Page Up/Down are triple-purpose: build mode's cursor Y level, or the
	 * scanner's item cycling (see {@link ScannerController}) when build mode
	 * isn't active. Home/End cycle the scanner's category instead.
	 */
	public static final KeyMapping LOOK_LEFT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY));
	public static final KeyMapping LOOK_RIGHT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY));
	public static final KeyMapping LOOK_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
	public static final KeyMapping LOOK_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.look_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, CATEGORY));

	public static final KeyMapping PAGE_UP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.page_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, CATEGORY));
	public static final KeyMapping PAGE_DOWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.page_down", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY));

	public static final KeyMapping SCANNER_PREV_CATEGORY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_prev_category", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, CATEGORY));
	public static final KeyMapping SCANNER_NEXT_CATEGORY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_next_category", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, CATEGORY));

	/**
	 * Targets the Scanner's focused item normally - but while already locked onto a mob (where
	 * targeting doesn't apply, so this would otherwise do nothing), interacts with the locked
	 * entity directly instead, the same as right-clicking it but without needing an actual
	 * crosshair hit. Meant for when another entity is physically in the way of the one actually
	 * locked on (two chickens crowded together while trying to breed them, say), where camera
	 * aim alone can't tell them apart.
	 */
	public static final KeyMapping SCANNER_TARGET = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, CATEGORY));
	/** Stops Scanner lock-on, or cancels Auto-Walk/a swim/a trail retrace - kept off Delete so it can't be hit by accident while reaching for Remove Marker. */
	public static final KeyMapping SCANNER_STOP_LOCK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_stop_lock", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, CATEGORY));
	/** Removes the focused marker, Markers category only - deliberately a separate key from SCANNER_STOP_LOCK. */
	public static final KeyMapping SCANNER_REMOVE_MARKER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_remove_marker", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY));
	public static final KeyMapping SCANNER_COORDINATES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.scanner_coordinates", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY));

	public static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.united_minecraft.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY));

	// Every KeyMapping declared above, discovered reflectively rather than hand-listed so a
	// future keybinding automatically gets the same backlog-proof tracking below without
	// anyone needing to remember to register it separately - forgetting would silently
	// reintroduce the exact bug this class exists to prevent, for just that one key.
	private static final KeyMapping[] ALL_MAPPINGS = discoverMappings();

	private static final Map<KeyMapping, Boolean> heldLastTick = new IdentityHashMap<>();
	private static final Map<KeyMapping, Boolean> justPressed = new IdentityHashMap<>();

	private ClientKeyBindings() {
	}

	/** No-op call that forces the static initializers above to run. */
	public static void register() {
	}

	private static KeyMapping[] discoverMappings() {
		List<KeyMapping> mappings = new ArrayList<>();
		for (Field field : ClientKeyBindings.class.getDeclaredFields()) {
			if (KeyMapping.class.isAssignableFrom(field.getType())) {
				try {
					mappings.add((KeyMapping) field.get(null));
				} catch (IllegalAccessException e) {
					// Every KeyMapping field here is public static final and already initialized
					// by the time this runs (it's assigned near the bottom of the class) - this
					// can't actually happen.
					throw new AssertionError(e);
				}
			}
		}
		return mappings.toArray(new KeyMapping[0]);
	}

	/**
	 * Refreshes every keybinding's "was it just pressed" state for this tick - {@link
	 * AccessibilityTickHandler#onEndTick} calls this exactly once per tick, unconditionally,
	 * before anything branches on mode or screen state. That unconditional timing is what
	 * makes {@link #pressed} immune to the backlog {@code consumeClick()} suffers: every
	 * key's held/not-held state gets updated every tick no matter what else is going on, so a
	 * key pressed while something was blocking it is already accounted for (not a "fresh"
	 * press) by the time whatever was blocking it stops.
	 */
	public static void updateAll() {
		for (KeyMapping mapping : ALL_MAPPINGS) {
			boolean down = mapping.isDown();
			boolean wasDown = heldLastTick.getOrDefault(mapping, false);
			justPressed.put(mapping, down && !wasDown);
			heldLastTick.put(mapping, down);
		}
	}

	/** True only on the tick {@code mapping} transitioned from up to down - see {@link #updateAll}. */
	public static boolean pressed(KeyMapping mapping) {
		return justPressed.getOrDefault(mapping, false);
	}

	/** Called when the player unloads, so a key held across a world/session boundary doesn't leak in as a stale state. */
	public static void resetPressState() {
		heldLastTick.clear();
		justPressed.clear();
	}

	/**
	 * Whether the mod's own secondary-action modifier is held - Alt, not Shift, deliberately:
	 * Shift is vanilla's sneak key, so using it here meant every dual-purpose press (Alt+arrows
	 * to snap-turn, say) also crouched the player as a side effect. Alt has no vanilla movement
	 * binding of its own, so it layers cleanly on top of everything this mod uses it for -
	 * narration keys ({@link #NARRATE_COORDINATES}, {@link #NARRATE_HEALTH}, {@link
	 * #NARRATE_BEARING}), Water Exit and Cave Trail's walk-there keys, Build Mode's facing cycle
	 * and snap-turn, the Scanner's walk-there and name-item actions, and Map Marker placement's
	 * own name-item layering - see each key's own doc for its specific Alt behavior.
	 */
	public static boolean isModifierDown(Minecraft client) {
		return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
				|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
	}
}
