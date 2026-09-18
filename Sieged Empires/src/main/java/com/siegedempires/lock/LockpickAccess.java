package com.siegedempires.lock;

import com.siegedempires.config.ModSettings;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Who may start a lockpick attempt on claimed land.
 * <ul>
 *   <li>Wilderness — always allowed</li>
 *   <li>Active invaders — anywhere in the war-banner siege town (whole invading town/empire)</li>
 *   <li>Monarch / Lord / Trusted / Emperor — anywhere including restricted, if online-member gate passes</li>
 *   <li>Regular citizens — town claims except restricted chunks, if enough other members are online</li>
 *   <li>Everyone else — denied</li>
 * </ul>
 */
public final class LockpickAccess {
	private LockpickAccess() {
	}

	/**
	 * @return deny reason for chat, or {@code null} if lockpicking is allowed
	 */
	public static Component denyReason(ServerPlayer player, BlockPos pos) {
		if (player == null || pos == null) {
			return Component.translatable("lock.siegedempires.lockpick_denied");
		}

		String dimension = player.level().dimension().identifier().toString();
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		TownData town = TownDataManager.getInstance().getTownAtChunk(cx, cz, dimension);
		if (town == null) {
			return null;
		}

		if (InvasionManager.isInvaderInInvadedTerritory(player.getUUID(), town)) {
			return null;
		}

		if (!isTownMember(town, player.getUUID())) {
			return Component.translatable("lock.siegedempires.lockpick_not_citizen");
		}

		ChunkPosition chunk = new ChunkPosition(cx, cz, dimension);
		if (town.getRestrictedChunks().contains(chunk) && !mayAccessRestricted(town, player.getUUID())) {
			return Component.translatable("lock.siegedempires.lockpick_restricted");
		}

		int requiredOthers = ModSettings.get().lockpickMinOtherOnline;
		MinecraftServer server = player.level().getServer();
		int othersOnline = countOtherOnlineMembers(town, player.getUUID(), server);
		if (othersOnline < requiredOthers) {
			return Component.translatable(
					"lock.siegedempires.lockpick_need_online",
					requiredOthers,
					othersOnline);
		}

		return null;
	}

	public static boolean isTownMember(TownData town, UUID playerId) {
		if (town == null || playerId == null) {
			return false;
		}
		if (playerId.equals(town.getMonarchUuid()) || town.isLord(playerId)) {
			return true;
		}
		return town.getMembers() != null && town.getMembers().containsKey(playerId);
	}

	/** Monarch, Lord, Trusted Citizen, or emperor of this town's empire. */
	public static boolean mayAccessRestricted(TownData town, UUID playerId) {
		if (town == null || playerId == null) {
			return false;
		}
		if (playerId.equals(town.getMonarchUuid()) || town.isLord(playerId)) {
			return true;
		}
		String role = town.getMembers() != null ? town.getMembers().get(playerId) : null;
		if ("Trusted Citizen".equals(role)) {
			return true;
		}
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			var empires = com.siegedempires.data.EmpireDataManager.getInstance();
			if (empires.isEmperor(playerId) && empireId.equals(empires.getEmpireForTown(town.getId()))) {
				return true;
			}
			if (town.isWarTown() && empires.isEmperor(playerId)
					&& empireId.equals(town.getEmpireId())) {
				return true;
			}
		}
		return false;
	}

	public static int countOtherOnlineMembers(TownData town, UUID exclude, MinecraftServer server) {
		if (town == null || server == null) {
			return 0;
		}
		int count = 0;
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (exclude != null && online.getUUID().equals(exclude)) {
				continue;
			}
			if (isTownMember(town, online.getUUID())) {
				count++;
			}
		}
		return count;
	}
}
