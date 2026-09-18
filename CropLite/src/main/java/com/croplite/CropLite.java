package com.croplite;

import com.croplite.block.ModBlocks;
import com.croplite.config.CropLiteConfig;
import com.croplite.item.ModCreativeTab;
import com.croplite.item.ModItemComponents;
import com.croplite.item.ModItems;
import com.croplite.loot.ModLoot;
import com.croplite.worldgen.ModWorldgen;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CropLite implements ModInitializer {
	public static final String MOD_ID = "croplite";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CropLiteConfig.load();
		com.croplite.item.ModConsumeEffects.initialize();
		com.croplite.item.ModDataComponents.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModItemComponents.initialize();
		com.croplite.item.ModBrewing.initialize();
		ModCreativeTab.initialize();
		ModLoot.initialize();
		ModWorldgen.initialize();

		LOGGER.info("CropLite initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
