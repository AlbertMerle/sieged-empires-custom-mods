package com.siegedempires.item;

import com.siegedempires.Siegedempires;
import com.siegedempires.block.ModBlocks;
import com.siegedempires.guide.GuideBook;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public final class ModItems {

    public static final Item KEY = register("key",
        props -> new KeyItem(props.stacksTo(1)));
    public static final Item LOCK = register("lock",
        props -> new LockItem(props.stacksTo(16)));
    public static final Item LOCKPICK = register("lockpick",
        LockpickItem::new);
    public static final Item GUIDE = register("guide",
        props -> new GuideBookItem(props.stacksTo(1)));
    public static final Item GOLD_COIN = register("gold_coin",
        props -> new GoldCoinItem(props.stacksTo(64)));

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Siegedempires.id(name));
        Item item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
            output.accept(ModBlocks.LOCKSMITHING_TABLE));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(KEY);
            output.accept(LOCK);
            output.accept(LOCKPICK);
            output.accept(GuideBook.create());
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
            output.accept(GuideBook.create());
            output.accept(GOLD_COIN);
        });
        Siegedempires.LOGGER.info("Registered lock system items + guide");
    }
}
