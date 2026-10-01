package com.nibblenerds.unitedminecraft.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.phys.Vec3;

/**
 * Server half of structure voices: every few ticks, tells each player running United Minecraft
 * which structures have a piece within {@link #MAX_RANGE} blocks of them, and where that
 * nearest piece is. Runs on a dedicated server with the mod installed, and on the integrated
 * server in single player, so it covers every structure the world actually generated, modded
 * ones included - the client can't work this out itself, since structure starts are never sent
 * to it.
 *
 * <p>Plain vanilla code: each loader module registers the payload, calls {@link #tick} at the
 * end of every server tick with its own way of reaching a player ({@link Network}), and {@link
 * #clear} when the server stops.
 *
 * <p>Only reads chunks the player already has loaded ({@code getChunkNow}), and caches each
 * structure's piece boxes, so the chunk holding a structure's start is looked up at most once
 * per structure rather than every scan.
 */
public final class StructureScanner {
	/** The furthest the client's announce radius can be set to - see {@code UnitedMinecraftConfig}. */
	public static final int MAX_RANGE = 64;
	private static final int SCAN_INTERVAL_TICKS = 5;
	/** How often an empty list is still sent, so the client knows this server reports structures at all. */
	private static final int EMPTY_HEARTBEAT_TICKS = 40;
	private static final int CACHE_SIZE = 1024;

	private static final Map<StartKey, CachedStart> cache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<StartKey, CachedStart> eldest) {
			return size() > CACHE_SIZE;
		}
	};
	/** Tick an empty list was last sent to each player, so empties are sent as a heartbeat rather than every scan. */
	private static final Map<UUID, Integer> lastEmptySent = new HashMap<>();

	private StructureScanner() {
	}

	/** How the loader sends {@link StructuresNearbyPayload} - the only loader-specific part of this. */
	public interface Network {
		/** Whether this player's client has United Minecraft, i.e. registered the payload. */
		boolean canSend(ServerPlayer player);

		void send(ServerPlayer player, StructuresNearbyPayload payload);
	}

	/** Call at the end of every server tick. */
	public static void tick(MinecraftServer server, Network network) {
		int now = server.getTickCount();
		if (now % SCAN_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!network.canSend(player)) {
				continue;
			}
			List<StructuresNearbyPayload.Entry> entries = scan(player);
			if (entries.isEmpty()) {
				Integer last = lastEmptySent.get(player.getUUID());
				if (last != null && now - last < EMPTY_HEARTBEAT_TICKS) {
					continue;
				}
				lastEmptySent.put(player.getUUID(), now);
			} else {
				lastEmptySent.remove(player.getUUID());
			}
			network.send(player, new StructuresNearbyPayload(entries));
		}
		if (lastEmptySent.size() > server.getPlayerList().getPlayerCount()) {
			lastEmptySent.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	private static List<StructuresNearbyPayload.Entry> scan(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		ChunkPos center = ChunkPos.containing(player.blockPosition());
		int chunkRadius = (MAX_RANGE >> 4) + 1;
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

		// A structure references every chunk its bounding box overlaps, so the same start turns
		// up once per chunk - collect each start once before measuring it.
		Map<StartKey, Structure> starts = new HashMap<>();
		for (int cx = center.x() - chunkRadius; cx <= center.x() + chunkRadius; cx++) {
			for (int cz = center.z() - chunkRadius; cz <= center.z() + chunkRadius; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<Structure, LongSet> reference : chunk.getAllReferences().entrySet()) {
					LongIterator iterator = reference.getValue().iterator();
					while (iterator.hasNext()) {
						starts.putIfAbsent(new StartKey(level.dimension(), reference.getKey(), iterator.nextLong()),
								reference.getKey());
					}
				}
			}
		}

		List<StructuresNearbyPayload.Entry> entries = new ArrayList<>();
		for (Map.Entry<StartKey, Structure> start : starts.entrySet()) {
			CachedStart cached = cache.computeIfAbsent(start.getKey(), key -> load(level, registry, start.getValue(), key.start()));
			if (cached.id() == null) {
				continue;
			}
			Vec3 nearest = cached.nearestPoint(level, eye);
			if (nearest != null && nearest.distanceToSqr(eye) <= (double) MAX_RANGE * MAX_RANGE) {
				entries.add(new StructuresNearbyPayload.Entry(cached.id(), start.getKey().start(),
						nearest.x(), nearest.y(), nearest.z()));
			}
		}
		entries.sort(Comparator.comparingDouble(entry -> eye.distanceToSqr(entry.x(), entry.y(), entry.z())));
		return entries.size() > StructuresNearbyPayload.MAX_ENTRIES
				? new ArrayList<>(entries.subList(0, StructuresNearbyPayload.MAX_ENTRIES))
				: entries;
	}

	private static CachedStart load(ServerLevel level, Registry<Structure> registry, Structure structure, long start) {
		Identifier id = registry.getKey(structure);
		List<BoundingBox> boxes = new ArrayList<>();
		// Loads the start's chunk to STRUCTURE_STARTS if it isn't already - the same call vanilla
		// makes for /locate and structure-bound mob spawns - and skips starts that turned out
		// empty (e.g. a village whose jigsaw assembly placed nothing).
		level.structureManager().fillStartsForStructure(structure, LongSets.singleton(start), found -> {
			for (StructurePiece piece : found.getPieces()) {
				boxes.add(piece.getBoundingBox());
			}
		});
		return new CachedStart(boxes.isEmpty() ? null : id, List.copyOf(boxes));
	}

	/** Forgets cached structures - on server stop, so a different world never reuses them. */
	public static void clear() {
		cache.clear();
		lastEmptySent.clear();
	}

	private record StartKey(ResourceKey<Level> dimension, Structure structure, long start) {
	}

	/**
	 * One structure's pieces, with whether each is visible from the surface world or buried below
	 * it. Buried pieces are never reported - strongholds, mineshafts, ancient cities and buried
	 * treasure are meant to be found by exploring, and announcing them through solid rock would
	 * be an x-ray (the same rule the Scanner applies to ore). A piece counts as buried when its
	 * top sits more than one block below the terrain at its centre; only worked out for
	 * dimensions with a sky, since the Nether and End have no surface to be buried under.
	 *
	 * <p>Whether a piece is buried is decided once and kept, but only when its chunk is loaded,
	 * so a piece in a chunk that is not ready yet is simply left out until it is, never guessed
	 * at - and never loaded on purpose just to find out.
	 *
	 * @param id null for a start that has no pieces, cached so it isn't looked up again
	 */
	private static final class CachedStart {
		private static final byte UNKNOWN = 0;
		private static final byte EXPOSED = 1;
		private static final byte BURIED = 2;
		/** How far below the terrain a piece's top may sit and still count as exposed. */
		private static final int BURIAL_TOLERANCE = 1;

		private final Identifier id;
		private final List<BoundingBox> boxes;
		private final byte[] state;

		CachedStart(Identifier id, List<BoundingBox> boxes) {
			this.id = id;
			this.boxes = boxes;
			this.state = new byte[boxes.size()];
		}

		Identifier id() {
			return id;
		}

		/** The closest point to {@code from} on any exposed piece of this structure, or null if none is. */
		Vec3 nearestPoint(ServerLevel level, Vec3 from) {
			Vec3 best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int i = 0; i < boxes.size(); i++) {
				if (state[i] == UNKNOWN) {
					state[i] = classify(level, boxes.get(i));
				}
				if (state[i] != EXPOSED) {
					continue;
				}
				BoundingBox box = boxes.get(i);
				// Block boxes are inclusive, so a piece's far faces are at max + 1.
				double x = Math.clamp(from.x(), box.minX(), box.maxX() + 1.0);
				double y = Math.clamp(from.y(), box.minY(), box.maxY() + 1.0);
				double z = Math.clamp(from.z(), box.minZ(), box.maxZ() + 1.0);
				double distance = from.distanceToSqr(x, y, z);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = new Vec3(x, y, z);
				}
			}
			return best;
		}

		private static byte classify(ServerLevel level, BoundingBox box) {
			if (!level.dimensionType().hasSkyLight()) {
				return EXPOSED;
			}
			int x = box.getCenter().getX();
			int z = box.getCenter().getZ();
			LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			if (chunk == null) {
				return UNKNOWN;
			}
			// The top of the terrain ignoring water, so a monument or wreck on the sea floor is
			// measured against the floor, not the waves above it.
			int terrain = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			return box.maxY() >= terrain - BURIAL_TOLERANCE ? EXPOSED : BURIED;
		}
	}
}
