package com.siegedempires.session;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.network.payload.SessionCinematicPayload;
import com.siegedempires.network.payload.SessionReleasedPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds joining players in spectator at their logout pose until they confirm
 * Join from the client Game Menu, then releases them to survival.
 */
public final class SessionGateManager {
	/** Biome base temperature below this counts as snowy wilderness (white title + snowday ambient). */
	private static final float SNOWY_TEMP_THRESHOLD = 0.2F;

	private static final Map<UUID, LockedPose> WAITING = new ConcurrentHashMap<>();

	private SessionGateManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> enter(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				WAITING.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				LockedPose pose = WAITING.get(player.getUUID());
				if (pose != null) {
					enforceLock(player, pose);
				}
			}
		});
	}

	public static boolean isWaiting(UUID playerId) {
		return WAITING.containsKey(playerId);
	}

	public static void enter(ServerPlayer player) {
		LockedPose pose = LockedPose.capture(player);
		WAITING.put(player.getUUID(), pose);
		player.setGameMode(GameType.SPECTATOR);
		enforceLock(player, pose);
		ServerPlayNetworking.send(player, new com.siegedempires.network.payload.SessionGateStartPayload());
	}

	/** Client clicked Join — reply with the location title for the cinematic. */
	public static void beginCinematic(ServerPlayer player) {
		LockedPose pose = WAITING.get(player.getUUID());
		if (pose == null) {
			return;
		}
		LocationTitle title = resolveLocationTitle(player, pose);
		ServerPlayNetworking.send(player, new SessionCinematicPayload(
				title.name(), title.kind(), title.subtitle(), title.snowy()));
	}

	/** Client finished the title card — unlock and enter survival. */
	public static void release(ServerPlayer player) {
		LockedPose pose = WAITING.remove(player.getUUID());
		if (pose == null) {
			return;
		}
		restorePose(player, pose);
		player.setGameMode(GameType.SURVIVAL);
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		ServerPlayNetworking.send(player, new SessionReleasedPayload());
	}

	private static void enforceLock(ServerPlayer player, LockedPose pose) {
		if (!player.level().dimension().identifier().toString().equals(pose.dimension())) {
			return;
		}
		player.setCamera(player);
		player.setDeltaMovement(Vec3.ZERO);
		player.teleportTo(
				(ServerLevel) player.level(),
				pose.x(),
				pose.y(),
				pose.z(),
				Set.of(),
				pose.yRot(),
				pose.xRot(),
				false
		);
		if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
			player.setGameMode(GameType.SPECTATOR);
		}
	}

	private static void restorePose(ServerPlayer player, LockedPose pose) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!level.dimension().identifier().toString().equals(pose.dimension())) {
			return;
		}
		player.teleportTo(level, pose.x(), pose.y(), pose.z(), Set.of(), pose.yRot(), pose.xRot(), false);
	}

	private static LocationTitle resolveLocationTitle(ServerPlayer player, LockedPose pose) {
		int chunkX = net.minecraft.util.Mth.floor(pose.x()) >> 4;
		int chunkZ = net.minecraft.util.Mth.floor(pose.z()) >> 4;
		TownData town = TownDataManager.getInstance().getTownAtChunk(chunkX, chunkZ, pose.dimension());
		if (town != null) {
			String empireId = town.getEmpireId();
			if (empireId != null && !empireId.isEmpty()) {
				EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
				if (empire != null && empire.getName() != null && !empire.getName().isEmpty()) {
					return new LocationTitle(empire.getName(), SessionCinematicPayload.KIND_CLAIM, "", false);
				}
			}
			String name = town.getName();
			if (name != null && !name.isEmpty()) {
				return new LocationTitle(name, SessionCinematicPayload.KIND_CLAIM, "", false);
			}
		}

		BlockPos pos = BlockPos.containing(pose.x(), pose.y(), pose.z());
		Holder<Biome> biome = player.level().getBiome(pos);
		boolean ocean = OceanBiomeHelper.isOcean(biome);
		String subtitle = resolveWildSubtitle(player.level(), ocean);
		if (ocean) {
			return new LocationTitle("Ocean", SessionCinematicPayload.KIND_OCEAN, subtitle, false);
		}
		boolean snowy = biome.value().getBaseTemperature() < SNOWY_TEMP_THRESHOLD;
		return new LocationTitle("Wilderness", SessionCinematicPayload.KIND_WILDERNESS, subtitle, snowy);
	}

	/**
	 * Day/night for wilderness and ocean. Ocean thunderstorms replace time-of-day
	 * (storms are the main danger cue in this pack).
	 */
	private static String resolveWildSubtitle(net.minecraft.world.level.Level level, boolean ocean) {
		if (ocean && level.isThundering()) {
			return "(Thunderstorm)";
		}
		if (level.isBrightOutside()) {
			return "(Daytime)";
		}
		return "(Nighttime)";
	}

	private record LockedPose(String dimension, double x, double y, double z, float yRot, float xRot) {
		static LockedPose capture(ServerPlayer player) {
			return new LockedPose(
					player.level().dimension().identifier().toString(),
					player.getX(),
					player.getY(),
					player.getZ(),
					player.getYRot(),
					player.getXRot()
			);
		}
	}

	private record LocationTitle(String name, String kind, String subtitle, boolean snowy) {
	}
}
