package com.siegedempires.guide;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.siegedempires.Siegedempires;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic “Crafting Recipes” guide section: guns, melee, locks, Shippy Ships builders + costs.
 * Craft recipes use {@link GuideBookContent#appendCrafting} so the book draws a 3×3 table GUI.
 * Each named recipe is wrapped in a recipe block so the title never paginates away from its grid.
 */
public final class GuideBookRecipes {
	private static final Style TITLE = Style.EMPTY.withBold(true);
	private static final Style RECIPE_NAME = Style.EMPTY.withBold(true);
	private static final Gson GSON = new Gson();
	private static final String E = GuideBookContent.EMPTY_CRAFT_CELL;

	private static final String[] BAYONET_MATS = {
			"wood", "stone", "copper", "iron", "gold", "diamond", "netherite"
	};

	/** Melee guide cycle (no copper): material ingredient + matching weapon result stay in sync. */
	private static final String[] TOOL_MATS = {
			"wood", "stone", "iron", "gold", "diamond", "netherite"
	};

	/** Display items for {@link #TOOL_MATS} (same order). */
	private static final String[] TOOL_MAT_ITEMS = {
			"minecraft:oak_planks",
			"minecraft:cobblestone",
			"minecraft:iron_ingot",
			"minecraft:gold_ingot",
			"minecraft:diamond",
			"minecraft:netherite_ingot"
	};

	private GuideBookRecipes() {
	}

	public static GuideBookContent.GuideSection section() {
		return new GuideBookContent.GuideSection(
				GuideBookContent.CRAFTING_SECTION_ID,
				"Crafting Recipes",
				pageComponents());
	}

	public static List<Component> pageComponents() {
		List<Component> pages = new ArrayList<>();
		pages.add(gunsIntroPage());
		pages.add(musketPartsPage());
		pages.add(musketBayonetPage());
		pages.add(scopedMusketPage());
		pages.add(otherGunsPage());
		pages.add(ammoPage());
		pages.add(meleeWeaponsPage());
		pages.add(locksPage());
		pages.add(shipBuildersPage());
		pages.add(shipCostsPage());
		pages.add(cannonPage());
		return pages;
	}

	/** Title (+ optional note) kept on the same page as the crafting grid. */
	private static void namedCrafting(MutableComponent page, String name, String note, String result, String... cells) {
		GuideBookContent.beginRecipeBlock(page);
		page.append(Component.literal(name).withStyle(RECIPE_NAME));
		if (note != null && !note.isEmpty()) {
			page.append("\n");
			page.append(note);
		}
		page.append("\n");
		GuideBookContent.appendCrafting(page, result, cells);
		GuideBookContent.endRecipeBlock(page);
	}

	private static Component gunsIntroPage() {
		MutableComponent page = Component.empty();
		page.append(Component.literal("Crafting Recipes").withStyle(TITLE));
		page.append("\n");
		page.append("Guns, javelins, knives, battleaxes, locks, and ships. Material recipes cycle in this book (one tier at a time).");
		page.append("\n");
		namedCrafting(page, "Gun Stock", null, "weaponmod:gun-stock",
				"stick", "stick", "oak_planks",
				E, E, E,
				E, E, E);
		return page;
	}

	private static Component musketPartsPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Musket Parts", null, "weaponmod:musket-ironpart",
				"iron_ingot", "iron_ingot", "flint_and_steel",
				E, E, "iron_ingot",
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Musket", null, "weaponmod:musket",
				E, "weaponmod:musket-ironpart", E,
				E, "weaponmod:gun-stock", E,
				E, E, E);
		return page;
	}

	private static Component musketBayonetPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Musket + Bayonet", "Musket + matching knife (material cycles):",
				cycleBayonetsCell("weaponmod", "musketbayonet"),
				"weaponmod:musket", cycleKnivesCell(), E,
				E, E, E,
				E, E, E);
		return page;
	}

	private static Component scopedMusketPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Scoped Musket", "Musket + Spyglass:",
				"weaponsmodaddon:scoped_musket",
				"weaponmod:musket", "minecraft:spyglass", E,
				E, E, E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Scoped Musket + Bayonet", "Bayonetted musket + Spyglass (cycles):",
				cycleBayonetsCell("weaponsmodaddon", "scoped_musketbayonet"),
				cycleBayonetsCell("weaponmod", "musketbayonet"), "minecraft:spyglass", E,
				E, E, E,
				E, E, E);
		return page;
	}

	private static Component otherGunsPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Flintlock Pistol", null, "weaponmod:flintlock",
				"iron_ingot", "iron_ingot", "flint_and_steel",
				E, "stick", "oak_planks",
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Blunderbuss Iron Part", null, "weaponmod:blunder-ironpart",
				"iron_ingot", E, E,
				E, "iron_ingot", "flint_and_steel",
				"iron_ingot", E, "iron_ingot");
		page.append("\n");
		namedCrafting(page, "Blunderbuss", null, "weaponmod:blunderbuss",
				E, "weaponmod:blunder-ironpart", E,
				E, "weaponmod:gun-stock", E,
				E, E, E);
		return page;
	}

	private static Component ammoPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Hand Mortar Iron Part", null, "weaponmod:mortar-ironpart",
				"iron_ingot", "iron_ingot", E,
				"iron_ingot", "iron_ingot", "flint_and_steel",
				E, E, "iron_ingot");
		page.append("\n");
		namedCrafting(page, "Hand Mortar", null, "weaponmod:mortar",
				E, "weaponmod:mortar-ironpart", E,
				E, "weaponmod:gun-stock", E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Bullet ×8", null, "weaponmod:bullet",
				"iron_ingot", "gunpowder", "paper",
				E, E, E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Shot ×8", null, "weaponmod:shot",
				"gravel", "gunpowder", "paper",
				E, E, E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Shell ×2", null, "weaponmod:shell",
				"iron_ingot", "gunpowder", "gunpowder",
				E, E, E,
				E, E, E);
		return page;
	}

	private static Component meleeWeaponsPage() {
		String mat = cycleToolMatItemsCell();
		String stick = "stick";
		MutableComponent page = Component.empty();
		namedCrafting(page, "Javelin", "Spears renamed — material cycles (wood → netherite):",
				cycleWeaponCell("spear"),
				E, E, mat,
				E, stick, E,
				stick, E, E);
		page.append("\n");
		namedCrafting(page, "Knife", "Material cycles (wood → netherite):",
				cycleWeaponCell("knife"),
				E, mat, E,
				E, stick, E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Battleaxe", "Material cycles (wood → netherite):",
				cycleWeaponCell("battleaxe"),
				mat, mat, mat,
				mat, stick, mat,
				E, stick, E);
		return page;
	}

	private static Component locksPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Locksmithing Table", null, "siegedempires:locksmithing_table",
				E, "iron_ingot", E,
				E, "oak_log", E,
				E, E, E);
		page.append("\n");
		namedCrafting(page, "Lock", null, "siegedempires:lock",
				"iron_ingot", "iron_ingot", "iron_ingot",
				"iron_ingot", "iron_nugget", "iron_ingot",
				"iron_ingot", "iron_ingot", "iron_ingot");
		page.append("\n");
		namedCrafting(page, "Key", null, "siegedempires:key",
				"iron_nugget", "iron_nugget", "iron_nugget",
				"iron_nugget", "iron_ingot", "iron_nugget",
				"iron_nugget", "iron_nugget", "iron_nugget");
		page.append("\n");
		namedCrafting(page, "Lockpick", null, "siegedempires:lockpick",
				"iron_ingot", "iron_ingot", "iron_ingot",
				E, E, E,
				E, E, E);
		return page;
	}

	private static Component shipBuildersPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Shippy Ships Builders", "Sailboat builder (3 logs in a row, middle row):",
				"shippy-ships:ship_builder",
				E, E, E,
				"oak_log", "oak_log", "oak_log",
				E, E, E);
		page.append("\n");
		GuideBookContent.beginRecipeBlock(page);
		page.append(Component.literal("Builder Upgrade Cycle").withStyle(RECIPE_NAME));
		page.append("\nBuilders upgrade in a cycle (not a crafting grid):");
		page.append("\nSailboat → Cog → Caravel → Decorative → Sailboat");
		page.append("\n ");
		page.append(GuideBookContent.itemMarker("shippy-ships", "ship_builder"));
		page.append(" → ");
		page.append(GuideBookContent.itemMarker("shippy-ships", "cog_builder"));
		page.append(" → ");
		page.append(GuideBookContent.itemMarker("shippy-ships", "caravel_builder"));
		page.append(" → ");
		page.append(GuideBookContent.itemMarker("shippy-ships", "ship_builder_deco"));
		GuideBookContent.endRecipeBlock(page);
		return page;
	}

	private static Component shipCostsPage() {
		ShipCosts costs = loadShipCosts();
		MutableComponent page = Component.empty();
		page.append(Component.literal("Ship Build Costs").withStyle(TITLE));
		page.append("\nRight-click the builder with materials in order (logs → planks → iron nuggets → wool → wax/resin). First log sets the wood type.");
		page.append("\n");
		GuideBookContent.beginRecipeBlock(page);
		page.append(Component.literal("Sailboat").withStyle(RECIPE_NAME));
		appendCostLine(page, costs.sailboat);
		GuideBookContent.endRecipeBlock(page);
		page.append("\n");
		GuideBookContent.beginRecipeBlock(page);
		page.append(Component.literal("Cog").withStyle(RECIPE_NAME));
		appendCostLine(page, costs.cog);
		GuideBookContent.endRecipeBlock(page);
		page.append("\n");
		GuideBookContent.beginRecipeBlock(page);
		page.append(Component.literal("Caravel").withStyle(RECIPE_NAME));
		appendCostLine(page, costs.caravel);
		GuideBookContent.endRecipeBlock(page);
		return page;
	}

	private static Component cannonPage() {
		MutableComponent page = Component.empty();
		namedCrafting(page, "Old Cannons", "Mount these on Shippy Ships for light explosive damage.",
				"oldcannons:cannonitem",
				"iron_block", E, "iron_block",
				"iron_ingot", "iron_block", "iron_ingot",
				"oak_planks", "oak_planks", "oak_planks");
		return page;
	}

	/** Icon + count for each step, joined with {@code +} (wax/resin cycles honeycomb ↔ resin). */
	private static void appendCostLine(MutableComponent page, int[] steps) {
		page.append("\n");
		boolean first = true;
		for (int i = 0; i < COST_ICONS.length; i++) {
			int amount = i < steps.length ? steps[i] : 0;
			if (amount <= 0) {
				continue;
			}
			if (!first) {
				page.append(" + ");
			}
			first = false;
			String icon = COST_ICONS[i];
			if (icon.startsWith("cycle:")) {
				page.append(GuideBookContent.cycleItemMarker(icon.substring("cycle:".length()).split("\\|")));
			} else {
				int colon = icon.indexOf(':');
				page.append(GuideBookContent.itemMarker(icon.substring(0, colon), icon.substring(colon + 1)));
			}
			page.append(" " + amount);
		}
	}

	/** Representative icons: logs, planks, nuggets, wool, wax|resin. */
	private static final String[] COST_ICONS = {
			"minecraft:oak_log",
			"minecraft:oak_planks",
			"minecraft:iron_nugget",
			"minecraft:white_wool",
			"cycle:minecraft:honeycomb|minecraft:resin_clump"
	};

	private static String cycleKnivesCell() {
		String[] ids = new String[BAYONET_MATS.length];
		for (int i = 0; i < BAYONET_MATS.length; i++) {
			ids[i] = "weaponmod:knife." + BAYONET_MATS[i];
		}
		return GuideBookContent.cycleCraftCell(ids);
	}

	private static String cycleBayonetsCell(String ns, String base) {
		String[] ids = new String[BAYONET_MATS.length];
		for (int i = 0; i < BAYONET_MATS.length; i++) {
			ids[i] = ns + ":" + base + "." + BAYONET_MATS[i];
		}
		return GuideBookContent.cycleCraftCell(ids);
	}

	private static String cycleToolMatItemsCell() {
		return GuideBookContent.cycleCraftCell(TOOL_MAT_ITEMS);
	}

	private static String cycleWeaponCell(String base) {
		String[] ids = new String[TOOL_MATS.length];
		for (int i = 0; i < TOOL_MATS.length; i++) {
			ids[i] = "weaponmod:" + base + "." + TOOL_MATS[i];
		}
		return GuideBookContent.cycleCraftCell(ids);
	}

	private static ShipCosts loadShipCosts() {
		Path path = Path.of("config", "shippy-ships.json");
		int[] sail = {10, 64, 16, 8, 1};
		int[] cog = {32, 64, 32, 12, 2};
		int[] caravel = {64, 64, 64, 64, 12};
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonObject json = GSON.fromJson(reader, JsonObject.class);
				if (json != null) {
					sail = readSteps(json, "sailboat", sail);
					cog = readSteps(json, "cog", cog);
					caravel = readSteps(json, "caravel", caravel);
				}
			} catch (Exception e) {
				Siegedempires.LOGGER.warn("Could not read shippy-ships.json for guide costs", e);
			}
		}
		return new ShipCosts(sail, cog, caravel);
	}

	private static int[] readSteps(JsonObject json, String prefix, int[] defaults) {
		int[] out = defaults.clone();
		for (int i = 0; i < 5; i++) {
			String key = prefix + "_step_" + (i + 1);
			if (json.has(key)) {
				out[i] = Math.max(0, json.get(key).getAsInt());
			}
		}
		return out;
	}

	private record ShipCosts(int[] sailboat, int[] cog, int[] caravel) {
	}
}
