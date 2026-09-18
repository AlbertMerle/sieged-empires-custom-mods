package com.siegedempires.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.util.Comparator;
import java.util.UUID;

public final class EmpireListBuilder {
    private static final Gson GSON = new GsonBuilder().create();

    private EmpireListBuilder() {
    }

    public static EmpireListData build(UUID playerUuid) {
        EmpireListData data = new EmpireListData();

        // Get the player's town to check for invites
        TownData playerTown = TownDataManager.getInstance().getPlayerTown(playerUuid);
        String playerTownId = playerTown != null ? playerTown.getId() : null;

        for (EmpireData empire : EmpireDataManager.getInstance().getAllEmpires()) {
            EmpireListData.EmpireInfo info = new EmpireListData.EmpireInfo();
            info.id = empire.getId();
            info.name = empire.getName();
            info.description = empire.getDescription();
            info.emperorName = empire.getEmperorName();
            info.emperorTitle = empire.getEmperorTitle();
            info.capitalTownId = empire.getCapitalTownId();
            info.memberCount = empire.getMemberTownIds().size();
            info.bannerPatterns = empire.getBannerPatterns();
            info.bannerBaseColor = empire.getBannerBaseColor();
            info.bannerPixels = empire.getBannerPixels();
            info.invited = playerTownId != null && empire.isInvited(playerTownId);
            info.empirePublic = empire.isEmpirePublic();
            data.empires.add(info);
        }

        // Sort: invited empires first, then by name
        data.empires.sort(Comparator
                .comparing((EmpireListData.EmpireInfo e) -> !e.invited)
                .thenComparing(e -> e.name.toLowerCase()));

        return data;
    }

    public static String toJson(EmpireListData data) {
        return GSON.toJson(data);
    }

    public static EmpireListData fromJson(String json) {
        EmpireListData data = GSON.fromJson(json, EmpireListData.class);
        return data != null ? data : new EmpireListData();
    }
}