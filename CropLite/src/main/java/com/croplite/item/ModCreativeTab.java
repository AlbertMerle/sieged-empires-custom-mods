package com.croplite.item;

import com.croplite.CropLite;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	private ModCreativeTab() {
	}

	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, CropLite.id("croplite"));

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.croplite"))
				.icon(() -> new ItemStack(ModItems.TOMATO))
				.displayItems((parameters, output) -> {
					output.accept(ModItems.TOMATO);
					output.accept(ModItems.TOMATO_SEEDS);
					output.accept(ModItems.PEPPER);
					output.accept(ModItems.PEPPER_SEEDS);
					output.accept(ModItems.EGGPLANT);
					output.accept(ModItems.EGGPLANT_SEEDS);
					output.accept(ModItems.CUCUMBER);
					output.accept(ModItems.CUCUMBER_SEEDS);
					output.accept(ModItems.SWEET_POTATO);
					output.accept(ModItems.BAKED_SWEET_POTATO);
					output.accept(ModItems.OATS);
					output.accept(ModItems.BEANS);
					output.accept(ModItems.RICE);
					output.accept(ModItems.BASIL);
					output.accept(ModItems.BASIL_SEEDS);
					output.accept(ModItems.COFFEE_BEAN);
					output.accept(ModItems.ROASTED_COFFEE_BEAN);
					output.accept(ModItems.BOTTLE_OF_COFFEE);
					output.accept(ModItems.GARLIC);
					output.accept(ModItems.CACTUS_FLESH);
					output.accept(ModItems.CANTELOPE);
					output.accept(ModItems.CANTELOPE_SLICE);
					output.accept(ModItems.CANTELOPE_SEEDS);
					output.accept(ModItems.PEACH);
					output.accept(ModItems.PEACH_SAPLING);
					output.accept(ModItems.PEACH_LEAVES);
					output.accept(ModItems.LEMON);
					output.accept(ModItems.LEMON_SAPLING);
					output.accept(ModItems.LEMON_LEAVES);
					output.accept(ModItems.BANANA);
					output.accept(ModItems.BANANA_SAPLING);
					output.accept(ModItems.BANANA_LEAVES);
					output.accept(ModItems.BANANA_STALK);
					output.accept(ModItems.HAY);
					output.accept(ModItems.LEMONADE);
					output.accept(ModItems.OATMEAL);
					output.accept(ModItems.RICE_AND_BEANS);
				})
				.build());
	}
}
