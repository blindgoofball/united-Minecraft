package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Collection;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.nibblenerds.unitedminecraft.client.access.AdvancementsScreenAccess;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;

/**
 * Exposes {@code AdvancementsScreen}'s private tab map and selection. Nothing here touches the
 * loader-specific tab paging NeoForge patches in, so it is the same on both.
 */
@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenAccessorMixin implements AdvancementsScreenAccess {
	@Shadow
	private Map<AdvancementHolder, AdvancementTab> tabs;
	@Shadow
	private AdvancementTab selectedTab;

	@Override
	public Collection<AdvancementTab> unitedMinecraft$getTabs() {
		return tabs.values();
	}

	@Override
	public AdvancementTab unitedMinecraft$getSelectedTab() {
		return selectedTab;
	}
}
