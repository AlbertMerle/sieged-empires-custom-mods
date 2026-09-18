package com.moremeatmilk;

import com.moremeatmilk.item.ModItems;
import com.moremeatmilk.loot.ModLoot;
import com.moremeatmilk.milking.ModMilking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Moremeatmilk implements ModInitializer {
	public static final String MOD_ID = "moremeatmilk";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.register();
		ModItems.initialize();
		ModLoot.initialize();
		ModMilking.initialize();
		LOGGER.info("More Meat & Milk initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
