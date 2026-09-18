package com.siegedempires.client.gui;

import com.siegedempires.Siegedempires;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads {@code how_to_play.txt}. A trailing or standalone {@code -} marks a new line
 * (hyphens inside words like {@code game-changing} are kept).
 */
public final class HowToPlayText {
	private static final Identifier RESOURCE =
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "texts/how_to_play.txt");

	private static List<String> cachedLines;

	private HowToPlayText() {
	}

	public static List<String> lines() {
		if (cachedLines == null) {
			cachedLines = load();
		}
		return cachedLines;
	}

	private static List<String> load() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return List.of();
		}
		try (InputStream stream = minecraft.getResourceManager().open(RESOURCE);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			List<String> lines = new ArrayList<>();
			String fileLine;
			while ((fileLine = reader.readLine()) != null) {
				String trimmed = fileLine.strip();
				if (trimmed.isEmpty() || "-".equals(trimmed)) {
					lines.add("");
				} else if (trimmed.endsWith("-")) {
					lines.add(trimmed.substring(0, trimmed.length() - 1).stripTrailing());
				} else {
					lines.add(trimmed);
				}
			}
			// Drop trailing blank lines from the file.
			while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			return Collections.unmodifiableList(lines);
		} catch (IOException e) {
			Siegedempires.LOGGER.error("Failed to load how-to-play text", e);
			return List.of("Failed to load How to Play text.");
		}
	}
}
