package com.voxmapsync.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voxmapsync.VoxelMapSync;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Soft reflection bridge to Sieged Empires town claim data (no hard dependency).
 */
public final class SiegedEmpiresClaimsBridge {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private SiegedEmpiresClaimsBridge() {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("siegedempires");
	}

	public static String buildClaimsJson() {
		if (!isAvailable()) {
			return "[]";
		}
		try {
			Class<?> managerClass = Class.forName("com.siegedempires.data.TownDataManager");
			Method getInstance = managerClass.getMethod("getInstance");
			Object manager = getInstance.invoke(null);
			Method getAllTowns = managerClass.getMethod("getAllTowns");
			@SuppressWarnings("unchecked")
			Collection<Object> towns = (Collection<Object>) getAllTowns.invoke(manager);

			Class<?> bannerHelper = Class.forName("com.siegedempires.banner.BannerHelper");
			Method dominantColor = bannerHelper.getMethod("getDominantFlagColorName",
					Class.forName("com.siegedempires.model.TownData"));
			Method colorRgb = bannerHelper.getMethod("getColorRgb", String.class);

			Class<?> empireManager = Class.forName("com.siegedempires.data.EmpireDataManager");
			Method empireGet = empireManager.getMethod("getInstance");
			Object empires = empireGet.invoke(null);
			Method getEmpire = empireManager.getMethod("getEmpire", String.class);

			List<ClaimTownDto> dtos = new ArrayList<>();
			for (Object town : towns) {
				Method isWarTown = town.getClass().getMethod("isWarTown");
				if (Boolean.TRUE.equals(isWarTown.invoke(town))) {
					continue;
				}
				Method getId = town.getClass().getMethod("getId");
				Method getName = town.getClass().getMethod("getName");
				Method getEmpireId = town.getClass().getMethod("getEmpireId");
				Method getClaimed = town.getClass().getMethod("getClaimedChunks");
				Method getMonarchTitle = town.getClass().getMethod("getMonarchTitle");
				Method getMonarchName = town.getClass().getMethod("getMonarchName");
				Method getBannerBase = town.getClass().getMethod("getBannerBaseColor");
				Method getBannerPatterns = town.getClass().getMethod("getBannerPatterns");
				Method getBannerPixels = null;
				try {
					getBannerPixels = town.getClass().getMethod("getBannerPixels");
				} catch (NoSuchMethodException ignored) {
					// Older SE jars without custom pixel banners.
				}

				String colorName = (String) dominantColor.invoke(null, town);
				int rgb = (int) colorRgb.invoke(null, colorName) & 0xFFFFFF;

				String empireId = (String) getEmpireId.invoke(town);
				String empireName = null;
				if (empireId != null && !empireId.isEmpty()) {
					Object empire = getEmpire.invoke(empires, empireId);
					if (empire != null) {
						empireName = (String) empire.getClass().getMethod("getName").invoke(empire);
					}
				}

				@SuppressWarnings("unchecked")
				Set<Object> chunks = (Set<Object>) getClaimed.invoke(town);
				List<int[]> chunkList = new ArrayList<>();
				String dimension = "minecraft:overworld";
				for (Object chunk : chunks) {
					int cx = (int) chunk.getClass().getMethod("getX").invoke(chunk);
					int cz = (int) chunk.getClass().getMethod("getZ").invoke(chunk);
					String dim = (String) chunk.getClass().getMethod("getDimension").invoke(chunk);
					if (dim != null && !dim.isEmpty()) {
						dimension = dim;
					}
					chunkList.add(new int[]{cx, cz});
				}
				if (chunkList.isEmpty()) {
					continue;
				}

				ClaimTownDto dto = new ClaimTownDto();
				dto.id = (String) getId.invoke(town);
				dto.name = (String) getName.invoke(town);
				dto.empire = empireName;
				dto.color = rgb;
				dto.dimension = dimension;
				dto.monarch = formatMonarch((String) getMonarchTitle.invoke(town), (String) getMonarchName.invoke(town));
				dto.bannerBase = (String) getBannerBase.invoke(town);
				@SuppressWarnings("unchecked")
				List<String> patterns = (List<String>) getBannerPatterns.invoke(town);
				dto.bannerPatterns = patterns != null ? new ArrayList<>(patterns) : List.of();
				if (getBannerPixels != null) {
					Object pixels = getBannerPixels.invoke(town);
					dto.bannerPixels = pixels instanceof String s ? s : null;
				}
				dto.chunks = chunkList;
				dtos.add(dto);
			}
			return GSON.toJson(dtos);
		} catch (Throwable t) {
			VoxelMapSync.LOGGER.warn("Failed to collect Sieged Empires claims: {}", t.toString());
			return "[]";
		}
	}

	private static String formatMonarch(String title, String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		if (title == null || title.isBlank()) {
			return name;
		}
		return title + " " + name;
	}

	@SuppressWarnings("unused")
	public static final class ClaimTownDto {
		public String id;
		public String name;
		public String empire;
		public int color;
		public String dimension;
		public String monarch;
		public String bannerBase;
		public List<String> bannerPatterns;
		/** SE custom 20×40 dye-pixel hex encoding; preferred when length is 800. */
		public String bannerPixels;
		public List<int[]> chunks;
	}
}
