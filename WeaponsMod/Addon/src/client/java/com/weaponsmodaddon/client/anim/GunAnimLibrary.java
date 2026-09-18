package com.weaponsmodaddon.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.weaponsmodaddon.WeaponsModAddon;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.GsonHelper;

/**
 * Loads {@code assets/weaponsmodaddon/animations/player_gun_anims.json}.
 */
public final class GunAnimLibrary {
	public static final String GUN_HOLDING = "gun_holding";
	public static final String GUN_AIM = "gunaim";
	public static final String GUN_HOLD_AIM = "gunholdaim";
	public static final String GUN_FIRE = "gunfire";
	public static final String GUN_RELOAD = "gunreload_musket";
	public static final String PISTOL_AIM = "pistolaim";
	public static final String PISTOL_AIM_HOLD = "pistolaimhold";
	public static final String PISTOL_FIRE = "pistolfire";

	/** Crawl variants from {@code crawlanim.gltf} (long guns only). */
	public static final String CRAWL_HOLDING = "crawling_holding";
	public static final String CRAWL_AIM = "crawling_aim";
	public static final String CRAWL_HOLD_AIM = "crawling_aimhold";
	public static final String CRAWL_FIRE = "crawling_firegun";
	public static final String CRAWL_RELOAD = "crawling_reload";

	/** Blockbench playback 20 vs default 100 → 0.2× real-time. */
	public static final float HOLD_PLAYBACK_SPEED = 0.2F;
	public static final float DEFAULT_PLAYBACK_SPEED = 1.0F;

	private static Map<String, GunAnimClip> clips = Map.of();

	private GunAnimLibrary() {
	}

	public static void load() {
		String path = "/assets/weaponsmodaddon/animations/player_gun_anims.json";
		try (InputStream in = GunAnimLibrary.class.getResourceAsStream(path)) {
			if (in == null) {
				WeaponsModAddon.LOGGER.error("Missing animation resource {}", path);
				clips = Map.of();
				return;
			}
			JsonObject root = GsonHelper.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			JsonObject anims = GsonHelper.getAsJsonObject(root, "animations");
			Map<String, GunAnimClip> loaded = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
				loaded.put(e.getKey(), parseClip(e.getKey(), e.getValue().getAsJsonObject()));
			}
			clips = Map.copyOf(loaded);
			WeaponsModAddon.LOGGER.info("Loaded {} gun player animation clips", clips.size());
		} catch (Exception ex) {
			WeaponsModAddon.LOGGER.error("Failed to load gun animations", ex);
			clips = Map.of();
		}
	}

	public static GunAnimClip get(String name) {
		return clips.get(name);
	}

	public static float speedFor(String clipName) {
		return switch (clipName) {
			case GUN_HOLDING, GUN_HOLD_AIM, PISTOL_AIM_HOLD, CRAWL_HOLDING, CRAWL_HOLD_AIM -> HOLD_PLAYBACK_SPEED;
			default -> DEFAULT_PLAYBACK_SPEED;
		};
	}

	/** Standing long-gun clip → crawl counterpart (same phase speeds). */
	public static String crawlVariant(String standingClip) {
		if (standingClip == null) {
			return null;
		}
		return switch (standingClip) {
			case GUN_HOLDING -> CRAWL_HOLDING;
			case GUN_AIM -> CRAWL_AIM;
			case GUN_HOLD_AIM -> CRAWL_HOLD_AIM;
			case GUN_FIRE -> CRAWL_FIRE;
			case GUN_RELOAD -> CRAWL_RELOAD;
			default -> standingClip;
		};
	}

	private static GunAnimClip parseClip(String name, JsonObject obj) {
		float duration = GsonHelper.getAsFloat(obj, "duration");
		JsonObject bonesObj = GsonHelper.getAsJsonObject(obj, "bones");
		Map<String, GunAnimClip.BoneChannels> bones = new HashMap<>();
		for (Map.Entry<String, JsonElement> be : bonesObj.entrySet()) {
			JsonObject bone = be.getValue().getAsJsonObject();
			List<GunAnimClip.Keyframe> rotation = parseKeys(bone, "rotation");
			List<GunAnimClip.Keyframe> translation = parseKeys(bone, "translation");
			bones.put(be.getKey(), new GunAnimClip.BoneChannels(rotation, translation));
		}
		return new GunAnimClip(name, duration, GunAnimClip.unmodifiable(bones));
	}

	private static List<GunAnimClip.Keyframe> parseKeys(JsonObject bone, String channel) {
		if (!bone.has(channel)) {
			return List.of();
		}
		JsonArray arr = GsonHelper.getAsJsonArray(bone, channel);
		List<GunAnimClip.Keyframe> keys = new ArrayList<>(arr.size());
		for (JsonElement el : arr) {
			JsonObject k = el.getAsJsonObject();
			keys.add(new GunAnimClip.Keyframe(
					GsonHelper.getAsFloat(k, "t"),
					GsonHelper.getAsFloat(k, "x"),
					GsonHelper.getAsFloat(k, "y"),
					GsonHelper.getAsFloat(k, "z")
			));
		}
		return List.copyOf(keys);
	}
}
