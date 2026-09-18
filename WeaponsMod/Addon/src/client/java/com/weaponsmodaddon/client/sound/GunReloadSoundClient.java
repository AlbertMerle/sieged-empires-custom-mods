package com.weaponsmodaddon.client.sound;

import com.weaponsmodaddon.gun.GunReload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.player.Player;

/**
 * Starts / stops per-player reload audio from reload state (works for local + remote).
 */
public final class GunReloadSoundClient {
	private static final Map<UUID, GunReloadSoundInstance> ACTIVE = new HashMap<>();

	private GunReloadSoundClient() {
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		SoundManager sounds = client.getSoundManager();
		if (level == null || client.player == null) {
			clear(sounds);
			return;
		}

		Map<UUID, Player> present = new HashMap<>();
		for (Player player : level.players()) {
			present.put(player.getUUID(), player);
			UUID id = player.getUUID();
			if (GunReload.isReloading(player)) {
				if (!GunReloadSoundInstance.isWithinHearRange(client.player, player)) {
					stopOne(sounds, id);
					continue;
				}
				GunReloadSoundInstance existing = ACTIVE.get(id);
				boolean needsNew = existing == null
						|| existing.isStopped()
						|| !sounds.isActive(existing);
				if (needsNew) {
					if (existing != null && !existing.isStopped()) {
						sounds.stop(existing);
					}
					int duration = GunReload.reloadDurationTicks(player.getUseItem());
					GunReloadSoundInstance next = new GunReloadSoundInstance(player, duration);
					SoundEngine.PlayResult result = sounds.play(next);
					if (result == SoundEngine.PlayResult.STARTED
							|| result == SoundEngine.PlayResult.STARTED_SILENTLY) {
						ACTIVE.put(id, next);
					} else {
						ACTIVE.remove(id);
					}
				}
			} else {
				stopOne(sounds, id);
			}
		}

		Iterator<Map.Entry<UUID, GunReloadSoundInstance>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, GunReloadSoundInstance> entry = it.next();
			if (!present.containsKey(entry.getKey()) || entry.getValue().isStopped()) {
				if (!entry.getValue().isStopped()) {
					sounds.stop(entry.getValue());
				}
				it.remove();
			}
		}
	}

	public static void clear() {
		Minecraft client = Minecraft.getInstance();
		clear(client.getSoundManager());
	}

	private static void clear(SoundManager sounds) {
		for (GunReloadSoundInstance instance : ACTIVE.values()) {
			if (!instance.isStopped()) {
				sounds.stop(instance);
			}
		}
		ACTIVE.clear();
	}

	private static void stopOne(SoundManager sounds, UUID id) {
		GunReloadSoundInstance instance = ACTIVE.remove(id);
		if (instance != null && !instance.isStopped()) {
			sounds.stop(instance);
		}
	}
}
