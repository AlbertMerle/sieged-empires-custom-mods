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
 * Loads {@code assets/weaponsmodaddon/animations/player_spear_anims.json}
 * (from {@code spearanim.gltf}).
 */
public final class SpearAnimLibrary {
	public static final String HOLDING = "spear_holding";
	public static final String AIM = "spear_aim";
	public static final String AIM_HOLD = "spear_aimhold";
	public static final String THROW = "spear_throw";
	public static final String ATTACK = "spear_attack";

	/** Blockbench-style hold loops at 0.2× (same as gun holds). */
	public static final float HOLD_PLAYBACK_SPEED = 0.2F;
	/** Throw clip at 5.0× real-time (see {@link com.weaponsmodaddon.spear.SpearThrowTiming}). */
	public static final float THROW_PLAYBACK_SPEED = com.weaponsmodaddon.spear.SpearThrowTiming.THROW_PLAYBACK_SPEED;
	public static final float DEFAULT_PLAYBACK_SPEED = 1.0F;

	private static Map<String, GunAnimClip> clips = Map.of();

	private SpearAnimLibrary() {
	}

	public static void load() {
		String path = "/assets/weaponsmodaddon/animations/player_spear_anims.json";
		try (InputStream in = SpearAnimLibrary.class.getResourceAsStream(path)) {
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
			WeaponsModAddon.LOGGER.info("Loaded {} spear/javelin player animation clips", clips.size());
		} catch (Exception ex) {
			WeaponsModAddon.LOGGER.error("Failed to load spear animations", ex);
			clips = Map.of();
		}
	}

	public static GunAnimClip get(String name) {
		return clips.get(name);
	}

	public static float speedFor(String clipName) {
		return switch (clipName) {
			case HOLDING, AIM_HOLD -> HOLD_PLAYBACK_SPEED;
			case THROW -> THROW_PLAYBACK_SPEED;
			default -> DEFAULT_PLAYBACK_SPEED;
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
