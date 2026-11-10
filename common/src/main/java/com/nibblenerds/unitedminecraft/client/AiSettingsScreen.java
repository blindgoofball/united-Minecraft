package com.nibblenerds.unitedminecraft.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Which AI service describes the world, and how to reach it. Shows only the fields of the chosen
 * service, so a player using Ollama is not tabbing past a Gemini key; changing the service
 * rebuilds the screen. Saved to {@link AiConfig}, not the main settings file, because of the keys.
 */
final class AiSettingsScreen extends SettingsListScreen {
	AiSettingsScreen() {
		super(Component.translatable("united_minecraft.ai_settings_screen.title"));
	}

	@Override
	protected void addRows() {
		AiConfig config = AiConfig.get();
		int x = this.width / 2 - ROW_WIDTH / 2;
		int y = 0;

		y = addCycle(x, y, "united_minecraft.ai_settings_screen.provider", List.of(AiConfig.Provider.values()),
				config.provider,
				value -> Component.translatable("united_minecraft.ai_settings_screen.provider." + value.name().toLowerCase(java.util.Locale.ROOT)),
				value -> {
					config.provider = value;
					rebuildWidgets();
				});

		switch (config.provider) {
			case GEMINI -> {
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.api_key", config.geminiKey, 200,
						value -> config.geminiKey = value.trim());
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.model", config.geminiModel, 100,
						value -> config.geminiModel = value.trim());
			}
			case ANTHROPIC -> {
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.api_key", config.anthropicKey, 200,
						value -> config.anthropicKey = value.trim());
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.model", config.anthropicModel, 100,
						value -> config.anthropicModel = value.trim());
			}
			case OLLAMA -> {
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.url", config.ollamaUrl, 200,
						value -> config.ollamaUrl = value.trim());
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.model", config.ollamaModel, 100,
						value -> config.ollamaModel = value.trim());
			}
			case OPENAI_COMPATIBLE -> {
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.url", config.openAiUrl, 200,
						value -> config.openAiUrl = value.trim());
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.api_key", config.openAiKey, 200,
						value -> config.openAiKey = value.trim());
				y = addTextField(x, y, "united_minecraft.ai_settings_screen.model", config.openAiModel, 100,
						value -> config.openAiModel = value.trim());
			}
			case OFF -> {
			}
		}

		addButton(x, y + ROW_SPACING, "united_minecraft.settings_screen.back", this::onClose);
	}

	@Override
	public void onClose() {
		AiConfig.save();
		Minecraft.getInstance().gui.setScreen(new SettingsScreen());
	}
}
