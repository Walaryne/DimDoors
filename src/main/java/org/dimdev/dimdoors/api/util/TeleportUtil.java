package org.dimdev.dimdoors.api.util;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.EulerAngle;
import org.dimdev.dimdoors.DimensionalDoorsInitializer;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;

import org.dimdev.dimdoors.entity.stat.ModStats;
import org.dimdev.dimdoors.network.ExtendedServerPlayNetworkHandler;
import org.dimdev.dimdoors.world.ModDimensions;

@SuppressWarnings("deprecation")
public final class TeleportUtil {
	public static  <E extends Entity> E teleport(E entity, World world, BlockPos pos, float yaw) {
		return teleport(entity, world, Vec3d.ofBottomCenter(pos), yaw);
	}

	public static  <E extends Entity> E teleport(E entity, World world, Vec3d pos, float yaw) {
		return teleport(entity, world, pos, new EulerAngle(entity.getPitch(), yaw, 0), entity.getVelocity());
	}

	public static  <E extends Entity> E teleport(E entity, World world, Vec3d pos, EulerAngle angle, Vec3d velocity) {
		if (world.isClient) {
			throw new UnsupportedOperationException("Only supported on ServerWorld");
		}

		if (entity.world.getRegistryKey().equals(world.getRegistryKey())) {
			entity.setYaw(angle.getYaw());
			entity.setPitch(angle.getPitch());
			entity.teleport(pos.x, pos.y, pos.z);
			entity.setVelocity(velocity);
		} else {
			entity = FabricDimensions.teleport(entity, (ServerWorld) world, new TeleportTarget(pos, velocity, angle.getYaw(), angle.getPitch()));
		}

		if (entity instanceof ServerPlayerEntity) {
			if (world.getRegistryKey() == ModDimensions.DUNGEON) {
				((PlayerEntity) entity).incrementStat(ModStats.TIMES_BEEN_TO_DUNGEON);
			}
			((ExtendedServerPlayNetworkHandler) ((ServerPlayerEntity) entity).networkHandler).getDimDoorsPacketHandler().syncPocketAddonsIfNeeded(world, new BlockPos(pos));
		}

		return entity;
	}

	public static  <E extends Entity> E teleport(E entity, World world, BlockPos pos, EulerAngle angle, Vec3d velocity) {
		if (world.isClient) {
			throw new UnsupportedOperationException("Only supported on ServerWorld");
		}

		return teleport(entity, world, Vec3d.ofBottomCenter(pos), angle, velocity);
	}

	public static ServerPlayerEntity teleport(ServerPlayerEntity player, Location location) {
		return teleport(player, DimensionalDoorsInitializer.getWorld(location.world), location.pos, 0);
	}

	public static ServerPlayerEntity teleport(ServerPlayerEntity player, RotatedLocation location) {
		return teleport(player, DimensionalDoorsInitializer.getWorld(location.world), location.pos, (int) location.yaw);
	}
	public static  <E extends Entity> E teleportRandom(E entity, World world, double y) {
		double scale = ThreadLocalRandom.current().nextGaussian() * ThreadLocalRandom.current().nextInt(90);
		return teleport(
				entity,
				world,
				entity.getPos()
						.subtract(0, entity.getY(), 0)
						.add(0, y, 0)
						.multiply(scale, 1, scale),
				entity.getYaw()
		);
	}

	public static  <E extends Entity> E teleportUntargeted(E entity, World world) {
		double actualScale = entity.world.getDimension().getCoordinateScale() / world.getDimension().getCoordinateScale();
		return teleport(
				entity,
				world,
				entity.getPos().multiply(actualScale, 1, actualScale),
				entity.getYaw()
		);
	}

	public static  <E extends Entity> E teleportUntargeted(E entity, World world, double y) {
		double actualScale = entity.world.getDimension().getCoordinateScale() / world.getDimension().getCoordinateScale();
		return teleport(
				entity,
				world,
				entity.getPos()
						.subtract(0, entity.getPos().getY(), 0)
						.add(0, y, 0)
						.multiply(actualScale, 1, actualScale),
				entity.getYaw()
		);
	}
}
