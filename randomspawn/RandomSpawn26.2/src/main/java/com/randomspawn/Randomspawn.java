package com.randomspawn;

import com.randomspawn.compat.SiegedEmpiresWilderness;
import com.randomspawn.config.ModConfigs;
import com.randomspawn.util.JoinData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Randomspawn implements ModInitializer {
	public static final String MOD_ID = "randomspawn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int MAX_SPAWN_ATTEMPTS = 128;

	private int minX;
	private int maxX;
	private int minZ;
	private int maxZ;

	@Override
	public void onInitialize() {
		loadConfig();

		ServerPlayerEvents.JOIN.register(player -> {
			if (JoinData.isFirstJoin(player) && ModConfigs.SpawnOnFirstJoin) {
				teleportPlayerToRandomLocation(player);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!playerHasRespawnPoint(newPlayer) && ModConfigs.SpawnOnRespawn) {
				newPlayer.level().getServer().execute(() -> teleportPlayerToRandomLocation(newPlayer));
			}
		});

		LOGGER.info(
				"RandomSpawn loaded (range X: {}..{}, Z: {}..{}, siegedempires wilderness check: {})",
				minX,
				maxX,
				minZ,
				maxZ,
				SiegedEmpiresWilderness.isAvailable()
		);
	}

	private void loadConfig() {
		ModConfigs.registerConfigs();
		minX = Math.min(ModConfigs.MinX, ModConfigs.MaxX);
		maxX = Math.max(ModConfigs.MinX, ModConfigs.MaxX);
		minZ = Math.min(ModConfigs.MinZ, ModConfigs.MaxZ);
		maxZ = Math.max(ModConfigs.MinZ, ModConfigs.MaxZ);
	}

	private boolean playerHasRespawnPoint(ServerPlayer player) {
		return player.getRespawnConfig() != null;
	}

	private void teleportPlayerToRandomLocation(ServerPlayer player) {
		ServerLevel world = player.level().getServer().overworld();
		BlockPos spawnPos = findRandomLandSpawn(world);

		if (spawnPos == null) {
			LOGGER.warn(
					"Could not find a dry wilderness spawn for {} after {} attempts within X:{}..{} Z:{}..{}",
					player.getGameProfile().name(),
					MAX_SPAWN_ATTEMPTS,
					minX,
					maxX,
					minZ,
					maxZ
			);
			return;
		}

		player.teleportTo(
				world,
				spawnPos.getX() + 0.5,
				spawnPos.getY(),
				spawnPos.getZ() + 0.5,
				Set.of(),
				player.getYRot(),
				player.getXRot(),
				false
		);
		LOGGER.info(
				"Teleported {} to land spawn {}, {}, {}",
				player.getGameProfile().name(),
				spawnPos.getX(),
				spawnPos.getY(),
				spawnPos.getZ()
		);
	}

	private BlockPos findRandomLandSpawn(ServerLevel world) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int spanX = maxX - minX + 1;
		int spanZ = maxZ - minZ + 1;

		for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
			int x = minX + random.nextInt(spanX);
			int z = minZ + random.nextInt(spanZ);
			BlockPos landPos = findSafeLandPosition(world, x, z);
			if (landPos != null) {
				return landPos;
			}
		}

		return null;
	}

	/**
	 * Finds a safe standing position on dry wilderness at the given X/Z.
	 * Rejects ocean biomes, fluid at feet/head/ground, and Sieged Empires town claims.
	 */
	private BlockPos findSafeLandPosition(ServerLevel world, int x, int z) {
		int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (surfaceY < world.getMinY() + 1 || surfaceY > world.getMaxY()) {
			return null;
		}

		BlockPos feetPos = new BlockPos(x, surfaceY, z);
		BlockPos groundPos = feetPos.below();
		BlockPos headPos = feetPos.above();

		if (world.getBiome(feetPos).is(BiomeTags.IS_OCEAN) || world.getBiome(feetPos).is(BiomeTags.IS_DEEP_OCEAN)) {
			return null;
		}

		BlockState groundState = world.getBlockState(groundPos);
		BlockState feetState = world.getBlockState(feetPos);
		BlockState headState = world.getBlockState(headPos);

		if (!groundState.blocksMotion() || isFluid(groundState) || isFluid(groundPos, world)) {
			return null;
		}

		if (!isStandableSpace(feetState, world, feetPos) || !isStandableSpace(headState, world, headPos)) {
			return null;
		}

		if (!SiegedEmpiresWilderness.isWilderness(world, x, z)) {
			return null;
		}

		return feetPos;
	}

	private static boolean isStandableSpace(BlockState state, ServerLevel world, BlockPos pos) {
		return (state.isAir() || !state.blocksMotion()) && !isFluid(state) && !isFluid(pos, world);
	}

	private static boolean isFluid(BlockState state) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() || fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
	}

	private static boolean isFluid(BlockPos pos, ServerLevel world) {
		FluidState fluid = world.getFluidState(pos);
		return !fluid.isEmpty() || fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
