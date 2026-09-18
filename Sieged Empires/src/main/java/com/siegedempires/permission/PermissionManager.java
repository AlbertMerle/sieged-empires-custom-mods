package com.siegedempires.permission;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.TownData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.UUID;

/**
 * Central claim policy per {@code layout_unfinished.txt} "Player/Claim Interaction".
 * Lock checks must run before this policy ({@code locked == true} always denies).
 */
public final class PermissionManager {
    private PermissionManager() {}

    public static boolean allowed(ServerPlayer player, BlockPos pos, PermissionType action, Block block, boolean locked) {
        if (locked) return false;

        String dimension = player.level().dimension().identifier().toString();
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        ChunkPosition chunk = new ChunkPosition(x, z, dimension);
        TownData town = TownDataManager.getInstance().getTownAtChunk(x, z, dimension);

        if (town == null) return true;

        // Active invasion privileges for the invading faction.
        // TNT / explosives in towns are invasion-only (invaders near the war flag).
        if (action == PermissionType.PLACE && isTntOrTntMinecart(block)) {
            return false;
        }

        if (InvasionManager.isInvaderInInvadedTerritory(player.getUUID(), town)) {
            if (InvasionManager.isWarBannerChunk(pos, dimension, town)) {
                return true;
            }
            if (action == PermissionType.PLACE && isTntOrTntMinecart(block)
                    && InvasionManager.isInWarBannerTntRadius(pos, dimension, town)) {
                return true;
            }
            BlockPermissionProfile invaderProfile = block == null
                    ? BlockPermissionProfile.DEFAULT_CITIZEN_ALLOWED
                    : BlockPermissions.classify(block);
            if (invaderProfile == BlockPermissionProfile.DENIED) {
                return false;
            }
            if (action == PermissionType.ENTER) {
                return true;
            }
            return invaderProfile.allowsCitizen(action);
        }

        UUID id = player.getUUID();
        ClaimRole role = resolveRole(id, town, chunk);
        boolean restricted = town.getRestrictedChunks().contains(chunk);

        if (action == PermissionType.ENTER) {
            return enterAllowed(role, restricted);
        }

        BlockPermissionProfile profile = block == null
            ? BlockPermissionProfile.DEFAULT_CITIZEN_ALLOWED
            : BlockPermissions.classify(block);

        if (profile == BlockPermissionProfile.DENIED) {
            return false;
        }

        return switch (role) {
            case EMPEROR, MONARCH, LORD -> true;
            // Trusted keep normal town powers in restricted chunks.
            case TRUSTED_CITIZEN -> trustedOrLordBlockAllowed(profile, action);
            // Regular citizens in restricted zones match outsider (non-citizen) town rules.
            case CITIZEN -> restricted
                    ? profile.allowsOutsider(action)
                    : profile.allowsCitizen(action);
            case OUTSIDER -> profile.allowsOutsider(action);
        };
    }

    private static boolean isTntOrTntMinecart(Block block) {
        if (block == null) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return "tnt".equals(id) || "tnt_minecart".equals(id);
    }

    /** Whether placing a TNT minecart item is allowed for an invader at this position. */
    public static boolean invaderMayPlaceTntMinecart(ServerPlayer player, BlockPos pos) {
        String dimension = player.level().dimension().identifier().toString();
        TownData town = TownDataManager.getInstance().getTownAtChunk(
                pos.getX() >> 4, pos.getZ() >> 4, dimension);
        if (town == null) {
            return true;
        }
        if (!InvasionManager.isInvaderInInvadedTerritory(player.getUUID(), town)) {
            return false;
        }
        return InvasionManager.isWarBannerChunk(pos, dimension, town)
                || InvasionManager.isInWarBannerTntRadius(pos, dimension, town);
    }

    static ClaimRole resolveRole(UUID id, TownData town, ChunkPosition chunk) {
        String empireId = town.getEmpireId();
        if (empireId != null && !empireId.isEmpty()) {
            EmpireDataManager empires = EmpireDataManager.getInstance();
            if (empires.isEmperor(id) && (empireId.equals(empires.getEmpireForTown(town.getId()))
                    || (town.isWarTown() && empireId.equals(town.getEmpireId())))) {
                return ClaimRole.EMPEROR;
            }
            // Wartowns act like capital land for any empire member.
            if (town.isWarTown()) {
                TownData playerTown = TownDataManager.getInstance().getPlayerTown(id);
                if (playerTown != null && empireId.equals(playerTown.getEmpireId())) {
                    return ClaimRole.CITIZEN;
                }
                return ClaimRole.OUTSIDER;
            }
        }

        if (id.equals(town.getMonarchUuid())) {
            return ClaimRole.MONARCH;
        }
        if (town.isLord(id)) {
            return ClaimRole.LORD;
        }

        String memberRole = town.getMembers().get(id);
        if ("Trusted Citizen".equals(memberRole)) {
            return ClaimRole.TRUSTED_CITIZEN;
        }
        if (memberRole != null && !"Monarch".equals(memberRole) && !"Lord".equals(memberRole)) {
            // Citizen land grants do not override Restricted Zones.
            boolean restricted = town.getRestrictedChunks().contains(chunk);
            if (!restricted && town.isCitizenChunkOwner(id, chunk)) {
                return ClaimRole.MONARCH;
            }
            return ClaimRole.CITIZEN;
        }

        // Open Borders: visiting citizens of an allied town/empire get citizen access.
        TownData playerTown = TownDataManager.getInstance().getPlayerTown(id);
        if (DiplomacyActions.hasOpenBordersAccess(playerTown, town)) {
            return ClaimRole.CITIZEN;
        }

        return ClaimRole.OUTSIDER;
    }

    private static boolean enterAllowed(ClaimRole role, boolean restricted) {
        return switch (role) {
            case EMPEROR, MONARCH, LORD, TRUSTED_CITIZEN -> true;
            // Citizens may physically walk in (warning title); ENTER denials are not
            // used to teleport them out. Outsiders still treated as non-privileged.
            case CITIZEN, OUTSIDER -> !restricted;
        };
    }

    /**
     * Trusted citizens and lords may build freely with any block except those on
     * the citizen denied / interact-only lists (workstations, doors for break, etc.).
     */
    private static boolean trustedOrLordBlockAllowed(BlockPermissionProfile profile, PermissionType action) {
        if (profile == BlockPermissionProfile.DEFAULT_CITIZEN_ALLOWED) {
            return true;
        }
        return profile.allowsCitizen(action);
    }

    /** Exposed for entity interaction checks in {@link com.siegedempires.Siegedempires}. */
    public static ClaimRole roleAt(ServerPlayer player, BlockPos pos) {
        String dimension = player.level().dimension().identifier().toString();
        int x = pos.getX() >> 4, z = pos.getZ() >> 4;
        TownData town = TownDataManager.getInstance().getTownAtChunk(x, z, dimension);
        if (town == null) return null;
        if (InvasionManager.isInvaderInInvadedTerritory(player.getUUID(), town)) {
            return ClaimRole.CITIZEN;
        }
        return resolveRole(player.getUUID(), town, new ChunkPosition(x, z, dimension));
    }

    /**
     * Whether {@code attacker} may deal PvP damage to {@code victim}.
     * <p>
     * Wilderness: always allowed. Inside a town claim: PvP is disabled by default.
     * Citizens, lords, monarchs, emperors, and members of other towns in the same empire
     * may PvP each other. Non-citizens and enemies gain a timed raid window (with boss bar)
     * when attacked, configured via {@code non-citizen-pvp-cooldown} in settings.json.
     */
    public static boolean pvpAllowed(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == null || victim == null) {
            return true;
        }

        BlockPos victimPos = victim.blockPosition();
        String dimension = victim.level().dimension().identifier().toString();
        int victimChunkX = victimPos.getX() >> 4;
        int victimChunkZ = victimPos.getZ() >> 4;
        TownData claimTown = TownDataManager.getInstance().getTownAtChunk(victimChunkX, victimChunkZ, dimension);

        if (claimTown == null) {
            return true;
        }

        Boolean policyOverride = PvpPolicyChecker.check(attacker, victim, claimTown);
        if (policyOverride != null) {
            return policyOverride;
        }

        boolean attackerAffiliated = isAffiliatedWithClaim(attacker.getUUID(), claimTown);
        boolean victimAffiliated = isAffiliatedWithClaim(victim.getUUID(), claimTown);

        if (attackerAffiliated && victimAffiliated) {
            return true;
        }

        if (TownPvpWindowManager.hasActiveWindow(attacker.getUUID(), claimTown)
                || TownPvpWindowManager.hasActiveWindow(victim.getUUID(), claimTown)) {
            return true;
        }

        if (!victimAffiliated) {
            TownPvpWindowManager.activate(victim, claimTown);
            return true;
        }

        return false;
    }

    /**
     * Whether {@code playerId} belongs to {@code claimTown} or another town in the same empire.
     */
    public static boolean isAffiliatedWithClaim(UUID playerId, TownData claimTown) {
        if (claimTown.isWarTown()) {
            String empireId = claimTown.getEmpireId();
            if (empireId == null || empireId.isEmpty()) {
                return false;
            }
            var empire = EmpireDataManager.getInstance().getEmpire(empireId);
            if (empire != null && empire.getEmperorUuid().equals(playerId)) {
                return true;
            }
            TownData playerTown = TownDataManager.getInstance().getPlayerTown(playerId);
            return playerTown != null && empireId.equals(playerTown.getEmpireId());
        }

        if (playerId.equals(claimTown.getMonarchUuid()) || claimTown.isLord(playerId)) {
            return true;
        }

        String memberRole = claimTown.getMembers().get(playerId);
        if (memberRole != null && !"Monarch".equals(memberRole) && !"Lord".equals(memberRole)) {
            return true;
        }

        String empireId = claimTown.getEmpireId();
        if (empireId == null || empireId.isEmpty()) {
            return false;
        }

        var empire = EmpireDataManager.getInstance().getEmpire(empireId);
        if (empire != null && empire.getEmperorUuid().equals(playerId)) {
            return true;
        }

        TownData playerTown = TownDataManager.getInstance().getPlayerTown(playerId);
        return playerTown != null && empireId.equals(playerTown.getEmpireId());
    }

    public static boolean isMemberOrAbove(ClaimRole role) {
        return role != null && role != ClaimRole.OUTSIDER;
    }
}
