package com.siegedempires.permission;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Classifies entities (boats, minecarts, etc.) into permission profiles.
 *
 * <p>Boats and minecarts are normally {@code Item}s in the inventory, but once
 * placed in the world they become {@code Entity} instances. Breaking them fires
 * {@code PlayerBlockBreakEvents.BEFORE} (because they occupy a block position),
 * placing them fires {@code BlockEvents.USE_ITEM_ON} (because they are
 * {@code BlockItem}s), and riding them fires {@code UseEntityCallback}.
 *
 * <p>This classifier lets {@code UseEntityCallback} apply the same
 * citizen-vs-outsider policy used for blocks, so the "outsiders may
 * <i>interact</i> but not break or place boats / minecarts" rule actually
 * fires when an outsider right-clicks to board one.
 *
 * <p>Entities not present in the classifier return {@code null}, which signals
 * to the caller that no permission check should be applied (vanilla behavior).
 */
public final class EntityPermissions {
    private EntityPermissions() {}

    /** TNT minecart as an entity is universally denied. */
    private static final String TNT_MINECART = "tnt_minecart";

    /** Classify a live entity. Returns {@code null} when no restriction applies. */
    public static BlockPermissionProfile classify(Entity entity) {
        EntityType<?> type = entity.getType();
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        return classifyById(id);
    }

    /** Classify by entity registry path. Returns {@code null} when unrestricted. */
    public static BlockPermissionProfile classifyById(String id) {
        // TNT minecart is universally denied.
        if (TNT_MINECART.equals(id)) return BlockPermissionProfile.DENIED;

        // Minecarts (all variants except tnt handled above).
        if (id.equals("minecart") || id.endsWith("_minecart")) return BlockPermissionProfile.CITIZEN_FULL_OUTSIDER_INTERACT;

        // Boats, rafts, chest boats, chest rafts.
        if (id.equals("boat") || id.equals("raft")
            || id.endsWith("_boat") || id.endsWith("_raft")
            || id.endsWith("_chest_boat") || id.endsWith("_chest_raft")) {
            return BlockPermissionProfile.CITIZEN_FULL_OUTSIDER_INTERACT;
        }

        // Every other entity (mobs, items, projectiles, villagers, etc.) is
        // unrestricted on claimed land - vanilla behavior applies.
        return null;
    }
}