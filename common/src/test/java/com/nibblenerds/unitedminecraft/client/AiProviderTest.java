package com.nibblenerds.unitedminecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Flow;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiProviderTest {
	@TempDir
	Path root;

	@Test
	void geminiSendsTheKeyInAHeaderNotTheUrl() {
		var request = new AiProvider.Gemini("secret", "gemini-test").request("hello");

		assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
				request.uri().toString());
		assertEquals("secret", request.headers().firstValue("x-goog-api-key").orElseThrow());
		assertTrue(!request.uri().toString().contains("secret"));
		assertEquals("hello", JsonParser.parseString(bodyOf(request)).getAsJsonObject()
				.getAsJsonArray("contents").get(0).getAsJsonObject()
				.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
	}

	@Test
	void geminiJoinsTheTextPartsOfTheFirstCandidate() throws Exception {
		String body = """
				{"candidates": [{"content": {"parts": [{"text": "You stand "}, {"text": "in a field."}]}}]}""";
		assertEquals("You stand in a field.", new AiProvider.Gemini("k", "m").parse(body));
	}

	@Test
	void geminiWithNoCandidatesIsAnErrorRatherThanSilence() {
		assertThrows(AiProvider.AiException.class,
				() -> new AiProvider.Gemini("k", "m").parse("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}"));
	}

	@Test
	void anthropicSendsItsKeyAndVersionHeaders() throws Exception {
		var provider = new AiProvider.Anthropic("secret", "claude-test");
		var request = provider.request("hello");

		assertEquals("https://api.anthropic.com/v1/messages", request.uri().toString());
		assertEquals("secret", request.headers().firstValue("x-api-key").orElseThrow());
		assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElseThrow());
		var body = JsonParser.parseString(bodyOf(request)).getAsJsonObject();
		assertEquals("claude-test", body.get("model").getAsString());
		assertTrue(body.get("max_tokens").getAsInt() > 0, "the API rejects a request without max_tokens");

		assertEquals("Hello there.", provider.parse(
				"{\"content\": [{\"type\": \"text\", \"text\": \"Hello \"}, {\"type\": \"text\", \"text\": \"there.\"}]}"));
		assertThrows(AiProvider.AiException.class, () -> provider.parse("{\"content\": []}"));
		assertEquals("invalid x-api-key", provider.errorMessage(
				"{\"type\": \"error\", \"error\": {\"type\": \"authentication_error\", \"message\": \"invalid x-api-key\"}}"));
	}

	@Test
	void ollamaAsksForOneWholeAnswerAtTheGivenAddress() {
		var request = new AiProvider.Ollama("http://localhost:11434/", "gemma4").request("hello");

		assertEquals("http://localhost:11434/api/generate", request.uri().toString(), "a trailing slash is not doubled");
		var body = JsonParser.parseString(bodyOf(request)).getAsJsonObject();
		assertEquals("gemma4", body.get("model").getAsString());
		assertEquals("hello", body.get("prompt").getAsString());
		assertEquals(false, body.get("stream").getAsBoolean(), "a streamed reply would arrive as many JSON lines");
	}

	@Test
	void ollamaReadsTheResponseField() throws Exception {
		assertEquals("A quiet shore.", new AiProvider.Ollama("http://x", "m").parse("{\"response\": \"A quiet shore.\", \"done\": true}"));
	}

	@Test
	void openAiCompatibleSendsABearerKeyOnlyWhenThereIsOne() {
		var withKey = new AiProvider.OpenAiCompatible("https://api.example.com/v1", "abc", "m").request("hi");
		var withoutKey = new AiProvider.OpenAiCompatible("http://localhost:1234/v1", "", "m").request("hi");

		assertEquals("https://api.example.com/v1/chat/completions", withKey.uri().toString());
		assertEquals("Bearer abc", withKey.headers().firstValue("Authorization").orElseThrow());
		assertTrue(withoutKey.headers().firstValue("Authorization").isEmpty(), "a local server needs no key");
	}

	@Test
	void openAiCompatibleReadsTheFirstChoice() throws Exception {
		String body = "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"Mist over the lake.\"}}]}";
		assertEquals("Mist over the lake.", new AiProvider.OpenAiCompatible("http://x", "", "m").parse(body));
		assertThrows(AiProvider.AiException.class,
				() -> new AiProvider.OpenAiCompatible("http://x", "", "m").parse("{\"choices\": []}"));
	}

	@Test
	void garbageBodiesAreAnErrorNotACrash() {
		assertThrows(AiProvider.AiException.class, () -> new AiProvider.Ollama("http://x", "m").parse("<html>502</html>"));
		assertThrows(AiProvider.AiException.class, () -> new AiProvider.Gemini("k", "m").parse("[1, 2]"));
	}

	@Test
	void errorMessagesAreFoundWhicheverShapeTheServiceUses() {
		var gemini = new AiProvider.Gemini("k", "m");
		assertEquals("API key not valid", gemini.errorMessage("{\"error\": {\"code\": 400, \"message\": \"API key not valid\"}}"));
		assertEquals("model 'x' not found", new AiProvider.Ollama("http://x", "x").errorMessage("{\"error\": \"model 'x' not found\"}"));
		assertNull(gemini.errorMessage("not json"));
		assertNull(gemini.errorMessage("{}"));
	}

	@Test
	void theChosenProviderComesFromConfigAndNeedsItsCredentials() throws IOException {
		TestPlatform.install(root);
		Files.createDirectories(root.resolve("config"));
		Files.writeString(root.resolve("config").resolve("united_minecraft_ai.json"),
				"{\"provider\": \"GEMINI\", \"geminiKey\": \"\"}");
		AiConfig.load();
		assertNull(AiProvider.of(AiConfig.get()), "Gemini without a key is not ready");

		Files.writeString(root.resolve("config").resolve("united_minecraft_ai.json"),
				"{\"provider\": \"OLLAMA\"}");
		AiConfig.load();
		assertTrue(AiProvider.of(AiConfig.get()) instanceof AiProvider.Ollama);
		assertEquals("gemma4", AiConfig.get().ollamaModel, "fields missing from the file keep their defaults");
	}

	@Test
	void anUnknownProviderNameFallsBackToOff() throws IOException {
		TestPlatform.install(root);
		Files.createDirectories(root.resolve("config"));
		Files.writeString(root.resolve("config").resolve("united_minecraft_ai.json"), "{\"provider\": \"SKYNET\"}");
		AiConfig.load();
		assertNull(AiProvider.of(AiConfig.get()));
	}

	private static String bodyOf(java.net.http.HttpRequest request) {
		var publisher = request.bodyPublisher().orElseThrow();
		var out = new StringBuilder();
		publisher.subscribe(new Flow.Subscriber<java.nio.ByteBuffer>() {
			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				subscription.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(java.nio.ByteBuffer item) {
				out.append(java.nio.charset.StandardCharsets.UTF_8.decode(item));
			}

			@Override
			public void onError(Throwable throwable) {
			}

			@Override
			public void onComplete() {
			}
		});
		return out.toString();
	}
}
