package com.weaponsmodaddon;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
	public static final TagKey<Item> TWO_HANDED_GUNS = TagKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "two_handed_guns"));

	public static final TagKey<Item> PISTOLS = TagKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "pistols"));

	/**
	 * WeaponMod spears (`#weaponmod:spears`) — Blockbench {@code spearanimation.GLTF} clips
	 * (holding / aim / aimhold / throw). Charge still uses +90° tip model while using.
	 */
	public static final TagKey<Item> SPEARS = TagKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath("weaponmod", "spears"));

	/** Javelins — same Blockbench clips + 180° hand flip while aiming/throwing. */
	public static final TagKey<Item> JAVELINS = TagKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "javelins"));

	/** Scoped muskets / bayonets — spyglass overlay + multi-stage zoom while aiming. */
	public static final TagKey<Item> SCOPED_MUSKETS = TagKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(WeaponsModAddon.MOD_ID, "scoped_muskets"));

	private ModItemTags() {
	}
}
