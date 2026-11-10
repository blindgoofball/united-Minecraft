package com.nibblenerds.unitedminecraft.client.mixin;

import com.nibblenerds.unitedminecraft.client.ClientHooks;

import net.minecraft.world.level.storage.LevelStorageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code deleteLevel} throws if the folder could not be removed, so reaching its tail means the
 * world is really gone and its markers and block names can go with it.
 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class LevelStorageAccessMixin {
	@Shadow
	public abstract String getLevelId();

	@Inject(method = "deleteLevel", at = @At("TAIL"))
	private void unitedMinecraft$forgetWorldData(CallbackInfo ci) {
		ClientHooks.onSingleplayerWorldDeleted(getLevelId());
	}
}
