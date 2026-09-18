package com.siegedempires.permission;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

/**
 * Enforces town-claim PvP rules via {@link PermissionManager#pvpAllowed(ServerPlayer, ServerPlayer)}.
 * Non-citizens attacked in a claim receive a timed raid boss bar from {@link TownPvpWindowManager}.
 */
public final class PvpProtectionInitializer {
	private PvpProtectionInitializer() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer victim)) {
				return true;
			}
			Entity attackerEntity = source.getEntity();
			if (!(attackerEntity instanceof ServerPlayer attacker)) {
				return true;
			}
			return PermissionManager.pvpAllowed(attacker, victim);
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer attacker)
					|| !(entity instanceof ServerPlayer victim)) {
				return InteractionResult.PASS;
			}
			return PermissionManager.pvpAllowed(attacker, victim)
					? InteractionResult.PASS
					: InteractionResult.FAIL;
		});
	}
}
