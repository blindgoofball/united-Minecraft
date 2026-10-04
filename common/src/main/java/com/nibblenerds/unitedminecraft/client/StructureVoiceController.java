package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.nibblenerds.unitedminecraft.structure.StructuresNearbyPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Structure voices: when a village, ocean monument, ruined portal or any other generated
 * structure comes within the Scanner's range ({@link UnitedMinecraftConfig#scannerRange}), its name is
 * spoken once from its direction - walk past a village on your left and you hear "Village" on
 * your left. The distance is measured to the structure's nearest piece in 3D, so a buried
 * ancient city is only announced once you're actually near it.
 *
 * <p>Direction is deliberately simple, like a screen rather than 3D audio, and fixed for the
 * whole word: left/right is stereo balance (-1 fully left at 90 degrees or more to that side,
 * +1 fully right), up/down relative to where you're looking is pitch (higher above, lower
 * below, up to {@link #PITCH_SEMITONES} at 45 degrees), and behind you is muffled and a little
 * quieter, since balance alone can't tell front from back.
 *
 * <p>Where structures are comes from the server ({@link StructuresNearbyPayload}), so this works
 * in single player and on servers that also run United Minecraft; elsewhere nothing is announced.
 * The same list backs the Scanner's Structures category ({@link #nearby}), for browsing them on
 * demand. The voice itself is the system's own text-to-speech, rendered
 * by {@link StructureVoiceAudio}; if that can't render audio here, names are narrated through the
 * screen reader with their direction instead.
 *
 * <p>Announced once per visit: a structure is re-announced only after moving {@link
 * #REARM_MARGIN} blocks beyond the radius, and not within {@link #COOLDOWN_TICKS} of last time,
 * so walking along the edge of a village doesn't keep repeating it.
 */
public final class StructureVoiceController {
	private static final double REARM_MARGIN = 16.0;
	private static final int COOLDOWN_TICKS = 20 * 30;
	/** A structure the server stopped reporting (out of its range) is forgotten after this long. */
	private static final int FORGET_TICKS = 20 * 10;
	private static final int GAP_TICKS = 6;
	private static final int NARRATION_GAP_TICKS = 30;
	/** Angle to the side at which the voice is fully in one ear. */
	private static final double FULL_PAN_DEGREES = 90.0;
	/** Angle above or below your view at which the pitch change is greatest. */
	private static final double FULL_PITCH_DEGREES = 45.0;
	private static final double PITCH_SEMITONES = 5.0;
	/** Something directly behind is spoken at this fraction of the volume it would have in front. */
	private static final float BEHIND_GAIN = 0.75f;
	/** Within this, you're standing in it and the voice is centred rather than pointing somewhere. */
	private static final double INSIDE_DISTANCE = 1.0;
	/** The furthest structure in range is spoken at this fraction of the volume setting, the nearest at full. */
	private static final float FAR_GAIN = 0.55f;

	private static List<StructuresNearbyPayload.Entry> latest = List.of();
	private static ResourceKey<Level> dimension;
	private static final Map<Key, Tracked> tracked = new HashMap<>();
	private static final ArrayDeque<Key> queue = new ArrayDeque<>();
	private static long ticks;
	private static long busyUntil;

	private StructureVoiceController() {
	}

	private record Key(Identifier structure, long start) {
	}

	private static final class Tracked {
		StructuresNearbyPayload.Entry entry;
		long lastSeen;
		long lastAnnounced = Long.MIN_VALUE / 2;
		/** Set once announced (or skipped for cooldown) this visit; cleared on moving well out of range. */
		boolean announced;
	}

	/** The server's latest list - called on the client thread by the loader's payload handler, via {@link ClientHooks}. */
	public static void onStructuresNearby(StructuresNearbyPayload payload) {
		latest = payload.entries();
	}

	/** Forgets everything - used when leaving a world. */
	public static void reset() {
		latest = List.of();
		dimension = null;
		tracked.clear();
		queue.clear();
		busyUntil = 0;
	}

	/**
	 * @param active false while a screen is open - announcements wait rather than being dropped
	 */
	public static void tick(Minecraft client, LocalPlayer player, boolean active) {
		ticks++;
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		ResourceKey<Level> current = player.level().dimension();
		if (!current.equals(dimension)) {
			// Starts are only unique within a dimension, and the last dimension's list is stale.
			dimension = current;
			latest = List.of();
			tracked.clear();
			queue.clear();
		}
		if (!config.structureVoicesEnabled) {
			queue.clear();
			return;
		}

		Vec3 eye = player.getEyePosition();
		// The Scanner's range rather than a setting of its own - its 64-block maximum is also as
		// far as StructureScanner reports.
		double radius = config.scannerRange;
		for (StructuresNearbyPayload.Entry entry : latest) {
			if (config.structureVoiceMuted.contains(entry.structure().toString())) {
				continue;
			}
			Key key = new Key(entry.structure(), entry.start());
			Tracked state = tracked.computeIfAbsent(key, unused -> new Tracked());
			state.entry = entry;
			state.lastSeen = ticks;
			double distance = eye.distanceTo(point(entry));
			if (distance <= radius) {
				if (!state.announced) {
					state.announced = true;
					if (ticks - state.lastAnnounced >= COOLDOWN_TICKS && !queue.contains(key)) {
						state.lastAnnounced = ticks;
						queue.add(key);
					}
				}
			} else if (distance > radius + REARM_MARGIN) {
				state.announced = false;
			}
		}
		tracked.values().removeIf(state -> ticks - state.lastSeen > FORGET_TICKS);

		if (active && player.isAlive() && ticks >= busyUntil) {
			playNext(client, player, config);
		}
	}

	private static void playNext(Minecraft client, LocalPlayer player, UnitedMinecraftConfig config) {
		Key key = queue.poll();
		Tracked state = key == null ? null : tracked.get(key);
		if (state == null) {
			return;
		}
		String name = spokenName(state.entry.structure());
		CompletableFuture<Optional<StructureVoiceAudio.Clip>> clip = StructureVoiceAudio.request(name);
		if (!clip.isDone()) {
			// Only the first time a name is heard - rendering takes milliseconds to a second.
			queue.addFirst(key);
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 point = point(state.entry);
		Optional<StructureVoiceAudio.Clip> audio = clip.isCompletedExceptionally() ? Optional.empty() : clip.join();
		if (audio.isPresent()) {
			Direction direction = direction(player, eye, point);
			double distance = eye.distanceTo(point);
			float nearness = 1.0f - (float) Math.min(1.0, distance / config.scannerRange);
			float volume = config.structureVoiceVolume / 100.0f * (FAR_GAIN + (1.0f - FAR_GAIN) * nearness)
					* (1.0f - (1.0f - BEHIND_GAIN) * direction.behind());
			float pitch = (float) Math.pow(2.0, direction.height() * PITCH_SEMITONES / 12.0);
			client.getSoundManager().play(new StructureVoiceSound(
					StructureVoiceAudio.variant(audio.get(), direction.pan(), direction.behind()), volume, pitch,
					SoundInstance.createUnseededRandom()));
			busyUntil = ticks + (long) Math.ceil(audio.get().durationTicks() / pitch) + GAP_TICKS;
			if (config.structureVoiceNarrate) {
				client.getNarrator().saySystemQueued(describe(name, eye, point));
			}
		} else {
			client.getNarrator().saySystemQueued(describe(name, eye, point));
			busyUntil = ticks + NARRATION_GAP_TICKS;
		}
	}

	/**
	 * A structure the server reported within its range, for the Scanner's Structures category.
	 *
	 * @param point the nearest point of its nearest piece
	 */
	public record NearbyStructure(Identifier structure, String name, Vec3 point) {
	}

	/**
	 * Every structure the server last reported within the Scanner's range, nearest first, leaving
	 * out muted ones - whether or not structure voices are switched on, so the Scanner can still
	 * browse them with the voices off. Empty in a dimension the list hasn't caught up with yet.
	 */
	public static List<NearbyStructure> nearby(LocalPlayer player) {
		if (!player.level().dimension().equals(dimension) && dimension != null) {
			return List.of();
		}
		UnitedMinecraftConfig config = UnitedMinecraftConfig.get();
		Vec3 eye = player.getEyePosition();
		List<NearbyStructure> result = new ArrayList<>();
		for (StructuresNearbyPayload.Entry entry : latest) {
			if (!config.structureVoiceMuted.contains(entry.structure().toString())
					&& eye.distanceTo(point(entry)) <= config.scannerRange) {
				result.add(new NearbyStructure(entry.structure(), spokenName(entry.structure()), point(entry)));
			}
		}
		return result;
	}

	private static Component describe(String name, Vec3 eye, Vec3 point) {
		double distance = eye.distanceTo(point);
		if (distance < INSIDE_DISTANCE) {
			return Component.translatable("united_minecraft.narrate.structure_here", name);
		}
		return Component.translatable("united_minecraft.narrate.structure_nearby", name,
				(int) Math.round(distance), CameraUtil.fullDirectionTo(eye, point));
	}

	/**
	 * @param pan -1 fully left to +1 fully right
	 * @param height -1 well below where you're looking to +1 well above
	 * @param behind 0 anywhere in front of you to 1 directly behind
	 */
	private record Direction(float pan, float height, float behind) {
	}

	private static Direction direction(LocalPlayer player, Vec3 eye, Vec3 point) {
		Vec3 offset = point.subtract(eye);
		if (offset.length() < INSIDE_DISTANCE) {
			return new Direction(0.0f, 0.0f, 0.0f);
		}
		double horizontal = Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z());
		// Minecraft yaw 0 faces south (+Z), and your right is then west (-X).
		double yaw = Math.toRadians(player.getYRot());
		double ahead = offset.x() * -Math.sin(yaw) + offset.z() * Math.cos(yaw);
		double right = offset.x() * -Math.cos(yaw) + offset.z() * -Math.sin(yaw);
		// Straight up or down there's no meaningful side, so keep it centred.
		double side = horizontal < INSIDE_DISTANCE ? 0.0 : Math.toDegrees(Math.atan2(right, ahead));
		float pan = (float) Math.clamp(side / FULL_PAN_DEGREES, -1.0, 1.0);
		float behind = (float) Math.clamp((Math.abs(side) - 90.0) / 90.0, 0.0, 1.0);
		// Pitch (xRot) is positive looking down, so the view's own elevation is -xRot.
		double elevation = Math.toDegrees(Math.atan2(offset.y(), horizontal)) + player.getXRot();
		float height = (float) Math.clamp(elevation / FULL_PITCH_DEGREES, -1.0, 1.0);
		return new Direction(pan, height, behind);
	}

	private static Vec3 point(StructuresNearbyPayload.Entry entry) {
		return new Vec3(entry.x(), entry.y(), entry.z());
	}

	/**
	 * "Village" for every {@code minecraft:village_*}, and so on - from the language file where
	 * there's an entry ({@code united_minecraft.structure.<namespace>.<path>}), otherwise the
	 * id's own words, so a modded structure is still spoken as something sensible.
	 */
	static String spokenName(Identifier structure) {
		String key = "united_minecraft.structure." + structure.getNamespace() + "." + structure.getPath().replace('/', '.');
		Language language = Language.getInstance();
		if (language.has(key)) {
			return language.getOrDefault(key);
		}
		String path = structure.getPath();
		String words = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').trim();
		return words.isEmpty() ? path : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
	}
}
