package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Falling;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Ray;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Sample;
import com.nibblenerds.unitedminecraft.client.WorldSnapshotText.Surface;

/**
 * Reads the live world around the player into a {@link WorldSnapshotText.Input}, for {@link
 * WorldDescriber} to render and send to the AI service. Experimental.
 *
 * <p>Three layers, all from what the client already has loaded - nothing is requested from the
 * server:
 * <ul>
 * <li>a grid of ground samples out to the loaded range (height, surface, trees, water depth),
 * from the chunks' own heightmaps, so even a distant mountain is known;
 * <li>falling water and lava, found by checking which chunk sections hold any at all before
 * looking inside them, since a waterfall is too thin for the sample grid to catch;
 * <li>rays cast from the eyes for the immediate surroundings (walls, ceiling, floor).
 * </ul>
 */
final class WorldSnapshotBuilder {
	private static final int MAX_RANGE = 192;
	private static final int[] ELEVATIONS = {-80, -60, -35, -15, 0, 15, 35, 60, 80};
	private static final int AZIMUTH_STEP = 15;
	private static final int RAY_RANGE_OUTDOORS = 32;
	private static final int RAY_RANGE_ENCLOSED = 48;
	private static final int MAX_FALLING_CELLS = 30000;
	private static final int MIN_FALL_HEIGHT = 6;
	private static final int ENCLOSED_FALLING_RANGE = 48;
	// How far below the surface a fall's top may sit and still count as one you could see from outside.
	private static final int BURIED_FALL_MARGIN = 4;
	// Tree trunks and canopies are walked through to reach the actual ground; this bounds the walk.
	private static final int MAX_TREE_HEIGHT = 48;
	private static final int MAX_WATER_DEPTH = 40;

	private WorldSnapshotBuilder() {
	}

	static WorldSnapshotText.Input capture(Minecraft client, LocalPlayer player) {
		Level level = player.level();
		FastBlockAccess blocks = new FastBlockAccess(level);
		BlockPos origin = player.blockPosition();

		int viewBlocks = client.options.renderDistance().get() * 16;
		int step = WorldSnapshotText.gridStep(Math.min(MAX_RANGE, viewBlocks));
		int range = Math.min(MAX_RANGE, viewBlocks) / step * step;

		boolean enclosed = isEnclosed(level, blocks, player);
		List<Sample> samples = new ArrayList<>();
		Map<String, Integer> biomes = new HashMap<>();
		if (!enclosed) {
			sampleGround(level, blocks, origin, range, step, samples, biomes);
		}
		List<Falling> falling = findFalling(blocks, origin, enclosed ? ENCLOSED_FALLING_RANGE : range);
		if (!enclosed) {
			falling = visibleFalling(falling, samples, step);
		}

		int rayRange = enclosed ? RAY_RANGE_ENCLOSED : RAY_RANGE_OUTDOORS;
		WorldSnapshotText.Facts facts = new WorldSnapshotText.Facts(
				level.dimension().identifier().getPath(),
				Math.floorMod((int) Math.round(player.getYRot()) + 180, 360),
				timeOfDay(level),
				level.isThundering() ? "thunderstorm" : level.isRaining() ? "rain" : "clear",
				level.getMaxLocalRawBrightness(origin),
				enclosed,
				new TreeMap<>(biomes));
		return new WorldSnapshotText.Input(facts, samples, step, range, falling,
				castRays(level, player, rayRange), rayRange);
	}

	/** Under a roof of solid blocks, or in a dimension with a ceiling - the land around isn't what's around the player. */
	private static boolean isEnclosed(Level level, FastBlockAccess blocks, LocalPlayer player) {
		if (level.dimensionType().hasCeiling()) {
			return true;
		}
		BlockPos pos = player.blockPosition();
		ChunkAccess chunk = blocks.chunkAt(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null) {
			return false;
		}
		int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15);
		return surface > player.getEyeY() + 3;
	}

	private static void sampleGround(Level level, FastBlockAccess blocks, BlockPos origin, int range, int step,
			List<Sample> samples, Map<String, Integer> biomes) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int n = range / step;
		for (int i = -n; i <= n; i++) {
			for (int j = -n; j <= n; j++) {
				int dx = i * step;
				int dz = j * step;
				if (dx * dx + dz * dz > range * range) {
					continue;
				}
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				ChunkAccess chunk = blocks.chunkAt(x >> 4, z >> 4);
				if (chunk == null) {
					continue;
				}
				Sample sample = sampleColumn(level, blocks, chunk, pos, x, z, dx, dz, origin.getY());
				if (sample == null) {
					continue;
				}
				samples.add(sample);
				pos.set(x, origin.getY() + sample.relY(), z);
				level.getBiome(pos).unwrapKey().ifPresent(key -> biomes.merge(key.identifier().getPath(), 1, Integer::sum));
			}
		}
	}

	private static Sample sampleColumn(Level level, FastBlockAccess blocks, ChunkAccess chunk,
			BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, int referenceY) {
		int minY = level.getMinY();
		int lx = x & 15;
		int lz = z & 15;
		// Heightmaps differ on whether they hand back the top block or the air above it; walking
		// down off any air makes this right either way.
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
		int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
		top = settle(blocks, pos, x, top, z, minY);
		ground = settle(blocks, pos, x, ground, z, minY);
		BlockState state = blocks.getBlockState(pos.set(x, ground, z));
		// A trunk blocks motion, so the "ground" under a tree is its top: walk down to the dirt.
		int guard = 0;
		while ((state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) && ground > minY && guard++ < MAX_TREE_HEIGHT) {
			ground--;
			state = blocks.getBlockState(pos.set(x, ground, z));
		}
		int canopy = 0;
		if (top > ground) {
			BlockState topState = blocks.getBlockState(pos.set(x, top, z));
			if (topState.is(BlockTags.LEAVES) || topState.is(BlockTags.LOGS)) {
				canopy = top - ground;
			}
		}

		FluidState fluid = state.getFluidState();
		int relY = ground - referenceY;
		if (fluid.is(FluidTags.WATER)) {
			int depth = 0;
			while (depth < MAX_WATER_DEPTH && ground - depth > minY
					&& blocks.getBlockState(pos.set(x, ground - depth, z)).getFluidState().is(FluidTags.WATER)) {
				depth++;
			}
			return new Sample(dx, dz, relY, Surface.WATER, canopy, depth, null);
		}
		if (fluid.is(FluidTags.LAVA)) {
			return new Sample(dx, dz, relY, Surface.LAVA, canopy, 0, null);
		}
		return classify(state, dx, dz, relY, canopy);
	}

	/** Steps down from {@code y} through air to the first non-air block, whichever convention the heightmap used. */
	private static int settle(FastBlockAccess blocks, BlockPos.MutableBlockPos pos, int x, int y, int z, int minY) {
		while (y > minY && blocks.getBlockState(pos.set(x, y, z)).isAir()) {
			y--;
		}
		return y;
	}

	private static Sample classify(BlockState state, int dx, int dz, int relY, int canopy) {
		Surface surface;
		String detail = null;
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)) {
			surface = Surface.GRASS;
		} else if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
			surface = Surface.SNOW;
		} else if (state.is(BlockTags.ICE)) {
			surface = Surface.ICE;
		} else if (state.is(BlockTags.SAND)) {
			surface = Surface.SAND;
		} else if (state.is(BlockTags.DIRT)) {
			surface = Surface.DIRT;
		} else if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE)) {
			surface = Surface.STONE;
		} else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.WOODEN_STAIRS)
				|| state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)
				|| state.is(Blocks.BRICKS) || state.is(Blocks.GLASS) || state.is(Blocks.COBBLESTONE_STAIRS)
				|| state.is(Blocks.COBBLESTONE_SLAB) || state.is(Blocks.TORCH)) {
			surface = Surface.BUILT;
		} else {
			surface = Surface.OTHER;
			detail = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		}
		return new Sample(dx, dz, relY, surface, canopy, 0, detail);
	}

	private static boolean isFalling(BlockState state) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() && fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
	}

	/**
	 * Falling water and lava within range. Most chunk sections hold none, and a section can say so
	 * from its palette without any of its blocks being read, so this stays cheap even across a
	 * whole render distance.
	 */
	private static List<Falling> findFalling(FastBlockAccess blocks, BlockPos origin, int range) {
		Set<Long> water = new HashSet<>();
		Set<Long> lava = new HashSet<>();
		int minChunkX = (origin.getX() - range) >> 4;
		int maxChunkX = (origin.getX() + range) >> 4;
		int minChunkZ = (origin.getZ() - range) >> 4;
		int maxChunkZ = (origin.getZ() + range) >> 4;
		for (int cx = minChunkX; cx <= maxChunkX && water.size() + lava.size() < MAX_FALLING_CELLS; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				ChunkAccess chunk = blocks.chunkAt(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int index = 0; index < sections.length; index++) {
					LevelChunkSection section = sections[index];
					if (section.hasOnlyAir() || !section.maybeHas(WorldSnapshotBuilder::isFalling)) {
						continue;
					}
					int baseY = (chunk.getMinSectionY() + index) << 4;
					for (int ly = 0; ly < 16; ly++) {
						for (int lz = 0; lz < 16; lz++) {
							for (int lx = 0; lx < 16; lx++) {
								BlockState state = section.getBlockState(lx, ly, lz);
								if (!isFalling(state)) {
									continue;
								}
								int wx = (cx << 4) + lx;
								int wz = (cz << 4) + lz;
								int ddx = wx - origin.getX();
								int ddz = wz - origin.getZ();
								if (ddx * ddx + ddz * ddz > range * range) {
									continue;
								}
								long packed = BlockPos.asLong(wx, baseY + ly, wz);
								FluidState fluid = state.getFluidState();
								if (fluid.is(FluidTags.LAVA)) {
									lava.add(packed);
								} else if (fluid.is(FluidTags.WATER)) {
									water.add(packed);
								}
							}
						}
					}
				}
			}
		}
		List<Falling> result = new ArrayList<>();
		cluster(water, false, origin, result);
		cluster(lava, true, origin, result);
		return result;
	}

	/**
	 * Keeps only falling liquid that reaches the surface. The loaded chunks are full of buried
	 * aquifers and lava pockets, which are not something to describe when standing outdoors -
	 * a fall that matters tops out about as high as the ground sampled at that spot.
	 */
	private static List<Falling> visibleFalling(List<Falling> falling, List<Sample> samples, int step) {
		Map<Long, Integer> surface = new HashMap<>();
		for (Sample s : samples) {
			surface.put(((long) Math.floorDiv(s.dx(), step) << 32) ^ (Math.floorDiv(s.dz(), step) & 0xffffffffL), s.relY());
		}
		List<Falling> visible = new ArrayList<>();
		for (Falling f : falling) {
			Integer ground = surface.get(((long) Math.round((float) f.dx() / step) << 32)
					^ (Math.round((float) f.dz() / step) & 0xffffffffL));
			if (ground != null && f.maxRelY() >= ground - BURIED_FALL_MARGIN) {
				visible.add(f);
			}
		}
		return visible;
	}

	/** Groups touching cells into bodies, keeping the ones tall enough to be a fall rather than a trickle. */
	private static void cluster(Set<Long> cells, boolean lava, BlockPos origin, List<Falling> out) {
		Set<Long> remaining = new HashSet<>(cells);
		while (!remaining.isEmpty()) {
			long start = remaining.iterator().next();
			remaining.remove(start);
			ArrayDeque<Long> queue = new ArrayDeque<>();
			queue.add(start);
			int count = 0;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			double sumX = 0;
			double sumZ = 0;
			while (!queue.isEmpty()) {
				long packed = queue.poll();
				int x = BlockPos.getX(packed);
				int y = BlockPos.getY(packed);
				int z = BlockPos.getZ(packed);
				count++;
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				sumX += x;
				sumZ += z;
				for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
					long next = BlockPos.asLong(x + d[0], y + d[1], z + d[2]);
					if (remaining.remove(next)) {
						queue.add(next);
					}
				}
			}
			if (maxY - minY + 1 >= MIN_FALL_HEIGHT) {
				out.add(new Falling(lava, (int) Math.round(sumX / count - origin.getX()),
						(int) Math.round(sumZ / count - origin.getZ()),
						minY - origin.getY(), maxY - origin.getY(), count));
			}
		}
	}

	private static List<Ray> castRays(Level level, LocalPlayer player, int rayRange) {
		List<Ray> rays = new ArrayList<>();
		Vec3 eye = player.getEyePosition();
		for (int compass = 0; compass < 360; compass += AZIMUTH_STEP) {
			for (int elevation : ELEVATIONS) {
				double az = Math.toRadians(compass);
				double el = Math.toRadians(elevation);
				// Compass 0 is north (-z), 90 is east (+x).
				Vec3 direction = new Vec3(Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el));
				BlockHitResult hit = level.clip(new ClipContext(
						eye, eye.add(direction.scale(rayRange)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
				if (hit.getType() == HitResult.Type.MISS) {
					rays.add(new Ray(compass, elevation, -1, null));
				} else {
					BlockState state = level.getBlockState(hit.getBlockPos());
					rays.add(new Ray(compass, elevation, eye.distanceTo(hit.getLocation()),
							BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()));
				}
			}
		}
		return rays;
	}

	private static String timeOfDay(Level level) {
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) {
			return "no day or night here";
		}
		long ticks = Math.floorMod(level.getDefaultClockTime(), 24000L);
		if (ticks < 1000) {
			return "sunrise";
		} else if (ticks < 6000) {
			return "morning";
		} else if (ticks < 9000) {
			return "midday";
		} else if (ticks < 12000) {
			return "afternoon";
		} else if (ticks < 13000) {
			return "sunset";
		} else if (ticks < 18000) {
			return "night";
		} else {
			return "late night";
		}
	}
}
