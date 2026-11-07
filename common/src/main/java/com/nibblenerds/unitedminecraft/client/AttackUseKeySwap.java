package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.settings.RecommendedKeysPrompt;
import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * One key to flip vanilla's Attack/Destroy and Place/Use between their default mouse buttons and
 * whatever keys the player prefers (comma and period, if they took the recommended setup).
 *
 * <p>Nothing is stored ahead of time that could go stale: pressing it while on keys other than the
 * mouse defaults reads what those two actions are bound to <em>right then</em>, remembers that as
 * the "other" set, and switches to the mouse. So rebinding them in Controls is picked up by the
 * next swap with no extra step. Pressing it while on the mouse restores the remembered set, or the
 * recommended comma and period if there is none yet.
 */
public final class AttackUseKeySwap {
	/** What a swap should do: the keys to switch to, and whether the keys being left should be remembered first. */
	record Plan(InputConstants.Key attack, InputConstants.Key use, boolean rememberCurrent) {
	}

	private AttackUseKeySwap() {
	}

	static void toggle(Minecraft client) {
		Options options = client.options;
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		InputConstants.Key attack = RecommendedKeysPrompt.currentKey(options.keyAttack);
		InputConstants.Key use = RecommendedKeysPrompt.currentKey(options.keyUse);
		Plan plan = plan(attack, use, options.keyAttack.getDefaultKey(), options.keyUse.getDefaultKey(),
				parse(config.otherAttackKey), parse(config.otherUseKey));

		if (plan.rememberCurrent()) {
			config.otherAttackKey = attack.getName();
			config.otherUseKey = use.getName();
			UnitedMinecraftConfig.save();
		}
		// A button held across the swap would stay "down" for a key that no longer maps to it.
		KeyMapping.releaseAll();
		options.keyAttack.setKey(plan.attack());
		options.keyUse.setKey(plan.use());
		KeyMapping.resetMapping();
		options.save();

		boolean mouse = plan.attack().equals(options.keyAttack.getDefaultKey())
				&& plan.use().equals(options.keyUse.getDefaultKey());
		client.getNarrator().saySystemNow(mouse
				? Component.translatable("united_minecraft.narrate.keys_mouse")
				: Component.translatable("united_minecraft.narrate.keys_custom",
						options.keyAttack.getTranslatedKeyMessage(), options.keyUse.getTranslatedKeyMessage()));
	}

	/**
	 * Pure decision, so it can be tested without a running game. {@code savedAttack}/{@code savedUse}
	 * are the remembered "other" keys, null when none has been saved.
	 */
	static Plan plan(InputConstants.Key attack, InputConstants.Key use, InputConstants.Key defaultAttack,
			InputConstants.Key defaultUse, InputConstants.Key savedAttack, InputConstants.Key savedUse) {
		boolean onMouse = attack.equals(defaultAttack) && use.equals(defaultUse);
		if (!onMouse) {
			return new Plan(defaultAttack, defaultUse, true);
		}
		if (savedAttack != null && savedUse != null) {
			return new Plan(savedAttack, savedUse, false);
		}
		return new Plan(RecommendedKeysPrompt.ATTACK_KEY, RecommendedKeysPrompt.USE_KEY, false);
	}

	/** The key a saved name refers to, or null for nothing saved (or a name that no longer parses). */
	private static InputConstants.Key parse(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		try {
			return InputConstants.getKey(name);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
