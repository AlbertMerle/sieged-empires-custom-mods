package com.voxmapsync;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;

/** VoxelMap region tiles (256×256 blocks) vs the live world border. */
public final class WorldBorderRegions {
	private WorldBorderRegions() {
	}

	/** True when any part of the region tile intersects the actual border shape. */
	public static boolean overlaps(WorldBorder border, int regionX, int regionZ) {
		int minBlockX = regionX * 256;
		int maxBlockX = minBlockX + 255;
		int minBlockZ = regionZ * 256;
		int maxBlockZ = minBlockZ + 255;
		if (maxBlockX < border.getMinX() || minBlockX > border.getMaxX()
				|| maxBlockZ < border.getMinZ() || minBlockZ > border.getMaxZ()) {
			return false;
		}
		int midY = 64;
		return border.isWithinBounds(new BlockPos(minBlockX, midY, minBlockZ))
				|| border.isWithinBounds(new BlockPos(maxBlockX, midY, minBlockZ))
				|| border.isWithinBounds(new BlockPos(minBlockX, midY, maxBlockZ))
				|| border.isWithinBounds(new BlockPos(maxBlockX, midY, maxBlockZ))
				|| border.isWithinBounds(new BlockPos((minBlockX + maxBlockX) / 2, midY, (minBlockZ + maxBlockZ) / 2));
	}
}
