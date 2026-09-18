package com.siegedempires.item;

import com.siegedempires.guide.GuideBook;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Dedicated Sieged Empire Guide item ({@code siegedempires:guide}).
 * Opens the custom guide GUI via the same book packet path as a written book.
 */
public class GuideBookItem extends Item {

	public GuideBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			GuideBook.applyContent(stack);
		}
		player.openItemGui(stack, hand);
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
