package com.siegedempires.client.tutorial;

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
 * Client-only tutorial flags (first-time Game Menu Info highlight).
 * Saved under {@code config/SiegedEmpires/client_tutorial.json}.
 */
public final class ClientTutorialState {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = Path.of("config", "SiegedEmpires", "client_tutorial.json");
	private static ClientTutorialState INSTANCE;

	@SerializedName("has_seen_how_to_play")
	public boolean hasSeenHowToPlay = false;

	private ClientTutorialState() {
	}

	public static ClientTutorialState get() {
		if (INSTANCE == null) {
			load();
		}
		return INSTANCE;
	}

	public static boolean needsInfoAttention() {
		return !get().hasSeenHowToPlay;
	}

	public static void markHowToPlaySeen() {
		ClientTutorialState state = get();
		if (state.hasSeenHowToPlay) {
			return;
		}
		state.hasSeenHowToPlay = true;
		save();
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				INSTANCE = GSON.fromJson(reader, ClientTutorialState.class);
				if (INSTANCE == null) {
					INSTANCE = new ClientTutorialState();
				}
			} catch (IOException e) {
				Siegedempires.LOGGER.error("Failed to load client tutorial state", e);
				INSTANCE = new ClientTutorialState();
			}
		} else {
			INSTANCE = new ClientTutorialState();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(get(), writer);
			}
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to save client tutorial state", e);
		}
	}
}
