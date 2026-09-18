package com.siegedempires.util;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Resolves Sieged Empires rank prefixes from town/empire config data.
 * Only the prefix text is colored; callers append the vanilla player name after it.
 */
public final class PlayerPrefixHelper {
	private static final String SPACER = "   ";

	private PlayerPrefixHelper() {
	}

	public static Component getPrefixComponent(ServerPlayer player) {
		PrefixInfo info = resolvePrefix(player);
		if (info == null) {
			return null;
		}
		return Component.literal("[" + info.title() + " of " + info.location() + "]" + SPACER)
				.withStyle(info.color());
	}

	private static PrefixInfo resolvePrefix(ServerPlayer player) {
		UUID playerId = player.getUUID();
		TownData town = TownDataManager.getInstance().getPlayerTown(playerId);
		if (town == null) {
			return null;
		}

		EmpireData empire = getEmpire(town);

		if (empire != null && playerId.equals(empire.getEmperorUuid())) {
			String title = empire.getEmperorTitle();
			if (title == null || title.isEmpty()) {
				title = "Emperor";
			}
			return new PrefixInfo(title, empire.getName(), ChatFormatting.GOLD);
		}

		if (playerId.equals(town.getMonarchUuid())) {
			String monarchTitle = town.getMonarchTitle();
			if (monarchTitle == null || monarchTitle.isEmpty()) {
				return null;
			}
			monarchTitle = normalizeMonarchTitle(monarchTitle);
			if (town.isWarTown()) {
				String title = "Queen".equals(monarchTitle) ? "Duchess" : "Duke";
				return new PrefixInfo(title, town.getName(), ChatFormatting.LIGHT_PURPLE);
			}
			if (empire != null) {
				String title = "Queen".equals(monarchTitle) ? "Duchess" : "Duke";
				return new PrefixInfo(title, town.getName(), ChatFormatting.LIGHT_PURPLE);
			}
			return new PrefixInfo(monarchTitle, town.getName(), ChatFormatting.LIGHT_PURPLE);
		}

		if (town.isLord(playerId)) {
			return new PrefixInfo("Lord", locationName(town, empire), ChatFormatting.RED);
		}

		String role = town.getMembers().get(playerId);
		if ("Trusted Citizen".equals(role)) {
			return new PrefixInfo("Nobel", locationName(town, empire), ChatFormatting.GREEN);
		}

		if (role != null && !"Monarch".equals(role) && !"Lord".equals(role)) {
			return new PrefixInfo("Citizen", locationName(town, empire), ChatFormatting.GRAY);
		}

		return null;
	}

	private static String locationName(TownData town, EmpireData empire) {
		if (empire != null) {
			return empire.getName();
		}
		return town.getName();
	}

	private static EmpireData getEmpire(TownData town) {
		String empireId = town.getEmpireId();
		if (empireId == null || empireId.isEmpty()) {
			return null;
		}
		return EmpireDataManager.getInstance().getEmpire(empireId);
	}

	private static String normalizeMonarchTitle(String monarchTitle) {
		if ("Queen".equals(monarchTitle)) {
			return "Queen";
		}
		return "King";
	}

	private record PrefixInfo(String title, String location, ChatFormatting color) {
	}
}
