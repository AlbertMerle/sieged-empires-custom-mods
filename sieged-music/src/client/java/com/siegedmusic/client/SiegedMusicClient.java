package com.siegedmusic.client;

import com.siegedmusic.client.music.GameMusicPlaylist;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SiegedMusicClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> GameMusicPlaylist.onEnterWorld());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GameMusicPlaylist.onLeaveWorld());
	}
}
