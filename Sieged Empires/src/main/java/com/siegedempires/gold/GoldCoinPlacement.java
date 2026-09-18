package com.siegedempires.gold;

import com.siegedempires.block.GoldCoinPileBlock;
import com.siegedempires.block.ModBlocks;
import com.siegedempires.item.ModItems;
import com.siegedempires.permission.PermissionManager;
import com.siegedempires.permission.PermissionType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class GoldCoinPlacement {
	private GoldCoinPlacement() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(GoldCoinPlacement::onUseBlock);
	}

	private static InteractionResult onUseBlock(
			net.minecraft.world.entity.player.Player player,
			Level level,
			net.minecraft.world.InteractionHand hand,
			BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack held = player.getItemInHand(hand);
		if (!held.is(ModItems.GOLD_COIN)) {
			return InteractionResult.PASS;
		}

		BlockPos clickedPos = hit.getBlockPos();
		BlockState clickedState = level.getBlockState(clickedPos);

		if (clickedState.is(ModBlocks.GOLD_COIN_PILE)) {
			return tryAddToPile(serverPlayer, (ServerLevel) level, clickedPos, clickedState, held);
		}

		BlockPos placementPos = clickedState.canBeReplaced()
				? clickedPos
				: clickedPos.relative(hit.getDirection());

		BlockState placementState = level.getBlockState(placementPos);
		if (placementState.is(ModBlocks.GOLD_COIN_PILE)) {
			return tryAddToPile(serverPlayer, (ServerLevel) level, placementPos, placementState, held);
		}

		if (!placementState.canBeReplaced()) {
			return InteractionResult.PASS;
		}

		if (!PermissionManager.allowed(serverPlayer, placementPos, PermissionType.PLACE, ModBlocks.GOLD_COIN_PILE, false)) {
			return InteractionResult.FAIL;
		}

		int toPlace = coinsToTransfer(held, 0);
		BlockState newState = ModBlocks.GOLD_COIN_PILE.defaultBlockState()
				.setValue(GoldCoinPileBlock.COUNT, toPlace);
		if (!newState.canSurvive(level, placementPos)) {
			return InteractionResult.FAIL;
		}

		level.setBlock(placementPos, newState, Block.UPDATE_ALL);
		GoldCoinSounds.play((ServerLevel) level, placementPos);
		if (!player.isCreative()) {
			held.shrink(toPlace);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryAddToPile(
			ServerPlayer player,
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			ItemStack held) {
		int count = state.getValue(GoldCoinPileBlock.COUNT);
		int toAdd = coinsToTransfer(held, count);
		if (toAdd <= 0) {
			return InteractionResult.FAIL;
		}

		if (!PermissionManager.allowed(player, pos, PermissionType.PLACE, ModBlocks.GOLD_COIN_PILE, false)) {
			return InteractionResult.FAIL;
		}

		level.setBlock(pos, state.setValue(GoldCoinPileBlock.COUNT, count + toAdd), Block.UPDATE_ALL);
		GoldCoinSounds.play(level, pos);
		if (!player.isCreative()) {
			held.shrink(toAdd);
		}
		return InteractionResult.SUCCESS;
	}

	/** How many coins from the held stack can be placed into a pile with {@code currentCount} coins. */
	private static int coinsToTransfer(ItemStack held, int currentCount) {
		int inHand = held.getCount();
		if (inHand <= 0) {
			return 0;
		}
		int space = GoldCoinPileBlock.MAX_COUNT - currentCount;
		if (space <= 0) {
			return 0;
		}
		return Math.min(inHand, space);
	}
}
