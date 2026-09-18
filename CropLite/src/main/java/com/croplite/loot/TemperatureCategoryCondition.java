package com.croplite.loot;

import com.croplite.crop.TemperatureCategory;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

import com.croplite.CropLite;

import java.util.List;
import java.util.Set;

/**
 * Passes when the loot origin's biome base temperature falls in one of the listed categories.
 * JSON: {@code { "condition": "croplite:temperature_category", "categories": ["temperate", "cold"] }}
 */
public record TemperatureCategoryCondition(List<TemperatureCategory> categories) implements LootItemCondition {
	public static final MapCodec<TemperatureCategoryCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TemperatureCategory.CODEC.listOf().fieldOf("categories").forGetter(TemperatureCategoryCondition::categories)
			).apply(instance, TemperatureCategoryCondition::new));

	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, CropLite.id("temperature_category"), MAP_CODEC);
	}

	@Override
	public MapCodec<TemperatureCategoryCondition> codec() {
		return MAP_CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin == null || categories.isEmpty()) {
			return false;
		}

		BlockPos pos = BlockPos.containing(origin);
		TemperatureCategory actual = TemperatureCategory.of(context.getLevel().getBiome(pos).value());
		return Set.copyOf(categories).contains(actual);
	}
}
