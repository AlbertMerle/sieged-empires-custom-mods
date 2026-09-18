package com.weaponsmodaddon.sound;

import com.weaponsmodaddon.WeaponsModAddon;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom gun audio from {@code newsounds/} (baked into addon assets).
 */
public final class ModSounds {
	public static final SoundEvent MUSKET_FIRE = register("musket_fire");
	public static final SoundEvent FLINTLOCK_FIRE = register("flintlock_fire");
	/** ~8.2s clip; client loops for long reloads and stops when reload ends. */
	public static final SoundEvent GUN_RELOAD = registerReload("gun_reload");
	/** Client-only tinnitus after firing (~7s envelope). */
	public static final SoundEvent EAR_RING = register("ear_ring");

	private ModSounds() {
	}

	public static void register() {
		// Static fields register on class init; method exists so init is explicit from main.
	}

	private static SoundEvent register(String path) {
		var id = WeaponsModAddon.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Short fixed range — client scales volume to {@link AddonConfig#reloadSoundMaxRange}. */
	private static SoundEvent registerReload(String path) {
		var id = WeaponsModAddon.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, 12.0f));
	}
}
