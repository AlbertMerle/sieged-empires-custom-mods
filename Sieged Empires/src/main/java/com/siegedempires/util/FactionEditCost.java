package com.siegedempires.util;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.util.UUID;

/**
 * Gold-bar cost to edit a town/empire name and banner.
 * <p>
 * {@code max(5, claimedChunks * 7 / 16)} — 7 gold per 16 chunks, floored at 5.
 * Examples: 16 chunks → 7; 1600 empire chunks → 700; small towns stay at 5.
 */
public final class FactionEditCost {
	public static final int BASE_COST = 5;
	private static final int RATE_NUM = 7;
	private static final int RATE_DEN = 16;

	private FactionEditCost() {
	}

	public static int costForChunks(int claimedChunks) {
		int chunks = Math.max(0, claimedChunks);
		return Math.max(BASE_COST, chunks * RATE_NUM / RATE_DEN);
	}

	public static int costForTown(TownData town) {
		if (town == null) {
			return BASE_COST;
		}
		return costForChunks(town.getChunkCount());
	}

	public static int costForEmpire(EmpireData empire) {
		if (empire == null) {
			return BASE_COST;
		}
		return costForChunks(countEmpireChunks(empire));
	}

	public static int countEmpireChunks(EmpireData empire) {
		if (empire == null || empire.getMemberTownIds() == null) {
			return 0;
		}
		int total = 0;
		TownDataManager towns = TownDataManager.getInstance();
		for (String townId : empire.getMemberTownIds()) {
			TownData town = towns.getTown(townId);
			if (town != null) {
				total += town.getChunkCount();
			}
		}
		return total;
	}

	public static int costForPlayerTown(UUID monarchUuid) {
		return costForTown(TownDataManager.getInstance().getMonarchTown(monarchUuid));
	}

	public static int costForPlayerEmpire(UUID emperorUuid) {
		return costForEmpire(EmpireDataManager.getInstance().getEmpireByEmperor(emperorUuid));
	}
}
