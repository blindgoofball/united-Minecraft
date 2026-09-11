package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
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
 * net.minecraft.world.level.saveddata.maps.MapDecorationType#explorationMapElement()} sounds like
 * it should mean exactly that, but bytecode inspection of {@link MapDecorationTypes}'s own
 * registration calls shows the opposite of what the name suggests: it's {@code true} for the
 * structure-icon types an explorer map generates (woodland mansion, ocean monument, the
 * village/temple/hut/trial chambers family) but {@code false} for {@code RED_X} - the actual "X
 * marks the spot" icon real buried treasure maps use (confirmed against vanilla's own loot tables
 * - {@code chests/shipwreck_map.json} and both {@code underwater_ruin_*.json}, each configuring
 * their {@code minecraft:exploration_map} loot function with {@code "decoration":
 * "minecraft:red_x"} - {@code TARGET_X}/{@code TARGET_POINT} are registered decoration types but,
 * per that same check, unused by any vanilla loot table in this version). So both signals are
 * needed: {@code explorationMapElement()} for structure-style explorer maps, plus an explicit
 * check for {@code RED_X} (and {@code TARGET_X}/{@code TARGET_POINT}, kept in case a future
 * version or a mod ever uses them the same way) for treasure maps - see {@link
 * #isNavigableTarget}. Everything else (banners, the player's own position dot, off-map/off-limits
 * arrows) is deliberately excluded - none of those are a real generated destination.
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
 */
public final class MapBeaconController {
	private static final double CHIME_FIXED_OFFSET_BLOCKS = 2.0;
	private static final int CHIME_INTERVAL_FAR_TICKS = 40;
	private static final int CHIME_INTERVAL_NEAR_TICKS = 10;
	// Once arrived, a slow idle pulse rather than silence - explorer-map imprecision (and the
	// total lack of a Y coordinate) means "arrived" is only ever approximate, so the final on-foot
	// search still benefits from an occasional nudge.
	private static final int CHIME_INTERVAL_ARRIVED_TICKS = 100;
	private static final double NEAR_DISTANCE_THRESHOLD_BLOCKS = 50.0;
	private static final double ARRIVAL_RADIUS_BLOCKS = 16.0;
	private static final float PITCH_FAR = 0.8f;
	private static final float PITCH_NEAR = 1.8f;
	private static final int NARRATION_INTERVAL_TICKS = 100;

	// The world position of the target currently being tracked - null whenever nothing valid is
	// held (no map, no qualifying decoration, or the map's own dimension doesn't match the
	// player's). Re-arms every timer/flag below the moment this changes, so switching to a
	// different map's target starts fresh rather than inheriting stale state.
	private static Vec3 currentTarget;
	// Tracks which specific (would-be) target position the wrong-dimension narration was already
	// given for, so it fires once per distinct map/target rather than once per tick for as long
	// as that same map stays in hand.
	private static Vec3 lastWrongDimensionTarget;
	private static boolean hasAnnouncedArrival;
	private static int chimeTicks;
	private static int narrationTicks;

	private MapBeaconController() {
	}

	public static void reset() {
		currentTarget = null;
		lastWrongDimensionTarget = null;
		hasAnnouncedArrival = false;
		chimeTicks = 0;
		narrationTicks = 0;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		if (!UnitedMinecraftConfig.get().mapBeaconEnabled) {
			reset();
			return;
		}

		InteractionHand hand = mapHand(player);
		if (hand == null) {
			// No qualifying item in hand at all - stay completely silent rather than narrating
			// "not holding a map" on every tick, and re-arm cleanly for whatever's picked up next.
			reset();
			return;
		}

		Target target = resolveTarget(player, player.getItemInHand(hand));
		if (target == null) {
			// A real map, but nothing on it counts as a navigable target (blank, still being
			// filled in, or only carries markers this feature doesn't care about) - silent, same
			// reasoning as above.
			reset();
			return;
		}

		if (target.wrongDimension()) {
			currentTarget = null;
			if (!target.pos().equals(lastWrongDimensionTarget)) {
				lastWrongDimensionTarget = target.pos();
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.map_beacon_wrong_dimension"));
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

		double distance = horizontalDistance(player.position(), currentTarget);
		if (!hasAnnouncedArrival && distance <= ARRIVAL_RADIUS_BLOCKS) {
			hasAnnouncedArrival = true;
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.map_beacon_arrived"));
		}

		if (chimeTicks <= 0) {
			playChime(client, player, currentTarget, distance);
			chimeTicks = chimeIntervalTicksFor(distance, hasAnnouncedArrival);
		} else {
			chimeTicks--;
		}

		if (narrationTicks <= 0) {
			narrateBearing(client, player, currentTarget, distance);
			narrationTicks = NARRATION_INTERVAL_TICKS;
		} else {
			narrationTicks--;
		}
	}

	/** Whichever hand holds a map item, main hand first - null if neither does. Mirrors {@link BuildModeController#bucketHand}. */
	private static InteractionHand mapHand(LocalPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).getItem() instanceof MapItem) {
				return hand;
			}
		}
		return null;
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
	private static Target resolveTarget(LocalPlayer player, ItemStack stack) {
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
		return new Target(new Vec3(nearest.x(), 0, nearest.z()), wrongDimension);
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
		return entry.type().value().explorationMapElement()
				|| entry.type().equals(MapDecorationTypes.RED_X)
				|| entry.type().equals(MapDecorationTypes.TARGET_X)
				|| entry.type().equals(MapDecorationTypes.TARGET_POINT);
	}

	private static double horizontalDistance(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** 0 at/beyond {@link #NEAR_DISTANCE_THRESHOLD_BLOCKS}, 1 at/inside {@link #ARRIVAL_RADIUS_BLOCKS}, linear between. */
	private static double proximityFactor(double distance) {
		if (distance <= ARRIVAL_RADIUS_BLOCKS) {
			return 1.0;
		}
		if (distance >= NEAR_DISTANCE_THRESHOLD_BLOCKS) {
			return 0.0;
		}
		return (NEAR_DISTANCE_THRESHOLD_BLOCKS - distance) / (NEAR_DISTANCE_THRESHOLD_BLOCKS - ARRIVAL_RADIUS_BLOCKS);
	}

	private static float pitchFor(double distance) {
		double t = proximityFactor(distance);
		return (float) (PITCH_FAR + (PITCH_NEAR - PITCH_FAR) * t);
	}

	private static int chimeIntervalTicksFor(double distance, boolean arrived) {
		if (arrived) {
			return CHIME_INTERVAL_ARRIVED_TICKS;
		}
		double t = proximityFactor(distance);
		return (int) Math.round(CHIME_INTERVAL_FAR_TICKS + (CHIME_INTERVAL_NEAR_TICKS - CHIME_INTERVAL_FAR_TICKS) * t);
	}

	/**
	 * Places a chime {@link #CHIME_FIXED_OFFSET_BLOCKS} from the player along the real direction
	 * to {@code target} - not at the target's real (possibly huge) distance, see the class doc -
	 * so Minecraft's own positional audio panning does the triangulation work. Unlike {@link
	 * NavRadarController#playCue}, which only ever has a discrete probe bearing to synthesize a
	 * direction vector from, this already has the real target vector on hand, so it's normalized
	 * directly rather than round-tripped through an angle.
	 */
	private static void playChime(Minecraft client, LocalPlayer player, Vec3 target, double distance) {
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
				SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0f, pitchFor(distance), random, x, player.getY(), z));
	}

	private static void narrateBearing(Minecraft client, LocalPlayer player, Vec3 target, double distance) {
		Component compass = CameraUtil.compassDirectionTo(player.position(), target);
		client.getNarrator().saySystemNow(Component.translatable(
				"united_minecraft.narrate.map_beacon_bearing", compass, Math.round(distance)));
	}

	/** {@code wrongDimension} is {@code true} when {@code pos} was resolved from a map whose own dimension isn't the player's current one. */
	private record Target(Vec3 pos, boolean wrongDimension) {
	}
}
