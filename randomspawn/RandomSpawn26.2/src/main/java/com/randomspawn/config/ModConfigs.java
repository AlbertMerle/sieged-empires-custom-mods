package com.randomspawn.config;

import com.mojang.datafixers.util.Pair;

public class ModConfigs {
	public static SimpleConfig CONFIG;
	private static ModConfigProvider configs;

	public static int MinZ;
	public static int MaxZ;
	public static int MinX;
	public static int MaxX;
	public static boolean SpawnOnFirstJoin;
	public static boolean SpawnOnRespawn;

	public static void registerConfigs() {
		configs = new ModConfigProvider();
		createConfigs();

		CONFIG = SimpleConfig.of("Randomspawn").provider(configs).request();

		assignConfigs();
	}

	private static void createConfigs() {
		configs.addKeyValuePair(new Pair<>("MinZ", -1000), "Minimum Z coordinate for random spawns");
		configs.addKeyValuePair(new Pair<>("MaxZ", 1000), "Maximum Z coordinate for random spawns");
		configs.addKeyValuePair(new Pair<>("MinX", -1000), "Minimum X coordinate for random spawns");
		configs.addKeyValuePair(new Pair<>("MaxX", 1000), "Maximum X coordinate for random spawns");
		configs.addKeyValuePair(new Pair<>("SpawnOnFirstJoin", true), "Teleport players to a random land location on first join");
		configs.addKeyValuePair(new Pair<>("SpawnOnRespawn", true), "Teleport players to a random land location on respawn when they have no bed/anchor");
	}

	private static void assignConfigs() {
		MinZ = CONFIG.getOrDefault("MinZ", -1000);
		MaxZ = CONFIG.getOrDefault("MaxZ", 1000);
		MinX = CONFIG.getOrDefault("MinX", -1000);
		MaxX = CONFIG.getOrDefault("MaxX", 1000);
		SpawnOnFirstJoin = CONFIG.getOrDefault("SpawnOnFirstJoin", true);
		SpawnOnRespawn = CONFIG.getOrDefault("SpawnOnRespawn", true);
	}
}
