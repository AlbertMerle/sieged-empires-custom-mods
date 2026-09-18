package com.tertonbiome;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Config whitelist for worldgen structures (vanilla + Terralith + any other datapack).
 * Anything not listed does not generate. Amethyst geodes are features, not structures,
 * but are gated by the same {@code allowed-structures} list.
 */
public final class AllowedStructures {
	private AllowedStructures() {}

	private static volatile Snapshot SNAP = Snapshot.EMPTY;

	public static void refresh(RegistryAccess access) {
		apply(parseConfig(), access.lookupOrThrow(Registries.STRUCTURE), access.lookupOrThrow(Registries.STRUCTURE_SET));
	}

	/** Called when structure sets are first queried (world load), before placement. */
	public static void refresh(HolderLookup<StructureSet> sets) {
		apply(parseConfig(), null, sets);
	}

	public static boolean areGeodesAllowed() {
		Snapshot snap = SNAP;
		return snap.allowAll || snap.allowGeodes;
	}

	public static boolean isStructureAllowed(Holder<Structure> selected, RegistryAccess access) {
		ensureReady(access);
		Snapshot snap = SNAP;
		if (snap.allowAll) {
			return true;
		}
		return selected.unwrapKey().map(snap.structures::contains).orElse(false);
	}

	/**
	 * True if at least one structure in this set is on the whitelist.
	 * Used to skip banned sets (including Terralith) before placement / locate.
	 */
	public static boolean isStructureSetAllowed(StructureSet structureSet) {
		Snapshot snap = SNAP;
		if (!snap.ready) {
			return true; // generate() still filters; avoid blocking before refresh
		}
		if (snap.allowAll) {
			return true;
		}
		for (StructureSet.StructureSelectionEntry entry : structureSet.structures()) {
			if (entry.structure().unwrapKey().map(snap.structures::contains).orElse(false)) {
				return true;
			}
		}
		return false;
	}

	private static ParsedConfig parseConfig() {
		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		List<String> raw = config.allowedStructures;
		if (raw == null) {
			raw = TerratonicbiomesConfig.defaultAllowedStructures();
		}

		boolean allowAll = false;
		boolean allowGeodes = false;
		List<String> tokens = new ArrayList<>();
		for (String entry : raw) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			String token = normalize(entry);
			if (token.equals("*") || token.equals("all")) {
				allowAll = true;
				break;
			}
			if (isGeodeAlias(token)) {
				allowGeodes = true;
			}
			tokens.add(token);
		}
		return new ParsedConfig(raw, tokens, allowAll, allowGeodes);
	}

	private static void apply(ParsedConfig parsed, Registry<Structure> structures, HolderLookup<StructureSet> sets) {
		if (parsed.allowAll) {
			SNAP = new Snapshot(true, true, true, Set.of());
			Terratonicbiomes.LOGGER.info("Terrabiome allowed-structures: ALL (wildcard)");
			return;
		}

		Set<ResourceKey<Structure>> allowed = new HashSet<>();
		if (structures != null) {
			structures.listElements().forEach(ref -> {
				if (matchesAny(parsed.tokens, ref.key().identifier())) {
					allowed.add(ref.key());
				}
			});
		}

		if (sets != null) {
			sets.listElements().forEach(ref -> {
				boolean setMatches = matchesAny(parsed.tokens, ref.key().identifier());
				for (StructureSet.StructureSelectionEntry entry : ref.value().structures()) {
					entry.structure().unwrapKey().ifPresent(key -> {
						if (setMatches || matchesAny(parsed.tokens, key.identifier())) {
							allowed.add(key);
						}
					});
				}
			});
		}

		SNAP = new Snapshot(true, false, parsed.allowGeodes, Set.copyOf(allowed));
		Terratonicbiomes.LOGGER.info(
			"Terrabiome allowed-structures: {} structure id(s), geodes={} (config {})",
			allowed.size(),
			parsed.allowGeodes,
			parsed.raw
		);
	}

	private static void ensureReady(RegistryAccess access) {
		if (!SNAP.ready) {
			refresh(access);
		}
	}

	private record ParsedConfig(List<String> raw, List<String> tokens, boolean allowAll, boolean allowGeodes) {}

	static String normalize(String raw) {
		return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
	}

	static boolean isGeodeAlias(String token) {
		String path = token.contains(":") ? token.substring(token.indexOf(':') + 1) : token;
		return path.equals("geode")
			|| path.equals("geodes")
			|| path.equals("amethyst_geode")
			|| path.equals("amethyst_geodes")
			|| path.endsWith("/amethyst_geode")
			|| path.contains("amethyst_geode");
	}

	static boolean matchesAny(List<String> tokens, Identifier id) {
		for (String token : tokens) {
			if (matches(token, id)) {
				return true;
			}
		}
		return false;
	}

	static boolean matches(String token, Identifier id) {
		String ns = id.getNamespace();
		String path = id.getPath();
		String last = path.substring(path.lastIndexOf('/') + 1);

		String tokenNs = null;
		String tokenPath = token;
		if (token.contains(":")) {
			Identifier parsed = Identifier.tryParse(token);
			if (parsed == null) {
				return false;
			}
			tokenNs = parsed.getNamespace();
			tokenPath = parsed.getPath();
		}
		if (tokenNs != null && !tokenNs.equals(ns)) {
			return false;
		}

		if (token.equals(id.toString()) || tokenPath.equals(path) || tokenPath.equals(last)) {
			return true;
		}
		return path.startsWith(tokenPath + "_")
			|| path.startsWith(tokenPath + "/")
			|| last.startsWith(tokenPath + "_");
	}

	private record Snapshot(boolean ready, boolean allowAll, boolean allowGeodes, Set<ResourceKey<Structure>> structures) {
		static final Snapshot EMPTY = new Snapshot(false, false, false, Set.of());
	}
}
