package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.nibblenerds.unitedminecraft.client.WallToneSynth;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;

/**
 * Supplies the wall tone loops from {@link WallToneSynth} instead of looking for {@code .ogg}
 * files that don't exist. Goes through vanilla's own {@code cache} rather than around it, so
 * each tone's OpenAL buffer is created once and released by vanilla's own {@code clear()} on
 * a resource reload or sound device change, exactly like a file-backed sound.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
	@Shadow
	@Final
	private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

	@Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
	private void unitedMinecraft$synthesizeWallTone(Identifier path, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
		if (WallToneSynth.handles(path)) {
			cir.setReturnValue(cache.computeIfAbsent(path, key -> CompletableFuture.completedFuture(WallToneSynth.create(key))));
		}
	}
}
