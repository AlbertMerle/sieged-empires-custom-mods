package com.weaponsmodaddon.item;

import ckathode.weaponmod.item.MeleeCompKnife;
import ckathode.weaponmod.item.MeleeCompNone;
import com.weaponsmodaddon.WeaponsModAddon;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class ScopedMusketItems {

	public static ScopedItemMusket SCOPED_MUSKET;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_WOOD;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_STONE;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_COPPER;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_IRON;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_GOLD;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_DIAMOND;
	public static ScopedItemMusket SCOPED_MUSKET_BAYONET_NETHERITE;

	private ScopedMusketItems() {
	}

	public static void register() {
		SCOPED_MUSKET = registerPlain("scoped_musket");
		SCOPED_MUSKET_BAYONET_WOOD =
				registerBayonet("scoped_musketbayonet.wood", ToolMaterial.WOOD, MeleeCompKnife.WOOD_ITEM);
		SCOPED_MUSKET_BAYONET_STONE =
				registerBayonet("scoped_musketbayonet.stone", ToolMaterial.STONE, MeleeCompKnife.STONE_ITEM);
		SCOPED_MUSKET_BAYONET_COPPER =
				registerBayonet("scoped_musketbayonet.copper", ToolMaterial.COPPER, MeleeCompKnife.COPPER_ITEM);
		SCOPED_MUSKET_BAYONET_IRON =
				registerBayonet("scoped_musketbayonet.iron", ToolMaterial.IRON, MeleeCompKnife.IRON_ITEM);
		SCOPED_MUSKET_BAYONET_GOLD =
				registerBayonet("scoped_musketbayonet.gold", ToolMaterial.GOLD, MeleeCompKnife.GOLD_ITEM);
		SCOPED_MUSKET_BAYONET_DIAMOND =
				registerBayonet("scoped_musketbayonet.diamond", ToolMaterial.DIAMOND, MeleeCompKnife.DIAMOND_ITEM);
		SCOPED_MUSKET_BAYONET_NETHERITE =
				registerBayonet("scoped_musketbayonet.netherite", ToolMaterial.NETHERITE, MeleeCompKnife.NETHERITE_ITEM);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(SCOPED_MUSKET);
			output.accept(SCOPED_MUSKET_BAYONET_WOOD);
			output.accept(SCOPED_MUSKET_BAYONET_STONE);
			output.accept(SCOPED_MUSKET_BAYONET_COPPER);
			output.accept(SCOPED_MUSKET_BAYONET_IRON);
			output.accept(SCOPED_MUSKET_BAYONET_GOLD);
			output.accept(SCOPED_MUSKET_BAYONET_DIAMOND);
			output.accept(SCOPED_MUSKET_BAYONET_NETHERITE);
		});
	}

	private static ScopedItemMusket registerPlain(String path) {
		Identifier id = WeaponsModAddon.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		ScopedItemMusket item = new ScopedItemMusket(new MeleeCompNone(null), null, id, null);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static ScopedItemMusket registerBayonet(String path, ToolMaterial material, Item bayonetItem) {
		Identifier id = WeaponsModAddon.id(path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		ScopedItemMusket item = new ScopedItemMusket(
				new MeleeCompKnife(material), bayonetItem, id, SCOPED_MUSKET);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
