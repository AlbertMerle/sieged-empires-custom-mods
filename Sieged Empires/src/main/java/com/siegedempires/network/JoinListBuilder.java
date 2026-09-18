package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class JoinListBuilder {
	private static final Gson GSON = new GsonBuilder().create();

	private JoinListBuilder() {
	}

	public static JoinListData build(UUID playerUuid) {
		JoinListData data = new JoinListData();
		Map<String, JoinListData.EmpireGroup> empireGroups = new LinkedHashMap<>();

		empireGroups.put("none", new JoinListData.EmpireGroup("none", "No Empire"));
		for (EmpireData empire : EmpireDataManager.getInstance().getAllEmpires()) {
			JoinListData.EmpireGroup group = new JoinListData.EmpireGroup(empire.getId(), empire.getName());
			group.bannerPatterns = empire.getBannerPatterns();
			group.bannerBaseColor = empire.getBannerBaseColor();
			group.bannerPixels = empire.getBannerPixels();
			empireGroups.put(empire.getId(), group);
		}

		for (TownData town : TownDataManager.getInstance().getVisibleTowns()) {
			JoinListData.TownInfo info = toTownInfo(town);

			if (town.isInvited(playerUuid)) {
				data.invitedTowns.add(info);
			}

			String empireId = town.getEmpireId();
			JoinListData.EmpireGroup group;
			if (empireId == null || empireId.isEmpty()) {
				group = empireGroups.get("none");
			} else {
				group = empireGroups.get(empireId);
				if (group == null) {
					group = new JoinListData.EmpireGroup(empireId, empireId);
					empireGroups.put(empireId, group);
				}
			}
			group.towns.add(info);
		}

		data.empires.addAll(empireGroups.values());
		return data;
	}

	public static String toJson(JoinListData data) {
		return GSON.toJson(data);
	}

	public static JoinListData fromJson(String json) {
		return GSON.fromJson(json, JoinListData.class);
	}

	private static JoinListData.TownInfo toTownInfo(TownData town) {
		JoinListData.TownInfo info = new JoinListData.TownInfo();
		info.id = town.getId();
		info.name = town.getName();
		info.nation = town.isNation();
		info.townPublic = town.isTownPublic();

		// Display flag rule: a town that is part of an empire adopts the
		// empire flag; independent towns show their own flag.
		EmpireData empire = null;
		if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
			empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
		}
		if (empire != null) {
			info.bannerPatterns = empire.getBannerPatterns();
			info.bannerBaseColor = empire.getBannerBaseColor();
			info.bannerPixels = empire.getBannerPixels();
		} else {
			info.bannerPatterns = town.getBannerPatterns();
			info.bannerBaseColor = town.getBannerBaseColor();
			info.bannerPixels = town.getBannerPixels();
		}
		return info;
	}
}
