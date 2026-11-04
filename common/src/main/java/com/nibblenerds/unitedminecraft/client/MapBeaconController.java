package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import java.util.Set;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Turns a treasure/explorer map's target - normally just a visual icon on a texture the player
 * has to look at - into something a blind/low-vision player can navigate to by ear: a short
 * chime toward the target's real bearing on a repeating interval, plus periodic spoken
 * bearing/distance. Purely ambient and config-gated (see {@link UnitedMinecraftConfig#mapBeaconEnabled})
 * rather than a manual toggle like {@link NavRadarController} - holding a qualifying map is
 * already an unambiguous "on" signal, so putting it away is the natural "off" one; no keybind.
 *
 * <p>Finding the target is plain vanilla API, no mixins - but it deliberately does <em>not</em>
 * read {@link MapItemSavedData#getDecorations()}. That map-local decoration list only ever
 * carries pixel coordinates relative to the map's own {@code centerX}/{@code centerZ}, and on the
 * client those two fields are always {@code 0} - bytecode inspection of {@code
 * MapItemSavedData.createForClient} (the factory {@code ClientPacketListener.handleMapItemData}
 * uses whenever it learns about a {@code MapId} it hasn't seen before) shows it's only ever
 * constructed with a hardcoded {@code centerX=0, centerZ=0}; the real values only ever exist
 * server-side and are never part of {@code ClientboundMapItemDataPacket}'s payload (which carries
 * just {@code mapId}, {@code scale}, {@code locked}, {@code decorations}, and a color patch - no
 * center at all). Converting a decoration's map-local coordinate through a client-side {@code
 * MapItemSavedData} therefore always resolves relative to world {@code (0, 0)} instead of the
 * map's real center - silently wrong every time, not just sometimes, which is exactly what an
 * earlier version of this class did.
 *
 * <p>The fix: read {@link ItemStack#get} for {@link DataComponents#MAP_DECORATIONS} directly off
 * the held stack instead. This is a genuinely different, item-level component - {@link
 * MapDecorations.Entry#x()}/{@link MapDecorations.Entry#z() z()} are already real, absolute world
 * coordinates (doubles, no pixel math needed at all), set once by {@code
 * MapItemSavedData.addTargetDecoration} at the moment a treasure/explorer map is created (loot
 * generation, or a cartographer trade) and left on the item from then on - ordinary item-component
 * sync means it's available immediately, with none of the "wait a tick or two for the per-{@code
 * MapId} broadcast" caveat the old {@code MapItemSavedData}-based approach had either.
 *
 * <p>Which entries actually count as "a real target" still needs the same care: {@link
 * MapDecorationType} no longer carries a boolean flag for this (its record was trimmed down to
 * just {@code assetId}/{@code showOnItemFrame}/{@code trackCount} as of Minecraft 26.3), so the
 * structure-icon types an explorer map generates (woodland mansion, ocean monument, the
 * village/temple/hut/trial chambers family) are enumerated explicitly in {@link
 * #STRUCTURE_DECORATION_TYPES} instead, plus an explicit check for {@code RED_X} - the actual "X
 * marks the spot" icon real buried treasure maps use (confirmed against vanilla's own loot tables
 * - {@code chests/shipwreck_map.json} and both {@code underwater_ruin_*.json}, each configuring
 * their {@code minecraft:exploration_map} loot function with {@code "decoration":
 * "minecraft:red_x"} - {@code TARGET_X}/{@code TARGET_POINT} are registered decoration types but
 * unused by any vanilla loot table in this version, kept here in case a future version or a mod
 * ever uses them the same way) - see {@link #isNavigableTarget}. Everything else (banners, the
 * player's own position dot, off-map/off-limits arrows) is deliberately excluded - none of those
 * are a real generated destination.
 *
 * <p>A map only ever encodes X/Z - there's no vertical component at all, so every distance/bearing
 * computation here is strictly horizontal, and "arrival" can only ever mean "close on the map
 * grid", not "standing on the exact spot" (worse for a mansion/monument's pixel-rounded position
 * than for treasure's precise one). Once within {@link #ARRIVAL_RADIUS_BLOCKS}, the chime downgrades
 * to a slow idle pulse instead of stopping outright, handing the final on-foot search off to the
 * Scanner/Mining Radar rather than going silent right when a few blocks of imprecision matter most.
 *
 * <p>The chime is deliberately placed a fixed short distance from the player (see {@link
 * #CHIME_FIXED_OFFSET_BLOCKS}) along the real bearing to the target, never at the target's real
 * (possibly enormous) distance - Minecraft's own positional audio falloff would make a chime placed
 * hundreds of blocks away inaudible, defeating the entire point. Real distance is instead encoded
 * by pitch and repeat-interval both rising as the player closes in (Geiger-counter style), giving a
 * redundant, always-audible distance cue independent of how far away the target actually is - the
 * same "fixed nearby placement, let Minecraft's own panning do the work" trick {@link
 * NavRadarController#playCue} already uses for its short-range obstacle cues.
 *
 * <p>A held compass works the same way, behind its own setting ({@link
 * UnitedMinecraftConfig#compassBeaconEnabled}): see {@link #resolveCompassTarget} for where each
 * kind points. The cue machinery above is shared; what differs is the arrival radius (a compass's
 * target is exact, a map's is not - {@link BeaconTiming} takes it as a parameter), that a lodestone
 * or a death spot has a height worth saying, and that a compass with nothing to point at, or whose
 * target is in another dimension, is silent as vanilla's spinning needle is.
 */
public final class MapBeaconController {
	private static final double CHIME_FIXED_OFFSET_BLOCKS = 2.0;
	private static final int NARRATION_INTERVAL_TICKS = 100;
	// Explorer-map imprecision (and the total lack of a Y coordinate) means "arrived" at a map's
	// target is only ever approximate. A compass's target is exact - a block - so it counts much
	// closer in.
	private static final double MAP_ARRIVAL_RADIUS_BLOCKS = 16.0;
	private static final double COMPASS_ARRIVAL_RADIUS_BLOCKS = 3.0;
	// A compass target with a real height (a lodestone, where you died) isn't arrived at from far
	// above or below it, whatever the horizontal distance.
	private static final int COMPASS_ARRIVAL_HEIGHT_BLOCKS = 4;
	// A height difference smaller than this isn't worth saying.
	private static final int VERTICAL_NARRATION_MIN_BLOCKS = 2;

	/** The structure-icon decoration types an explorer map generates - see {@link #isNavigableTarget}. */
	private static final Set<Holder<MapDecorationType>> STRUCTURE_DECORATION_TYPES = Set.of(
			MapDecorationTypes.WOODLAND_MANSION, MapDecorationTypes.OCEAN_MONUMENT,
			MapDecorationTypes.DESERT_VILLAGE, MapDecorationTypes.PLAINS_VILLAGE,
			MapDecorationTypes.SAVANNA_VILLAGE, MapDecorationTypes.SNOWY_VILLAGE,
			MapDecorationTypes.TAIGA_VILLAGE, MapDecorationTypes.JUNGLE_TEMPLE,
			MapDecorationTypes.SWAMP_HUT, MapDecorationTypes.TRIAL_CHAMBERS,
			MapDecorationTypes.ABANDONED_CAMP, MapDecorationTypes.ANCIENT_CITY,
			MapDecorationTypes.DESERT_PYRAMID, MapDecorationTypes.MINESHAFT,
			MapDecorationTypes.OCEAN_RUIN_WARM);

	// The world position of the target currently being tracked - null whenever nothing valid is
	// held (no map, no qualifying decoration, or the map's own dimension doesn't match the
	// player's). Re-arms every timer/flag below the moment this changes, so switching to a
	// different map's target starts fresh rather than inheriting stale state.
	private static Vec3 currentTarget;
	// Tracks which specific (would-be) target position the wrong-dimension narration was already
	// given for, so it fires once per distinct map/target rather than once per tick for as long
	// as that same map stays in hand.
	private static Vec3 lastWrongDimensionTarget;
	// Which compass was last told "no target" (a compass with nothing to point at, which spins),
	// so that is said once per compass picked up rather than every tick.
	private static Source lastNoTargetSource;
	private static boolean hasAnnouncedArrival;
	private static int chimeTicks;
	private static int narrationTicks;

	private MapBeaconController() {
	}

	public static void reset() {
		currentTarget = null;
		lastWrongDimensionTarget = null;
		lastNoTargetSource = null;
		hasAnnouncedArrival = false;
		chimeTicks = 0;
		narrationTicks = 0;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		if (!config.mapBeaconEnabled && !config.compassBeaconEnabled) {
			reset();
			return;
		}

		Target target = heldTarget(client, player, config);
		if (target == null) {
			// Nothing qualifying in hand at all, or a map with nothing on it that counts as a
			// navigable target (blank, still being filled in, or only carrying markers this feature
			// doesn't care about) - stay completely silent rather than narrating "not holding a map"
			// on every tick, and re-arm cleanly for whatever's picked up next.
			reset();
			return;
		}

		if (target.pos() == null) {
			// A compass with nothing to point at: it spins, so there's no direction to give.
			currentTarget = null;
			if (target.source() != lastNoTargetSource) {
				lastNoTargetSource = target.source();
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.compass_beacon_no_target"));
			}
			return;
		}
		lastNoTargetSource = null;

		if (target.wrongDimension()) {
			currentTarget = null;
			if (!target.pos().equals(lastWrongDimensionTarget)) {
				lastWrongDimensionTarget = target.pos();
				client.getNarrator().saySystemNow(target.source() == Source.MAP
						? Component.translatable("united_minecraft.narrate.map_beacon_wrong_dimension")
						: Component.translatable("united_minecraft.narrate.compass_beacon_wrong_dimension", target.source().label()));
			}
			return;
		}
		lastWrongDimensionTarget = null;

		if (!target.pos().equals(currentTarget)) {
			currentTarget = target.pos();
			hasAnnouncedArrival = false;
			// Fire both cues immediately on a new target rather than waiting out a full interval.
			chimeTicks = 0;
			narrationTicks = 0;
		}

		double radius = target.source().arrivalRadius();
		double distance = horizontalDistance(player.position(), currentTarget);
		if (!hasAnnouncedArrival && distance <= radius && isLevelWithTarget(player, target)) {
			hasAnnouncedArrival = true;
			client.getNarrator().saySystemQueued(target.source() == Source.MAP
					? Component.translatable("united_minecraft.narrate.map_beacon_arrived")
					: Component.translatable("united_minecraft.narrate.compass_beacon_arrived", target.source().label()));
		}

		if (chimeTicks <= 0) {
			playChime(client, player, currentTarget, distance, radius);
			chimeTicks = BeaconTiming.chimeIntervalTicksFor(distance, radius, hasAnnouncedArrival);
		} else {
			chimeTicks--;
		}

		if (narrationTicks <= 0) {
			narrateBearing(client, player, target, distance);
			narrationTicks = NARRATION_INTERVAL_TICKS;
		} else {
			narrationTicks--;
		}
	}

	/** A target with a real height isn't arrived at from far above or below it; one without (a map, spawn) has no height to compare. */
	private static boolean isLevelWithTarget(LocalPlayer player, Target target) {
		return !target.source().hasHeight()
				|| Math.abs(targetBlockY(target) - player.blockPosition().getY()) <= COMPASS_ARRIVAL_HEIGHT_BLOCKS;
	}

	private static int targetBlockY(Target target) {
		return (int) Math.floor(target.pos().y());
	}

	/**
	 * What the first hand holding something qualifying points at, main hand first - null if neither
	 * does, or a map in hand has nothing on it that counts as a target. A map is only looked at while
	 * Map Beacon is on, and a compass while Compass Beacon is. Mirrors {@link BuildModeController#bucketHand}.
	 */
	private static Target heldTarget(Minecraft client, LocalPlayer player, UnitedMinecraftConfig config) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			Target target = null;
			if (config.mapBeaconEnabled && stack.getItem() instanceof MapItem) {
				target = resolveMapTarget(player, stack);
			} else if (config.compassBeaconEnabled && (stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS))) {
				target = resolveCompassTarget(client, player, stack);
			}
			if (target != null) {
				return target;
			}
		}
		return null;
	}

	/**
	 * Where a compass in hand points, from the same data vanilla draws its needle from: a normal
	 * compass at world spawn (read off the level), a lodestone compass at the position stored on the
	 * item, a recovery compass at the player's last death location. The target's {@code pos} is null
	 * when it has nothing to point at (a lodestone compass whose lodestone is gone, a recovery compass
	 * before you have died) - the needle spins - and {@code wrongDimension} when the target is in
	 * another dimension from the one the player is in, where vanilla's needle spins too. A lodestone
	 * compass is a plain compass carrying a lodestone tracker, the way vanilla's own item model tells
	 * the two apart.
	 */
	private static Target resolveCompassTarget(Minecraft client, LocalPlayer player, ItemStack stack) {
		Source source;
		GlobalPos where;
		if (stack.is(Items.RECOVERY_COMPASS)) {
			source = Source.DEATH;
			where = player.getLastDeathLocation().orElse(null);
		} else {
			LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
			if (tracker != null) {
				source = Source.LODESTONE;
				where = tracker.target().orElse(null);
			} else {
				source = Source.SPAWN;
				where = client.level == null ? null : client.level.getRespawnData().globalPos();
			}
		}
		if (where == null) {
			return new Target(source, null, false);
		}
		return new Target(source, Vec3.atCenterOf(where.pos()), !where.dimension().equals(player.level().dimension()));
	}

	/**
	 * The nearest qualifying decoration's real world position (already absolute - see the class
	 * doc on why this reads {@link DataComponents#MAP_DECORATIONS} off the item rather than the
	 * map's own map-local decoration list), or {@code null} if the stack has no such component at
	 * all (a blank map, or one that's never had a target attached) or nothing on it {@link
	 * #isNavigableTarget} accepts. Multiple qualifying entries (rare) resolve to whichever is
	 * currently nearest, with no narration about the ambiguity - just deterministic nearest-wins.
	 *
	 * <p>{@link MapItemSavedData} is only still consulted for its {@code dimension} field, to
	 * catch a stale map carried into the wrong dimension - a {@code null} result there (no synced
	 * map data yet) is treated as "trust it", since the target itself is already known good
	 * straight from the item component regardless.
	 */
	private static Target resolveMapTarget(LocalPlayer player, ItemStack stack) {
		MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
		if (decorations == null) {
			return null;
		}

		Vec3 playerPos = player.position();
		MapDecorations.Entry nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (MapDecorations.Entry entry : decorations.decorations().values()) {
			if (!isNavigableTarget(entry)) {
				continue;
			}
			double distance = horizontalDistance(playerPos, new Vec3(entry.x(), 0, entry.z()));
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = entry;
			}
		}
		if (nearest == null) {
			return null;
		}

		MapItemSavedData data = MapItem.getSavedData(stack, player.level());
		boolean wrongDimension = data != null && !data.dimension.equals(player.level().dimension());
		return new Target(Source.MAP, new Vec3(nearest.x(), 0, nearest.z()), wrongDimension);
	}

	/**
	 * See the class doc for why this checks both signals: {@code explorationMapElement()} for the
	 * structure-icon types an explorer map generates (mansion, monument, village/temple/hut/trial
	 * chambers), plus an explicit check for {@code RED_X} - the actual "X" decoration real buried
	 * treasure maps use (verified against vanilla's own loot tables, not assumed - see the class
	 * doc) - despite being the archetypal "exploration map target," it's registered with {@code
	 * explorationMapElement=false}. {@code TARGET_X}/{@code TARGET_POINT} are included too on the
	 * off chance a future version or a mod repurposes them the same way, even though no vanilla
	 * loot table currently uses either. Excludes banners, the player's own position dot, and
	 * off-map/off-limits arrows, none of which are a real generated destination.
	 */
	private static boolean isNavigableTarget(MapDecorations.Entry entry) {
		return STRUCTURE_DECORATION_TYPES.contains(entry.type())
				|| entry.type().equals(MapDecorationTypes.RED_X)
				|| entry.type().equals(MapDecorationTypes.TARGET_X)
				|| entry.type().equals(MapDecorationTypes.TARGET_POINT);
	}

	private static double horizontalDistance(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Places a chime {@link #CHIME_FIXED_OFFSET_BLOCKS} from the player along the real direction
	 * to {@code target} - not at the target's real (possibly huge) distance, see the class doc -
	 * so Minecraft's own positional audio panning does the triangulation work. Unlike {@link
	 * NavRadarController#playCue}, which only ever has a discrete probe bearing to synthesize a
	 * direction vector from, this already has the real target vector on hand, so it's normalized
	 * directly rather than round-tripped through an angle.
	 */
	private static void playChime(Minecraft client, LocalPlayer player, Vec3 target, double distance, double arrivalRadius) {
		double dx = target.x() - player.getX();
		double dz = target.z() - player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0e-4) {
			// Standing essentially on top of the target - no meaningful direction to place a cue at.
			return;
		}

		double x = player.getX() + (dx / length) * CHIME_FIXED_OFFSET_BLOCKS;
		double z = player.getZ() + (dz / length) * CHIME_FIXED_OFFSET_BLOCKS;
		RandomSource random = player.getRandom();
		client.getSoundManager().play(new SimpleSoundInstance(
				SoundEvents.BELL_BLOCK, SoundSource.MASTER, CueVolume.scale(1.0f),
				BeaconTiming.pitchFor(distance, arrivalRadius), random, x, player.getY(), z));
	}

	/**
	 * A map reads its bearing and distance. A compass also says what it points at, and - for a
	 * target with a real height, a lodestone or the place you died - how far above or below you it is.
	 */
	private static void narrateBearing(Minecraft client, LocalPlayer player, Target target, double distance) {
		Component direction = CameraUtil.compassDirectionTo(player.position(), target.pos());
		if (target.source() == Source.MAP) {
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.map_beacon_bearing", direction, Math.round(distance)));
			return;
		}
		Component message = Component.translatable("united_minecraft.narrate.compass_beacon_bearing",
				target.source().label(), direction, Math.round(distance));
		if (target.source().hasHeight()) {
			int above = targetBlockY(target) - player.blockPosition().getY();
			if (Math.abs(above) >= VERTICAL_NARRATION_MIN_BLOCKS) {
				message = message.copy().append(Component.literal(", ")).append(Component.translatable(
						above > 0 ? "united_minecraft.narrate.compass_beacon_above" : "united_minecraft.narrate.compass_beacon_below",
						Math.abs(above)));
			}
		}
		client.getNarrator().saySystemNow(message);
	}

	/** What a beacon is pointing at, which sets how close counts as arrived and whether it has a height. */
	private enum Source {
		MAP(MAP_ARRIVAL_RADIUS_BLOCKS, false, null),
		SPAWN(COMPASS_ARRIVAL_RADIUS_BLOCKS, false, "united_minecraft.narrate.compass_target_spawn"),
		LODESTONE(COMPASS_ARRIVAL_RADIUS_BLOCKS, true, "united_minecraft.narrate.compass_target_lodestone"),
		DEATH(COMPASS_ARRIVAL_RADIUS_BLOCKS, true, "united_minecraft.narrate.compass_target_death");

		private final double arrivalRadius;
		private final boolean hasHeight;
		private final String labelKey;

		Source(double arrivalRadius, boolean hasHeight, String labelKey) {
			this.arrivalRadius = arrivalRadius;
			this.hasHeight = hasHeight;
			this.labelKey = labelKey;
		}

		double arrivalRadius() {
			return arrivalRadius;
		}

		/** Whether the target's height is worth comparing with the player's: not a map's (it has none), nor world spawn's (usually unrelated to where you are headed). */
		boolean hasHeight() {
			return hasHeight;
		}

		/** What this is called aloud - compasses only; a map just says its bearing. */
		Component label() {
			return Component.translatable(labelKey);
		}
	}

	/**
	 * {@code pos} is null when a compass has nothing to point at, and {@code wrongDimension} is
	 * {@code true} when it was resolved in a dimension other than the player's current one.
	 */
	private record Target(Source source, Vec3 pos, boolean wrongDimension) {
	}
}
