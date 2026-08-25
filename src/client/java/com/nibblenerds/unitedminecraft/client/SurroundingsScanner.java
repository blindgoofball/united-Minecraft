package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Backs the "read what's in front of me" key. Blocks are found with a plain,
 * fixed-range raycast (like the vanilla crosshair, but independent of gamemode
 * reach). Entities use a wider forward cone instead of an exact ray, since
 * requiring a screen-reader user to aim their exact, invisible crosshair at a
 * mob's hitbox isn't practical - the nearest living thing roughly ahead of you
 * is what's actually useful to know about.
 */
public final class SurroundingsScanner {
	private static final double RANGE_BLOCKS = 6.0;
	private static final double ENTITY_CONE_HALF_ANGLE_DEG = 35.0;

	private SurroundingsScanner() {
	}

	public static void narrateFront(Minecraft client, LocalPlayer player) {
		BlockHit blockHit = findBlockAhead(player);
		EntityHit entityHit = findNearestEntityAhead(player);

		Component message;
		if (blockHit != null && entityHit != null) {
			message = Component.translatable("united_minecraft.narrate.front_both",
					blockHit.name(), blockHit.distance(), entityHit.name(), entityHit.distance());
		} else if (blockHit != null) {
			message = Component.translatable("united_minecraft.narrate.front_single", blockHit.name(), blockHit.distance());
		} else if (entityHit != null) {
			message = Component.translatable("united_minecraft.narrate.front_single", entityHit.name(), entityHit.distance());
		} else {
			message = Component.translatable("united_minecraft.narrate.front_clear");
		}
		client.getNarrator().saySystemNow(message);
	}

	private static BlockHit findBlockAhead(LocalPlayer player) {
		Level level = player.level();
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(RANGE_BLOCKS));

		BlockHitResult hit = level.clip(
				new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		int distance = (int) Math.round(from.distanceTo(hit.getLocation()));
		Component name = state.getBlock().getName().copy()
				.append(Component.literal(", "))
				.append(faceName(hit.getDirection()));
		return new BlockHit(name, distance);
	}

	/**
	 * Which side of the block the raycast actually landed on - useful for placement/orientation
	 * the same way it is for a sighted player glancing at where their crosshair lands. Top/Bottom
	 * rather than Up/Down for the vertical faces, distinct from the pitch-facing narration
	 * elsewhere ({@link CameraUtil}), since "the block's top face" and "which way you're looking"
	 * are different things worth different words.
	 */
	private static Component faceName(Direction direction) {
		return Component.translatable(switch (direction) {
			case NORTH -> "united_minecraft.direction.north";
			case SOUTH -> "united_minecraft.direction.south";
			case EAST -> "united_minecraft.direction.east";
			case WEST -> "united_minecraft.direction.west";
			case UP -> "united_minecraft.direction.top";
			case DOWN -> "united_minecraft.direction.bottom";
		});
	}

	private static EntityHit findNearestEntityAhead(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double cosThreshold = Math.cos(Math.toRadians(ENTITY_CONE_HALF_ANGLE_DEG));

		AABB searchBox = player.getBoundingBox().inflate(RANGE_BLOCKS);
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
				candidateEntity -> candidateEntity != player && candidateEntity.isAlive())) {
			Vec3 toEntity = candidate.getBoundingBox().getCenter().subtract(eye);
			double distance = toEntity.length();
			if (distance > RANGE_BLOCKS || distance < 1.0e-4) {
				continue;
			}
			double cosAngle = look.dot(toEntity.scale(1.0 / distance));
			if (cosAngle < cosThreshold) {
				continue;
			}
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}

		if (nearest == null) {
			return null;
		}
		return new EntityHit(nearest.getDisplayName(), (int) Math.round(nearestDistance));
	}

	private record BlockHit(Component name, int distance) {
	}

	private record EntityHit(Component name, int distance) {
	}
}
