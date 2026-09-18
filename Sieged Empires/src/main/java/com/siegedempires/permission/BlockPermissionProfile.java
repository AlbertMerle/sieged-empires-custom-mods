package com.siegedempires.permission;

/**
 * Profile describing what actions are allowed by {@link #allowsCitizen citizens}
 * (town members of any role: Monarch, Lord, Trusted Citizen, Citizen, Emperor of
 * the parent empire) versus {@link #allowsOutsider outsiders} (non-members,
 * enemies, members of an allied town, and any non-affiliated player).
 *
 * <p>The action rules were specified by the project owner and must not be
 * loosened without explicit approval.
 */
public enum BlockPermissionProfile {
    /** Citizen: full access (break, place, interact). Outsider: interact only. */
    CITIZEN_FULL_OUTSIDER_INTERACT,

    /** Citizen: full access (break, place, interact). Outsider: denied. */
    CITIZEN_FULL,

    /** Citizen: place + interact (cannot break). Outsider: denied. */
    CITIZEN_PLACE_INTERACT,

    /** Citizen: interact only. Outsider: denied. */
    CITIZEN_INTERACT_ONLY,

    /** Citizen: full access. Outsider: full access. */
    UNIVERSAL_FULL,

    /** Citizen: interact only. Outsider: interact only. */
    UNIVERSAL_INTERACT,

    /** Citizen: denied. Outsider: interact only. */
    OUTSIDER_INTERACT_ONLY,

    /** Citizen and outsider: denied for every action. */
    DENIED,

    /**
     * Default fallback for blocks that are not explicitly listed in
     * {@link BlockPermissions}. Citizens have full access (so they can build
     * with dirt, stone, etc.) while outsiders cannot touch the block.
     */
    DEFAULT_CITIZEN_ALLOWED;

    /**
     * Whether a citizen (town member) may perform the given action on a block
     * assigned this profile.
     */
    public boolean allowsCitizen(PermissionType action) {
        switch (this) {
            case CITIZEN_FULL:
            case CITIZEN_FULL_OUTSIDER_INTERACT:
            case UNIVERSAL_FULL:
            case DEFAULT_CITIZEN_ALLOWED:
                return true;
            case CITIZEN_PLACE_INTERACT:
                return action == PermissionType.PLACE || action == PermissionType.INTERACT;
            case CITIZEN_INTERACT_ONLY:
            case UNIVERSAL_INTERACT:
                return action == PermissionType.INTERACT;
            case OUTSIDER_INTERACT_ONLY:
            case DENIED:
                return false;
            default:
                return false;
        }
    }

    /**
     * Whether an outsider (non-member, enemy, citizen of an ally town, or
     * non-affiliated player) may perform the given action on a block assigned
     * this profile.
     */
    public boolean allowsOutsider(PermissionType action) {
        switch (this) {
            case UNIVERSAL_FULL:
                return true;
            case CITIZEN_FULL_OUTSIDER_INTERACT:
            case UNIVERSAL_INTERACT:
            case OUTSIDER_INTERACT_ONLY:
                return action == PermissionType.INTERACT;
            case CITIZEN_FULL:
            case CITIZEN_PLACE_INTERACT:
            case CITIZEN_INTERACT_ONLY:
            case DENIED:
            case DEFAULT_CITIZEN_ALLOWED:
            default:
                return false;
        }
    }

    /**
     * Convenience overload combining both rules.
     *
     * @param outsider {@code true} when the player is not a member of the town
     * @param action   the action being attempted
     * @return whether the action is allowed
     */
    public boolean allows(boolean outsider, PermissionType action) {
        return outsider ? allowsOutsider(action) : allowsCitizen(action);
    }
}