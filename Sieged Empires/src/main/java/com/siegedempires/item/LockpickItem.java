package com.siegedempires.item;

import net.minecraft.world.item.Item;

/** One-use lockpick consumed after each lockpicking attempt. */
public class LockpickItem extends Item {
	public LockpickItem(Properties properties) {
		super(properties.stacksTo(16));
	}
}