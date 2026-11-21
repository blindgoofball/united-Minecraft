package com.nibblenerds.unitedminecraft.client;

import java.util.Optional;
import java.util.UUID;

/**
 * The id a server with the mod gave the world the player is in - see {@link
 * com.nibblenerds.unitedminecraft.world.WorldIdRelay}. When there is one it is exact, so {@link
 * WorldScopedStore} prefers it to anything guessed from what a vanilla server sends.
 */
final class ServerWorldId {
	private static UUID id;

	private ServerWorldId() {
	}

	static void onReceived(UUID received) {
		id = received;
	}

	static Optional<UUID> current() {
		return Optional.ofNullable(id);
	}

	static void reset() {
		id = null;
	}
}
