package com.tertonbiome.mixin;

import com.tertonbiome.CaveBiomeBan;
import com.tertonbiome.CaveBiomeRules;
import com.tertonbiome.CoastalBeachFilter;
import com.tertonbiome.ColdMountainOverride;
import com.tertonbiome.ClimateZone;
import com.tertonbiome.DirectLandBiomeSelector;
import com.tertonbiome.HighAltitudeBiomes;
import com.tertonbiome.HotClimateMix;
import com.tertonbiome.LatitudeTemperature;
import com.tertonbiome.SkylandBan;
import com.tertonbiome.SwampBan;
import com.tertonbiome.TerratonicbiomesConfig;
import com.tertonbiome.ZoneBiomeEnforcer;
import com.tertonbiome.ZoneBiomeRules;
import com.tertonbiome.SurfaceBiomeBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Overworld biome placement for Terralith + Tectonic: remaps multi-noise
 * temperature (and Hot-band humidity) from block Z, then enforces a per-zone
 * biome whitelist so Terralith/Tectonic only <em>suggest</em> biomes — terrain
 * parameters (continentalness, erosion, depth, weirdness) are never changed.
 *
 * <p>Below Y {@link SurfaceBiomeBounds#minY()} only vanilla caves may appear
 * (rates in {@code cave-biomes}; Terralith caves banned). At and above that
 * height only {@link com.tertonbiome.ZoneBiomeCatalog} surface biomes.</p>
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
	@Unique
	private volatile Map<net.minecraft.resources.ResourceKey<Biome>, Holder<Biome>> terrabiome$biomeLookup;

	@Shadow
	public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint target);

	@Shadow
	public abstract boolean stable(net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList> expected);

	@Inject(
		method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void terrabiome$latitudeTemperature(
		int quartX,
		int quartY,
		int quartZ,
		Climate.Sampler sampler,
		CallbackInfoReturnable<Holder<Biome>> cir
	) {
		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		if (!config.enabled) {
			return;
		}
		// Nether (and any non-overworld preset) must keep vanilla climate sampling.
		if (!this.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD) && !terrabiome$assumeOverworldInline()) {
			return;
		}
		int blockY = QuartPos.toBlock(quartY);
		// Underground: vanilla caves only (configurable rates); no Terralith caves.
		if (SurfaceBiomeBounds.isUnderground(blockY)) {
			Climate.TargetPoint sample = sampler.sample(quartX, quartY, quartZ);
			Holder<Biome> biome = this.getNoiseBiome(sample);
			MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
			biome = CaveBiomeRules.applyBelowSurface(
				biome,
				quartX,
				quartY,
				quartZ,
				sample,
				this::getNoiseBiome,
				key -> CaveBiomeRules.findInSource(self, key)
			);
			cir.setReturnValue(biome);
			return;
		}

		Holder<Biome> biome = terrabiome$pickAt(quartX, quartY, quartZ, sampler);
		cir.setReturnValue(biome);
	}

	/** Full latitude pipeline for one quart; samples climate exactly once. */
	@Unique
	private Holder<Biome> terrabiome$pickAt(
		int quartX,
		int quartY,
		int quartZ,
		Climate.Sampler sampler
	) {
		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		Climate.TargetPoint sample = sampler.sample(quartX, quartY, quartZ);
		int blockX = QuartPos.toBlock(quartX);
		int blockY = QuartPos.toBlock(quartY);
		int blockZ = QuartPos.toBlock(quartZ);
		float originalTemp = Climate.unquantizeCoord(sample.temperature());
		float originalHumidity = Climate.unquantizeCoord(sample.humidity());
		double effectiveZ = LatitudeTemperature.effectiveZ(blockX, blockZ);
		ClimateZone zone = ClimateZone.atEffectiveZ(effectiveZ);
		float latNoise = LatitudeTemperature.noiseFromEffectiveZ(effectiveZ, originalTemp);

		boolean hotMix = config.hotDesertJungleMix && zone == ClimateZone.HOT;
		boolean wantWet = hotMix && HotClimateMix.wantWet(blockX, blockZ);
		float humidity = hotMix ? HotClimateMix.remapHumidity(originalHumidity, wantWet) : originalHumidity;

		Climate.TargetPoint forced = new Climate.TargetPoint(
			Climate.quantizeCoord(latNoise),
			Climate.quantizeCoord(humidity),
			sample.continentalness(),
			sample.erosion(),
			sample.depth(),
			sample.weirdness()
		);
		forced = SurfaceBiomeBounds.biasToSurface(forced, blockY);

		Holder<Biome> classified = this.getNoiseBiome(forced);
		Holder<Biome> biome = classified;

		if (config.enforceZoneBiomes
			&& !CoastalBeachFilter.isRiver(classified)
			&& (!CoastalBeachFilter.isOcean(classified)
				|| Climate.unquantizeCoord(forced.continentalness()) >= config.oceanBleedContinentalnessMin)) {
			biome = DirectLandBiomeSelector.selectLand(
				classified,
				blockX,
				blockZ,
				zone,
				wantWet,
				key -> terrabiome$findBiome((MultiNoiseBiomeSource) (Object) this, key)
			);
		} else if (!config.enforceZoneBiomes && config.preferLandOverOcean) {
			biome = terrabiome$preferLand(biome, forced, config);
		}

		if (config.swapPlainsMeadow) {
			biome = terrabiome$swapPlainsMeadow(biome, blockX, blockZ, config);
		}

		if (config.disableSkylands) {
			biome = terrabiome$replaceSkyland(biome, forced);
		}

		// High Y in Freezing/Cold/Temperate: keep snowy peaks (skip hills replace).
		if (config.overrideColdMountains
			&& !HighAltitudeBiomes.allowSnowyPeaks(blockY, blockX, blockZ)) {
			biome = terrabiome$overrideColdMountain(biome, blockX, blockZ);
		}

		if (config.banSwampsNorthOfWarm) {
			biome = terrabiome$banWarmSwamp(biome, blockX, blockZ, forced);
		}

		if (config.enforceZoneBiomes) {
			biome = terrabiome$enforceZoneBiomes(biome, blockX, blockZ, forced);
		} else if (config.zoneBiomeOverrides) {
			biome = terrabiome$zoneBiomeOverrides(biome, blockX, blockZ, forced);
		}

		if (blockY >= SurfaceBiomeBounds.minY()) {
			biome = terrabiome$stripCaveBiomes(biome, blockX, blockZ, forced);
		}

		// High Y: Warm/Hot cooler highlands; Temperate mountains may snow.
		if (config.highAltitudeZoneShift) {
			MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
			biome = HighAltitudeBiomes.apply(
				biome,
				blockX,
				blockY,
				blockZ,
				forced,
				key -> terrabiome$findBiome(self, key)
			);
			// Highlands must not break Hot wet/dry area balance or zone whitelist.
			if (config.enforceZoneBiomes) {
				biome = terrabiome$enforceZoneBiomes(biome, blockX, blockZ, forced);
			}
		}

		if (config.coastalBeachRules) {
			MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
			biome = CoastalBeachFilter.applyConstantTime(
				biome,
				classified,
				zone,
				forced,
				key -> terrabiome$findBiome(self, key)
			);
		}

		return biome;
	}

	/**
	 * Terralith may supply an inline parameter list (Either.left) instead of the
	 * {@code minecraft:overworld} preset holder. Treat large inline lists as overworld.
	 */
	@Unique
	private boolean terrabiome$assumeOverworldInline() {
		// If stable(OVERWORLD) failed, still apply when this source is not the nether preset.
		return !this.stable(MultiNoiseBiomeSourceParameterLists.NETHER);
	}

	@Unique
	private Holder<Biome> terrabiome$preferLand(Holder<Biome> biome, Climate.TargetPoint forced, TerratonicbiomesConfig config) {
		if (!biome.is(BiomeTags.IS_OCEAN)) {
			return biome;
		}
		float cont = Climate.unquantizeCoord(forced.continentalness());
		if (cont < config.oceanBleedContinentalnessMin) {
			return biome; // real ocean
		}
		float biasedCont = Math.max(cont, config.landBiasContinentalness);
		Climate.TargetPoint landBiased = new Climate.TargetPoint(
			forced.temperature(),
			forced.humidity(),
			Climate.quantizeCoord(biasedCont),
			forced.erosion(),
			forced.depth(),
			forced.weirdness()
		);
		Holder<Biome> land = this.getNoiseBiome(landBiased);
		if (land.is(BiomeTags.IS_OCEAN)) {
			return biome;
		}
		return land;
	}

	/**
	 * Meadow (~0.5) belongs north of the Temperate|Warm cutoff; plains (~0.8) south.
	 * Uses the same wobbled Z as climate bands so the swap is not a straight line.
	 * North: plains / sunflower_plains → meadow.
	 * South: meadow → plains.
	 */
	@Unique
	private Holder<Biome> terrabiome$swapPlainsMeadow(Holder<Biome> biome, int blockX, int blockZ, TerratonicbiomesConfig config) {
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		if (LatitudeTemperature.effectiveZ(blockX, blockZ) < config.plainsMeadowSwapZ()) {
			if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS)) {
				Holder<Biome> meadow = terrabiome$findBiome(self, Biomes.MEADOW);
				return meadow != null ? meadow : biome;
			}
		} else {
			if (biome.is(Biomes.MEADOW)) {
				Holder<Biome> plains = terrabiome$findBiome(self, Biomes.PLAINS);
				return plains != null ? plains : biome;
			}
		}
		return biome;
	}

	/**
	 * Terralith skylands sit in an ocean-climate slot and spawn floating islands
	 * as biome features. Re-query with erosion outside that slot so Terralith
	 * picks a neighbor; fall back to climate-matched ground biomes.
	 */
	@Unique
	private Holder<Biome> terrabiome$replaceSkyland(Holder<Biome> biome, Climate.TargetPoint forced) {
		if (!SkylandBan.isSkylandBiome(biome)) {
			return biome;
		}
		float biasedErosion = Math.max(
			Climate.unquantizeCoord(forced.erosion()),
			SkylandBan.EROSION_OUTSIDE_SKYLANDS
		);
		Climate.TargetPoint noSky = new Climate.TargetPoint(
			forced.temperature(),
			forced.humidity(),
			forced.continentalness(),
			Climate.quantizeCoord(biasedErosion),
			forced.depth(),
			forced.weirdness()
		);
		Holder<Biome> neighbor = this.getNoiseBiome(noSky);
		if (!SkylandBan.isSkylandBiome(neighbor)) {
			return neighbor;
		}
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		for (net.minecraft.resources.ResourceKey<Biome> key : SkylandBan.fallbacksFor(biome)) {
			Holder<Biome> found = terrabiome$findBiome(self, key);
			if (found != null && !SkylandBan.isSkylandBiome(found)) {
				return found;
			}
		}
		for (Holder<Biome> candidate : self.possibleBiomes()) {
			if (!SkylandBan.isSkylandBiome(candidate)) {
				return candidate;
			}
		}
		return biome;
	}

	/**
	 * South of coldMountainCutoffZ (default 0, wobbled + blended like zone
	 * borders): frozen_peaks / jagged_peaks / snowy_slopes → windswept_hills.
	 * In the Hot band, those become stony_peaks instead. Tectonic height unchanged.
	 */
	@Unique
	private Holder<Biome> terrabiome$overrideColdMountain(Holder<Biome> biome, int blockX, int blockZ) {
		if (!ColdMountainOverride.isColdMountain(biome)) {
			return biome;
		}
		if (!ColdMountainOverride.shouldReplace(blockX, blockZ)) {
			return biome;
		}
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		Holder<Biome> replacement = terrabiome$findBiome(self, ColdMountainOverride.replacementKey(blockX, blockZ));
		return replacement != null ? replacement : biome;
	}

	/**
	 * Temperate and north: replace warm swamps with a drier multi-noise pick,
	 * else meadow/plains. Warm + Hot keep swamps. ice_marsh is untouched.
	 */
	@Unique
	private Holder<Biome> terrabiome$banWarmSwamp(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced
	) {
		if (!SwampBan.isWarmSwamp(biome)) {
			return biome;
		}
		if (!SwampBan.shouldReplace(blockX, blockZ)) {
			return biome;
		}
		float humid = Climate.unquantizeCoord(forced.humidity());
		float drier = Math.min(humid, -0.15f);
		Climate.TargetPoint dry = new Climate.TargetPoint(
			forced.temperature(),
			Climate.quantizeCoord(drier),
			forced.continentalness(),
			forced.erosion(),
			forced.depth(),
			forced.weirdness()
		);
		Holder<Biome> neighbor = this.getNoiseBiome(dry);
		if (!SwampBan.isWarmSwamp(neighbor)) {
			return neighbor;
		}
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		Holder<Biome> fallback = terrabiome$findBiome(self, SwampBan.fallbackKey(blockX, blockZ));
		return fallback != null ? fallback : biome;
	}

	/** Strip cave/underground biomes on surface columns (Y ≥ minY). */
	@Unique
	private Holder<Biome> terrabiome$stripCaveBiomes(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced
	) {
		if (!CaveBiomeBan.isCaveBiome(biome)) {
			return biome;
		}
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		return ZoneBiomeEnforcer.enforceSurface(
			biome,
			blockX,
			blockZ,
			forced,
			key -> terrabiome$findBiome(self, key)
		);
	}

	/**
	 * Hard zone whitelist — any Terralith/vanilla pick outside {@link com.tertonbiome.ZoneBiomeCatalog}
	 * is replaced with the closest allowed biome for this latitude band.
	 */
	@Unique
	private Holder<Biome> terrabiome$enforceZoneBiomes(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced
	) {
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		return ZoneBiomeEnforcer.enforce(
			biome,
			blockX,
			blockZ,
			forced,
			key -> terrabiome$findBiome(self, key)
		);
	}

	/** Legacy partial overrides when {@code enforceZoneBiomes} is false. */
	@Unique
	private Holder<Biome> terrabiome$zoneBiomeOverrides(
		Holder<Biome> biome,
		int blockX,
		int blockZ,
		Climate.TargetPoint forced
	) {
		MultiNoiseBiomeSource self = (MultiNoiseBiomeSource) (Object) this;
		return ZoneBiomeRules.apply(
			biome,
			blockX,
			blockZ,
			forced,
			this::getNoiseBiome,
			key -> terrabiome$findBiome(self, key)
		);
	}

	@Unique
	private Holder<Biome> terrabiome$findBiome(MultiNoiseBiomeSource source, net.minecraft.resources.ResourceKey<Biome> key) {
		Map<net.minecraft.resources.ResourceKey<Biome>, Holder<Biome>> lookup = terrabiome$biomeLookup;
		if (lookup == null) {
			synchronized (this) {
				lookup = terrabiome$biomeLookup;
				if (lookup == null) {
					Map<net.minecraft.resources.ResourceKey<Biome>, Holder<Biome>> built = new LinkedHashMap<>();
					for (Holder<Biome> holder : source.possibleBiomes()) {
						holder.unwrapKey().ifPresent(holderKey -> built.putIfAbsent(holderKey, holder));
					}
					lookup = Map.copyOf(built);
					terrabiome$biomeLookup = lookup;
				}
			}
		}
		return lookup.get(key);
	}
}
