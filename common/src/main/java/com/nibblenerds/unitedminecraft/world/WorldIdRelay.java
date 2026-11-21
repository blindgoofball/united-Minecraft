package com.nibblenerds.unitedminecraft.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nibblenerds.unitedminecraft.client.SafeFiles;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Gives a world a stable random id and tells each joining player who has the mod. The id lives in
 * a small file in the world's own folder, so it travels with the world - a copy of the folder is
 * the same world to the player - and is made the first time anyone joins.
 *
 * <p>Runs on the integrated server too, where the client ignores it (a singleplayer world is
 * already told apart by its save folder).
 */
public final class WorldIdRelay {
	static final String FILE_NAME = "united_minecraft_world_id.txt";
	private static final Logger LOGGER = LoggerFactory.getLogger("united_minecraft");

	private static MinecraftServer cachedFor;
	private static UUID cached;

	private WorldIdRelay() {
	}

	/** How the loader reaches a player - the only loader-specific part of this. */
	public interface Network {
		/** Whether this player's client has United Minecraft, i.e. registered the world id payload. */
		boolean canReceive(ServerPlayer player);

		void send(ServerPlayer player, WorldIdPayload payload);
	}

	public static void onJoin(ServerPlayer player, Network network) {
		if (!network.canReceive(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		if (cachedFor != server) {
			cached = readOrCreate(server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME));
			cachedFor = server;
		}
		network.send(player, new WorldIdPayload(cached));
	}

	public static void clear() {
		cachedFor = null;
		cached = null;
	}

	/**
	 * The id in {@code file}, made and saved first if there is none. A file that does not hold an
	 * id is set aside rather than overwritten, and a new id is made in its place.
	 */
	static UUID readOrCreate(Path file) {
		if (Files.exists(file)) {
			try {
				return UUID.fromString(Files.readString(file, StandardCharsets.UTF_8).strip());
			} catch (IOException | IllegalArgumentException e) {
				LOGGER.warn("Could not read the world id in {}; making a new one", file, e);
				SafeFiles.preserveUnreadable(file, LOGGER);
			}
		}
		UUID id = UUID.randomUUID();
		try {
			SafeFiles.writeAtomically(file, writer -> writer.write(id + "\n"));
		} catch (IOException e) {
			// The id still works for this session; it just will not survive a restart.
			LOGGER.warn("Could not save the world id to {}", file, e);
		}
		return id;
	}
}
