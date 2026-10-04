package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * How {@link ClientKeyBindings#updateAll} decides which action a key press belongs to, run
 * against the mod's real default bindings with the keyboard and game state passed in.
 */
class KeyResolutionTest {
	/** In a world, no screen open, no mode on - what most of these tests run under. */
	private static final Predicate<KeybindAction> NORMAL_PLAY = contexts(KeybindContext.GLOBAL, KeybindContext.NORMAL_LOOK);

	private static Predicate<KeybindAction> contexts(KeybindContext first, KeybindContext... rest) {
		Set<KeybindContext> active = EnumSet.of(first, rest);
		return action -> active.contains(action.context());
	}

	@BeforeEach
	void restoreDefaults() {
		for (KeybindAction action : ClientKeyBindings.allActions()) {
			action.resetToDefault();
		}
		ClientKeyBindings.rebuildIndex();
		ClientKeyBindings.resetPressState();
	}

	private static void tick(Set<Integer> keysDown, int modifiers, Predicate<KeybindAction> eligible) {
		ClientKeyBindings.updateAll(keysDown::contains, modifiers, eligible);
	}

	private static void tick(Set<Integer> keysDown, int modifiers) {
		tick(keysDown, modifiers, NORMAL_PLAY);
	}

	@Test
	void aPlainKeyFiresItsUnmodifiedAction() {
		tick(Set.of(InputConstants.KEY_H), 0);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
		assertFalse(ClientKeyBindings.NARRATE_EXPERIENCE.isJustPressed());
	}

	@Test
	void theMostSpecificChordWins() {
		tick(Set.of(InputConstants.KEY_H), InputConstants.MOD_SHIFT);
		assertTrue(ClientKeyBindings.NARRATE_EXPERIENCE.isJustPressed());
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed(), "plain H must lose to Shift+H");
	}

	@Test
	void aChordIsMatchedAsASubsetOfWhatIsHeld() {
		// Ctrl isn't part of either binding, so plain H still answers Ctrl+H.
		tick(Set.of(InputConstants.KEY_H), InputConstants.MOD_CONTROL);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}

	@Test
	void justPressedFiresOnceWhileHeldStaysDown() {
		tick(Set.of(InputConstants.KEY_H), 0);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
		tick(Set.of(InputConstants.KEY_H), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isDown());
		tick(Set.of(), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isDown());
	}

	@Test
	void altArrowSnapTurnsWhilePlainArrowLooks() {
		tick(Set.of(InputConstants.KEY_LEFT), InputConstants.MOD_ALT);
		assertTrue(ClientKeyBindings.SNAP_TURN_LEFT.isJustPressed());
		assertFalse(ClientKeyBindings.LOOK_LEFT.isDown());

		restoreDefaults();
		tick(Set.of(InputConstants.KEY_LEFT), 0);
		assertTrue(ClientKeyBindings.LOOK_LEFT.isDown());
		assertFalse(ClientKeyBindings.SNAP_TURN_LEFT.isJustPressed());
	}

	@Test
	void aNarrowerContextBeatsGlobalOnAnEqualChord() {
		tick(Set.of(InputConstants.KEY_LEFT), 0, contexts(KeybindContext.GLOBAL, KeybindContext.BUILD_MODE));
		assertTrue(ClientKeyBindings.BUILD_CURSOR_LEFT.isJustPressed());
		assertFalse(ClientKeyBindings.LOOK_LEFT.isDown(), "camera look must not run under Build Mode's cursor");
	}

	@Test
	void nothingFiresWhenNoContextIsActive() {
		tick(Set.of(InputConstants.KEY_H), 0, action -> false);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isDown());
	}

	@Test
	void aTapBetweenTicksStillCounts() {
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, 0);
		tick(Set.of(), 0);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed(), "a key released before the tick polled it");

		tick(Set.of(), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed(), "a tap only counts for one tick");
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isDown());
	}

	@Test
	void aTapUsesTheModifiersHeldWhenItWasPressed() {
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, InputConstants.MOD_SHIFT);
		tick(Set.of(), 0);
		assertTrue(ClientKeyBindings.NARRATE_EXPERIENCE.isJustPressed());
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}

	@Test
	void aRepressBetweenTicksFiresAgainWhileHeld() {
		tick(Set.of(InputConstants.KEY_H), 0);
		// Released and pressed again within one tick - the poll only ever sees it down.
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, 0);
		tick(Set.of(InputConstants.KEY_H), 0);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}

	@Test
	void aPressWithAScreenOpenIsNeverReplayed() {
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, true);
		tick(Set.of(), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}

	@Test
	void aTapIsForgottenWhenThePlayerLeavesTheWorld() {
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, 0);
		ClientKeyBindings.resetPressState();
		tick(Set.of(), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}

	@Test
	void aTapOnAnIneligibleKeyIsStillConsumed() {
		ClientKeyBindings.recordKeyPress(InputConstants.KEY_H, 0);
		tick(Set.of(), 0, action -> false);
		tick(Set.of(), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed(), "a press made unusable must not fire later");
	}

	@Test
	void reboundActionsAnswerTheirNewKey() {
		ClientKeyBindings.NARRATE_HEALTH.setCurrent(new Keybind(InputConstants.KEY_J, 0));
		ClientKeyBindings.rebuildIndex();
		tick(Set.of(InputConstants.KEY_H), 0);
		assertFalse(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
		tick(Set.of(InputConstants.KEY_J), 0);
		assertTrue(ClientKeyBindings.NARRATE_HEALTH.isJustPressed());
	}
}
