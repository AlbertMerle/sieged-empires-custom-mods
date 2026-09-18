package com.croplite.mixin;

import com.croplite.crop.CropClimate;
import com.croplite.crop.CropGrowth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CocoaBlock.class)
public class CocoaMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void croplite$rotAndSeason(
			BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)
				|| !CropGrowth.allowRandomGrowthTick(level, pos, random)) {
			ci.cancel();
		}
	}

	@Inject(method = "isValidBonemealTarget", at = @At("HEAD"), cancellable = true)
	private void croplite$noBonemealWhenRotting(
			LevelReader level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isBonemealSuccess", at = @At("RETURN"), cancellable = true)
	private void croplite$scaleBonemealByGrowthRate(
			Level level, RandomSource random, BlockPos pos, BlockState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !CropGrowth.allowBonemealSuccess(random)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
	private void croplite$noPerformBonemealWhenRotting(
			ServerLevel level, RandomSource random, BlockPos pos, BlockState state, CallbackInfo ci) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)) {
			ci.cancel();
		}
	}
}
