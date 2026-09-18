package com.tertonbiome;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps worldgen datapacks in the Sieged Empires stack order.
 *
 * <p>Later packs override earlier ones. Desired order (low → high priority):</p>
 * <ol>
 *   <li>{@code terralith} — biome library (+ its own terrain files, which must lose)</li>
 *   <li>{@code tectonic:tectonic} — terrain / density (full say over land shape)</li>
 *   <li>{@code terratonicbiomes:biomes} — this mod’s marker pack (biome paint is mixin-only)</li>
 * </ol>
 *
 * <p>Without this, Fabric auto-enables packs in alphabetical {@code TreeMap} order, so
 * {@code terralith} loads <em>after</em> {@code tectonic:tectonic} and silently overrides
 * Tectonic density — looking like Tectonic “never registered”.</p>
 */
public final class DatapackStack {
	public static final String TERRALITH_PACK = "terralith";
	public static final String TECTONIC_PACK = "tectonic:tectonic";
	public static final String TERRABIOME_PACK = "terratonicbiomes:biomes";

	private static String lastLoggedOrder = "";

	private DatapackStack() {}

	/**
	 * Force-enable required packs and reorder so Terralith &lt; Tectonic &lt; Terrabiome.
	 */
	public static List<Pack> apply(PackRepository repository, Collection<Pack> selected) {
		Map<String, Pack> available = new LinkedHashMap<>();
		for (Pack pack : repository.getAvailablePacks()) {
			available.put(pack.getId(), pack);
		}

		LinkedHashMap<String, Pack> ordered = new LinkedHashMap<>();
		for (Pack pack : selected) {
			ordered.put(pack.getId(), pack);
		}

		boolean tectonicMod = FabricLoader.getInstance().isModLoaded("tectonic");
		boolean terralithMod = FabricLoader.getInstance().isModLoaded("terralith");

		if (tectonicMod) {
			Pack tectonic = available.get(TECTONIC_PACK);
			if (tectonic != null) {
				ordered.putIfAbsent(TECTONIC_PACK, tectonic);
			} else {
				Terratonicbiomes.LOGGER.warn(
					"Tectonic mod is loaded but datapack '{}' is missing from the pack repository — terrain will not be Tectonic",
					TECTONIC_PACK
				);
			}
		}

		Pack self = available.get(TERRABIOME_PACK);
		if (self != null) {
			ordered.putIfAbsent(TERRABIOME_PACK, self);
		}

		List<Pack> result = reorder(ordered.values());
		maybeLog(result, tectonicMod, terralithMod);
		return result;
	}

	private static void maybeLog(List<Pack> result, boolean tectonicMod, boolean terralithMod) {
		if (!TerratonicbiomesConfig.get().logDatapackStack) {
			return;
		}
		String order = result.stream().map(Pack::getId).toList().toString();
		if (order.equals(lastLoggedOrder)) {
			return;
		}
		lastLoggedOrder = order;
		Terratonicbiomes.LOGGER.info(
			"Datapack stack (low→high): {} | mods tectonic={} terralith={}",
			order,
			tectonicMod,
			terralithMod
		);
	}

	/**
	 * Stable reorder: everything else keeps relative order; pinned packs are pulled out and
	 * re-appended as terralith → tectonic → terrabiome (last = highest priority).
	 */
	public static List<Pack> reorder(Collection<Pack> selected) {
		Pack terralith = null;
		Pack tectonic = null;
		Pack terrabiome = null;
		List<Pack> others = new ArrayList<>(selected.size());

		for (Pack pack : selected) {
			String id = pack.getId();
			if (isTerralith(id)) {
				terralith = pack;
			} else if (isTectonic(id)) {
				tectonic = pack;
			} else if (isTerrabiome(id)) {
				terrabiome = pack;
			} else {
				others.add(pack);
			}
		}

		List<Pack> out = new ArrayList<>(selected.size());
		out.addAll(others);
		if (terralith != null) {
			out.add(terralith);
		}
		if (tectonic != null) {
			out.add(tectonic);
		}
		if (terrabiome != null) {
			out.add(terrabiome);
		}
		return out;
	}

	public static boolean isTerralith(String id) {
		return TERRALITH_PACK.equals(id);
	}

	public static boolean isTectonic(String id) {
		if (TECTONIC_PACK.equals(id)) {
			return true;
		}
		String lower = id.toLowerCase(Locale.ROOT);
		return lower.equals("tectonic") || lower.startsWith("tectonic:");
	}

	public static boolean isTerrabiome(String id) {
		if (TERRABIOME_PACK.equals(id)) {
			return true;
		}
		String lower = id.toLowerCase(Locale.ROOT);
		return lower.equals("terratonicbiomes") || lower.startsWith("terratonicbiomes:");
	}
}
