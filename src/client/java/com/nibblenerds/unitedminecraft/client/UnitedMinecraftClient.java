package com.nibblenerds.unitedminecraft.client;

import net.fabricmc.api.ClientModInitializer;

public class UnitedMinecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientKeyBindings.register();
		AccessibilityTickHandler.register();
		MenuAccessibilityController.register();
	}
}
