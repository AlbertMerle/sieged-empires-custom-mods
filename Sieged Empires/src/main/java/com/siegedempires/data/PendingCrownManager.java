package com.siegedempires.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.siegedempires.Siegedempires;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks pending Duke/Duchess crown prompts until the target picks a title. */
public final class PendingCrownManager {
	public record CrownPrompt(String wartownId, String wartownName, String emperorName, UUID emperorUuid,
	                          boolean monarchAssigned) {
		public CrownPrompt(String wartownId, String wartownName, String emperorName, UUID emperorUuid) {
			this(wartownId, wartownName, emperorName, emperorUuid, false);
		}
	}

	private static final String FOLDER = "config/SiegedEmpires/PendingCrowns";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, CrownPrompt> PENDING = new ConcurrentHashMap<>();

	private PendingCrownManager() {
	}

	public static void initialize() {
		try {
			Files.createDirectories(Paths.get(FOLDER));
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to create pending crown folder", e);
		}
	}

	public static void set(UUID targetUuid, CrownPrompt prompt) {
		if (targetUuid == null || prompt == null) {
			return;
		}
		PENDING.put(targetUuid, prompt);
		save(targetUuid, prompt);
	}

	public static CrownPrompt get(UUID targetUuid) {
		if (targetUuid == null) {
			return null;
		}
		CrownPrompt cached = PENDING.get(targetUuid);
		if (cached != null) {
			return cached;
		}
		CrownPrompt loaded = load(targetUuid);
		if (loaded != null) {
			PENDING.put(targetUuid, loaded);
		}
		return loaded;
	}

	public static CrownPrompt clear(UUID targetUuid) {
		if (targetUuid == null) {
			return null;
		}
		delete(targetUuid);
		return PENDING.remove(targetUuid);
	}

	/** Recover a pending title pick from wartown state when disk cache is missing. */
	public static CrownPrompt resolveFromTownState(UUID playerUuid) {
		TownData town = TownDataManager.getInstance().getPlayerTown(playerUuid);
		if (town == null || !town.isWarTown() || !playerUuid.equals(town.getMonarchUuid())) {
			return null;
		}
		if (town.getMonarchTitle() != null && !town.getMonarchTitle().isEmpty()) {
			return null;
		}
		String empireId = town.getEmpireId();
		if (empireId == null || empireId.isEmpty()) {
			return null;
		}
		EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
		if (empire == null) {
			return null;
		}
		return new CrownPrompt(town.getId(), town.getName(), empire.getEmperorName(), empire.getEmperorUuid(), true);
	}

	public static CrownPrompt resolveForPlayer(UUID playerUuid) {
		CrownPrompt prompt = get(playerUuid);
		if (prompt != null) {
			return prompt;
		}
		return resolveFromTownState(playerUuid);
	}

	private static void save(UUID targetUuid, CrownPrompt prompt) {
		File file = crownFile(targetUuid);
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(prompt, writer);
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to save pending crown for " + targetUuid, e);
		}
	}

	private static CrownPrompt load(UUID targetUuid) {
		File file = crownFile(targetUuid);
		if (!file.exists()) {
			return null;
		}
		try (FileReader reader = new FileReader(file)) {
			return GSON.fromJson(reader, CrownPrompt.class);
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to load pending crown for " + targetUuid, e);
			return null;
		}
	}

	private static void delete(UUID targetUuid) {
		File file = crownFile(targetUuid);
		if (file.exists() && !file.delete()) {
			Siegedempires.LOGGER.warn("Failed to delete pending crown file for " + targetUuid);
		}
	}

	private static File crownFile(UUID targetUuid) {
		return new File(FOLDER, targetUuid + ".json");
	}
}
