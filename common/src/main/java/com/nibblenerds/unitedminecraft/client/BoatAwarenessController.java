package com.nibblenerds.unitedminecraft.client;

import com.nibblenerds.unitedminecraft.client.settings.UnitedMinecraftConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shore and obstacle awareness for a boat, gated by {@link UnitedMinecraftConfig#boatAwarenessEnabled}:
 * a sighted boater sees the bank or the wall coming; this says so.
 *
 * <p>Each tick while the player drives a boat, the next few seconds are predicted - the boat keeps
 * the heading it is swinging to, and the player keeps holding forward if they are - and every step is
 * tested against the real blocks. How the path ends decides the cue:
 * <ul>
 * <li><b>Land</b>: the boat sails out of the water and ends up on the bank. Low, thudding beeps.</li>
 * <li><b>An obstacle</b>: the boat would run into a block while still in the water - a cliff, a pier,
 * a pillar, a wall of ice - and stop there. Higher, sharper beeps, the same sound as an elytra wall
 * warning.</li>
 * </ul>
 * Either is spoken the moment it comes within a few seconds, then beeped at a quickening rate as it
 * nears, with the spoken warning repeated in the last moments.
 *
 * <p>The boat's speed follows vanilla: drag takes a tenth off it every tick, and a held forward adds
 * 0.04 along the way the boat faces ({@code AbstractBoat#floatBoat} and {@code #controlBoat}).
 */
public final class BoatAwarenessController {
	private enum Kind {
		LAND, BLOCK
	}

	/** {@code ticks} is how far ahead the contact is; {@code blocks} how far the boat travels to get there. */
	private record Contact(Kind kind, int ticks, double blocks) {
	}

	private static final int HORIZON_TICKS = 60;
	private static final int CAUTION_TICKS = 50;
	private static final int WARNING_TICKS = 30;
	private static final int CRITICAL_TICKS = 14;
	private static final double MIN_SPEED = 0.06;
	private static final double DRAG = 0.9;
	private static final double FORWARD_PUSH = 0.04;
	private static final int REPEAT_CRITICAL_TICKS = 30;
	private static final int[] BEEP_INTERVAL_TICKS = {0, 14, 7, 3};

	// 0 = nothing, 1 = caution, 2 = warning, 3 = critical.
	private static int level;
	private static Kind warnedKind;
	private static int sinceBeep;
	private static int sinceSpeech;
	private static float lastYaw;
	private static boolean haveLastYaw;

	private BoatAwarenessController() {
	}

	public static void reset() {
		level = 0;
		warnedKind = null;
		sinceBeep = 0;
		sinceSpeech = 0;
		haveLastYaw = false;
	}

	public static void tick(Minecraft client, LocalPlayer player) {
		boolean driving = UnitedMinecraftConfig.get().boatAwarenessEnabled && !player.isSpectator()
				&& player.getVehicle() instanceof AbstractBoat boat && boat.getControllingPassenger() == player;
		if (!driving) {
			reset();
			return;
		}
		AbstractBoat boat = (AbstractBoat) player.getVehicle();
		sinceBeep++;
		sinceSpeech++;

		float yaw = boat.getYRot();
		double turnSpeed = haveLastYaw ? Mth.wrapDegrees(yaw - lastYaw) : 0.0;
		lastYaw = yaw;
		haveLastYaw = true;

		Contact contact = predict(player, boat, turnSpeed);
		int urgency = contact == null ? 0 : urgency(contact.ticks());
		if (urgency == 0) {
			level = 0;
			warnedKind = null;
			return;
		}
		warn(client, player, contact, urgency);
	}

	private static int urgency(int ticks) {
		return ticks <= CRITICAL_TICKS ? 3 : ticks <= WARNING_TICKS ? 2 : ticks <= CAUTION_TICKS ? 1 : 0;
	}

	private static void warn(Minecraft client, LocalPlayer player, Contact contact, int urgency) {
		boolean worse = urgency > level || contact.kind() != warnedKind;
		boolean repeat = urgency == 3 && sinceSpeech >= REPEAT_CRITICAL_TICKS;
		if (worse || repeat) {
			client.getNarrator().saySystemNow(Component.translatable(
					"united_minecraft.narrate.boat_aware_" + contact.kind().name().toLowerCase() + "_" + urgency,
					(int) Math.round(contact.blocks())));
			sinceSpeech = 0;
		}
		int interval = BEEP_INTERVAL_TICKS[urgency];
		if (interval > 0 && sinceBeep >= interval) {
			if (contact.kind() == Kind.LAND) {
				ElytraAwarenessController.play(client, player, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, urgency == 3 ? 1.0f : 0.7f);
			} else {
				ElytraAwarenessController.play(client, player, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, urgency == 3 ? 1.8f : 1.3f);
			}
			sinceBeep = 0;
		}
		level = urgency;
		warnedKind = contact.kind();
	}

	// ------------------------------------------------------------------ prediction

	/** Sails the boat on, and reports where it comes to land or to a block, or null if neither within the horizon. */
	private static Contact predict(LocalPlayer player, AbstractBoat boat, double turnSpeed) {
		Level world = boat.level();
		Vec3 velocity = boat.getDeltaMovement();
		double vx = velocity.x();
		double vz = velocity.z();
		boolean forward = player.input.keyPresses.forward();
		if (Math.hypot(vx, vz) < MIN_SPEED && !forward) {
			return null;
		}

		AABB start = boat.getBoundingBox().deflate(1.0e-3);
		boolean alreadyTouching = hitsBlock(world, start);
		boolean onWater = overWater(world, boat.getX(), boat.getY(), boat.getZ());
		float yaw = boat.getYRot();
		double turn = turnSpeed;
		double ox = 0.0;
		double oz = 0.0;

		for (int t = 1; t <= HORIZON_TICKS; t++) {
			turn *= DRAG;
			yaw += (float) turn;
			vx *= DRAG;
			vz *= DRAG;
			if (forward) {
				vx += Mth.sin(-yaw * Mth.DEG_TO_RAD) * FORWARD_PUSH;
				vz += Mth.cos(yaw * Mth.DEG_TO_RAD) * FORWARD_PUSH;
			}
			if (Math.hypot(vx, vz) < MIN_SPEED / 2.0) {
				return null;
			}
			ox += vx;
			oz += vz;
			double blocks = Math.hypot(ox, oz);
			if (!alreadyTouching && hitsBlock(world, start.move(ox, 0.0, oz))) {
				return new Contact(Kind.BLOCK, t, blocks);
			}
			if (onWater && !overWater(world, boat.getX() + ox, boat.getY(), boat.getZ() + oz)) {
				return new Contact(Kind.LAND, t, blocks);
			}
		}
		return null;
	}

	/** Whether there is water under the boat's middle - the boat floats on the surface, so this reads the block it sits in, then the one below. */
	private static boolean overWater(Level world, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y, z);
		return world.getFluidState(at).is(FluidTags.WATER) || world.getFluidState(at.below()).is(FluidTags.WATER);
	}

	/** Whether the box overlaps a block the boat could not sail through; a lily pad does not count, the boat sweeps it aside. */
	private static boolean hitsBlock(Level world, AABB box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
			for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					if (state.isAir() || state.is(Blocks.LILY_PAD)) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(world, pos);
					if (shape.isEmpty()) {
						continue;
					}
					for (AABB part : shape.toAabbs()) {
						if (part.move(pos).intersects(box)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
