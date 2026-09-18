package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.util.FactionEditCost;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;

public final class ManageEmpireBuilder {
	private static final Gson GSON = new GsonBuilder().create();

	private ManageEmpireBuilder() {
	}

	public static ManageEmpireData build(ServerPlayer player) {
		ManageEmpireData data = new ManageEmpireData();
		EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
		if (empire == null) {
			return data;
		}

		data.empireId = empire.getId();
		data.empireName = empire.getName();
		data.capitalTownId = empire.getCapitalTownId();
		data.empirePublic = empire.isEmpirePublic();
		data.claimedChunks = FactionEditCost.countEmpireChunks(empire);
		data.bannerPatterns = empire.getBannerPatterns() != null
				? new ArrayList<>(empire.getBannerPatterns())
				: new ArrayList<>();
		data.bannerBaseColor = empire.getBannerBaseColor();
		data.bannerPixels = empire.getBannerPixels();

		for (String townId : empire.getMemberTownIds()) {
			TownData town = TownDataManager.getInstance().getTown(townId);
			if (town == null || town.isWarTown()) {
				continue;
			}
			ManageEmpireData.TownInfo info = new ManageEmpireData.TownInfo();
			info.id = town.getId();
			info.name = town.getName();
			info.nation = town.isNation();
			info.monarchUuid = town.getMonarchUuid() != null ? town.getMonarchUuid().toString() : null;
			info.monarchName = town.getMonarchName() != null && !town.getMonarchName().isEmpty()
					? town.getMonarchName()
					: (town.getMonarchUuid() != null ? town.getMemberName(town.getMonarchUuid()) : "");
			// Member towns are part of this empire, so their display flag
			// is the empire flag.
			info.bannerPatterns = empire.getBannerPatterns();
			info.bannerBaseColor = empire.getBannerBaseColor();
			info.bannerPixels = empire.getBannerPixels();
			data.memberTowns.add(info);
		}

		data.memberTowns.sort(Comparator.comparing(t -> t.name.toLowerCase()));

		for (TownData warTown : TownDataManager.getInstance().getWarTownsForEmpire(empire.getId())) {
			ManageEmpireData.WarTownInfo info = new ManageEmpireData.WarTownInfo();
			info.id = warTown.getId();
			info.name = warTown.getName();
			info.bannerPatterns = empire.getBannerPatterns();
			info.bannerBaseColor = empire.getBannerBaseColor();
			info.bannerPixels = empire.getBannerPixels();
			data.warTowns.add(info);
		}

		for (TownData town : TownDataManager.getInstance().getVisibleTowns()) {
			if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
				continue;
			}
			if (empire.getInvitedTowns().contains(town.getId())) {
				continue;
			}
			ManageEmpireData.TownInfo info = new ManageEmpireData.TownInfo();
			info.id = town.getId();
			info.name = town.getName();
			info.nation = town.isNation();
			// Inviteable towns are empire-less, so they show their own flag.
			info.bannerPatterns = town.getBannerPatterns();
			info.bannerBaseColor = town.getBannerBaseColor();
			info.bannerPixels = town.getBannerPixels();
			data.inviteableTowns.add(info);
		}

		data.inviteableTowns.sort(Comparator.comparing(t -> t.name.toLowerCase()));
		return data;
	}

	public static String toJson(ManageEmpireData data) {
		return GSON.toJson(data);
	}

	public static ManageEmpireData fromJson(String json) {
		ManageEmpireData data = GSON.fromJson(json, ManageEmpireData.class);
		return data != null ? data : new ManageEmpireData();
	}
}
