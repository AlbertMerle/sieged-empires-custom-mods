package com.voxmapsync.server;

import com.voxmapsync.VoxelMapSync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Console percentage logging and a purple boss bar for OP players during admin render tasks.
 */
public final class MapSyncProgress {
	private static final UUID BOSS_BAR_ID = UUID.fromString("8f3c2a10-7b4e-4d91-9c6f-a1b2c3d4e5f6");

	private static ServerBossEvent bossBar;
	private static volatile boolean active;
	private static volatile String taskName = "";
	private static volatile int totalSteps;
	private static volatile int completedSteps;
	private static volatile int lastLoggedPercent = -1;

	private MapSyncProgress() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void start(MinecraftServer server, String task, int steps) {
		taskName = task;
		totalSteps = Math.max(1, steps);
		completedSteps = 0;
		lastLoggedPercent = -1;
		active = true;

		server.execute(() -> {
			if (bossBar != null) {
				bossBar.removeAllPlayers();
			}
			bossBar = new ServerBossEvent(
					BOSS_BAR_ID,
					Component.literal(formatTitle(0)),
					BossEvent.BossBarColor.PURPLE,
					BossEvent.BossBarOverlay.PROGRESS);
			bossBar.setProgress(0f);
			syncOpPlayers(server);
		});
		logPercent(0);
	}

	public static void advance(MinecraftServer server, int completed) {
		if (!active) {
			return;
		}
		completedSteps = Math.min(completed, totalSteps);
		int percent = percent();
		if (percent == lastLoggedPercent) {
			return;
		}
		lastLoggedPercent = percent;
		logPercent(percent);
		server.execute(() -> {
			if (bossBar != null && active) {
				bossBar.setProgress(progressFloat());
				bossBar.setName(Component.literal(formatTitle(percent)));
				syncOpPlayers(server);
			}
		});
	}

	public static void increment(MinecraftServer server) {
		advance(server, completedSteps + 1);
	}

	public static void finish(MinecraftServer server, boolean cancelled) {
		if (!active) {
			return;
		}
		int percent = cancelled ? percent() : 100;
		active = false;
		server.execute(() -> {
			if (bossBar != null) {
				if (!cancelled) {
					bossBar.setProgress(1f);
					bossBar.setName(Component.literal(formatTitle(100)));
				}
				bossBar.removeAllPlayers();
				bossBar = null;
			}
		});
		if (cancelled) {
			VoxelMapSync.LOGGER.info("VoxelMapSync {} stopped at {}% ({}/{})",
					taskName, percent, completedSteps, totalSteps);
		} else {
			lastLoggedPercent = 100;
			logPercent(100);
			VoxelMapSync.LOGGER.info("VoxelMapSync {} complete (100%)", taskName);
		}
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (!active || bossBar == null) {
			return;
		}
		if (isOp(player)) {
			bossBar.addPlayer(player);
		}
	}

	public static void onPlayerDisconnect(ServerPlayer player) {
		if (bossBar != null) {
			bossBar.removePlayer(player);
		}
	}

	private static void syncOpPlayers(MinecraftServer server) {
		if (bossBar == null) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(bossBar.getPlayers())) {
			if (!isOp(player)) {
				bossBar.removePlayer(player);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (isOp(player) && !bossBar.getPlayers().contains(player)) {
				bossBar.addPlayer(player);
			}
		}
	}

	private static boolean isOp(ServerPlayer player) {
		return player.level().getServer().getPlayerList().isOp(player.nameAndId());
	}

	private static int percent() {
		if (totalSteps <= 0) {
			return 0;
		}
		return Math.min(100, (completedSteps * 100) / totalSteps);
	}

	private static float progressFloat() {
		if (totalSteps <= 0) {
			return 0f;
		}
		return Math.min(1f, (float) completedSteps / totalSteps);
	}

	private static String formatTitle(int percent) {
		return "VoxelMapSync: " + taskName + " — " + percent + "%";
	}

	private static void logPercent(int percent) {
		VoxelMapSync.LOGGER.info("VoxelMapSync progress: {} — {}% ({}/{})",
				taskName, percent, completedSteps, totalSteps);
	}
}
