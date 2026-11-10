package com.nibblenerds.unitedminecraft.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.nibblenerds.unitedminecraft.platform.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the AI world description is sent, and with what credentials. A file of its own
 * ({@code united_minecraft_ai.json}) rather than part of {@link UnitedMinecraftConfig}, because it
 * holds API keys: someone sharing their settings to help another player should not be handing out
 * a key with them.
 *
 * <p>Each provider keeps its own fields, so switching between, say, a local Ollama and Gemini does
 * not lose either one's setup.
 */
final class AiConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/ai");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AiConfig instance = new AiConfig();

	/** Which service answers. {@code OFF} until the player has chosen one. */
	enum Provider {
		OFF, GEMINI, ANTHROPIC, OLLAMA, OPENAI_COMPATIBLE
	}

	Provider provider = Provider.OFF;

	String geminiKey = "";
	String geminiModel = "gemini-3.6-flash";

	String anthropicKey = "";
	String anthropicModel = "claude-haiku-5-5";

	String ollamaUrl = "http://localhost:11434";
	String ollamaModel = "gemma4";

	/** Any service that speaks the OpenAI chat API: OpenAI itself, OpenRouter, Groq, LM Studio, and so on. */
	String openAiUrl = "https://api.openai.com/v1";
	String openAiKey = "";
	String openAiModel = "gpt-6-luna";

	private AiConfig() {
	}

	static AiConfig get() {
		return instance;
	}

	private void sanitize() {
		if (provider == null) {
			provider = Provider.OFF;
		}
		geminiKey = trimmed(geminiKey);
		geminiModel = orDefault(geminiModel, "gemini-3.6-flash");
		anthropicKey = trimmed(anthropicKey);
		anthropicModel = orDefault(anthropicModel, "claude-haiku-5-5");
		ollamaUrl = orDefault(ollamaUrl, "http://localhost:11434");
		ollamaModel = orDefault(ollamaModel, "gemma4");
		openAiUrl = orDefault(openAiUrl, "https://api.openai.com/v1");
		openAiKey = trimmed(openAiKey);
		openAiModel = orDefault(openAiModel, "gpt-6-luna");
	}

	private static String trimmed(String value) {
		return value == null ? "" : value.trim();
	}

	private static String orDefault(String value, String fallback) {
		String trimmed = trimmed(value);
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private static Path file() {
		return Platform.get().configDir().resolve("united_minecraft_ai.json");
	}

	static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			AiConfig loaded = GSON.fromJson(reader, AiConfig.class);
			if (loaded != null) {
				loaded.sanitize();
				instance = loaded;
			}
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to load AI settings from {}", file, e);
			SafeFiles.preserveUnreadable(file, LOGGER);
		}
	}

	static void save() {
		instance.sanitize();
		Path file = file();
		try {
			SafeFiles.writeAtomically(file, writer -> GSON.toJson(instance, writer));
		} catch (IOException e) {
			LOGGER.warn("Failed to save AI settings to {}", file, e);
		}
	}
}
