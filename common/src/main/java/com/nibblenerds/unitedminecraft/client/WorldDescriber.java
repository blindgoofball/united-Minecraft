package com.nibblenerds.unitedminecraft.client;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a {@link WorldSnapshotBuilder world snapshot} to the AI service chosen in {@link AiConfig}
 * and speaks what comes back. Experimental.
 *
 * <p>The snapshot is read on the client thread, as everything touching the world must be, but the
 * request itself is asynchronous - a slow service must never freeze the game - and the answer is
 * handed back to the client thread to be spoken. One request at a time: asking again while one is
 * out is told so rather than queueing a second.
 *
 * <p>What leaves the machine is only the snapshot text, which names no player, world, server or
 * coordinates - positions in it are all relative to the player.
 */
final class WorldDescriber {
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft/ai");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private static boolean inFlight;
	/** Bumped on {@link #reset} so an answer that arrives after leaving the world is dropped, not spoken. */
	private static int generation;

	private WorldDescriber() {
	}

	static void reset() {
		inFlight = false;
		generation++;
	}

	/** {@link ClientKeyBindings#DESCRIBE_WORLD}. */
	static void describe(Minecraft client, LocalPlayer player) {
		AiProvider provider = AiProvider.of(AiConfig.get());
		if (provider == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.ai_not_set_up"));
			return;
		}
		if (inFlight) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.ai_busy"));
			return;
		}
		String prompt = WorldSnapshotText.render(WorldSnapshotBuilder.capture(client, player));
		inFlight = true;
		int mine = generation;
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.ai_working"));

		HTTP.sendAsync(provider.request(prompt), HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> read(provider, response))
				.exceptionally(WorldDescriber::failure)
				.thenAccept(result -> client.execute(() -> {
					if (mine != generation) {
						return;
					}
					inFlight = false;
					client.getNarrator().saySystemNow(result);
				}));
	}

	/** The line to speak for a finished response - the description, or why there isn't one. */
	private static Component read(AiProvider provider, HttpResponse<String> response) {
		try {
			if (response.statusCode() / 100 != 2) {
				String reason = provider.errorMessage(response.body());
				return failed(reason != null ? reason : "status " + response.statusCode());
			}
			String text = provider.parse(response.body()).trim();
			if (text.isEmpty()) {
				return failed("the answer was empty");
			}
			return Component.literal(text);
		} catch (AiProvider.AiException e) {
			return failed(e.getMessage());
		} catch (RuntimeException e) {
			// An answer shaped unlike what the service is documented to send.
			LOGGER.warn("Could not read the AI response", e);
			return failed("the answer was unreadable");
		}
	}

	private static Component failure(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		LOGGER.warn("AI request failed", cause);
		if (cause instanceof HttpTimeoutException) {
			return failed("it took too long to answer");
		}
		if (cause instanceof ConnectException) {
			return failed("could not connect");
		}
		return failed(cause.getClass().getSimpleName());
	}

	private static Component failed(String reason) {
		return Component.translatable("united_minecraft.narrate.ai_failed", reason);
	}
}
