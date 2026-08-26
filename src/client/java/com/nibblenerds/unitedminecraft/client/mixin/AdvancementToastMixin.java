package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Optional;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates only an earned advancement's description/flavor text, deliberately not its
 * title or the player's name - vanilla already speaks "PlayerName has made the
 * advancement [Title]" through the ordinary system-chat narration path ({@code
 * ChatListener.handleSystemMessage} -&gt; {@code GameNarrator.saySystemChatQueued}) whenever
 * the Narrator setting is Chat or All, entirely independent of this toast. That path
 * never includes the description, which only ever appears here (and on the advancement
 * screen) - so this adds new information instead of repeating the announcement.
 */
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void unitedMinecraft$narrate(AdvancementHolder advancement, CallbackInfo ci) {
		Optional<DisplayInfo> display = advancement.value().display();
		if (display.isEmpty()) {
			return;
		}
		Component description = display.get().getDescription();
		if (description.getString().isBlank()) {
			return;
		}
		ToastNarrationController.narrate(description);
	}
}
