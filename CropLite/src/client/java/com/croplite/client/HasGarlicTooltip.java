package com.croplite.client;

import com.croplite.item.ModDataComponents;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Adds "Has Garlic" below the name for foods crafted with garlic. */
public final class HasGarlicTooltip {
	private HasGarlicTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.has(ModDataComponents.HAS_GARLIC) && !lines.isEmpty()) {
				lines.add(1, Component.translatable("item.croplite.has_garlic").withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
