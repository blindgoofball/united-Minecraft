package com.nibblenerds.unitedminecraft.platform;

import java.nio.file.Path;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The handful of things this mod needs from its mod loader that aren't plain vanilla
 * Minecraft - two directories, and sending a payload to the server (vanilla has no loader-free
 * way to check a server's channels). Everything else the mod does is either
 * vanilla API, Mixin, or GLFW, all of which are identical across loaders.
 *
 * <p>The loader-specific module installs an implementation through {@link #set} before
 * anything else runs; shared code only ever sees {@link #get}. Keeping this to an
 * interface rather than, say, a static utility that checks which loader is present is
 * what lets the shared source set compile with no loader on the classpath at all - if
 * someone reaches for a loader API directly from shared code, it fails to compile
 * rather than failing at runtime on whichever loader wasn't tested.
 */
public interface Platform {
	/** The loader's config directory - {@code .minecraft/config} on both supported loaders. */
	Path configDir();

	/** The game directory - the {@code .minecraft} instance root. */
	Path gameDir();

	/**
	 * Whether the server this client is connected to has registered {@code type}, i.e. has the
	 * mod and so will understand a payload of that type. False when not connected.
	 */
	boolean serverAccepts(CustomPacketPayload.Type<?> type);

	/** Sends {@code payload} to the server. Only call after {@link #serverAccepts} said yes. */
	void sendToServer(CustomPacketPayload payload);

	static Platform get() {
		Platform platform = Holder.instance;
		if (platform == null) {
			throw new IllegalStateException(
					"Platform.set() was never called - the loader entrypoint must install one before any shared code runs");
		}
		return platform;
	}

	static void set(Platform platform) {
		Holder.instance = platform;
	}

	/** Holds the installed implementation; {@link Platform} can't declare mutable state itself. */
	final class Holder {
		private static volatile Platform instance;

		private Holder() {
		}
	}
}
