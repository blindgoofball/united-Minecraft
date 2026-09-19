package com.nibblenerds.unitedminecraft.client;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Vanilla's own {@code Animal#playEatingSound()} is a silent no-op for most animals (sheep,
 * cow, chicken, and every other species that doesn't specifically override it) - checked via
 * the decompiled bytecode, not assumed. Feeding one to breed it, or to grow a baby up faster,
 * is otherwise completely silent; sighted players get the heart/growth particles instead, but
 * that's no help at all without seeing them.
 *
 * <p>This predicts the same feed gate vanilla's own {@code Animal#mobInteract} uses and plays a
 * confirmation sound whenever it would succeed - deliberately unconditional even for the
 * handful of species that already have their own real eating sound, rather than trying to
 * detect and skip those, since a slightly doubled-up cue on an uncommon animal is a much
 * smaller problem than silence on every common one.
 *
 * <p>{@link #playFeedSoundIfSuccessful} is exposed for {@link ScannerController} to call
 * directly - {@code UseEntityCallback} only fires from {@code Minecraft}'s own mouse-click
 * handling (before it ever calls {@code gameMode.interact}), so a caller like {@link
 * ScannerController#interactWithLocked} that invokes {@code gameMode.interact} straight,
 * bypassing that click handling entirely (on purpose - see its own doc comment), would
 * otherwise never trigger this sound at all.
 */
public final class AnimalFeedingController {
	private AnimalFeedingController() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(AnimalFeedingController::onUseEntity);
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
		if (level.isClientSide()) {
			playFeedSoundIfSuccessful(entity, player.getItemInHand(hand));
		}
		// Never overrides vanilla's own result - this only ever adds a sound alongside
		// whatever the real interaction does.
		return InteractionResult.PASS;
	}

	/** Plays the feed confirmation sound if using {@code stack} on {@code entity} would actually feed it. */
	static void playFeedSoundIfSuccessful(Entity entity, ItemStack stack) {
		if (!(entity instanceof Animal animal) || !willFeedSucceed(animal, stack)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL,
				1.0f, 1.0f, animal.getRandom(), animal.getX(), animal.getY(), animal.getZ()));
	}

	/**
	 * Mirrors {@code Animal#mobInteract}'s real feed gate as closely as client-visible state
	 * allows. {@code canAgeUp()} (a baby's growth gate) is exact - it's built from
	 * {@code isBaby()}/age-lock, both synced to the client. {@code canFallInLove()} (an adult's
	 * breeding gate) is not: its cooldown timer is a plain server-only field, never networked,
	 * so an adult always reads as available here even mid-cooldown from a previous feeding -
	 * accepted as the closest approximation actually available without server cooperation.
	 */
	private static boolean willFeedSucceed(Animal animal, ItemStack stack) {
		if (!animal.isFood(stack)) {
			return false;
		}
		return !animal.isBaby() || animal.canAgeUp();
	}
}
