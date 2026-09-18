package com.siegedempires.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.siegedempires.Siegedempires;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gameplay settings saved to {@code config/SiegedEmpires/settings.json}.
 */
public final class ModSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = Path.of("config", "SiegedEmpires", "settings.json");
	private static ModSettings INSTANCE;

	@SerializedName("lockpick_duration_seconds")
	public int lockpickDurationSeconds = 30;

	/** Chance (0–100) that a completed lockpick attempt opens the lock. */
	@SerializedName("lockpick_success_percent")
	public int lockpickSuccessPercent = 5;

	/**
	 * How many other town members must be online (besides the lockpicker) for a
	 * citizen to lockpick inside their town. Invaders ignore this requirement.
	 */
	@SerializedName("lockpick-min-other-online")
	public int lockpickMinOtherOnline = 2;

	/**
	 * Max HP for vanilla boats/rafts (not Shippy Ships). Unlike vanilla's regenerating
	 * damage meter, this does not heal, so boats can actually be crashed/destroyed.
	 */
	@SerializedName("vanilla-boat-health")
	public float vanillaBoatHealth = 30.0F;

	/** Seconds a non-citizen may PvP in a town/empire claim after being attacked. */
	@SerializedName("non-citizen-pvp-cooldown")
	public int nonCitizenPvpCooldownSeconds = 30;

	/** Minimum online players in the target town/empire required to start an invasion. */
	@SerializedName("invasion-min-online-players")
	public int invasionMinOnlinePlayers = 3;

	/** How long an active invasion lasts, in seconds (default 20 minutes). */
	@SerializedName("invasion-duration-seconds")
	public int invasionDurationSeconds = 1200;

	/** Cooldown after an invasion ends before the same pair can fight again, in seconds (default 1 hour). */
	@SerializedName("invasion-cooldown-seconds")
	public int invasionCooldownSeconds = 3600;

	/** Hit points for a placed War Banner entity (default 120). */
	@SerializedName("war-banner-health")
	public int warBannerHealth = 120;

	/**
	 * Full-sail max speed for Shippy Ships sailboats and cogs, in blocks per second
	 * (clear weather). Open sail is still 0–100% of this cap.
	 */
	@SerializedName("shippy-sailboat-cog-full-sail-bps")
	public float shippySailboatCogFullSailBps = 12.0F;

	/**
	 * Full-sail max speed for Shippy Ships caravels, in blocks per second (clear weather).
	 */
	@SerializedName("shippy-caravel-full-sail-bps")
	public float shippyCaravelFullSailBps = 10.0F;

	/** Gold ingot cost to start an invasion (default 2). */
	@SerializedName("invasion-price")
	public int invasionPrice = 2;

	/** Gold ingot cost to create a town (default 5). */
	@SerializedName("create-town-cost")
	public int createTownCost = 5;

	/**
	 * World scale shown in the Sieged Empire Guide book (default {@code 1:60}).
	 */
	@SerializedName("world-scale-book")
	public String worldScaleBook = "1:60";

	public static final String DEFAULT_JOIN_BUTTON_IP_URL =
			"https://gist.githubusercontent.com/AlbertMerle/f874dad80a72f0273f06bc58348ec9b8/raw";

	/**
	 * Server address for the title-screen "Join Sieged Empires" button
	 * (host or host:port). Used until {@code join-button-ip-url} succeeds, and
	 * as fallback if the URL is blank or unreachable. Blank disables joining
	 * when no gist address has been fetched.
	 */
	@SerializedName("join-button-ip")
	public String joinButtonIp = "";

	/**
	 * HTTP URL whose body is one line of {@code host} or {@code host:port}
	 * (GitHub gist raw). Blank disables remote lookup. Missing from JSON uses
	 * the default gist.
	 */
	@SerializedName("join-button-ip-url")
	public String joinButtonIpUrl = DEFAULT_JOIN_BUTTON_IP_URL;

	public static ModSettings get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				INSTANCE = GSON.fromJson(reader, ModSettings.class);
				if (INSTANCE == null) {
					INSTANCE = new ModSettings();
				}
				INSTANCE.clampValues();
				save();
				Siegedempires.LOGGER.info(
						"Loaded settings (lockpick: {}s, {}% success, min other online: {}, boat HP: {}, non-citizen PvP: {}s, invasion min online: {}, war banner HP: {}, invasion price: {}, create town: {}, world scale: {})",
						INSTANCE.lockpickDurationSeconds, INSTANCE.lockpickSuccessPercent,
						INSTANCE.lockpickMinOtherOnline, INSTANCE.vanillaBoatHealth,
						INSTANCE.nonCitizenPvpCooldownSeconds, INSTANCE.invasionMinOnlinePlayers,
						INSTANCE.warBannerHealth, INSTANCE.invasionPrice, INSTANCE.createTownCost,
						INSTANCE.worldScaleBook);
			} catch (IOException e) {
				Siegedempires.LOGGER.error("Failed to load settings, using defaults", e);
				INSTANCE = new ModSettings();
				save();
			}
		} else {
			INSTANCE = new ModSettings();
			save();
		}
	}

	public static void save() {
		if (INSTANCE == null) {
			INSTANCE = new ModSettings();
		}
		INSTANCE.clampValues();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(INSTANCE, writer);
			}
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to save settings", e);
		}
	}

	private void clampValues() {
		lockpickDurationSeconds = Math.max(1, lockpickDurationSeconds);
		lockpickSuccessPercent = Math.max(0, Math.min(100, lockpickSuccessPercent));
		lockpickMinOtherOnline = Math.max(0, lockpickMinOtherOnline);
		vanillaBoatHealth = Math.max(1.0F, vanillaBoatHealth);
		nonCitizenPvpCooldownSeconds = Math.max(1, nonCitizenPvpCooldownSeconds);
		invasionMinOnlinePlayers = Math.max(1, invasionMinOnlinePlayers);
		invasionDurationSeconds = Math.max(60, invasionDurationSeconds);
		invasionCooldownSeconds = Math.max(0, invasionCooldownSeconds);
		warBannerHealth = Math.max(1, warBannerHealth);
		shippySailboatCogFullSailBps = Math.max(0.1F, shippySailboatCogFullSailBps);
		shippyCaravelFullSailBps = Math.max(0.1F, shippyCaravelFullSailBps);
		invasionPrice = Math.max(0, invasionPrice);
		createTownCost = Math.max(0, createTownCost);
		if (worldScaleBook == null || worldScaleBook.isBlank()) {
			worldScaleBook = "1:60";
		} else {
			worldScaleBook = worldScaleBook.trim();
		}
		if (joinButtonIp == null) {
			joinButtonIp = "";
		} else {
			joinButtonIp = joinButtonIp.trim();
		}
		if (joinButtonIpUrl == null) {
			joinButtonIpUrl = DEFAULT_JOIN_BUTTON_IP_URL;
		} else {
			joinButtonIpUrl = joinButtonIpUrl.trim();
		}
	}

	/** Trimmed packed join address (fallback), or empty when unset. */
	public String joinButtonAddress() {
		return joinButtonIp == null ? "" : joinButtonIp.trim();
	}

	/** Trimmed lookup URL, or empty when remote lookup is disabled. */
	public String joinButtonIpUrl() {
		return joinButtonIpUrl == null ? "" : joinButtonIpUrl.trim();
	}

	public int lockpickDurationTicks() {
		return lockpickDurationSeconds * 20;
	}

	public int nonCitizenPvpCooldownTicks() {
		return nonCitizenPvpCooldownSeconds * 20;
	}

	public int invasionDurationTicks() {
		return invasionDurationSeconds * 20;
	}

	public long invasionCooldownMillis() {
		return invasionCooldownSeconds * 1000L;
	}
}
