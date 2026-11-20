package com.nibblenerds.unitedminecraft.client;

import java.util.OptionalLong;

import net.minecraft.world.level.storage.LevelData;

/**
 * Tells two worlds served from one address apart. Vanilla used to send the (obfuscated) world seed
 * in its login and respawn packets, which was the one thing a client learned that differed between
 * such worlds; 26.4 dropped it. The world spawn point, which the server still sends on every join,
 * stands in for it: two worlds with different seeds almost always spawn somewhere different.
 *
 * <p>It is only a stand-in. Two worlds that share a spawn point (two void or superflat worlds, say)
 * are indistinguishable, and moving the spawn with {@code /setworldspawn} makes the world look new.
 *
 * <p>Written on the client thread by {@code ClientPacketListenerMixin} once the packet has been
 * handled, so a world change mid-connection switches the value.
 */
final class ServerWorldFingerprint {
	private static boolean known;
	private static long value;

	private ServerWorldFingerprint() {
	}

	static void onSpawn(LevelData.RespawnData spawn) {
		value = fingerprint(spawn.dimension().identifier().toString(),
				spawn.pos().getX(), spawn.pos().getY(), spawn.pos().getZ());
		known = true;
	}

	/** Pure so it can be tested without a live packet. */
	static long fingerprint(String dimension, int x, int y, int z) {
		long hash = dimension.hashCode();
		hash = hash * 31 + x;
		hash = hash * 31 + y;
		hash = hash * 31 + z;
		return hash;
	}

	static OptionalLong current() {
		return known ? OptionalLong.of(value) : OptionalLong.empty();
	}

	static void reset() {
		known = false;
		value = 0;
	}
}
