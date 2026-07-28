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
	MARKERS("united_minecraft.scanner.category.markers");

	private final String translationKey;

	ScannerCategory(String translationKey) {
		this.translationKey = translationKey;
	}

	public MutableComponent label() {
		return Component.translatable(translationKey);
	}
}
