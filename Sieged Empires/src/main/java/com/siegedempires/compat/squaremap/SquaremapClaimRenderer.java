package com.siegedempires.compat.squaremap;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.Siegedempires;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.marker.Polygon;

import static xyz.jpenilla.squaremap.api.Key.key;

final class SquaremapClaimRenderer {
	private static final Key LAYER_KEY = key("siegedempires_claims");
	private static final String DEFAULT_DIMENSION = "minecraft:overworld";

	private final Map<WorldIdentifier, SimpleLayerProvider> layers = new HashMap<>();

	void registerLayers() {
		for (MapWorld world : SquaremapProvider.get().mapWorlds()) {
			ensureLayer(world);
		}
	}

	void refreshAll() {
		registerLayers();
		Squaremap squaremap = SquaremapProvider.get();
		for (MapWorld world : squaremap.mapWorlds()) {
			refreshDimension(world.identifier().asString());
		}
	}

	void refreshTown(TownData town) {
		if (town == null) {
			return;
		}
		Set<String> dimensions = new HashSet<>();
		for (ChunkPosition chunk : town.getClaimedChunks()) {
			dimensions.add(resolveDimension(chunk));
		}
		for (String dimension : dimensions) {
			refreshDimension(dimension);
		}
	}

	void refreshDimension(String dimension) {
		Optional<MapWorld> world = SquaremapProvider.get().getWorldIfEnabled(WorldIdentifier.parse(dimension));
		if (world.isEmpty()) {
			return;
		}
		SimpleLayerProvider layer = ensureLayer(world.get());
		layer.clearMarkers();

		int markerCount = 0;
		for (TownData town : TownDataManager.getInstance().getAllTowns()) {
			if (town.isWarTown()) {
				continue;
			}
			markerCount += drawTown(layer, town, dimension);
		}
		if (markerCount > 0) {
			Siegedempires.LOGGER.debug("Squaremap: drew {} claim markers in {}", markerCount, dimension);
		}
	}

	private SimpleLayerProvider ensureLayer(MapWorld world) {
		WorldIdentifier id = world.identifier();
		SimpleLayerProvider cached = layers.get(id);
		if (cached != null) {
			return cached;
		}

		var registry = world.layerRegistry();
		if (registry.hasEntry(LAYER_KEY)) {
			LayerProvider existing = registry.get(LAYER_KEY);
			if (existing instanceof SimpleLayerProvider simple) {
				layers.put(id, simple);
				return simple;
			}
		}

		SimpleLayerProvider layer = SimpleLayerProvider.builder("Sieged Empires Claims")
				.layerPriority(5)
				.showControls(true)
				.defaultHidden(false)
				.build();
		registry.register(LAYER_KEY, layer);
		layers.put(id, layer);
		Siegedempires.LOGGER.info("Registered Squaremap claims layer for {}", id.asString());
		return layer;
	}

	private int drawTown(SimpleLayerProvider layer, TownData town, String dimension) {
		List<ClaimChunk> claims = new ArrayList<>();
		for (ChunkPosition chunk : town.getClaimedChunks()) {
			if (dimension.equals(resolveDimension(chunk))) {
				claims.add(new ClaimChunk(chunk.getX(), chunk.getZ(), town.getId()));
			}
		}
		if (claims.isEmpty()) {
			return 0;
		}

		List<ClaimGroup> groups = groupClaims(claims);
		Color color = toAwtColor(BannerHelper.getDominantFlagColorName(town));
		MarkerOptions.Builder options = MarkerOptions.builder()
				.strokeColor(color)
				.strokeWeight(2)
				.strokeOpacity(0.85)
				.fillColor(color)
				.fillOpacity(0.25)
				.clickTooltip(buildTooltip(town));

		for (int i = 0; i < groups.size(); i++) {
			ClaimGroup group = groups.get(i);
			Polygon polygon = RectangleMerge.toPolygon(group.claims());
			polygon.markerOptions(options);
			Key markerKey = key("siegedempires_" + town.getId() + "_" + group.id() + "_" + i);
			layer.addMarker(markerKey, polygon);
		}
		return groups.size();
	}

	private static String resolveDimension(ChunkPosition chunk) {
		String dimension = chunk.getDimension();
		return dimension != null && !dimension.isEmpty() ? dimension : DEFAULT_DIMENSION;
	}

	private static List<ClaimGroup> groupClaims(List<ClaimChunk> claims) {
		Map<String, List<ClaimChunk>> byTown = new HashMap<>();
		for (ClaimChunk claim : claims) {
			byTown.computeIfAbsent(claim.townId(), $ -> new ArrayList<>()).add(claim);
		}

		List<ClaimGroup> combined = new ArrayList<>();
		for (List<ClaimChunk> townClaims : byTown.values()) {
			townClaims.sort(Comparator.comparing(ClaimChunk::x).thenComparing(ClaimChunk::z));
			List<ClaimGroup> groups = new ArrayList<>();
			nextClaim:
			for (ClaimChunk claim : townClaims) {
				for (ClaimGroup group : groups) {
					if (group.isTouching(claim)) {
						group.add(claim);
						continue nextClaim;
					}
				}
				groups.add(new ClaimGroup(claim, claim.townId()));
			}

			nextGroup:
			for (ClaimGroup group : groups) {
				for (ClaimGroup existing : combined) {
					if (existing.isTouching(group)) {
						existing.add(group);
						continue nextGroup;
					}
				}
				combined.add(group);
			}
		}
		return combined;
	}

	private static String buildTooltip(TownData town) {
		StringBuilder tooltip = new StringBuilder("<b>").append(town.getName()).append("</b>");
		if (town.getMonarchTitle() != null && town.getMonarchName() != null) {
			tooltip.append("<br>").append(town.getMonarchTitle()).append(" ").append(town.getMonarchName());
		}
		if (town.isNation()) {
			tooltip.append("<br><i>Nation</i>");
		}
		String empireId = town.getEmpireId();
		if (empireId != null && !empireId.isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null) {
				tooltip.append("<br>Empire: ").append(empire.getName());
			}
		}
		tooltip.append("<br>Chunks: ").append(town.getChunkCount());
		return tooltip.toString();
	}

	private static Color toAwtColor(String colorName) {
		int rgb = BannerHelper.getColorRgb(colorName);
		return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
	}

	void disable() {
		for (SimpleLayerProvider layer : layers.values()) {
			layer.clearMarkers();
		}
		layers.clear();
	}
}
