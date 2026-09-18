package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative per-latitude whitelist of <em>land</em> biomes (vanilla + Terralith).
 * Oceans, rivers, and beaches are not listed — {@link ZoneBiomeEnforcer} leaves them alone
 * so a temperature mod can keep ocean climates (~0.5) intact. Land-over-ocean bleed is
 * handled separately in the multi-noise mixin.
 *
 * <p>Terralith/Tectonic multi-noise only <em>suggests</em> a biome; the enforcer replaces
 * land picks outside this catalog. Terrain (continentalness, erosion, depth) is never
 * changed — only the biome id at pick time.</p>
 *
 * <p>See {@code ZONE_BIOMES.md} for the human-readable list.</p>
 */
public final class ZoneBiomeCatalog {
	private ZoneBiomeCatalog() {}

	/** Hot-band land moisture split for 50/50 wet (jungle/swamp) vs dry (desert/mesa/savanna) patches. */
	public enum HotMoisture {
		DRY,
		WET
	}

	public record Entry(ResourceKey<Biome> key, float displayTemp, float downfall, boolean water, HotMoisture hotMoisture) {}

	private static final Map<ResourceKey<Biome>, Entry> ENTRIES = new HashMap<>();
	private static final Map<ClimateZone, Set<ResourceKey<Biome>>> ZONE_ALLOW = new EnumMap<>(ClimateZone.class);
	private static final Map<ClimateZone, List<ResourceKey<Biome>>> LAND_CANDIDATES = new EnumMap<>(ClimateZone.class);
	private static final List<ResourceKey<Biome>> HOT_DRY_CANDIDATES;
	private static final List<ResourceKey<Biome>> HOT_WET_CANDIDATES;
	private static final Set<String> GLOBALLY_BANNED_PATHS = Set.of(
		"alpha_islands",
		"alpha_islands_winter",
		"mushroom_fields",
		"hot_shrubland",
		"windswept_forest"
	);

	static {
		for (ClimateZone zone : ClimateZone.values()) {
			ZONE_ALLOW.put(zone, new HashSet<>());
		}
		registerZone();
		for (ClimateZone zone : ClimateZone.values()) {
			LAND_CANDIDATES.put(zone, buildCandidates(zone, null));
		}
		HOT_DRY_CANDIDATES = buildCandidates(ClimateZone.HOT, HotMoisture.DRY);
		HOT_WET_CANDIDATES = buildCandidates(ClimateZone.HOT, HotMoisture.WET);
	}

	public static boolean isAllowed(ClimateZone zone, Holder<Biome> biome) {
		return biome.unwrapKey()
			.map(key -> isAllowed(zone, key))
			.orElse(false);
	}

	public static boolean isAllowed(ClimateZone zone, ResourceKey<Biome> key) {
		Set<ResourceKey<Biome>> allowed = ZONE_ALLOW.get(zone);
		return allowed != null && allowed.contains(key);
	}

	public static boolean isGloballyBanned(Holder<Biome> biome) {
		if (SkylandBan.isSkylandBiome(biome)) {
			return true;
		}
		return biome.unwrapKey()
			.map(key -> GLOBALLY_BANNED_PATHS.contains(key.identifier().getPath()))
			.orElse(false);
	}

	public static Entry entry(ResourceKey<Biome> key) {
		return ENTRIES.get(key);
	}

	/** Allowed biome keys for {@code zone}, optionally filtered to water or land (never caves). */
	public static List<ResourceKey<Biome>> candidates(ClimateZone zone, boolean waterOnly) {
		return candidates(zone, waterOnly, null);
	}

	/**
	 * Like {@link #candidates(ClimateZone, boolean)} but in Hot, when {@code hotMoisture}
	 * is set, only land biomes tagged with that moisture class are returned.
	 */
	public static List<ResourceKey<Biome>> candidates(ClimateZone zone, boolean waterOnly, HotMoisture hotMoisture) {
		if (!waterOnly) {
			if (zone == ClimateZone.HOT && hotMoisture == HotMoisture.DRY) {
				return HOT_DRY_CANDIDATES;
			}
			if (zone == ClimateZone.HOT && hotMoisture == HotMoisture.WET) {
				return HOT_WET_CANDIDATES;
			}
			return LAND_CANDIDATES.getOrDefault(zone, List.of());
		}
		return List.of();
	}

	private static List<ResourceKey<Biome>> buildCandidates(ClimateZone zone, HotMoisture hotMoisture) {
		List<ResourceKey<Biome>> out = new ArrayList<>();
		for (ResourceKey<Biome> key : ZONE_ALLOW.get(zone)) {
			if (CaveBiomeBan.isCaveKey(key)) {
				continue;
			}
			Entry e = ENTRIES.get(key);
			if (e == null) {
				continue;
			}
			if (e.water()) {
				continue;
			}
			if (zone == ClimateZone.HOT && hotMoisture != null && !e.water() && e.hotMoisture() != hotMoisture) {
				continue;
			}
			out.add(key);
		}
		out.sort(java.util.Comparator.comparing(key -> key.identifier().toString()));
		return List.copyOf(out);
	}

	/** Hot-band moisture tag, or null for non-Hot / unclassified entries. */
	public static HotMoisture hotMoisture(ResourceKey<Biome> key) {
		Entry e = ENTRIES.get(key);
		return e != null ? e.hotMoisture() : null;
	}

	public static boolean matchesHotMoisture(Holder<Biome> biome, boolean wantWet) {
		return biome.unwrapKey()
			.map(key -> {
				HotMoisture tag = hotMoisture(key);
				if (tag == null) {
					return true;
				}
				return wantWet ? tag == HotMoisture.WET : tag == HotMoisture.DRY;
			})
			.orElse(true);
	}

	public static Set<ResourceKey<Biome>> allForZone(ClimateZone zone) {
		return Set.copyOf(ZONE_ALLOW.get(zone));
	}

	public static int countForZone(ClimateZone zone) {
		return ZONE_ALLOW.get(zone).size();
	}

	private static void registerZone() {
		// Land only — oceans/rivers/beaches are never zone-whitelisted.
		// Terralith shortlist (user keep set); vanilla land lists unchanged.
		// --- Freezing ---
		zone(ClimateZone.FREEZING, mc("frozen_peaks"), -0.7f, 0.9f, false);
		zone(ClimateZone.FREEZING, mc("grove"), -0.2f, 0.8f, false);
		zone(ClimateZone.FREEZING, mc("ice_spikes"), 0.0f, 0.5f, false);
		zone(ClimateZone.FREEZING, mc("jagged_peaks"), -0.7f, 0.9f, false);
		zone(ClimateZone.FREEZING, mc("snowy_plains"), 0.0f, 0.5f, false);
		zone(ClimateZone.FREEZING, mc("snowy_slopes"), -0.3f, 0.9f, false);
		zone(ClimateZone.FREEZING, mc("snowy_taiga"), -0.5f, 0.4f, false);
		zone(ClimateZone.FREEZING, tl("alpine_grove"), -0.2f, 0.8f, false);
		zone(ClimateZone.FREEZING, tl("birch_taiga"), 0.22f, 0.2f, false);
		zone(ClimateZone.FREEZING, tl("ice_marsh"), 0.14f, 0.9f, false);
		zone(ClimateZone.FREEZING, tl("scarlet_mountains"), 0.1f, 0.4f, false);
		zone(ClimateZone.FREEZING, tl("snowy_maple_forest"), 0.1f, 0.2f, false);

		// --- Cold ---
		zone(ClimateZone.COLD, mc("old_growth_birch_forest"), 0.6f, 0.6f, false);
		zone(ClimateZone.COLD, mc("old_growth_pine_taiga"), 0.3f, 0.8f, false);
		zone(ClimateZone.COLD, mc("old_growth_spruce_taiga"), 0.25f, 0.8f, false);
		zone(ClimateZone.COLD, mc("taiga"), 0.25f, 0.8f, false);
		zone(ClimateZone.COLD, mc("windswept_gravelly_hills"), 0.2f, 0.3f, false);
		zone(ClimateZone.COLD, tl("alpine_highlands"), 0.45f, 0.1f, false);
		zone(ClimateZone.COLD, tl("caldera"), 0.45f, 0.8f, false);
		zone(ClimateZone.COLD, tl("yellowstone"), 0.24775f, 0.8f, false);

		// --- Temperate ---
		zone(ClimateZone.TEMPERATE, mc("birch_forest"), 0.6f, 0.6f, false);
		zone(ClimateZone.TEMPERATE, mc("cherry_grove"), 0.5f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("dark_forest"), 0.7f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("flower_forest"), 0.7f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("forest"), 0.7f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("meadow"), 0.5f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("old_growth_birch_forest"), 0.6f, 0.6f, false);
		zone(ClimateZone.TEMPERATE, mc("pale_garden"), 0.7f, 0.8f, false);
		zone(ClimateZone.TEMPERATE, mc("windswept_hills"), 0.2f, 0.3f, false);
		zone(ClimateZone.TEMPERATE, tl("moonlight_grove"), 0.7f, 0.8f, false);

		// --- Warm: land only; Hot/WET Terralith registered first so Entry keeps WET ---
		zone(ClimateZone.WARM, mc("dark_forest"), 0.7f, 0.8f, false);
		zone(ClimateZone.WARM, mc("mangrove_swamp"), 0.8f, 0.9f, false);
		zone(ClimateZone.WARM, mc("plains"), 0.8f, 0.4f, false);
		zone(ClimateZone.WARM, mc("sunflower_plains"), 0.8f, 0.4f, false);
		zone(ClimateZone.WARM, mc("swamp"), 0.8f, 0.9f, false);
		zone(ClimateZone.WARM, tl("mirage_isles"), 0.7f, 0.8f, false);
		zone(ClimateZone.WARM, tl("moonlight_grove"), 0.7f, 0.8f, false);
		zone(ClimateZone.WARM, tl("temperate_highlands"), 0.5f, 0.2f, false);

		// --- Hot: dry = desert/mesa/savanna; wet = jungle/swamp ---
		hotDry(mc("badlands"), 2.0f, 0.0f);
		hotWet(mc("bamboo_jungle"), 0.95f, 0.9f);
		hotDry(mc("desert"), 2.0f, 0.0f);
		hotDry(mc("eroded_badlands"), 2.0f, 0.0f);
		hotWet(mc("jungle"), 0.95f, 0.9f);
		hotWet(mc("mangrove_swamp"), 0.8f, 0.9f);
		hotDry(mc("savanna"), 1.2f, 0.0f);
		hotDry(mc("savanna_plateau"), 1.0f, 0.0f);
		hotWet(mc("sparse_jungle"), 0.95f, 0.8f);
		hotDry(mc("stony_peaks"), 1.0f, 0.3f);
		hotWet(mc("swamp"), 0.8f, 0.9f);
		hotDry(mc("windswept_savanna"), 2.0f, 0.0f);
		hotDry(mc("wooded_badlands"), 2.0f, 0.0f);
		hotDry(tl("brushland"), 1.2f, 0.2f);
		hotDry(tl("desert_oasis"), 2.0f, 0.0f);
		hotWet(tl("jungle_mountains"), 0.95f, 0.9f);
		hotDry(tl("lush_desert"), 2.0f, 0.0f);
		hotWet(tl("orchid_swamp"), 0.8f, 0.9f);
		hotDry(tl("sandstone_valley"), 2.0f, 0.0f);
		hotWet(tl("tropical_jungle"), 0.95f, 0.9f);

		// Warm also allows bamboo + Hot/WET Terralith (Entry already WET from above).
		ZONE_ALLOW.get(ClimateZone.WARM).add(mc("bamboo_jungle"));
		ZONE_ALLOW.get(ClimateZone.WARM).add(tl("jungle_mountains"));
		ZONE_ALLOW.get(ClimateZone.WARM).add(tl("orchid_swamp"));
		ZONE_ALLOW.get(ClimateZone.WARM).add(tl("tropical_jungle"));
	}

	private static void zone(ClimateZone zone, ResourceKey<Biome> key, float displayTemp, float downfall, boolean water) {
		zone(zone, key, displayTemp, downfall, water, null);
	}

	private static void hotDry(ResourceKey<Biome> key, float displayTemp, float downfall) {
		zone(ClimateZone.HOT, key, displayTemp, downfall, false, HotMoisture.DRY);
	}

	private static void hotWet(ResourceKey<Biome> key, float displayTemp, float downfall) {
		zone(ClimateZone.HOT, key, displayTemp, downfall, false, HotMoisture.WET);
	}

	private static void zone(
		ClimateZone zone,
		ResourceKey<Biome> key,
		float displayTemp,
		float downfall,
		boolean water,
		HotMoisture hotMoisture
	) {
		ENTRIES.putIfAbsent(key, new Entry(key, displayTemp, downfall, water, hotMoisture));
		ZONE_ALLOW.get(zone).add(key);
	}

	private static ResourceKey<Biome> mc(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("minecraft", path));
	}

	private static ResourceKey<Biome> tl(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terralith", path));
	}
}
