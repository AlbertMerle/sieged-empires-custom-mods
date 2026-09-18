package com.siegedempires.permission;

import com.siegedempires.data.TownDataManager;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.model.TownData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Suppresses explosions inside town claims until that town is under an active
 * invasion. Block lists are filtered at the blast edge; entity damage and
 * full detonations are cancelled when the center or target lies in a
 * protected claim.
 */
public final class ExplosionProtection {
	private ExplosionProtection() {
	}

	public static boolean isBlockProtected(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}
		String dimension = level.dimension().identifier().toString();
		TownData town = TownDataManager.getInstance().getTownAtChunk(
				pos.getX() >> 4, pos.getZ() >> 4, dimension);
		if (town == null) {
			return false;
		}
		return !InvasionManager.isTownBeingInvaded(town);
	}

	/** Whether an explosion centered here should be fully suppressed (no blocks, entities, or effects). */
	public static boolean isExplosionSuppressedAt(ServerLevel level, Vec3 center) {
		if (level == null || center == null) {
			return false;
		}
		return isBlockProtected(level, BlockPos.containing(center));
	}

	/** Whether explosion damage may be applied to {@code entity} (vanilla or WeaponsMod). */
	public static boolean shouldDamageEntity(ServerLevel level, Vec3 center, Entity entity) {
		if (level == null || entity == null) {
			return true;
		}
		if (center != null && isExplosionSuppressedAt(level, center)) {
			return false;
		}
		return !isBlockProtected(level, entity.blockPosition());
	}

	/** Removes protected town blocks from an explosion's destroy/fire list. */
	public static void filterProtectedBlocks(ServerLevel level, List<BlockPos> blocks) {
		if (level == null || blocks == null || blocks.isEmpty()) {
			return;
		}
		blocks.removeIf(pos -> isBlockProtected(level, pos));
	}
}
