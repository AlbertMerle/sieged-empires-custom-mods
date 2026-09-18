package com.tertonbiome.mixin;

import com.tertonbiome.AllowedStructures;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Geodes are configured features, not structure sets. Gate them with the same
 * {@code allowed-structures} list (default includes {@code amethyst_geodes}).
 */
@Mixin(GeodeFeature.class)
public abstract class GeodeFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void terrabiome$filterGeodes(
		FeaturePlaceContext<GeodeConfiguration> context,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (!AllowedStructures.areGeodesAllowed()) {
			cir.setReturnValue(false);
		}
	}
}
