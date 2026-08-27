package com.nibblenerds.unitedminecraft.client.mixin;

import java.util.Optional;

import com.nibblenerds.unitedminecraft.client.ToastNarrationController;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Narrates the earned advancement's full toast content - the "Advancement Made!"/"Goal
 * Reached!"/"Challenge Complete!" header (from the advancement's {@link
 * net.minecraft.advancements.AdvancementType}), its title, and its description/flavor
 * text, all self-contained here rather than split across this toast and vanilla's own
 * system-chat narration ({@code ChatListener.handleSystemMessage} -&gt; {@code
 * GameNarrator.saySystemChatQueued}). That chat path used to be relied on for the "you
 * earned an advancement" half of the announcement, but chat narration gets interrupted by
 * other chat traffic arriving around the same time, so it's an unreliable way to actually
 * hear that an advancement happened - narrating the whole thing from the toast itself
 * doesn't have that problem.
 */
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void unitedMinecraft$narrate(AdvancementHolder advancement, CallbackInfo ci) {
		Optional<DisplayInfo> display = advancement.value().display();
		if (display.isEmpty()) {
			return;
		}
		DisplayInfo info = display.get();
		MutableComponent message = info.getType().getDisplayName().copy()
				.append(Component.literal(": "))
				.append(info.getTitle());
		Component description = info.getDescription();
		if (!description.getString().isBlank()) {
			message = message.append(Component.literal(". ")).append(description);
		}
		ToastNarrationController.narrate(message);
	}
}
