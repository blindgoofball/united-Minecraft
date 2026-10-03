package com.nibblenerds.unitedminecraft.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum ScannerCategory {
	STORAGE("united_minecraft.scanner.category.storage"),
	WORKSTATIONS("united_minecraft.scanner.category.workstations"),
	MECHANISMS("united_minecraft.scanner.category.mechanisms"),
	HOSTILE_MOBS("united_minecraft.scanner.category.hostile_mobs"),
	PASSIVE_MOBS("united_minecraft.scanner.category.passive_mobs"),
	ENTITIES("united_minecraft.scanner.category.entities"),
	ITEMS("united_minecraft.scanner.category.items"),
	ORES("united_minecraft.scanner.category.ores"),
	TREES("united_minecraft.scanner.category.trees"),
	CROPS("united_minecraft.scanner.category.crops"),
	TERRAIN("united_minecraft.scanner.category.terrain"),
	BIOMES_AND_STRUCTURES("united_minecraft.scanner.category.biomes_and_structures"),
	PLAYERS("united_minecraft.scanner.category.players"),
	MARKERS("united_minecraft.scanner.category.markers"),
	SEARCH("united_minecraft.scanner.category.search");

	private final String translationKey;

	ScannerCategory(String translationKey) {
		this.translationKey = translationKey;
	}

	public MutableComponent label() {
		return Component.translatable(translationKey);
	}
}
