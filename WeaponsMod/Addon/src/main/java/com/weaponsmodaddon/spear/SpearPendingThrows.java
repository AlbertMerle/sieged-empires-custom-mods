package com.weaponsmodaddon.spear;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side delayed spear throws: release starts the throw clip; projectile spawns at halfway
 * (item stays in hand until then, then shrinks).
 */
public final class SpearPendingThrows {
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private SpearPendingThrows() {
	}

	public static boolean hasPending(Player player) {
		return PENDING.containsKey(player.getUUID());
	}

	public static void schedule(Player player, ItemStack spearCopy, InteractionHand hand, float power, boolean crit) {
		PENDING.put(
				player.getUUID(),
				new Pending(spearCopy.copy(), hand, power, crit, SpearThrowTiming.releaseDelayTicks()));
	}

	public static void cancel(Player player) {
		PENDING.remove(player.getUUID());
	}

	public static void tickServer(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> e = it.next();
			Pending pending = e.getValue();
			pending.ticksLeft--;
			if (pending.ticksLeft > 0) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive()) {
				continue;
			}
			SpearChargeThrow.spawnThrownSpear(player, pending.spear, pending.hand, pending.power, pending.crit);
		}
	}

	private static final class Pending {
		final ItemStack spear;
		final InteractionHand hand;
		final float power;
		final boolean crit;
		int ticksLeft;

		Pending(ItemStack spear, InteractionHand hand, float power, boolean crit, int ticksLeft) {
			this.spear = spear;
			this.hand = hand;
			this.power = power;
			this.crit = crit;
			this.ticksLeft = ticksLeft;
		}
	}
}
