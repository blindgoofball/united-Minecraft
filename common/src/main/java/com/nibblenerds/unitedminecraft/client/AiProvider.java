package com.nibblenerds.unitedminecraft.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * One AI service the world description can be sent to. Only the shape of the exchange lives here
 * - the request to build and how to read the answer - so each one is plain data in, plain data
 * out and testable without a network. {@link WorldDescriber} does the sending.
 *
 * <p>Adding a service is one more implementation of this and a case in {@link #of}.
 */
interface AiProvider {
	/** The request for {@code prompt}. */
	HttpRequest request(String prompt);

	/** The reply text out of a 2xx response body. */
	String parse(String body) throws AiException;

	/** A service-specific message out of a non-2xx response body, or null to use the bare status. */
	default String errorMessage(String body) {
		return errorField(body);
	}

	/** Local models can take a while to load before they answer. */
	Duration timeout();

	/** The service could not give an answer; {@link #getMessage()} is fit to speak to the player. */
	final class AiException extends Exception {
		AiException(String message) {
			super(message);
		}
	}

	/** The provider the player has chosen, or null if none is chosen or it has no credentials yet. */
	static AiProvider of(AiConfig config) {
		return switch (config.provider) {
			case GEMINI -> config.geminiKey.isEmpty() ? null : new Gemini(config.geminiKey, config.geminiModel);
			case ANTHROPIC -> config.anthropicKey.isEmpty() ? null : new Anthropic(config.anthropicKey, config.anthropicModel);
			case OLLAMA -> new Ollama(config.ollamaUrl, config.ollamaModel);
			case OPENAI_COMPATIBLE -> new OpenAiCompatible(config.openAiUrl, config.openAiKey, config.openAiModel);
			case OFF -> null;
		};
	}

	static String join(String base, String path) {
		return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
	}

	static JsonObject object(String body) throws AiException {
		try {
			JsonElement root = JsonParser.parseString(body);
			if (root.isJsonObject()) {
				return root.getAsJsonObject();
			}
		} catch (JsonParseException e) {
			// Falls through to the same message as a body that is not an object.
		}
		throw new AiException("The service sent back something unreadable");
	}

	/** {@code "error": {"message": ...}} or {@code "error": "..."}, which is how all three report problems. */
	static String errorField(String body) {
		try {
			JsonElement error = JsonParser.parseString(body).getAsJsonObject().get("error");
			if (error == null) {
				return null;
			}
			if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
				return error.getAsJsonObject().get("message").getAsString();
			}
			return error.isJsonPrimitive() ? error.getAsString() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	record Gemini(String key, String model) implements AiProvider {
		@Override
		public HttpRequest request(String prompt) {
			JsonObject part = new JsonObject();
			part.addProperty("text", prompt);
			JsonArray parts = new JsonArray();
			parts.add(part);
			JsonObject content = new JsonObject();
			content.add("parts", parts);
			JsonArray contents = new JsonArray();
			contents.add(content);
			JsonObject body = new JsonObject();
			body.add("contents", contents);
			return HttpRequest.newBuilder(URI.create(
							"https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
					.header("Content-Type", "application/json")
					// In a header rather than the URL, so it never ends up in a log line.
					.header("x-goog-api-key", key)
					.timeout(timeout())
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
		}

		@Override
		public String parse(String body) throws AiException {
			JsonObject root = object(body);
			JsonArray candidates = root.has("candidates") && root.get("candidates").isJsonArray()
					? root.getAsJsonArray("candidates") : new JsonArray();
			if (candidates.isEmpty()) {
				// A prompt Gemini declines to answer comes back with no candidates, only a reason.
				throw new AiException("Gemini gave no answer");
			}
			StringBuilder text = new StringBuilder();
			JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
			if (content != null && content.has("parts")) {
				for (JsonElement part : content.getAsJsonArray("parts")) {
					if (part.getAsJsonObject().has("text")) {
						text.append(part.getAsJsonObject().get("text").getAsString());
					}
				}
			}
			return text.toString();
		}

		@Override
		public Duration timeout() {
			return Duration.ofSeconds(60);
		}
	}

	record Anthropic(String key, String model) implements AiProvider {
		@Override
		public HttpRequest request(String prompt) {
			JsonObject message = new JsonObject();
			message.addProperty("role", "user");
			message.addProperty("content", prompt);
			JsonArray messages = new JsonArray();
			messages.add(message);
			JsonObject body = new JsonObject();
			body.addProperty("model", model);
			// Required by the API; far more than a description needs.
			body.addProperty("max_tokens", 1024);
			body.add("messages", messages);
			return HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/messages"))
					.header("Content-Type", "application/json")
					.header("x-api-key", key)
					.header("anthropic-version", "2023-06-01")
					.timeout(timeout())
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
		}

		@Override
		public String parse(String body) throws AiException {
			JsonObject root = object(body);
			StringBuilder text = new StringBuilder();
			if (root.has("content") && root.get("content").isJsonArray()) {
				for (JsonElement block : root.getAsJsonArray("content")) {
					JsonObject item = block.getAsJsonObject();
					if (item.has("text")) {
						text.append(item.get("text").getAsString());
					}
				}
			}
			if (text.isEmpty()) {
				throw new AiException("Claude gave no answer");
			}
			return text.toString();
		}

		@Override
		public Duration timeout() {
			return Duration.ofSeconds(60);
		}
	}

	record Ollama(String url, String model) implements AiProvider {
		@Override
		public HttpRequest request(String prompt) {
			JsonObject body = new JsonObject();
			body.addProperty("model", model);
			body.addProperty("prompt", prompt);
			body.addProperty("stream", false);
			return HttpRequest.newBuilder(URI.create(join(url, "/api/generate")))
					.header("Content-Type", "application/json")
					.timeout(timeout())
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
		}

		@Override
		public String parse(String body) throws AiException {
			JsonObject root = object(body);
			if (!root.has("response")) {
				throw new AiException("Ollama gave no answer");
			}
			return root.get("response").getAsString();
		}

		@Override
		public Duration timeout() {
			return Duration.ofSeconds(180);
		}
	}

	/** The chat API OpenAI introduced and most other services now copy. */
	record OpenAiCompatible(String url, String key, String model) implements AiProvider {
		@Override
		public HttpRequest request(String prompt) {
			JsonObject message = new JsonObject();
			message.addProperty("role", "user");
			message.addProperty("content", prompt);
			JsonArray messages = new JsonArray();
			messages.add(message);
			JsonObject body = new JsonObject();
			body.addProperty("model", model);
			body.add("messages", messages);
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(join(url, "/chat/completions")))
					.header("Content-Type", "application/json")
					.timeout(timeout())
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
			if (!key.isEmpty()) {
				// A local server such as LM Studio needs no key at all.
				request.header("Authorization", "Bearer " + key);
			}
			return request.build();
		}

		@Override
		public String parse(String body) throws AiException {
			JsonObject root = object(body);
			JsonArray choices = root.has("choices") && root.get("choices").isJsonArray()
					? root.getAsJsonArray("choices") : new JsonArray();
			if (choices.isEmpty()) {
				throw new AiException("The service gave no answer");
			}
			JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
			if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
				throw new AiException("The service gave no answer");
			}
			return message.get("content").getAsString();
		}

		@Override
		public Duration timeout() {
			return Duration.ofSeconds(120);
		}
	}
}
