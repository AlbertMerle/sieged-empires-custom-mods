package com.siegedempires.permission;

/**
 * Player role within a specific claimed chunk, derived from town membership,
 * empire leadership, and per-chunk citizen land grants.
 */
public enum ClaimRole {
    /** Emperor/empress of the empire this town belongs to — full access in every member town. */
    EMPEROR,
    /** Monarch of this town — full access everywhere in their town. */
    MONARCH,
    /** Lord of this town — full access, including restricted chunks. */
    LORD,
    /** Trusted citizen — broad access including restricted chunks (denied-block list still applies). */
    TRUSTED_CITIZEN,
    /** Regular citizen — whitelist blocks town-wide; full autonomy only on granted chunks. */
    CITIZEN,
    /** Non-member (enemy, ally outsider, unaffiliated player, or monarch of another town). */
    OUTSIDER
}
