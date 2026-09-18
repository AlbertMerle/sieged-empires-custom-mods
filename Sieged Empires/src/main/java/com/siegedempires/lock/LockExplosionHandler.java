package com.siegedempires.lock;

import com.siegedempires.item.LockItem;
import com.siegedempires.item.ModItems;
import com.siegedempires.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Explosions are one of the only ways to destroy a locked block
 * (lockpick is the other — implemented separately). When a locked
 * block is in an explosion's destroy list, drop the Lock item with
 * its password and clear lock data for all related positions.
 */
public final class LockExplosionHandler {

	private LockExplosionHandler() {}

	public static void onBlocksExploded(ServerLevel level, List<BlockPos> targetBlocks) {
		if (targetBlocks == null || targetBlocks.isEmpty()) return;

		Set<BlockPos> handled = new HashSet<>();
		boolean any = false;

		for (BlockPos pos : targetBlocks) {
			if (handled.contains(pos)) continue;
			if (!LockDataManager.isLocked(level, pos)) continue;

			BlockState state = level.getBlockState(pos);
			List<BlockPos> related = LockInteractionHandler.relatedPositions(state, pos);

			String password = null;
			for (BlockPos relatedPos : related) {
				String found = LockDataManager.getPassword(level, relatedPos);
				if (found != null) password = found;
			}

			if (password != null) {
				ItemStack lock = new ItemStack(ModItems.LOCK);
				LockItem.setPassword(lock, password);
				level.addFreshEntity(new ItemEntity(level,
					pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, lock));
			}

			for (BlockPos relatedPos : related) {
				LockDataManager.unlock(level, relatedPos);
				handled.add(relatedPos);
			}
			any = true;
		}

		if (any) {
			ModNetworking.broadcastLockSync(level);
		}
	}
}
