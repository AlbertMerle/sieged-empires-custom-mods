package com.croplite.mixin;

import com.croplite.crop.CropClimate;
import com.croplite.crop.CropGrowth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soil / global / seasonal growth multipliers plus climate rot gating for crops that use
 * {@link CropBlock#randomTick} / {@link CropBlock#getGrowthSpeed} / bonemeal.
 *
 * <p>Bonemeal must be blocked the same way as natural ticks: wrong biome / temperature means
 * {@link CropClimate#isRotting} and no age increase.
 *
 * <p>Worldgen: {@code FEATURES} runs before the light engine. Vanilla
 * {@link CropBlock#hasSufficientLight} would make {@code canSurvive} fail and pop wild
 * crops when later decoration notifies neighbors — treat worldgen light as sufficient.
 */
@Mixin(CropBlock.class)
public class CropBlockMixin {
	@Inject(method = "hasSufficientLight", at = @At("HEAD"), cancellable = true)
	private static void croplite$worldgenLightOk(
			LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (level instanceof WorldGenLevel) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getGrowthSpeed", at = @At("RETURN"), cancellable = true)
	private static void croplite$modifyGrowthSpeed(
			Block type, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(CropGrowth.modify(cir.getReturnValue(), level, pos));
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void croplite$cancelWhenRotting(
			BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)) {
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

	@Inject(method = "growCrops", at = @At("HEAD"), cancellable = true)
	private void croplite$noGrowCropsWhenRotting(
			Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
		if (CropClimate.isRotting((Block) (Object) this, level, pos)) {
			ci.cancel();
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
