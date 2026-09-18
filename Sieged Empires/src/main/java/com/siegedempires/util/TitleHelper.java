package com.siegedempires.util;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class TitleHelper {
	public static final int FADE_IN = 10;
	public static final int STAY = 70;
	public static final int FADE_OUT = 20;

	/** Flash cadence for the restricted-zone warning title (ticks). */
	public static final int RESTRICTED_FLASH_INTERVAL = 20;
	private static final int RESTRICTED_FLASH_STAY = 12;

	private TitleHelper() {
	}

	public static void showEnterTownTitle(ServerPlayer player, TownData town) {
		if (town.isWarTown()) {
			Component empireName = warTownDisplayName(town);
			sendTitle(player, empireName, null);
			return;
		}
		sendTitle(player, Component.literal(town.getName()), getEmpireSubtitle(town));
	}

	public static void showLeaveTownTitle(ServerPlayer player, TownData town) {
		if (town.isWarTown()) {
			Component empireName = warTownDisplayName(town);
			sendTitle(player, Component.literal("Leaving ").append(empireName), null);
			return;
		}
		sendTitle(player, Component.literal("Leaving " + town.getName()), getEmpireSubtitle(town));
	}

	/** Flashing red restricted-zone warning shown while a citizen remains in the chunk. */
	public static void showRestrictedZoneWarning(ServerPlayer player) {
		sendTitle(player,
				Component.literal("RESTRICTED ZONE! LEAVE NOW!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				null,
				0,
				RESTRICTED_FLASH_STAY,
				4);
	}

	public static void clearTitles(ServerPlayer player) {
		player.connection.send(new ClientboundClearTitlesPacket(true));
	}

	private static Component warTownDisplayName(TownData town) {
		if (town.getName() != null && town.getName().startsWith("New ")) {
			return Component.literal(town.getName());
		}
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null) {
				return Component.literal(empire.getName());
			}
		}
		return Component.literal(town.getName());
	}

	private static Component getEmpireSubtitle(TownData town) {
		String empireId = town.getEmpireId();
		if (empireId == null || empireId.isEmpty()) {
			return null;
		}

		EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
		if (empire == null) {
			return null;
		}

		return Component.literal(empire.getName());
	}

	public static void showTitle(ServerPlayer player, Component title, int stayTicks) {
		sendTitle(player, title, null, FADE_IN, stayTicks, FADE_OUT);
	}

	/**
	 * Centered feedback at subtitle scale (2×) instead of the huge title scale (4×).
	 * Empty title keeps the subtitle visible (vanilla only draws subtitles with a title set).
	 */
	public static void showSmallTitle(ServerPlayer player, Component text, int stayTicks) {
		sendTitle(player, Component.empty(), text, FADE_IN, stayTicks, FADE_OUT);
	}

	private static void sendTitle(ServerPlayer player, Component title, Component subtitle) {
		sendTitle(player, title, subtitle, FADE_IN, STAY, FADE_OUT);
	}

	private static void sendTitle(ServerPlayer player, Component title, Component subtitle,
	                              int fadeIn, int stay, int fadeOut) {
		// Stop any in-progress title so the new one starts immediately (fresh fade-in).
		player.connection.send(new ClientboundClearTitlesPacket(false));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				subtitle != null ? subtitle : Component.empty()
		));
	}
}
