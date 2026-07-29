package com.nibblenerds.unitedminecraft.client;

import net.fabricmc.api.ClientModInitializer;

public class UnitedMinecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		UnitedMinecraftConfig.load();
		ClientKeyBindings.register();
		AccessibilityTickHandler.register();
		MenuAccessibilityController.register();
		CreativeInventoryController.register();
	}
}
