package com.siegedempires.config;

import com.siegedempires.Siegedempires;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Central configuration for Sieged Empires.
 * <p>
 * Defines: diamond costs, max town members, role-based permissions,
 * and which FLAN permission identifiers each town role grants.
 * <p>
 * Saved to {@code config/SiegedEmpires/config.json} and auto-reloaded.
 * Uses a simple JSON format — no annotations required.
 */
public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig INSTANCE;
    private static Path configPath;

    // ── Economy ───────────────────────────────────────────────
    @SerializedName("town_create_cost") public int townCreateCost = 5;
    @SerializedName("empire_create_cost") public int empireCreateCost = 8;
    @SerializedName("max_town_members") public int maxTownMembers = 50;

    // ── Claiming ──────────────────────────────────────────────
    @SerializedName("claim_radius") public int claimRadius = 1;
    @SerializedName("max_claims_per_town") public int maxClaimsPerTown = 200;

    // ── Role-based FLAN permission mappings ───────────────────
    /**
     * Each town role maps to a set of FLAN permission Identifiers.
     * "Monarch" and "Lord" get all permissions by default.
     * "Citizen" gets break, place, open_container, doors.
     * "Trusted Citizen" gets citizen + interact_block.
     * "Outsider" gets nothing (FLAN denies by default on claimed land).
     */
    @SerializedName("role_permissions")
    public Map<String, List<String>> rolePermissions = new LinkedHashMap<>();

    // ── Cache ─────────────────────────────────────────────────
    /** Cached town lookup for quick permission resolution (server-side). */
    @SerializedName("_cache_version") public int cacheVersion = 1;

    private ModConfig() {
        // Default role permissions
        rolePermissions.put("Monarch", List.of("*"));
        rolePermissions.put("Lord", List.of("*"));
        rolePermissions.put("Trusted Citizen", List.of(
            "flan:break", "flan:place", "flan:open_container",
            "flan:door", "flan:trapdoor", "flan:fence_gate",
            "flan:interact_block"
        ));
        rolePermissions.put("Citizen", List.of(
            "flan:break", "flan:place", "flan:open_container",
            "flan:door", "flan:trapdoor", "flan:fence_gate"
        ));
        rolePermissions.put("Outsider", List.of());
    }

    // ── Singleton ─────────────────────────────────────────────

    public static ModConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        if (configPath == null) {
            configPath = Path.of("config", "SiegedEmpires", "config.json");
        }
        if (Files.exists(configPath)) {
            try (Reader r = Files.newBufferedReader(configPath)) {
                INSTANCE = GSON.fromJson(r, ModConfig.class);
                Siegedempires.LOGGER.info("Loaded config with {} role entries", INSTANCE.rolePermissions.size());
            } catch (IOException e) {
                Siegedempires.LOGGER.error("Failed to load config, using defaults", e);
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        if (configPath == null) {
            configPath = Path.of("config", "SiegedEmpires", "config.json");
        }
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            Siegedempires.LOGGER.error("Failed to save config", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Get the FLAN permission identifiers a role has. Returns empty for unknown roles. */
    public List<String> getPermissions(String role) {
        return rolePermissions.getOrDefault(role, List.of());
    }

    /** Does this role have all permissions (wildcard)? */
    public boolean hasAllPermissions(String role) {
        List<String> perms = rolePermissions.get(role);
        return perms != null && perms.contains("*");
    }
}