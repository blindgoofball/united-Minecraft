package com.nibblenerds.unitedminecraft.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Makes a thrown Eye of Ender usable without seeing it. An eye flies for about four seconds
 * toward the nearest stronghold and then either drops as an item or shatters, which is
 * invisible to a player who can't watch it - so the flight is followed and narrated instead:
 * the heading once it is clear, then how it ended. The heading is kept afterwards so {@link
 * ClientKeyBindings#FACE_EYE_OF_ENDER} can turn the player to it at any point later.
 *
 * <p>The client never learns where the stronghold is, only what the eye does, so everything
 * here is inferred from its movement:
 * <ul>
 * <li>Its horizontal heading over the first few blocks points at the stronghold.
 * <li>An eye that is far from the stronghold is sent 12 blocks toward it and 8 up, so it always
 * travels the full 12 blocks. One that is nearer goes straight to the stronghold instead, so
 * travelling clearly less than that means the stronghold is about that far away - the "close"
 * report.
 * <li>The server removes it with no client-visible reason, and either leaves an item where it
 * was or shatters it, so the two are told apart by whether an Eye of Ender item turns up
 * beside its last position in the next few ticks.
 * </ul>
 */
public final class EyeOfEnderController {
	// A freshly thrown eye appears right at the thrower, which is how the player's own eyes are
	// told apart from anyone else's nearby.
	private static final double THROW_DISCOVERY_RANGE = 4.0;
	// Client-side age, in ticks, under which an eye still counts as newly thrown.
	private static final int NEW_EYE_MAX_AGE = 5;
	// Horizontal blocks flown before the heading is considered clear enough to announce.
	private static final double HEADING_DISTANCE = 3.0;
	// Shorter than this and there is no usable heading to remember at all.
	private static final double MIN_BEARING_DISTANCE = 1.0;
	// A far eye flies the full 12 blocks; allow a margin for the flight timing out short of that.
	private static final double CLOSE_TRAVEL_THRESHOLD = 10.0;
	// Ticks to wait after an eye vanishes for its dropped item to show up before calling it shattered.
	private static final int OUTCOME_WAIT_TICKS = 3;
	private static final double ITEM_SEARCH_RADIUS = 4.0;

	private static final Map<Integer, Tracked> watched = new HashMap<>();
	private static final Map<Integer, Pending> pending = new HashMap<>();
	private static int nextPendingId;

	// Horizontal direction (start to last position) of the most recent eye, null until one has flown.
	private static Vec3 lastDirection;

	private static final class Tracked {
		Vec3 start;
		Vec3 lastPos;
		boolean headingAnnounced;
	}

	private static final class Pending {
		Vec3 start;
		Vec3 lastPos;
		int ticksLeft = OUTCOME_WAIT_TICKS;
	}

	private EyeOfEnderController() {
	}

	public static void reset() {
		watched.clear();
		pending.clear();
		lastDirection = null;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		AABB near = player.getBoundingBox().inflate(THROW_DISCOVERY_RANGE);
		for (Entity entity : player.level().getEntities(player, near,
				e -> e instanceof EyeOfEnder && e.tickCount <= NEW_EYE_MAX_AGE)) {
			watched.computeIfAbsent(entity.getId(), id -> {
				Tracked tracked = new Tracked();
				tracked.start = entity.position();
				tracked.lastPos = tracked.start;
				return tracked;
			});
		}

		Iterator<Map.Entry<Integer, Tracked>> iterator = watched.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, Tracked> entry = iterator.next();
			Tracked tracked = entry.getValue();
			Entity entity = player.level().getEntity(entry.getKey());
			if (entity == null) {
				Pending ended = new Pending();
				ended.start = tracked.start;
				ended.lastPos = tracked.lastPos;
				pending.put(nextPendingId++, ended);
				iterator.remove();
				continue;
			}
			tracked.lastPos = entity.position();
			if (!tracked.headingAnnounced && horizontalDistance(tracked.start, tracked.lastPos) >= HEADING_DISTANCE) {
				tracked.headingAnnounced = true;
				rememberDirection(tracked.start, tracked.lastPos);
				announceHeading(client, player, tracked);
			}
		}

		Iterator<Pending> outcomes = pending.values().iterator();
		while (outcomes.hasNext()) {
			Pending ended = outcomes.next();
			boolean dropped = droppedItemNear(player, ended.lastPos);
			if (dropped || --ended.ticksLeft <= 0) {
				resolve(client, player, ended, dropped);
				outcomes.remove();
			}
		}
	}

	private static void announceHeading(Minecraft client, LocalPlayer player, Tracked tracked) {
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.eye_heading",
				CameraUtil.compassDirectionTo(tracked.start, tracked.lastPos)));
		// Played at the eye itself, so the ping arrives from the direction it is flying.
		playAt(client, player, SoundEvents.NOTE_BLOCK_FLUTE.value(), tracked.lastPos, 1.0f, 1.2f);
	}

	private static void resolve(Minecraft client, LocalPlayer player, Pending ended, boolean dropped) {
		rememberDirection(ended.start, ended.lastPos);
		double travelled = horizontalDistance(ended.start, ended.lastPos);

		Component message = Component.translatable(dropped
				? "united_minecraft.narrate.eye_dropped" : "united_minecraft.narrate.eye_shattered");
		if (travelled < CLOSE_TRAVEL_THRESHOLD && travelled >= MIN_BEARING_DISTANCE) {
			message = message.copy().append(Component.literal(" ")).append(Component.translatable(
					"united_minecraft.narrate.eye_close", Math.round(travelled),
					CameraUtil.compassDirectionTo(ended.start, ended.lastPos)));
			playAt(client, player, SoundEvents.NOTE_BLOCK_COW_BELL.value(), player.position(), 1.0f, 2.0f);
		}
		client.getNarrator().saySystemNow(message);
		playAt(client, player, dropped ? SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value() : SoundEvents.NOTE_BLOCK_SNARE.value(),
				ended.lastPos, 1.0f, dropped ? 1.4f : 1.0f);
	}

	private static boolean droppedItemNear(LocalPlayer player, Vec3 pos) {
		AABB box = new AABB(pos, pos).inflate(ITEM_SEARCH_RADIUS);
		return !player.level().getEntitiesOfClass(ItemEntity.class, box,
				item -> item.getItem().is(Items.ENDER_EYE)).isEmpty();
	}

	private static void rememberDirection(Vec3 start, Vec3 end) {
		Vec3 flat = new Vec3(end.x() - start.x(), 0.0, end.z() - start.z());
		if (flat.length() >= MIN_BEARING_DISTANCE) {
			lastDirection = flat;
		}
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.hypot(b.x() - a.x(), b.z() - a.z());
	}

	/** {@link ClientKeyBindings#FACE_EYE_OF_ENDER} - turns the player to the last eye's heading. */
	public static void faceLastBearing(Minecraft client, LocalPlayer player) {
		if (lastDirection == null) {
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.eye_none"));
			return;
		}
		// Same yaw formula CameraUtil#aimAt uses (Minecraft yaw 0 = south).
		float yaw = (float) Math.toDegrees(Math.atan2(-lastDirection.x(), lastDirection.z()));
		player.setYRot(yaw);
		player.setOldRot();
		player.setYHeadRot(yaw);
		Vec3 here = player.position();
		client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.eye_facing",
				CameraUtil.compassDirectionTo(here, here.add(lastDirection))));
	}

	private static void playAt(Minecraft client, LocalPlayer player, SoundEvent sound, Vec3 pos, float volume, float pitch) {
		if (!UnitedMinecraftConfig.get().eyeOfEnderCuesEnabled) {
			return;
		}
		client.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.MASTER, volume, pitch,
				player.getRandom(), pos.x(), pos.y(), pos.z()));
	}
}
