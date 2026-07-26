package com.nibblenerds.unitedminecraft;

import java.util.Set;

import com.nibblenerds.unitedminecraft.network.TeleportRequestPayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnitedMinecraft implements ModInitializer {
	public static final String MOD_ID = "united_minecraft";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("United Minecraft initializing");

		PayloadTypeRegistry.serverboundPlay().register(TeleportRequestPayload.TYPE, TeleportRequestPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ServerLevel level = (ServerLevel) player.level();

			Vec3 destination = new Vec3(payload.x(), payload.y(), payload.z());
			AABB destinationBox = player.getBoundingBox().move(destination.subtract(player.position()));
			// Re-validate server-side rather than trusting the client's own pre-check.
			if (!level.noCollision(player, destinationBox) || level.containsAnyLiquid(destinationBox)) {
				return;
			}

			player.teleportTo(level, payload.x(), payload.y(), payload.z(), Set.<Relative>of(), payload.yaw(), payload.pitch(), true);
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
