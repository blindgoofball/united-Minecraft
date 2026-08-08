package com.nibblenerds.unitedminecraft.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum ScannerCategory {
	INTERACTABLES("united_minecraft.scanner.category.interactables"),
	MECHANISMS("united_minecraft.scanner.category.mechanisms"),
	ITEMS("united_minecraft.scanner.category.items"),
	PASSIVE_MOBS("united_minecraft.scanner.category.passive_mobs"),
	HOSTILE_MOBS("united_minecraft.scanner.category.hostile_mobs"),
	TREES("united_minecraft.scanner.category.trees"),
	ORES("united_minecraft.scanner.category.ores"),
	LIQUIDS("united_minecraft.scanner.category.liquids"),
	CROPS("united_minecraft.scanner.category.crops"),
	BIOMES("united_minecraft.scanner.category.biomes"),
	MARKERS("united_minecraft.scanner.category.markers"),
	PLAYERS("united_minecraft.scanner.category.players"),
	VEHICLES("united_minecraft.scanner.category.vehicles");

	private final String translationKey;

	ScannerCategory(String translationKey) {
		this.translationKey = translationKey;
	}

	public MutableComponent label() {
		return Component.translatable(translationKey);
	}
}
