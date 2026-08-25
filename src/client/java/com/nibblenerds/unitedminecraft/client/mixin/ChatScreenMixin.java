package com.nibblenerds.unitedminecraft.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * {@code ChatScreen} narrates its input box's entire current message every time the narration
 * state refreshes (see {@code updateNarrationState}), independent of and in addition to {@link
 * EditBoxMixin}'s own per-keystroke narration - so without this, every character typed in chat
 * gets spoken twice: once as the delta, once as the whole message read from the start. Suppressed
 * here since {@link EditBoxMixin} already covers typing. Recalling a past message with Up/Down
 * still gets a full read, since that's a real full-content change worth announcing in full.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@Redirect(
			method = "updateNarrationState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;"))
	private String unitedMinecraft$suppressPerKeystrokeNarration(EditBox instance) {
		return "";
	}

	@Inject(method = "moveInHistory", at = @At("TAIL"))
	private void unitedMinecraft$narrateRecalledMessage(int direction, CallbackInfo ci) {
		String recalled = input.getValue();
		if (!recalled.isEmpty()) {
			Minecraft.getInstance().getNarrator().saySystemNow(recalled);
		}
	}
}
