package com.nibblenerds.unitedminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Flight awareness for elytra gliding, gated by {@link UnitedMinecraftConfig#elytraAwarenessEnabled}:
 * a sighted pilot sees the wall, the lava or the ground coming; this says so.
 *
 * <p>Each tick while gliding, the rest of the flight is predicted - assuming the player keeps looking
 * where they are looking - with the same elytra physics {@link ElytraAutopilot} uses ({@link
 * ElytraPhysics}), testing every step against the real blocks. How the path ends decides the cue:
 * <ul>
 * <li><b>A wall</b> (a side face), <b>a hard landing</b> (ground while sinking fast enough to hurt),
 * <b>a harmful block</b> (cactus, magma, a campfire) or <b>lava</b> is a warning: spoken the moment it
 * comes within a few seconds, then a beeping that quickens as it nears, with a spoken command in the
 * last moments. Only impacts that would actually hurt count - a graze along a wall or a gentle skim
 * over the ground is not worth an alarm.</li>
 * <li><b>A gentle landing</b> (or water) is good news: a chime and "Safe landing", then the height
 * above the ground called out as it comes down.</li>
 * </ul>
 * While {@link ElytraLandingController} is flying the landing itself, only the warnings still sound -
 * it already narrates the approach.
 *
 * <p>Damage estimates mirror vanilla: hitting a wall costs ten times the horizontal speed lost against
 * it, minus three ({@code LivingEntity#handleFallFlyingCollisions}), and a landing hurts once the
 * fall distance - which gliding keeps capped at one block unless sinking faster than half a block per
 * tick ({@code Entity#checkFallDistanceAccumulation}) - passes the safe-fall distance. Armor and
 * protection enchantments are not counted, so a predicted hit is a worst case.
 */
public final class ElytraAwarenessController {
	private enum Kind {
		WALL, HARD_LANDING, HAZARD, LAVA, SAFE_LANDING, WATER
	}

	/** {@code damage} is the estimated damage (zero for the safe kinds); {@code ticks} is how far ahead the contact is. */
	private record Impact(Kind kind, int ticks, double damage) {
	}

	// Looks ahead further than the warnings need (they only care about the last 4 seconds), so a landing
	// is recognised early enough for the height callouts to start well before the ground.
	private static final int HORIZON_TICKS = 160;
	private static final int CAUTION_TICKS = 80;
	private static final int WARNING_TICKS = 55;
	private static final int CRITICAL_TICKS = 28;
	// Fall damage becomes possible once sinking at 0.5 blocks/tick or faster at touchdown.
	private static final double CAUTION_SINK = 0.45;
	private static final double MIN_DAMAGE = 1.0;
	private static final double PLAYER_CENTER_HEIGHT = 0.3;
	private static final int[] CALLOUT_HEIGHTS = {5, 10, 20, 30, 50};
	private static final double MAX_HEIGHT_PROBE = 120.0;
	private static final int REPEAT_CRITICAL_TICKS = 30;
	private static final int[] BEEP_INTERVAL_TICKS = {0, 0, 9, 4};

	// 0 = nothing, 1 = caution, 2 = warning, 3 = critical.
	private static int level;
	private static Kind dangerKind;
	private static int sinceBeep;
	private static int sinceSpeech;
	private static boolean landingAnnounced;
	private static int lastCallout = Integer.MAX_VALUE;
	private static boolean wasGliding;
	private static boolean safeWhenLastGliding;

	private ElytraAwarenessController() {
	}

	public static void reset() {
		level = 0;
		dangerKind = null;
		sinceBeep = 0;
		sinceSpeech = 0;
		landingAnnounced = false;
		lastCallout = Integer.MAX_VALUE;
		wasGliding = false;
		safeWhenLastGliding = false;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean gliding = player.isFallFlying() && UnitedMinecraftConfig.get().elytraAwarenessEnabled
				&& !player.isSpectator() && !player.getAbilities().invulnerable;
		if (!gliding) {
			if (wasGliding && safeWhenLastGliding && player.onGround() && !ElytraLandingController.isActive()) {
				client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_aware_landed"));
			}
			reset();
			return;
		}
		wasGliding = true;
		sinceBeep++;
		sinceSpeech++;

		Impact impact = predict(player);
		boolean danger = impact != null && isDanger(impact.kind());
		boolean safe = impact != null && !danger;
		safeWhenLastGliding = safe;

		if (danger) {
			landingAnnounced = false;
			lastCallout = Integer.MAX_VALUE;
			warn(client, player, impact);
			return;
		}
		level = 0;
		dangerKind = null;

		if (safe && !ElytraLandingController.isActive()) {
			announceLanding(client, player, impact);
		} else {
			landingAnnounced = false;
			lastCallout = Integer.MAX_VALUE;
		}
	}

	private static boolean isDanger(Kind kind) {
		return kind == Kind.WALL || kind == Kind.HARD_LANDING || kind == Kind.HAZARD || kind == Kind.LAVA;
	}

	// ------------------------------------------------------------------ warnings

	private static void warn(Minecraft client, LocalPlayer player, Impact impact) {
		int urgency = impact.ticks() <= CRITICAL_TICKS ? 3 : impact.ticks() <= WARNING_TICKS ? 2 : impact.ticks() <= CAUTION_TICKS ? 1 : 0;
		if (urgency == 0) {
			level = 0;
			dangerKind = null;
			return;
		}
		boolean worse = urgency > level || impact.kind() != dangerKind;
		boolean repeat = urgency == 3 && sinceSpeech >= REPEAT_CRITICAL_TICKS;
		boolean fatal = impact.damage() >= player.getHealth() + player.getAbsorptionAmount();
		if (worse || repeat) {
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.elytra_aware_" + impact.kind().name().toLowerCase() + "_" + urgency,
					(int) Math.round(impact.damage() / 2.0)));
			sinceSpeech = 0;
		}
		int interval = BEEP_INTERVAL_TICKS[urgency];
		if (interval > 0 && sinceBeep >= interval) {
			float pitch = (urgency == 3 ? 1.8f : 1.3f) + (fatal ? 0.2f : 0.0f);
			play(client, player, SoundEvents.NOTE_BLOCK_BIT.value(), fatal ? 1.0f : 0.8f, pitch);
			sinceBeep = 0;
		}
		level = urgency;
		dangerKind = impact.kind();
	}

	// ------------------------------------------------------------------ safe landing

	private static void announceLanding(Minecraft client, LocalPlayer player, Impact impact) {
		double height = heightAboveGround(player);
		if (!landingAnnounced) {
			landingAnnounced = true;
			lastCallout = Integer.MAX_VALUE;
			for (int callout : CALLOUT_HEIGHTS) {
				if (callout >= height) {
					lastCallout = callout;
					break;
				}
			}
			play(client, player, SoundEvents.NOTE_BLOCK_BANJO.value(), 0.8f, 1.5f);
			client.getNarrator().saySystemNow(Component.translatable(impact.kind() == Kind.WATER
					? "united_minecraft.narrate.elytra_aware_water" : "united_minecraft.narrate.elytra_aware_safe"));
			return;
		}
		// Back up past a callout already given (the player climbed) re-arms the ones below it.
		if (lastCallout != Integer.MAX_VALUE && height > lastCallout + 8) {
			lastCallout = Integer.MAX_VALUE;
			for (int callout : CALLOUT_HEIGHTS) {
				if (callout >= height) {
					lastCallout = callout;
					break;
				}
			}
		}
		int crossed = -1;
		for (int callout : CALLOUT_HEIGHTS) {
			if (height <= callout && callout < lastCallout) {
				crossed = callout;
				break;
			}
		}
		if (crossed > 0) {
			lastCallout = crossed;
			client.getNarrator().saySystemNow(Component.translatable("united_minecraft.narrate.elytra_aware_height", crossed));
		}
	}

	/** How far the ground is straight below the player. */
	private static double heightAboveGround(LocalPlayer player) {
		Vec3 from = player.position();
		Vec3 to = from.add(0.0, -MAX_HEIGHT_PROBE, 0.0);
		BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return hit.getType() == HitResult.Type.MISS ? MAX_HEIGHT_PROBE : from.y - hit.getLocation().y;
	}

	// ------------------------------------------------------------------ prediction

	/** Flies the rest of the glide on the current look and reports how it ends, or null if nothing hurtful or notable within the horizon. */
	private static Impact predict(LocalPlayer player) {
		Level world = player.level();
		Vec3 velocity = player.getDeltaMovement();
		double vx = velocity.x();
		double vy = velocity.y();
		double vz = velocity.z();
		double x = player.getX();
		double y = player.getY() + PLAYER_CENTER_HEIGHT;
		double z = player.getZ();
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		double fall = player.fallDistance;
		double safeFall = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		double fallMultiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);

		for (int t = 1; t <= HORIZON_TICKS; t++) {
			double[] v = ElytraPhysics.step(vx, vy, vz, yaw, pitch);
			vx = v[0];
			vy = v[1];
			vz = v[2];
			// Vanilla's checkFallDistanceAccumulation, then the move's own accumulation.
			if (vy > -0.5 && fall > 1.0) {
				fall = 1.0;
			}
			if (vy < 0.0) {
				fall -= vy;
			}
			Vec3 from = new Vec3(x, y, z);
			Vec3 to = new Vec3(x + vx, y + vy, z + vz);
			BlockHitResult hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
			if (hit.getType() == HitResult.Type.BLOCK) {
				return classify(world, hit, t, vx, vy, vz, fall, safeFall, fallMultiplier);
			}
			x += vx;
			y += vy;
			z += vz;
		}
		return null;
	}

	private static Impact classify(Level world, BlockHitResult hit, int ticks, double vx, double vy, double vz,
			double fall, double safeFall, double fallMultiplier) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = world.getBlockState(pos);
		if (!world.getFluidState(pos).isEmpty() && state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
			return new Impact(world.getFluidState(pos).is(FluidTags.LAVA) ? Kind.LAVA : Kind.WATER, ticks, 0.0);
		}
		Direction face = hit.getDirection();
		if (face == Direction.UP) {
			if (state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.FIRE)
					|| state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH)) {
				return new Impact(Kind.HAZARD, ticks, 0.0);
			}
			double damage = Math.max(0.0, Mth.floor((fall + 1.0E-6 - safeFall) * fallMultiplier));
			double sink = -vy;
			if (damage >= MIN_DAMAGE || sink > CAUTION_SINK) {
				return new Impact(Kind.HARD_LANDING, ticks, damage);
			}
			return new Impact(Kind.SAFE_LANDING, ticks, 0.0);
		}
		if (face == Direction.DOWN) {
			// A ceiling stops the climb without the kinetic damage a wall does.
			return null;
		}
		double lost = Math.abs(vx * face.getStepX() + vz * face.getStepZ());
		double damage = lost * 10.0 - 3.0;
		return damage >= MIN_DAMAGE ? new Impact(Kind.WALL, ticks, damage) : null;
	}

	private static void play(Minecraft client, LocalPlayer player, SoundEvent sound, float volume, float pitch) {
		client.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.MASTER, CueVolume.scale(volume), pitch,
				player.getRandom(), player.getX(), player.getY(), player.getZ()));
	}
}
