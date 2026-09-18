package nl.ThebigTijn.randomspawn.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

public class JoinData {

	public static boolean isFirstJoin(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) <= 10;
	}

}