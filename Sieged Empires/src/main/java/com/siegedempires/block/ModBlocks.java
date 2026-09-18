package com.siegedempires.block;

import com.siegedempires.Siegedempires;
import com.siegedempires.gold.GoldCoinSounds;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public final class ModBlocks {

    public static final Block LOCKSMITHING_TABLE = register(
        ModBlockItemIds.LOCKSMITHING_TABLE,
        LocksmithingTableBlock::new,
        BlockBehaviour.Properties.of()
            .strength(2.5F)
            .sound(SoundType.WOOD)
            .noOcclusion()
    );

    /** Placed with gold ingots; no block item. */
    public static final Block GOLD_BAR_PILE = registerBlockOnly(
        ResourceKey.create(Registries.BLOCK, Siegedempires.id("gold_bar_pile")),
        GoldBarPileBlock::new,
        BlockBehaviour.Properties.of()
            .strength(0.5F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()
    );

    /** Placed with gold coins; no block item. */
    public static final Block GOLD_COIN_PILE = registerBlockOnly(
        ResourceKey.create(Registries.BLOCK, Siegedempires.id("gold_coin_pile")),
        GoldCoinPileBlock::new,
        BlockBehaviour.Properties.of()
            .instabreak()
            .sound(GoldCoinSounds.BLOCK_SOUND)
            .noOcclusion()
            .noLootTable()
    );

    private static Block registerBlockOnly(ResourceKey<Block> key,
                                           Function<BlockBehaviour.Properties, Block> factory,
                                           BlockBehaviour.Properties properties) {
        Block block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static Block register(BlockItemId id, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        Block block = register(id.block(), factory, properties);
        BlockItem blockItem = new BlockItem(block,
            new Item.Properties().useBlockDescriptionPrefix().setId(id.item()));
        Registry.register(BuiltInRegistries.ITEM, id.item(), blockItem);
        return block;
    }

    private static Block register(ResourceKey<Block> key, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        Block block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static void initialize() {}
}
