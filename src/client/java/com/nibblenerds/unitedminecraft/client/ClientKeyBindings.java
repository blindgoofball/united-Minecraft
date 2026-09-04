package com.nibblenerds.unitedminecraft.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * United Minecraft's rebindable actions. Every action here is a {@link KeybindAction} - an
 * arbitrary key + modifier chord (see {@link Keybind}), entirely independent of vanilla
 * {@link net.minecraft.client.KeyMapping} and vanilla's own Controls screen; rebinding happens
 * through {@link KeybindScreen} instead, and current bindings persist via {@link KeybindConfig}.
 *
 * <p>Several of this mod's older dual-purpose keys (e.g. plain H narrating health, Shift+H
 * narrating XP, Alt+H narrating armor/effects) have each become fully independent actions -
 * see each action's own doc comment for what it used to be layered onto.
 *
 * <p><b>Never poll a key's own held state directly - call {@link #pressed} or
 * {@link KeybindAction#isDown()} instead.</b> Resolution against every other action sharing the
 * same primary key (context eligibility, modifier-subset matching, most-specific-wins) happens
 * centrally in {@link #updateAll()}; a raw {@code InputConstants.isKeyDown} poll anywhere else
 * would bypass that and could fire alongside an action that's supposed to be more specific.
 */
public final class ClientKeyBindings {
	public static final KeybindAction NARRATE_COORDINATES =
			new KeybindAction("narrate_coordinates", new Keybind(GLFW.GLFW_KEY_C, 0));
	/** Was Shift+C - see {@link #NARRATE_LIGHT_LEVEL}. */
	public static final KeybindAction NARRATE_LIGHT_LEVEL =
			new KeybindAction("narrate_light_level", new Keybind(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction NARRATE_HEALTH =
			new KeybindAction("narrate_health", new Keybind(GLFW.GLFW_KEY_H, 0));
	/** Was Shift+H - see {@link #NARRATE_EXPERIENCE}; was Alt+H - see {@link #NARRATE_ARMOR_AND_EFFECTS}. */
	public static final KeybindAction NARRATE_EXPERIENCE =
			new KeybindAction("narrate_experience", new Keybind(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_SHIFT));
	public static final KeybindAction NARRATE_ARMOR_AND_EFFECTS =
			new KeybindAction("narrate_armor_and_effects", new Keybind(GLFW.GLFW_KEY_H, GLFW.GLFW_MOD_ALT));

	public static final KeybindAction NARRATE_BEARING =
			new KeybindAction("narrate_bearing", new Keybind(GLFW.GLFW_KEY_B, 0));
	/** Was Shift+B - see {@link #RESET_ROTATION_TO_NORTH}. */
	public static final KeybindAction RESET_ROTATION_TO_NORTH =
			new KeybindAction("reset_rotation_to_north", new Keybind(GLFW.GLFW_KEY_B, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction SCAN_SURROUNDINGS =
			new KeybindAction("scan_surroundings", new Keybind(GLFW.GLFW_KEY_R, 0));
	/** Was Shift+R - see {@link #TOGGLE_AUTO_CROSSHAIR_NARRATION}. */
	public static final KeybindAction TOGGLE_AUTO_CROSSHAIR_NARRATION =
			new KeybindAction("toggle_auto_crosshair_narration", new Keybind(GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction NARRATE_TIME =
			new KeybindAction("narrate_time", new Keybind(GLFW.GLFW_KEY_V, 0));
	/** Was Shift+V - see {@link #NARRATE_WEATHER_AND_MOON}. */
	public static final KeybindAction NARRATE_WEATHER_AND_MOON =
			new KeybindAction("narrate_weather_and_moon", new Keybind(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction PLACE_MARKER =
			new KeybindAction("place_marker", new Keybind(GLFW.GLFW_KEY_U, 0));
	/** Was Shift+U - see {@link #SCANNER_NAME_FOCUSED_ITEM}. */
	public static final KeybindAction SCANNER_NAME_FOCUSED_ITEM =
			new KeybindAction("scanner_name_focused_item", new Keybind(GLFW.GLFW_KEY_U, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction TOGGLE_BUILD_MODE =
			new KeybindAction("toggle_build_mode", new Keybind(GLFW.GLFW_KEY_I, 0));
	/** Was Alt+I while already active - see {@link #BUILD_MODE_RECENTER_CURSOR}. */
	public static final KeybindAction BUILD_MODE_RECENTER_CURSOR = new KeybindAction(
			"build_mode_recenter_cursor", new Keybind(GLFW.GLFW_KEY_I, GLFW.GLFW_MOD_ALT), KeybindContext.BUILD_MODE);

	/** Bare Right Ctrl by default - a deliberate hold-to-place trigger, not a chord's primary key. */
	public static final KeybindAction BUILD_PLACE =
			new KeybindAction("build_place", new Keybind(GLFW.GLFW_KEY_RIGHT_CONTROL, 0));
	/** Bare Right Shift by default - a deliberate hold-to-mine trigger, not a chord's primary key. */
	public static final KeybindAction BUILD_BREAK =
			new KeybindAction("build_break", new Keybind(GLFW.GLFW_KEY_RIGHT_SHIFT, 0));

	public static final KeybindAction BUILD_WALK_TO_CURSOR =
			new KeybindAction("build_walk_to_cursor", new Keybind(GLFW.GLFW_KEY_G, 0));

	public static final KeybindAction BUILD_CYCLE_FACING =
			new KeybindAction("build_cycle_facing", new Keybind(GLFW.GLFW_KEY_J, 0), KeybindContext.BUILD_MODE);
	/** Was Alt+J - see {@link #BUILD_CYCLE_FACING_REVERSE}. */
	public static final KeybindAction BUILD_CYCLE_FACING_REVERSE = new KeybindAction(
			"build_cycle_facing_reverse", new Keybind(GLFW.GLFW_KEY_J, GLFW.GLFW_MOD_ALT), KeybindContext.BUILD_MODE);

	public static final KeybindAction TOGGLE_NAV_RADAR =
			new KeybindAction("toggle_nav_radar", new Keybind(GLFW.GLFW_KEY_N, 0));

	public static final KeybindAction TOGGLE_MINING_RADAR =
			new KeybindAction("toggle_mining_radar", new Keybind(GLFW.GLFW_KEY_M, 0));

	public static final KeybindAction TOGGLE_COMBAT_MODE =
			new KeybindAction("toggle_combat_mode", new Keybind(GLFW.GLFW_KEY_K, 0));

	public static final KeybindAction WATER_ESCAPE =
			new KeybindAction("water_escape", new Keybind(GLFW.GLFW_KEY_Y, 0));
	/** Was Shift+Y - see {@link #WATER_ESCAPE_AUTO_SWIM}. */
	public static final KeybindAction WATER_ESCAPE_AUTO_SWIM =
			new KeybindAction("water_escape_auto_swim", new Keybind(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction TRAIL =
			new KeybindAction("trail", new Keybind(GLFW.GLFW_KEY_X, 0));
	/** Was Shift+X - see {@link #TRAIL_WALK_BACK}; was Alt+X - see {@link #TRAIL_MARK_START}. */
	public static final KeybindAction TRAIL_WALK_BACK =
			new KeybindAction("trail_walk_back", new Keybind(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_SHIFT));
	public static final KeybindAction TRAIL_MARK_START =
			new KeybindAction("trail_mark_start", new Keybind(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_ALT));

	/**
	 * Continuous camera-look keys, GLOBAL - {@code handleCameraLook} only ever runs outside
	 * Build Mode (a separate branch of the tick handler's own mutually-exclusive priority
	 * chain), so these no longer double as the Build Mode cursor's own movement the way they
	 * used to - see {@link #BUILD_CURSOR_LEFT} etc. below for that, now a fully separate,
	 * independently rebindable action. Kept sharing these same default keys is still safe:
	 * {@link #updateAll}'s specificity resolution prefers a narrower ({@link
	 * KeybindContext#BUILD_MODE}) context over {@link KeybindContext#GLOBAL} on an otherwise-tied
	 * match, so {@link #BUILD_CURSOR_LEFT} still wins over this one while Build Mode is active.
	 */
	public static final KeybindAction LOOK_LEFT =
			new KeybindAction("look_left", new Keybind(GLFW.GLFW_KEY_LEFT, 0));
	public static final KeybindAction LOOK_RIGHT =
			new KeybindAction("look_right", new Keybind(GLFW.GLFW_KEY_RIGHT, 0));
	public static final KeybindAction LOOK_UP =
			new KeybindAction("look_up", new Keybind(GLFW.GLFW_KEY_UP, 0));
	public static final KeybindAction LOOK_DOWN =
			new KeybindAction("look_down", new Keybind(GLFW.GLFW_KEY_DOWN, 0));

	/** Was Alt held during normal camera look - see {@link #SNAP_TURN_RIGHT}/{@link #SNAP_TURN_UP}/{@link #SNAP_TURN_DOWN}. */
	public static final KeybindAction SNAP_TURN_LEFT = new KeybindAction(
			"snap_turn_left", new Keybind(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_ALT), KeybindContext.NORMAL_LOOK);
	public static final KeybindAction SNAP_TURN_RIGHT = new KeybindAction(
			"snap_turn_right", new Keybind(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_ALT), KeybindContext.NORMAL_LOOK);
	public static final KeybindAction SNAP_TURN_UP = new KeybindAction(
			"snap_turn_up", new Keybind(GLFW.GLFW_KEY_UP, GLFW.GLFW_MOD_ALT), KeybindContext.NORMAL_LOOK);
	public static final KeybindAction SNAP_TURN_DOWN = new KeybindAction(
			"snap_turn_down", new Keybind(GLFW.GLFW_KEY_DOWN, GLFW.GLFW_MOD_ALT), KeybindContext.NORMAL_LOOK);

	/** Was LOOK_LEFT/RIGHT/UP/DOWN reused for Build Mode's own cursor movement - now its own action, independently rebindable. */
	public static final KeybindAction BUILD_CURSOR_LEFT = new KeybindAction(
			"build_cursor_left", new Keybind(GLFW.GLFW_KEY_LEFT, 0), KeybindContext.BUILD_MODE);
	public static final KeybindAction BUILD_CURSOR_RIGHT = new KeybindAction(
			"build_cursor_right", new Keybind(GLFW.GLFW_KEY_RIGHT, 0), KeybindContext.BUILD_MODE);
	public static final KeybindAction BUILD_CURSOR_UP = new KeybindAction(
			"build_cursor_up", new Keybind(GLFW.GLFW_KEY_UP, 0), KeybindContext.BUILD_MODE);
	public static final KeybindAction BUILD_CURSOR_DOWN = new KeybindAction(
			"build_cursor_down", new Keybind(GLFW.GLFW_KEY_DOWN, 0), KeybindContext.BUILD_MODE);

	/** Was Alt held while steering the Build Mode cursor - see {@link #BUILD_REORIENT_RIGHT}. */
	public static final KeybindAction BUILD_REORIENT_LEFT = new KeybindAction(
			"build_reorient_left", new Keybind(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_ALT), KeybindContext.BUILD_MODE);
	public static final KeybindAction BUILD_REORIENT_RIGHT = new KeybindAction(
			"build_reorient_right", new Keybind(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_ALT), KeybindContext.BUILD_MODE);

	public static final KeybindAction PAGE_UP =
			new KeybindAction("page_up", new Keybind(GLFW.GLFW_KEY_PAGE_UP, 0));
	public static final KeybindAction PAGE_DOWN =
			new KeybindAction("page_down", new Keybind(GLFW.GLFW_KEY_PAGE_DOWN, 0));
	/** Was Alt+Page Up/Down during Scanner item cycling - jumps to the next/previous item of the same type. */
	public static final KeybindAction SCANNER_PAGE_UP_SAME_TYPE =
			new KeybindAction("scanner_page_up_same_type", new Keybind(GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_MOD_ALT));
	public static final KeybindAction SCANNER_PAGE_DOWN_SAME_TYPE =
			new KeybindAction("scanner_page_down_same_type", new Keybind(GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_MOD_ALT));

	public static final KeybindAction SCANNER_PREV_CATEGORY =
			new KeybindAction("scanner_prev_category", new Keybind(GLFW.GLFW_KEY_HOME, 0));
	public static final KeybindAction SCANNER_NEXT_CATEGORY =
			new KeybindAction("scanner_next_category", new Keybind(GLFW.GLFW_KEY_END, 0));

	public static final KeybindAction SCANNER_TARGET =
			new KeybindAction("scanner_target", new Keybind(GLFW.GLFW_KEY_ENTER, 0));
	/** Was Shift+Enter - see {@link #SCANNER_TARGET_WALK_THERE}. */
	public static final KeybindAction SCANNER_TARGET_WALK_THERE =
			new KeybindAction("scanner_target_walk_there", new Keybind(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT));

	public static final KeybindAction SCANNER_STOP_LOCK =
			new KeybindAction("scanner_stop_lock", new Keybind(GLFW.GLFW_KEY_BACKSPACE, 0));
	public static final KeybindAction SCANNER_REMOVE_MARKER =
			new KeybindAction("scanner_remove_marker", new Keybind(GLFW.GLFW_KEY_DELETE, 0));
	public static final KeybindAction SCANNER_COORDINATES =
			new KeybindAction("scanner_coordinates", new Keybind(GLFW.GLFW_KEY_BACKSLASH, 0));

	public static final KeybindAction OPEN_SETTINGS =
			new KeybindAction("open_settings", new Keybind(GLFW.GLFW_KEY_F6, 0));

	public static final KeybindAction NARRATE_BOSS_BARS =
			new KeybindAction("narrate_boss_bars", new Keybind(GLFW.GLFW_KEY_SEMICOLON, 0));

	public static final KeybindAction NARRATE_SCOREBOARD =
			new KeybindAction("narrate_scoreboard", new Keybind(GLFW.GLFW_KEY_APOSTROPHE, 0));
	/** Was Alt+' - see {@link #NARRATE_SCOREBOARD_FULL}. */
	public static final KeybindAction NARRATE_SCOREBOARD_FULL =
			new KeybindAction("narrate_scoreboard_full", new Keybind(GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_MOD_ALT));

	// Every KeybindAction declared above, discovered reflectively rather than hand-listed so a
	// future action automatically gets the same tracking below without anyone needing to
	// remember to register it separately - see ClientKeyBindings' historical doc for why that
	// matters (this class used to do the same thing for vanilla KeyMapping before it became
	// the owner of the binding type itself). Has to stay the last KeybindAction-typed field for
	// that to actually work (static fields initialize top-to-bottom).
	private static final KeybindAction[] ALL_ACTIONS = discoverActions();

	private static final Map<Integer, List<KeybindAction>> BY_PRIMARY_KEY = new HashMap<>();

	private static final int[] MODIFIER_KEYS = {
			GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
			GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
			GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
			GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
	};

	private ClientKeyBindings() {
	}

	/** No-op call that forces the static initializers above to run. */
	public static void register() {
		rebuildIndex();
	}

	public static KeybindAction[] allActions() {
		return ALL_ACTIONS.clone();
	}

	public static KeybindAction byId(String id) {
		for (KeybindAction action : ALL_ACTIONS) {
			if (action.id().equals(id)) {
				return action;
			}
		}
		return null;
	}

	private static KeybindAction[] discoverActions() {
		List<KeybindAction> actions = new ArrayList<>();
		for (Field field : ClientKeyBindings.class.getDeclaredFields()) {
			if (KeybindAction.class.isAssignableFrom(field.getType())) {
				try {
					KeybindAction action = (KeybindAction) field.get(null);
					if (action != null) {
						actions.add(action);
					}
				} catch (IllegalAccessException e) {
					// Every KeybindAction field here is public static final and already
					// initialized by the time this runs - this can't actually happen.
					throw new AssertionError(e);
				}
			}
		}
		return actions.toArray(new KeybindAction[0]);
	}

	/**
	 * Rebuilds the primary-key index {@link #updateAll} resolves against, and clears every
	 * action's held/just-pressed state. Called once at startup ({@link #register}), once more
	 * after {@link KeybindConfig#load} overlays saved bindings onto the defaults, and after any
	 * rebind/unbind/reset made through {@link KeybindScreen} - all rare enough that rebuilding
	 * the whole index each time is simpler than patching it incrementally, and clearing press
	 * state alongside it avoids a stale "held" flag surviving a binding change (harmless in
	 * practice regardless, since nothing reads {@link #pressed}/{@code isDown()} while a screen
	 * is open - see {@link KeybindScreen}).
	 */
	public static void rebuildIndex() {
		BY_PRIMARY_KEY.clear();
		for (KeybindAction action : ALL_ACTIONS) {
			action.resetPressState();
			if (!action.current().isUnbound()) {
				BY_PRIMARY_KEY.computeIfAbsent(action.current().key(), key -> new ArrayList<>()).add(action);
			}
		}
	}

	/**
	 * Refreshes every action's "was it just pressed" state for this tick - {@link
	 * AccessibilityTickHandler#onEndTick} calls this exactly once per tick, unconditionally,
	 * matching the old vanilla-{@code KeyMapping}-based version's own backlog-proofing timing.
	 *
	 * <p>For each primary key currently held down, every action bound to it is a candidate only
	 * if its {@link KeybindContext} is presently eligible and every modifier bit it requires is
	 * currently held (a subset check - a plain, unmodified binding is trivially satisfied by any
	 * held-modifier state). Among the candidates, {@link #isMoreSpecific} picks the winner - most
	 * modifier bits first, then (on a tie) a narrower, non-GLOBAL context beats GLOBAL; every
	 * other action sharing that key is held false for the tick. The modifier-bits rule is what
	 * lets e.g. a plain {@link #LOOK_LEFT} (GLOBAL, no modifiers) and a chorded {@link
	 * #SNAP_TURN_LEFT} (NORMAL_LOOK, Alt) share the same default key without one having to know
	 * the other exists; the context tiebreak is what lets two equally-unmodified actions share a
	 * key too, e.g. {@link #LOOK_LEFT} (GLOBAL) and {@link #BUILD_CURSOR_LEFT} (BUILD_MODE) -
	 * without it, which of two zero-modifier bindings on the same key wins would depend on
	 * {@link #ALL_ACTIONS} declaration order instead of which one is actually the narrower,
	 * more-specific match for the current context. See {@link KeybindContext}'s own doc for the
	 * full reasoning.
	 */
	public static void updateAll() {
		Minecraft client = Minecraft.getInstance();
		Window window = client.getWindow();
		int heldMods = currentModifierBitmask(window);
		for (Map.Entry<Integer, List<KeybindAction>> entry : BY_PRIMARY_KEY.entrySet()) {
			boolean keyDown = InputConstants.isKeyDown(window, entry.getKey());
			KeybindAction winner = null;
			if (keyDown) {
				for (KeybindAction action : entry.getValue()) {
					Keybind keybind = action.current();
					if (!action.isEligibleNow()) {
						continue;
					}
					if ((keybind.modifiers() & heldMods) != keybind.modifiers()) {
						continue;
					}
					if (winner == null || isMoreSpecific(action, winner)) {
						winner = action;
					}
				}
			}
			for (KeybindAction action : entry.getValue()) {
				action.updateHeld(action == winner);
			}
		}
	}

	/** Whether {@code candidate} should win over {@code current} for the same primary key - see {@link #updateAll}. */
	private static boolean isMoreSpecific(KeybindAction candidate, KeybindAction current) {
		int candidateBits = Integer.bitCount(candidate.current().modifiers());
		int currentBits = Integer.bitCount(current.current().modifiers());
		if (candidateBits != currentBits) {
			return candidateBits > currentBits;
		}
		return candidate.context() != KeybindContext.GLOBAL && current.context() == KeybindContext.GLOBAL;
	}

	/** True only on the tick {@code action} transitioned from up to down - see {@link #updateAll}. */
	public static boolean pressed(KeybindAction action) {
		return action.isJustPressed();
	}

	/** Called when the player unloads, so a key held across a world/session boundary doesn't leak in as a stale state. */
	public static void resetPressState() {
		for (KeybindAction action : ALL_ACTIONS) {
			action.resetPressState();
		}
	}

	private static int currentModifierBitmask(Window window) {
		int mods = 0;
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
			mods |= GLFW.GLFW_MOD_SHIFT;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
			mods |= GLFW.GLFW_MOD_CONTROL;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
			mods |= GLFW.GLFW_MOD_ALT;
		}
		if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SUPER) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SUPER)) {
			mods |= GLFW.GLFW_MOD_SUPER;
		}
		return mods;
	}

	public static boolean isModifierKeycode(int key) {
		for (int modifierKey : MODIFIER_KEYS) {
			if (modifierKey == key) {
				return true;
			}
		}
		return false;
	}
}
