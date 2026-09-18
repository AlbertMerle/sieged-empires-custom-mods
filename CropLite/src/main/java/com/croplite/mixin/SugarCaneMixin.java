package com.croplite.mixin;

import com.croplite.crop.CropClimate;
import com.croplite.crop.CropGrowth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SugarCaneBlock.class)
public class SugarCaneMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void croplite$rotAndSeason(
			BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)
				|| !CropGrowth.allowRandomGrowthTick(level, pos, random)) {
			ci.cancel();
		}
	}
}
