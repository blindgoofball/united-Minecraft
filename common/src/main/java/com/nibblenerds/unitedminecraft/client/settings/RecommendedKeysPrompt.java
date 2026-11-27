package com.nibblenerds.unitedminecraft.client.settings;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * The one-time offer, on first reaching the title screen, to move vanilla's Attack/Destroy to
 * comma and Place/Use to period - this mod's recommended setup. Asked at most once ever,
 * recorded in the config whatever the answer, so a player who declines (or rebinds them back
 * later) is never nagged. Skipped silently when the keys are already set that way.
 *
 * <p>The screen is opened from the client tick, not from the title screen's own init, so it is
 * never swapped in while that screen is still mid-initialization. If the player has already moved
 * on to another screen by then, it waits for the title screen to come back.
 */
public final class RecommendedKeysPrompt {
	public static final InputConstants.Key ATTACK_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_COMMA);
	public static final InputConstants.Key USE_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_PERIOD);

	private static boolean checkDone;
	private static boolean pending;

	private RecommendedKeysPrompt() {
	}

	/** A screen finished init - the first title screen of the session decides whether to ask. */
	public static void onScreenInit(Screen screen) {
		if (checkDone || !(screen instanceof TitleScreen)) {
			return;
		}
		checkDone = true;
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		if (config.recommendedKeysAsked) {
			return;
		}
		Options options = Minecraft.getInstance().options;
		if (alreadyRecommended(currentKey(options.keyAttack), currentKey(options.keyUse))) {
			config.recommendedKeysAsked = true;
			UnitedMinecraftConfig.save();
			return;
		}
		pending = true;
	}

	public static void tick(Minecraft client) {
		if (!pending || !(client.gui.screen() instanceof TitleScreen title)) {
			return;
		}
		pending = false;
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		config.recommendedKeysAsked = true;
		UnitedMinecraftConfig.save();
		client.setScreenAndShow(new ConfirmScreen(accepted -> onAnswer(client, title, accepted),
				Component.translatable("united_minecraft.recommended_keys.title"),
				Component.translatable("united_minecraft.recommended_keys.message"),
				Component.translatable("united_minecraft.recommended_keys.yes"),
				Component.translatable("united_minecraft.recommended_keys.no")));
	}

	private static void onAnswer(Minecraft client, Screen previous, boolean accepted) {
		if (accepted) {
			apply(client.options);
		}
		client.setScreenAndShow(previous);
		if (accepted) {
			client.getNarrator().saySystemQueued(Component.translatable("united_minecraft.recommended_keys.done"));
		}
	}

	static void apply(Options options) {
		options.keyAttack.setKey(ATTACK_KEY);
		options.keyUse.setKey(USE_KEY);
		KeyMapping.resetMapping();
		options.save();
	}

	/** Pulled out so the decision can be tested without a running game. */
	static boolean alreadyRecommended(InputConstants.Key attack, InputConstants.Key use) {
		return ATTACK_KEY.equals(attack) && USE_KEY.equals(use);
	}

	public static InputConstants.Key currentKey(KeyMapping mapping) {
		return InputConstants.getKey(mapping.saveString());
	}
}
