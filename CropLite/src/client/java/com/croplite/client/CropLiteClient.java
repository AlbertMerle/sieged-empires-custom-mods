package com.croplite.client;

import com.croplite.block.ModBlocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import net.minecraft.client.color.block.BlockTintSources;

import java.util.List;

public class CropLiteClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CropRotTint.register();
		HasGarlicTooltip.register();

		// Greyscale leaf textures + fruit overlays; biome foliage tint like oak leaves.
		BlockColorRegistry.register(List.of(BlockTintSources.foliage()),
				ModBlocks.PEACH_LEAVES, ModBlocks.LEMON_LEAVES, ModBlocks.BANANA_LEAVES);
	}
}
