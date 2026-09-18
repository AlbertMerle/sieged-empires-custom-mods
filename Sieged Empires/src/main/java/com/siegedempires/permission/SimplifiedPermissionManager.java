package com.siegedempires.permission;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

public final class SimplifiedPermissionManager {

    private SimplifiedPermissionManager() {}

    public enum Action { BREAK, PLACE, INTERACT }

    public static boolean allowed(ServerPlayer player, BlockPos pos, Action action,
                                  Block block, boolean locked) {
        if (locked) return false;

        String dimension = player.level().dimension().identifier().toString();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        TownData town = TownDataManager.getInstance().getTownAtChunk(cx, cz, dimension);
        if (town == null) return true;

        UUID pid = player.getUUID();
        ChunkPosition chunk = new ChunkPosition(cx, cz, dimension);
        Role role = resolveRole(pid, town, chunk);
        BlockRules.Category cat = block == null ? BlockRules.Category.DEFAULT : BlockRules.classify(block);
        return cat.allows(role, action);
    }

    private static Role resolveRole(UUID pid, TownData town, ChunkPosition chunk) {
        String empireId = town.getEmpireId();
        if (empireId != null && !empireId.isEmpty()
            && EmpireDataManager.getInstance().isEmperor(pid)
            && empireId.equals(EmpireDataManager.getInstance().getEmpireForTown(town.getId()))) {
            return Role.EMPEROR;
        }
        if (pid.equals(town.getMonarchUuid())) return Role.MONARCH;
        if (town.isLord(pid)) return Role.LORD;
        String r = town.getMembers().get(pid);
        if ("Trusted Citizen".equals(r)) return Role.TRUSTED_CITIZEN;
        if (r != null && !"Monarch".equals(r) && !"Lord".equals(r)) {
            if (town.isCitizenChunkOwner(pid, chunk)) return Role.MONARCH;
            return Role.CITIZEN;
        }
        TownData playerTown = TownDataManager.getInstance().getPlayerTown(pid);
        if (DiplomacyActions.hasOpenBordersAccess(playerTown, town)) {
            return Role.CITIZEN;
        }
        return Role.OUTSIDER;
    }

    public enum Role { EMPEROR, MONARCH, LORD, TRUSTED_CITIZEN, CITIZEN, OUTSIDER }
}