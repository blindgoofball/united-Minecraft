package com.nibblenerds.unitedminecraft.client;

import java.util.OptionalLong;

import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;

/**
 * The obfuscated world seed the connected server last sent in its login or respawn packet. It is
 * the only thing a vanilla client is told that differs between two worlds served from one address,
 * so {@link WorldScopedStore} uses it to keep their markers and block names apart.
 *
 * <p>Written on the client thread by {@code ClientPacketListenerMixin} once the packet has been
 * handled, so a respawn into a different world switches the value mid-connection.
 */
final class ServerWorldSeed {
	private static boolean known;
	private static long seed;

	private ServerWorldSeed() {
	}

	static void onSpawnInfo(CommonPlayerSpawnInfo info) {
		seed = info.seed();
		known = true;
	}

	static OptionalLong current() {
		return known ? OptionalLong.of(seed) : OptionalLong.empty();
	}

	static void reset() {
		known = false;
		seed = 0;
	}
}
