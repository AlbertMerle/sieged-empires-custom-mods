package com.siegedempires.permission;

import com.siegedempires.model.TownData;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pluggable overrides for town-claim PvP. Registered checks run before default rules;
 * return {@code true}/{@code false} to force allow/deny, or {@code null} to defer.
 */
public final class PvpPolicyChecker {
	@FunctionalInterface
	public interface PvpCheck {
		/**
		 * @return {@code true} to allow, {@code false} to deny, or {@code null} for default rules
		 */
		Boolean evaluate(ServerPlayer attacker, ServerPlayer victim, TownData claimTown);
	}

	private static final List<PvpCheck> CHECKS = new CopyOnWriteArrayList<>();

	private PvpPolicyChecker() {
	}

	public static void register(PvpCheck check) {
		CHECKS.add(check);
	}

	public static Boolean check(ServerPlayer attacker, ServerPlayer victim, TownData claimTown) {
		for (PvpCheck check : CHECKS) {
			Boolean result = check.evaluate(attacker, victim, claimTown);
			if (result != null) {
				return result;
			}
		}
		return null;
	}
}
