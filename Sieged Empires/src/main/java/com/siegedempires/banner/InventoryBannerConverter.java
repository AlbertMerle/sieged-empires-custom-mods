package com.siegedempires.banner;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.network.payload.ConvertInventoryBannersPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handler for {@link ConvertInventoryBannersPayload}.
 */
public final class InventoryBannerConverter {
	private InventoryBannerConverter() {}

	/**
	 * @return error message for the player, or {@code null} on success
	 */
	public static String convert(ServerPlayer player, String factionKind) {
		if (player == null) {
			return "Invalid player!";
		}
		if (ConvertInventoryBannersPayload.KIND_EMPIRE.equalsIgnoreCase(factionKind)) {
			return convertEmpire(player);
		}
		if (ConvertInventoryBannersPayload.KIND_TOWN.equalsIgnoreCase(factionKind)) {
			return convertTown(player);
		}
		return "Unknown faction type!";
	}

	private static String convertTown(ServerPlayer player) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null) {
			return "You are not in a town!";
		}
		boolean canManage = town.getMonarchUuid().equals(player.getUUID()) || town.isLord(player.getUUID());
		if (!canManage) {
			return "Only the Monarch or a Lord can convert banners!";
		}
		String base = town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white";
		int converted = BannerHelper.convertInventoryBanners(
				player, base, town.getBannerPatterns(), town.getBannerPixels(),
				town.getId(), town.getEmpireId());
		if (converted <= 0) {
			return Component.translatable("gui.siegedempires.convert_banners_none").getString();
		}
		player.sendSystemMessage(Component.translatable("gui.siegedempires.convert_banners_done", converted));
		return null;
	}

	private static String convertEmpire(ServerPlayer player) {
		EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
		if (empire == null) {
			return "Only the Emperor can convert banners!";
		}
		String base = empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white";
		int converted = BannerHelper.convertInventoryBanners(
				player, base, empire.getBannerPatterns(), empire.getBannerPixels(),
				null, empire.getId());
		if (converted <= 0) {
			return Component.translatable("gui.siegedempires.convert_banners_none").getString();
		}
		player.sendSystemMessage(Component.translatable("gui.siegedempires.convert_banners_done", converted));
		return null;
	}
}
