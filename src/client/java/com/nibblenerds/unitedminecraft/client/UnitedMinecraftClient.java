package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.speech.PrismController;

import net.fabricmc.api.ClientModInitializer;

public class UnitedMinecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UnitedMinecraftConfig.load();
		PrismController.register();
		ClientKeyBindings.register();
		AccessibilityTickHandler.register();
		MenuAccessibilityController.register();
		CreativeInventoryController.register();
		AnimalFeedingController.register();
		DurabilityAwarenessController.register();
		ToolHarvestAwarenessController.register();
	}
}
