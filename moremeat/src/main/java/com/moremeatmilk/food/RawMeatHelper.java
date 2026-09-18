package com.moremeatmilk.food;

import com.moremeatmilk.Moremeatmilk;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class RawMeatHelper {

	private static final Set<Item> EXEMPT = Set.of(Items.BEEF, Items.MUTTON);

	private RawMeatHelper() {
	}

	public static boolean isPenalizedRawMeat(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) {
			return false;
		}

		Item item = stack.getItem();
		if (EXEMPT.contains(item) || item == Items.ROTTEN_FLESH) {
			return false;
		}

		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		String path = id.getPath();
		if (path.startsWith("cooked_") || path.startsWith("cooked")) {
			return false;
		}

		if (stack.is(ItemTags.MEAT)) {
			return true;
		}

		if (Moremeatmilk.MOD_ID.equals(id.getNamespace()) && path.startsWith("raw_")) {
			return true;
		}

		return path.startsWith("raw_") || path.startsWith("raw");
	}

}
