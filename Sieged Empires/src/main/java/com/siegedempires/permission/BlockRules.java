package com.siegedempires.permission;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import java.util.Set;

import static com.siegedempires.permission.SimplifiedPermissionManager.Action;
import static com.siegedempires.permission.SimplifiedPermissionManager.Role;

public final class BlockRules {
    private BlockRules() {}

    public enum Category {
        CITIZEN_FULL, CITIZEN_PLACE_INTERACT, CITIZEN_INTERACT_ONLY,
        UNIVERSAL_FULL, UNIVERSAL_INTERACT, DEFAULT, DENIED;

        public boolean allows(Role role, Action action) {
            if (this == DENIED) return false;
            if (this == UNIVERSAL_FULL) return true;
            if (role == Role.EMPEROR || role == Role.MONARCH) return true;
            if (role == Role.LORD || role == Role.TRUSTED_CITIZEN) {
                if (action == Action.INTERACT) return true;
                return this != CITIZEN_PLACE_INTERACT && this != CITIZEN_INTERACT_ONLY;
            }
            if (this == UNIVERSAL_INTERACT) return action == Action.INTERACT;
            if (role == Role.CITIZEN) {
                return switch (this) {
                    case CITIZEN_FULL -> true;
                    case CITIZEN_PLACE_INTERACT -> action != Action.BREAK;
                    case CITIZEN_INTERACT_ONLY -> action == Action.INTERACT;
                    default -> false;
                };
            }
            return false;
        }
    }
public static Category classify(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (DENIED_IDS.contains(id)) return Category.DENIED;
        if (UNIVERSAL_FULL_IDS.contains(id)) return Category.UNIVERSAL_FULL;
        if (UNIVERSAL_INTERACT_IDS.contains(id)) return Category.UNIVERSAL_INTERACT;
        if (CITIZEN_FULL_IDS.contains(id)) return Category.CITIZEN_FULL;
        if (CITIZEN_PLACE_INTERACT_IDS.contains(id)) return Category.CITIZEN_PLACE_INTERACT;
        if (CITIZEN_INTERACT_ONLY_IDS.contains(id)) return Category.CITIZEN_INTERACT_ONLY;
        return Category.DEFAULT;
    }
private static final Set<String> DENIED_IDS = Set.of("tnt_minecart");

    private static final Set<String> UNIVERSAL_FULL_IDS = Set.of(
        "campfire", "soul_campfire",
        "torch", "soul_torch", "wall_torch", "soul_wall_torch",
        "lantern", "soul_lantern"
    );

    private static final Set<String> UNIVERSAL_INTERACT_IDS = Set.of(
        "lever", "stone_button", "oak_button", "spruce_button", "birch_button",
        "jungle_button", "acacia_button", "dark_oak_button", "mangrove_button",
        "cherry_button", "bamboo_button", "crimson_button", "warped_button",
        "polished_blackstone_button", "stone_pressure_plate",
        "oak_pressure_plate", "spruce_pressure_plate", "birch_pressure_plate",
        "heavy_weighted_pressure_plate", "light_weighted_pressure_plate",
        "redstone_wire", "redstone_torch", "redstone_wall_torch",
        "repeater", "comparator", "observer", "daylight_detector",
        "tripwire_hook", "target", "hopper",
        "oak_fence_gate", "spruce_fence_gate", "birch_fence_gate",
        "jungle_fence_gate", "acacia_fence_gate", "dark_oak_fence_gate",
        "mangrove_fence_gate", "cherry_fence_gate", "bamboo_fence_gate",
        "crimson_fence_gate", "warped_fence_gate", "pale_oak_fence_gate",
        "crafting_table", "grindstone", "smithing_table",
        "stonecutter", "cartography_table", "loom",
        "furnace", "blast_furnace", "smoker", "brewing_stand",
        "composter", "crafter", "enchanting_table",
        "jukebox", "chiseled_bookshelf",
        "barrel", "beehive", "bee_nest", "bell", "cauldron", "lectern",
        "anvil", "chipped_anvil", "damaged_anvil",
        "dispenser", "dropper", "noteblock", "ender_chest",
        "rail", "activator_rail", "detector_rail", "powered_rail"
    );

    private static final Set<String> CITIZEN_FULL_IDS = Set.of(
        "ladder", "scaffolding", "lantern", "soul_lantern",
        "torch", "soul_torch", "wall_torch", "soul_wall_torch",
        "candle", "white_candle", "orange_candle", "magenta_candle",
        "light_blue_candle", "yellow_candle", "lime_candle", "pink_candle",
        "gray_candle", "light_gray_candle", "cyan_candle", "purple_candle",
        "blue_candle", "brown_candle", "green_candle", "red_candle", "black_candle",
        "campfire", "soul_campfire", "flower_pot",
        "white_banner", "orange_banner", "magenta_banner", "light_blue_banner",
        "yellow_banner", "lime_banner", "pink_banner", "gray_banner",
        "light_gray_banner", "cyan_banner", "purple_banner", "blue_banner",
        "brown_banner", "green_banner", "red_banner", "black_banner",
        "ender_chest",
        "rail", "activator_rail", "detector_rail", "powered_rail"
    );
private static final Set<String> CITIZEN_PLACE_INTERACT_IDS = Set.of(
        "oak_sign", "spruce_sign", "birch_sign", "jungle_sign",
        "acacia_sign", "dark_oak_sign", "mangrove_sign", "cherry_sign",
        "bamboo_sign", "crimson_sign", "warped_sign",
        "oak_hanging_sign", "spruce_hanging_sign", "birch_hanging_sign",
        "jungle_hanging_sign", "acacia_hanging_sign", "dark_oak_hanging_sign",
        "mangrove_hanging_sign", "cherry_hanging_sign", "bamboo_hanging_sign",
        "crimson_hanging_sign", "warped_hanging_sign"
    );

    private static final Set<String> CITIZEN_INTERACT_ONLY_IDS = Set.of(
        "oak_door", "spruce_door", "birch_door", "jungle_door",
        "acacia_door", "dark_oak_door", "mangrove_door", "cherry_door",
        "bamboo_door", "crimson_door", "warped_door", "iron_door",
        "copper_door", "exposed_copper_door", "weathered_copper_door",
        "oxidized_copper_door", "waxed_copper_door", "waxed_exposed_copper_door",
        "waxed_weathered_copper_door", "waxed_oxidized_copper_door",
        "oak_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor",
        "acacia_trapdoor", "dark_oak_trapdoor", "mangrove_trapdoor",
        "cherry_trapdoor", "bamboo_trapdoor", "crimson_trapdoor",
        "warped_trapdoor", "iron_trapdoor", "copper_trapdoor",
        "exposed_copper_trapdoor", "weathered_copper_trapdoor",
        "oxidized_copper_trapdoor", "waxed_copper_trapdoor",
        "waxed_exposed_copper_trapdoor", "waxed_weathered_copper_trapdoor",
        "waxed_oxidized_copper_trapdoor",
        "oak_fence_gate", "spruce_fence_gate", "birch_fence_gate",
        "jungle_fence_gate", "acacia_fence_gate", "dark_oak_fence_gate",
        "mangrove_fence_gate", "cherry_fence_gate", "bamboo_fence_gate",
        "crimson_fence_gate", "warped_fence_gate", "pale_oak_fence_gate",
        "hopper", "lever", "stone_button", "oak_button",
        "spruce_button", "birch_button", "jungle_button", "acacia_button",
        "dark_oak_button", "mangrove_button", "cherry_button", "bamboo_button",
        "crimson_button", "warped_button", "polished_blackstone_button",
        "stone_pressure_plate", "oak_pressure_plate", "spruce_pressure_plate",
        "birch_pressure_plate", "heavy_weighted_pressure_plate",
        "light_weighted_pressure_plate", "redstone_wire", "redstone_torch",
        "redstone_wall_torch", "repeater", "comparator", "observer",
        "daylight_detector", "tripwire_hook", "target", "piston", "sticky_piston",
        "anvil", "chipped_anvil", "damaged_anvil", "dispenser", "dropper"
    );
}