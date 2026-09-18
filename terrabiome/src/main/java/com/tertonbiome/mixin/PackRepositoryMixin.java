package com.tertonbiome.mixin;

import com.google.common.collect.ImmutableList;
import com.tertonbiome.DatapackStack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

/**
 * Forces datapack selection order so Tectonic terrain wins over Terralith, with
 * Terrabiome’s marker pack on top. See {@link DatapackStack}.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
	private void terrabiome$stackOrder(Collection<String> selectedNames, CallbackInfoReturnable<List<Pack>> cir) {
		PackRepository self = (PackRepository) (Object) this;
		List<Pack> stacked = DatapackStack.apply(self, cir.getReturnValue());
		cir.setReturnValue(ImmutableList.copyOf(stacked));
	}
}
