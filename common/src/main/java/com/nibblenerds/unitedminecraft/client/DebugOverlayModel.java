package com.nibblenerds.unitedminecraft.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nibblenerds.unitedminecraft.client.access.DebugScreenOverlayAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.core.registries.BuiltInClientRegistries;
import net.minecraft.client.gui.components.debug.DebugCustomRenderer;
import net.minecraft.client.gui.components.debug.DebugFact;
import net.minecraft.client.gui.components.debug.DebugGroup;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A live, structured copy of the F3 overlay. Vanilla flattens every entry's output into two
 * lists of strings before drawing, losing which entry (or group) a line came from, so this runs
 * the same enabled entries itself every tick with a displayer that keeps them apart. Each
 * section is one entry or group; its lines are recomputed every tick, so reading a line always
 * gives the current value. An entry that adds no text (the renderer toggles) makes no section.
 */
final class DebugOverlayModel {
	/**
	 * One entry's or group's lines. {@code key} tells sections apart from one tick to the next: the
	 * entry's {@link Identifier} for lines an entry files under itself, or the {@link DebugGroup}.
	 */
	record Section(Object key, Component name, List<String> lines) {
	}

	private static List<Section> sections = List.of();

	private DebugOverlayModel() {
	}

	static List<Section> sections() {
		return sections;
	}

	static boolean isOverlayShown(Minecraft client) {
		return client.getDebugOverlay().showDebugScreen();
	}

	static void reset() {
		sections = List.of();
	}

	static void tick(Minecraft client) {
		if (client.level == null || !isOverlayShown(client)) {
			reset();
			return;
		}
		DebugScreenOverlayAccess access = (DebugScreenOverlayAccess) client.getDebugOverlay();
		Level level = access.unitedMinecraft$getLevel();
		if (level == null) {
			return;
		}

		Map<Object, Collected> collected = new LinkedHashMap<>();
		Collector collector = new Collector(collected);
		for (ResourceKey<DebugScreenEntry> key : client.debugEntries.getCurrentlyEnabled()) {
			DebugScreenEntry entry = BuiltInClientRegistries.DEBUG_SCREEN_ENTRY.getValue(key);
			if (entry == null) {
				continue;
			}
			collector.current = key.identifier();
			entry.display(collector, level, access.unitedMinecraft$getClientChunk(), access.unitedMinecraft$getServerChunk());
		}
		List<Section> built = new ArrayList<>(collected.size());
		collected.forEach((key, group) -> built.add(new Section(key, group.name, List.copyOf(group.lines))));
		sections = List.copyOf(built);
	}

	private record Collected(Component name, List<String> lines) {
		Collected(Component name) {
			this(name, new ArrayList<>());
		}
	}

	/** Files plain lines under the entry being displayed and grouped lines under their group. */
	private static final class Collector implements DebugScreenDisplayer {
		private final Map<Object, Collected> out;
		Identifier current;

		Collector(Map<Object, Collected> out) {
			this.out = out;
		}

		@Override
		public void addPriorityLine(String line) {
			out.computeIfAbsent(current, k -> new Collected(DebugOverlayController.sectionName(current))).lines.add(line);
		}

		@Override
		public void addToGroup(DebugGroup group, Collection<String> lines) {
			for (String line : lines) {
				add(group, line);
			}
		}

		@Override
		public void addToGroup(DebugGroup group, String line) {
			add(group, line);
		}

		@Override
		public void addFactToGroup(DebugGroup group, String label, Consumer<DebugFact> fact) {
			DebugFact built = new DebugFact();
			built.text(label);
			fact.accept(built);
			add(group, built.result().getString());
		}

		/** Graphical renderers (the chunk load map, the lightmap) have no text to read. */
		@Override
		public void addToGroup(DebugGroup group, DebugCustomRenderer renderer) {
		}

		private void add(DebugGroup group, String line) {
			out.computeIfAbsent(group, k -> new Collected(group.title())).lines.add(line);
		}
	}
}
