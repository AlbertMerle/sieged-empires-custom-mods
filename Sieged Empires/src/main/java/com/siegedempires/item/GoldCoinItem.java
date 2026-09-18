package com.siegedempires.item;

import com.siegedempires.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GoldCoinItem extends Item {
	public GoldCoinItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		if (stack.getCount() >= 2) {
			return Component.translatable("item.siegedempires.gold_coin.plural");
		}
		return super.getName(stack);
	}

	@Override
	public void onCraftedBy(ItemStack stack, Player player) {
		if (player.level() instanceof ServerLevel level) {
			level.playSound(
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					ModSounds.COIN_CRAFT.value(),
					SoundSource.PLAYERS,
					0.9F,
					1.0F
			);
		}
	}
}
