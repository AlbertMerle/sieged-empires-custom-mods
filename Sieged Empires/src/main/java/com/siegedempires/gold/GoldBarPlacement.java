package com.siegedempires.gold;

import com.siegedempires.block.GoldBarPileBlock;
import com.siegedempires.block.ModBlocks;
import com.siegedempires.permission.PermissionManager;
import com.siegedempires.permission.PermissionType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class GoldBarPlacement {
	private GoldBarPlacement() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(GoldBarPlacement::onUseBlock);
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
		if (!held.is(Items.GOLD_INGOT)) {
			return InteractionResult.PASS;
		}

		BlockPos clickedPos = hit.getBlockPos();
		BlockState clickedState = level.getBlockState(clickedPos);

		if (clickedState.is(ModBlocks.GOLD_BAR_PILE)) {
			return tryAddToPile(serverPlayer, (ServerLevel) level, clickedPos, clickedState, held);
		}

		BlockPos placementPos = clickedState.canBeReplaced()
				? clickedPos
				: clickedPos.relative(hit.getDirection());

		BlockState placementState = level.getBlockState(placementPos);
		if (placementState.is(ModBlocks.GOLD_BAR_PILE)) {
			return tryAddToPile(serverPlayer, (ServerLevel) level, placementPos, placementState, held);
		}

		if (!placementState.canBeReplaced()) {
			return InteractionResult.PASS;
		}

		if (!PermissionManager.allowed(serverPlayer, placementPos, PermissionType.PLACE, ModBlocks.GOLD_BAR_PILE, false)) {
			return InteractionResult.FAIL;
		}

		BlockState newState = ModBlocks.GOLD_BAR_PILE.defaultBlockState()
				.setValue(GoldBarPileBlock.ROTATED, level.getRandom().nextBoolean());
		if (!newState.canSurvive(level, placementPos)) {
			return InteractionResult.FAIL;
		}

		level.setBlock(placementPos, newState, Block.UPDATE_ALL);
		GoldBarSounds.play((ServerLevel) level, placementPos);
		if (!player.isCreative()) {
			held.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryAddToPile(
			ServerPlayer player,
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			ItemStack held) {
		int count = state.getValue(GoldBarPileBlock.COUNT);
		if (count >= GoldBarPileBlock.MAX_COUNT) {
			return InteractionResult.FAIL;
		}

		if (!PermissionManager.allowed(player, pos, PermissionType.PLACE, ModBlocks.GOLD_BAR_PILE, false)) {
			return InteractionResult.FAIL;
		}

		level.setBlock(pos, state.setValue(GoldBarPileBlock.COUNT, count + 1), Block.UPDATE_ALL);
		GoldBarSounds.play(level, pos);
		if (!player.isCreative()) {
			held.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
