package com.siegedempires.claim;

import com.siegedempires.Siegedempires;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import net.minecraft.server.level.ServerLevel;

/**
 * Placeholder for future FLAN integration.
 * <p>
 * FLAN 26.1.x is not compatible with Minecraft 26.2 (breaks on {@code Items.RED_BED}
 * during datapack reload). Land permissions are handled by {@link com.siegedempires.permission.PermissionManager}
 * until a 26.2 FLAN release is available.
 */
public final class FlanClaimBridge {

    private FlanClaimBridge() {}

    public static void claimChunk(TownData town, ChunkPosition chunk, ServerLevel level) {
        Siegedempires.LOGGER.debug("Town {} claimed chunk [{}, {}] in {}",
            town.getId(), chunk.getX(), chunk.getZ(), level.dimension().identifier());
    }
}
