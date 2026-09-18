package com.siegedempires.permission;

import com.siegedempires.config.ModSettings;
import com.siegedempires.model.TownData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary PvP windows for non-citizens attacked inside a town or empire claim.
 */
public final class TownPvpWindowManager {
	private static final Component BOSS_BAR_TITLE = Component.literal("PVP ACTIVE IN TOWN");
	private static final Map<UUID, ActiveWindow> WINDOWS = new ConcurrentHashMap<>();

	private TownPvpWindowManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (WINDOWS.isEmpty()) {
				return;
			}
			for (Iterator<Map.Entry<UUID, ActiveWindow>> it = WINDOWS.entrySet().iterator(); it.hasNext(); ) {
				ActiveWindow window = it.next().getValue();
				if (!window.tick()) {
					it.remove();
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				removeWindow(handler.getPlayer().getUUID()));
	}

	public static String scopeFor(TownData town) {
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			return "empire:" + empireId;
		}
		return "town:" + town.getId();
	}

	public static boolean hasActiveWindow(UUID playerId, TownData claimTown) {
		ActiveWindow window = WINDOWS.get(playerId);
		if (window == null) {
			return false;
		}
		return window.scope.equals(scopeFor(claimTown));
	}

	public static void activate(ServerPlayer player, TownData claimTown) {
		if (player == null || claimTown == null) {
			return;
		}

		int totalTicks = ModSettings.get().nonCitizenPvpCooldownTicks();
		UUID playerId = player.getUUID();
		String scope = scopeFor(claimTown);

		ActiveWindow existing = WINDOWS.get(playerId);
		if (existing != null && existing.scope.equals(scope)) {
			existing.refresh(totalTicks);
			return;
		}

		if (existing != null) {
			existing.cleanup();
		}

		ServerBossEvent bossBar = new ServerBossEvent(
				UUID.randomUUID(),
				BOSS_BAR_TITLE,
				BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_10
		);
		bossBar.setProgress(1.0F);
		bossBar.addPlayer(player);

		WINDOWS.put(playerId, new ActiveWindow(scope, bossBar, totalTicks));
	}

	private static void removeWindow(UUID playerId) {
		ActiveWindow window = WINDOWS.remove(playerId);
		if (window != null) {
			window.cleanup();
		}
	}

	private static final class ActiveWindow {
		private final String scope;
		private final ServerBossEvent bossBar;
		private final int totalTicks;
		private int ticksRemaining;

		private ActiveWindow(String scope, ServerBossEvent bossBar, int totalTicks) {
			this.scope = scope;
			this.bossBar = bossBar;
			this.totalTicks = totalTicks;
			this.ticksRemaining = totalTicks;
		}

		private void refresh(int totalTicks) {
			this.ticksRemaining = totalTicks;
			bossBar.setProgress(1.0F);
		}

		private boolean tick() {
			ticksRemaining--;
			bossBar.setProgress(Math.max(0.0F, (float) ticksRemaining / totalTicks));
			if (ticksRemaining <= 0) {
				cleanup();
				return false;
			}
			return true;
		}

		private void cleanup() {
			bossBar.removeAllPlayers();
		}
	}
}
