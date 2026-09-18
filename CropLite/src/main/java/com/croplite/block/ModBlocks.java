package com.croplite.block;

import com.croplite.CropLite;
import com.croplite.item.ModItems;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Optional;
import java.util.function.Function;

public final class ModBlocks {
	private ModBlocks() {
	}

	public static final ResourceKey<ConfiguredFeature<?, ?>> PEACH_TREE = configuredFeature("peach_tree");
	public static final ResourceKey<ConfiguredFeature<?, ?>> LEMON_TREE = configuredFeature("lemon_tree");
	public static final ResourceKey<ConfiguredFeature<?, ?>> BANANA_TREE = configuredFeature("banana_tree");

	public static final TreeGrower PEACH_TREE_GROWER = new TreeGrower("croplite_peach", Optional.empty(), Optional.of(PEACH_TREE), Optional.empty());
	public static final TreeGrower LEMON_TREE_GROWER = new TreeGrower("croplite_lemon", Optional.empty(), Optional.of(LEMON_TREE), Optional.empty());
	public static final TreeGrower BANANA_TREE_GROWER = new TreeGrower("croplite_banana", Optional.empty(), Optional.of(BANANA_TREE), Optional.empty());

	public static final ResourceKey<Block> CANTELOPE_KEY = blockKey("cantelope");
	public static final ResourceKey<Block> CANTELOPE_STEM_KEY = blockKey("cantelope_stem");
	public static final ResourceKey<Block> ATTACHED_CANTELOPE_STEM_KEY = blockKey("attached_cantelope_stem");
	public static final ResourceKey<Item> CANTELOPE_SEEDS_KEY = ResourceKey.create(Registries.ITEM, CropLite.id("cantelope_seeds"));

	public static final Block TOMATO_CROP = register("tomato_crop",
			p -> new TallFruitingCropBlock(() -> ModItems.TOMATO_SEEDS, () -> ModItems.TOMATO, 4, 6, p), cropProperties());
	public static final Block PEPPER_CROP = register("pepper_crop",
			p -> new FruitingCropBlock(() -> ModItems.PEPPER_SEEDS, () -> ModItems.PEPPER, 4, 6, p), cropProperties());
	public static final Block EGGPLANT_CROP = register("eggplant_crop",
			p -> new FruitingCropBlock(() -> ModItems.EGGPLANT_SEEDS, () -> ModItems.EGGPLANT, 2, 4, p), cropProperties());
	public static final Block CUCUMBER_CROP = register("cucumber_crop",
			p -> new FruitingCropBlock(() -> ModItems.CUCUMBER_SEEDS, () -> ModItems.CUCUMBER, 4, 6, p), cropProperties());
	public static final Block COFFEE_CROP = register("coffee_crop",
			p -> new FruitingCropBlock(() -> ModItems.COFFEE_BEAN, () -> ModItems.COFFEE_BEAN, 1, 2, p), cropProperties());
	public static final Block GARLIC_CROP = register("garlic_crop",
			p -> new SimpleCropBlock(() -> ModItems.GARLIC, p), cropProperties());

	public static final Block SWEET_POTATO_CROP = register("sweet_potato_crop",
			p -> new SimpleCropBlock(() -> ModItems.SWEET_POTATO, p), cropProperties());
	public static final Block BASIL_CROP = register("basil_crop",
			p -> new SimpleCropBlock(() -> ModItems.BASIL_SEEDS, p), cropProperties());

	public static final Block OATS_CROP = register("oats_crop",
			p -> new TallCropBlock(() -> ModItems.OATS, p), cropProperties());
	public static final Block BEANS_CROP = register("beans_crop",
			p -> new SimpleCropBlock(() -> ModItems.BEANS, p), cropProperties());
	public static final Block RICE_CROP = register("rice_crop",
			p -> new TallCropBlock(() -> ModItems.RICE, p), cropProperties());

	public static final Block CANTELOPE = register(CANTELOPE_KEY, Block::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GREEN)
			.strength(1.0F)
			.sound(SoundType.WOOD)
			.pushReaction(PushReaction.DESTROY));

	public static final Block ATTACHED_CANTELOPE_STEM = register(ATTACHED_CANTELOPE_STEM_KEY,
			p -> new AttachedStemBlock(CANTELOPE_STEM_KEY, CANTELOPE_KEY, CANTELOPE_SEEDS_KEY, BlockTags.SUPPORTS_MELON_STEM, p),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.PLANT)
					.noCollision()
					.instabreak()
					.sound(SoundType.WOOD)
					.pushReaction(PushReaction.DESTROY));

	public static final Block CANTELOPE_STEM = register(CANTELOPE_STEM_KEY,
			p -> new StemBlock(CANTELOPE_KEY, ATTACHED_CANTELOPE_STEM_KEY, CANTELOPE_SEEDS_KEY,
					BlockTags.SUPPORTS_MELON_STEM, BlockTags.SUPPORTS_MELON_STEM_FRUIT, p),
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.PLANT)
					.noCollision()
					.randomTicks()
					.instabreak()
					.sound(SoundType.HARD_CROP)
					.pushReaction(PushReaction.DESTROY));

	public static final Block PEACH_LEAVES = register("peach_leaves", FruitLeavesBlock::new, leavesProperties());
	public static final Block LEMON_LEAVES = register("lemon_leaves", FruitLeavesBlock::new, leavesProperties());
	public static final Block BANANA_LEAVES = register("banana_leaves", FruitLeavesBlock::new, leavesProperties());

	public static final Block BANANA_STALK = register("banana_stalk", RotatedPillarBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_YELLOW)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.0F)
			.sound(SoundType.WOOD)
			.ignitedByLava());

	public static final Block PEACH_SAPLING = register("peach_sapling",
			p -> new SaplingBlock(PEACH_TREE_GROWER, p), saplingProperties());
	public static final Block LEMON_SAPLING = register("lemon_sapling",
			p -> new SaplingBlock(LEMON_TREE_GROWER, p), saplingProperties());
	public static final Block BANANA_SAPLING = register("banana_sapling",
			p -> new SaplingBlock(BANANA_TREE_GROWER, p), saplingProperties());

	private static BlockBehaviour.Properties cropProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.PLANT)
				.noCollision()
				.randomTicks()
				.instabreak()
				.sound(SoundType.CROP)
				.pushReaction(PushReaction.DESTROY);
	}

	private static BlockBehaviour.Properties saplingProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.PLANT)
				.noCollision()
				.randomTicks()
				.instabreak()
				.sound(SoundType.GRASS)
				.pushReaction(PushReaction.DESTROY);
	}

	private static BlockBehaviour.Properties leavesProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.PLANT)
				.strength(0.2F)
				.randomTicks()
				.sound(SoundType.GRASS)
				.noOcclusion()
				.isSuffocating((state, level, pos) -> false)
				.isViewBlocking((state, level, pos) -> false)
				.isRedstoneConductor((state, level, pos) -> false)
				.ignitedByLava()
				.pushReaction(PushReaction.DESTROY);
	}

	private static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, CropLite.id(name));
	}

	private static ResourceKey<ConfiguredFeature<?, ?>> configuredFeature(String name) {
		return ResourceKey.create(Registries.CONFIGURED_FEATURE, CropLite.id(name));
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return register(blockKey(name), factory, properties);
	}

	private static Block register(ResourceKey<Block> key, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
	}
}
