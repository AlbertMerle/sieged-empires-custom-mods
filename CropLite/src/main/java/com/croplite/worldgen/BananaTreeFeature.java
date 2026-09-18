package com.croplite.worldgen;

import com.croplite.CropLite;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

/**
 * Places one of the banana tree structure templates at random, pivoted so the
 * trunk base sits on the feature origin (sapling / worldgen surface position).
 */
public class BananaTreeFeature extends Feature<NoneFeatureConfiguration> {
	private static final List<TemplateSpec> TEMPLATES = List.of(
			new TemplateSpec("banana_tree_1", new BlockPos(1, 0, 0)),
			new TemplateSpec("banana_tree_2", new BlockPos(3, 0, 2)),
			new TemplateSpec("banana_tree_3", new BlockPos(4, 0, 3)),
			new TemplateSpec("banana_tree_4", new BlockPos(4, 0, 4)),
			new TemplateSpec("banana_tree_large", new BlockPos(6, 0, 6)));

	public BananaTreeFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		TemplateSpec spec = TEMPLATES.get(random.nextInt(TEMPLATES.size()));
		StructureTemplateManager manager = level.getLevel().getServer().getStructureManager();
		Optional<StructureTemplate> loaded = manager.get(spec.id());
		if (loaded.isEmpty()) {
			CropLite.LOGGER.warn("Missing banana tree structure {}", spec.id());
			return false;
		}

		StructureTemplate template = loaded.get();
		if (template.getSize().getX() < 1 || template.getSize().getY() < 1 || template.getSize().getZ() < 1) {
			CropLite.LOGGER.warn("Banana tree structure {} has empty size {}", spec.id(), template.getSize());
			return false;
		}

		Rotation rotation = Rotation.getRandom(random);
		BlockPos pivot = StructureTemplate.transform(spec.pivot(), Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos placePos = origin.subtract(pivot);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation)
				.setRandom(random)
				.setIgnoreEntities(true);

		// Sapling growth clears this first; worldgen may still have replaceable ground cover.
		level.setBlock(origin, Blocks.AIR.defaultBlockState(), 4);

		// Flag 3 matches vanilla TemplateFeature (UPDATE_CLIENTS | UPDATE_NEIGHBORS).
		return template.placeInWorld(level, placePos, placePos, settings, random, 3);
	}

	private record TemplateSpec(Identifier id, BlockPos pivot) {
		TemplateSpec(String path, BlockPos pivot) {
			this(CropLite.id(path), pivot);
		}
	}
}
