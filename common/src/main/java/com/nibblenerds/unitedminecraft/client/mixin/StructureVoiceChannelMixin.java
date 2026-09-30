package com.nibblenerds.unitedminecraft.client.mixin;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTDirectChannels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.nibblenerds.unitedminecraft.client.StructureVoiceAudio;

/**
 * Plays structure voices' stereo buffers straight to the left and right outputs. By default
 * OpenAL Soft runs even a stereo sound through HRTF (as two virtual speakers in front of you)
 * when Directional Audio is on, which smears the exact balance {@link StructureVoiceAudio} baked
 * in. Only ever set on those buffers - every other sound plays exactly as before.
 *
 * <p>Runs on the sound thread just before vanilla starts the source, so it's in place from the
 * first sample.
 */
@Mixin(Channel.class)
public abstract class StructureVoiceChannelMixin {
	@Shadow
	@Final
	private int source;

	@Inject(method = "attachStaticBuffer", at = @At("HEAD"))
	private void unitedMinecraft$playStructureVoiceDirect(SoundBuffer buffer, CallbackInfo ci) {
		if (StructureVoiceAudio.isVoiceBuffer(buffer) && AL.getCapabilities().AL_SOFT_direct_channels) {
			AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
		}
	}
}
