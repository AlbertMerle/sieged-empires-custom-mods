package com.siegedempires.permission;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Classifies Minecraft blocks into a {@link BlockPermissionProfile} based on
 * their vanilla block registry ID.
 *
 * <p>This is the single source of truth for the per-block policy on claimed
 * land. Every block listed by the project owner appears below; anything not
 * listed falls back to {@link BlockPermissionProfile#DEFAULT_CITIZEN_ALLOWED}
 * so that citizens can build normally while outsiders remain denied.
 *
 * <p><b>Citizen (town member) policy:</b>
 * <ul>
 *   <li><b>Full access</b> (break, place, interact): ladders, scaffolding,
 *       lanterns, soul lanterns, torches, soul torches, all candles and
 *       candle cakes, campfires, soul campfires, flowerpots, all potted
 *       plants, all banners, all non-TNT minecarts, ender chests, all boats
 *       and rafts, all rails.</li>
 *   <li><b>Place + interact</b> (cannot break): all standing and wall signs,
 *       all hanging signs.</li>
 *   <li><b>Interact only</b> (cannot break or place): all doors, all
 *       trapdoors, all fence gates, hoppers, all redstone components,
 *       anvils, dispensers, droppers.</li>
 *   <li><b>Denied</b>: TNT minecart, crafting tables, grindstones, smithing
 *       tables, stonecutters, cartography tables, loom, all furnaces, brewing
 *       stands, composter, crafter, enchanting tables, jukeboxes, chiseled
 *       bookshelves, all villager workstation blocks, noteblocks.</li>
 * </ul>
 *
 * <p><b>Outsider (non-member, enemy, citizen of an allied town, non-affiliated
 * player) policy:</b>
 * <ul>
 *   <li><b>Full access</b>: torches, soul torches, lanterns, soul lanterns,
 *       campfires, soul campfires.</li>
 *   <li><b>Interact only</b>: all redstone components, hoppers, all fence
 *       gates, all boats and rafts, all non-TNT minecarts, ender chests,
 *       crafting tables, grindstones, smithing tables, stonecutters,
 *       cartography tables, loom, all furnaces, brewing stands, composter,
 *       crafter, enchanting tables, jukeboxes, chiseled bookshelves,
 *       dispensers, droppers, anvils, villager workstation blocks,
 *       noteblocks.</li>
 *   <li><b>Denied</b>: TNT minecart, and every other block not in the
 *       lists above.</li>
 * </ul>
 */
public final class BlockPermissions {
    /**
     * TNT minecart is universally denied until further notice (per project
     * owner instruction).
     */
    private static final Set<String> UNIVERSALLY_DENIED = Set.of(
        "tnt_minecart"
    );

    /** Both citizen and outsider: break + place + interact. */
    private static final Set<String> UNIVERSAL_FULL_IDS = Set.of(
        "torch", "wall_torch", "soul_torch", "soul_wall_torch",
        "lantern", "soul_lantern",
        "campfire", "soul_campfire"
    );

    /** Citizen: place + interact (cannot break). Outsider: denied. */
    private static final Set<String> CITIZEN_PLACE_INTERACT_IDS = Set.of(
        // No direct IDs; signs are matched by suffix below.
    );

    /** Citizen: interact only. Outsider: denied. */
    private static final Set<String> CITIZEN_INTERACT_ONLY_IDS = Set.of(
        // Doors and trapdoors are matched by suffix below.
    );

    /** Both citizen and outsider: interact only. */
    private static final Set<String> UNIVERSAL_INTERACT_IDS = Set.of(
        "hopper", "dispenser", "dropper",
        "anvil", "chipped_anvil", "damaged_anvil"
    );

    /** Citizen: denied. Outsider: interact only. */
    private static final Set<String> OUTSIDER_INTERACT_ONLY_IDS = Set.of(
        "crafting_table",
        "grindstone",
        "smithing_table",
        "stonecutter",
        "cartography_table",
        "loom",
        "fletching_table",
        "furnace", "blast_furnace", "smoker",
        "brewing_stand",
        "composter",
        "crafter",
        "enchanting_table",
        "jukebox",
        "chiseled_bookshelf",
        "note_block",
        // Villager workstation blocks beyond the explicitly-named ones above.
        "lectern",         // librarian
        "cauldron",        // leatherworker / some cleric recipes
        "barrel",          // fisherman
        "bell"             // villager meeting bell
    );

    /** Vanilla block IDs that are pure redstone components (universal interact). */
    private static final Set<String> REDSTONE_COMPONENT_IDS = Set.of(
        "repeater", "comparator", "observer", "piston", "sticky_piston",
        "tripwire", "tripwire_hook", "daylight_detector", "target",
        "trapped_chest", "lever", "redstone_wire", "redstone_block",
        "redstone_lamp", "redstone_torch", "redstone_wall_torch"
        // TNT and TNT minecart are intentionally NOT here.
        //   - tnt_minecart -> UNIVERSALLY_DENIED above.
        //   - tnt block    -> falls through to DEFAULT_CITIZEN_ALLOWED
        //     (citizens may use TNT in their own town; outsiders cannot touch it).
    );

    /** Rail block IDs. */
    private static final Set<String> RAIL_IDS = Set.of(
        "rail", "powered_rail", "detector_rail", "activator_rail"
    );

    /** Classify a Block instance by registry path. */
    public static BlockPermissionProfile classify(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return classifyById(id);
    }

    /** Classify a block by its vanilla block registry path (e.g. {@code oak_door}). */
    public static BlockPermissionProfile classifyById(String id) {
        // 1. Universal denylist (checked first so it overrides everything).
        if (UNIVERSALLY_DENIED.contains(id)) return BlockPermissionProfile.DENIED;

        // 2. Explicit per-ID buckets.
        if (UNIVERSAL_FULL_IDS.contains(id)) return BlockPermissionProfile.UNIVERSAL_FULL;
        if (UNIVERSAL_INTERACT_IDS.contains(id)) return BlockPermissionProfile.UNIVERSAL_INTERACT;
        if (OUTSIDER_INTERACT_ONLY_IDS.contains(id)) return BlockPermissionProfile.OUTSIDER_INTERACT_ONLY;
        if (CITIZEN_INTERACT_ONLY_IDS.contains(id)) return BlockPermissionProfile.CITIZEN_INTERACT_ONLY;
        if (CITIZEN_PLACE_INTERACT_IDS.contains(id)) return BlockPermissionProfile.CITIZEN_PLACE_INTERACT;

        // 3. Pattern matching for vanilla families.

        // Doors / trapdoors -> citizen interact only.
        if (id.endsWith("_door")) return BlockPermissionProfile.CITIZEN_INTERACT_ONLY;
        if (id.endsWith("_trapdoor")) return BlockPermissionProfile.CITIZEN_INTERACT_ONLY;

        // Fence gates -> universal interact.
        if (id.endsWith("_fence_gate")) return BlockPermissionProfile.UNIVERSAL_INTERACT;

        // Redstone components: buttons, levers, pressure plates, redstone_*, etc.
        if (id.endsWith("_button")) return BlockPermissionProfile.UNIVERSAL_INTERACT;
        if (id.endsWith("_pressure_plate")) return BlockPermissionProfile.UNIVERSAL_INTERACT;
        if (id.startsWith("redstone_") || id.contains("_redstone_")) return BlockPermissionProfile.UNIVERSAL_INTERACT;
        if (REDSTONE_COMPONENT_IDS.contains(id)) return BlockPermissionProfile.UNIVERSAL_INTERACT;

        // Candles and candle cakes (16 colors each).
        if (id.endsWith("_candle") || id.endsWith("_candle_cake")) return BlockPermissionProfile.CITIZEN_FULL;

        // Flower pots (empty + every potted_* variant).
        if (id.equals("flower_pot") || id.startsWith("potted_")) return BlockPermissionProfile.CITIZEN_FULL;

        // Banners (15 colors + wall variants).
        if (id.endsWith("_banner") || id.endsWith("_wall_banner")) return BlockPermissionProfile.CITIZEN_FULL;

        // Standing signs, wall signs, hanging signs, wall hanging signs.
        if (id.endsWith("_sign") || id.endsWith("_wall_sign")
            || id.endsWith("_hanging_sign") || id.endsWith("_wall_hanging_sign")) {
            return BlockPermissionProfile.CITIZEN_PLACE_INTERACT;
        }

        // Ladders and scaffolding.
        if (id.equals("ladder") || id.equals("scaffolding")) return BlockPermissionProfile.CITIZEN_FULL;

        // All rail types.
        if (RAIL_IDS.contains(id)) return BlockPermissionProfile.CITIZEN_FULL;

        // All minecart variants (TNT minecart already handled above).
        if (id.equals("minecart") || id.endsWith("_minecart")) return BlockPermissionProfile.CITIZEN_FULL_OUTSIDER_INTERACT;

        // All boats and rafts (oak_boat, birch_boat, ..., oak_chest_boat, ...).
        if (id.endsWith("_boat") || id.endsWith("_raft")
            || id.endsWith("_chest_boat") || id.endsWith("_chest_raft")
            || id.equals("boat") || id.equals("raft")) {
            return BlockPermissionProfile.CITIZEN_FULL_OUTSIDER_INTERACT;
        }

        // Ender chest.
        if (id.equals("ender_chest")) return BlockPermissionProfile.CITIZEN_FULL_OUTSIDER_INTERACT;

        // 4. Default fallback: citizens may break/place/interact (so they can
        //    build normally), outsiders are denied.
        return BlockPermissionProfile.DEFAULT_CITIZEN_ALLOWED;
    }
}