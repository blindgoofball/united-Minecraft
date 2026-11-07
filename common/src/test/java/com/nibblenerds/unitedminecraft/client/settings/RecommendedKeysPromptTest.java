package com.nibblenerds.unitedminecraft.client.settings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.InputConstants;

class RecommendedKeysPromptTest {
	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}

	@Test
	void alreadyRecommendedOnlyWhenBothKeysMatch() {
		assertTrue(RecommendedKeysPrompt.alreadyRecommended(key(InputConstants.KEY_COMMA), key(InputConstants.KEY_PERIOD)));
		assertFalse(RecommendedKeysPrompt.alreadyRecommended(key(InputConstants.KEY_COMMA), key(InputConstants.KEY_SPACE)));
		assertFalse(RecommendedKeysPrompt.alreadyRecommended(key(InputConstants.KEY_SPACE), key(InputConstants.KEY_PERIOD)));
		// The vanilla mouse-button defaults.
		assertFalse(RecommendedKeysPrompt.alreadyRecommended(
				InputConstants.Type.MOUSE.getOrCreate(0), InputConstants.Type.MOUSE.getOrCreate(1)));
	}

	@Test
	void swappedKeysDoNotCount() {
		assertFalse(RecommendedKeysPrompt.alreadyRecommended(key(InputConstants.KEY_PERIOD), key(InputConstants.KEY_COMMA)));
	}
}
