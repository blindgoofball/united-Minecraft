package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.input.KeyEvent;

class KeybindTest {
	/** One side's bit of a two-sided modifier mask - what a live event reports for, say, left Shift alone. */
	private static int oneSide(int mask) {
		return mask & -mask;
	}

	private static KeyEvent press(int key, int modifiers) {
		return new KeyEvent(key, 0, modifiers);
	}

	@Test
	void normalizingWidensOneSideToTheWholeModifier() {
		assertEquals(InputConstants.MOD_SHIFT, Keybind.normalizeModifiers(oneSide(InputConstants.MOD_SHIFT)));
		assertEquals(InputConstants.MOD_CONTROL | InputConstants.MOD_ALT,
				Keybind.normalizeModifiers(oneSide(InputConstants.MOD_CONTROL) | oneSide(InputConstants.MOD_ALT)));
		assertEquals(0, Keybind.normalizeModifiers(0));
	}

	@Test
	void normalizingDropsLockKeys() {
		assertEquals(0, Keybind.normalizeModifiers(InputConstants.MOD_CAPS_LOCK | InputConstants.MOD_NUM_LOCK));
	}

	@Test
	void matchesTheExactChordWithEitherSideOfAModifier() {
		Keybind shiftC = new Keybind(InputConstants.KEY_C, InputConstants.MOD_SHIFT);
		assertTrue(shiftC.matches(press(InputConstants.KEY_C, oneSide(InputConstants.MOD_SHIFT))));
		assertTrue(shiftC.matches(press(InputConstants.KEY_C, InputConstants.MOD_SHIFT)));
	}

	@Test
	void containerChordsNeedExactModifiersNotASubset() {
		Keybind shiftC = new Keybind(InputConstants.KEY_C, InputConstants.MOD_SHIFT);
		assertFalse(shiftC.matches(press(InputConstants.KEY_C, 0)));
		assertFalse(shiftC.matches(press(InputConstants.KEY_C, InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL)));
		assertFalse(new Keybind(InputConstants.KEY_C, 0).matches(press(InputConstants.KEY_C, InputConstants.MOD_SHIFT)));
	}

	@Test
	void capsLockDoesNotBreakAMatch() {
		assertTrue(new Keybind(InputConstants.KEY_C, 0).matches(press(InputConstants.KEY_C, InputConstants.MOD_CAPS_LOCK)));
	}

	@Test
	void enterAndNumpadEnterAreTheSameKey() {
		assertTrue(new Keybind(InputConstants.KEY_RETURN, 0).matches(press(InputConstants.KEY_NUMPADENTER, 0)));
		assertTrue(new Keybind(InputConstants.KEY_NUMPADENTER, 0).matches(press(InputConstants.KEY_RETURN, 0)));
		assertFalse(new Keybind(InputConstants.KEY_RETURN, 0).matches(press(InputConstants.KEY_SPACE, 0)));
	}

	@Test
	void anUnboundKeyNeverMatches() {
		assertTrue(Keybind.UNBOUND.isUnbound());
		assertFalse(Keybind.UNBOUND.matches(press(-1, 0)));
	}
}
