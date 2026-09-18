package com.tertonbiome.mixin;

import com.tertonbiome.SkylandBan;
import com.tertonbiome.TerratonicbiomesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels Terralith {@code skylands/} placed features (floating islands, extend-down,
 * etc.) even if a biome still lists them.
 */
@Mixin(PlacedFeature.class)
public abstract class SkylandFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void terrabiome$blockSkylandPlace(
		WorldGenLevel level,
		ChunkGenerator generator,
		RandomSource random,
		BlockPos origin,
		CallbackInfoReturnable<Boolean> cir
	) {
		terrabiome$cancelIfSkyland(cir);
	}

	@Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
	private void terrabiome$blockSkylandPlaceBiomed(
		WorldGenLevel level,
		ChunkGenerator generator,
		RandomSource random,
		BlockPos origin,
		CallbackInfoReturnable<Boolean> cir
	) {
		terrabiome$cancelIfSkyland(cir);
	}

	@Unique
	private void terrabiome$cancelIfSkyland(CallbackInfoReturnable<Boolean> cir) {
		if (!TerratonicbiomesConfig.get().disableSkylands) {
			return;
		}
		PlacedFeature self = (PlacedFeature) (Object) this;
		if (SkylandBan.isSkylandFeature(self.feature())) {
			cir.setReturnValue(false);
		}
	}
}
