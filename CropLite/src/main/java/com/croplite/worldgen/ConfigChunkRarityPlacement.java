package com.croplite.worldgen;

import com.croplite.config.CropLiteConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Fruit-tree placement gate: climate rules + deterministic 1-in-N chunk rarity from
 * {@link CropLiteConfig} ({@code *_tree_chunks_per_spawn}).
 *
 * <p>Uses a stable chunk hash (not {@code random.nextFloat()}) so density cannot drift
 * from RNG stream quirks and averages exactly {@code 1/N} of eligible chunks.
 * {@code 0} disables; {@code 1} attempts every eligible chunk; {@code 30} ≈ 1 per 30 chunks.
 */
public final class ConfigChunkRarityPlacement extends PlacementFilter {
	public static final MapCodec<ConfigChunkRarityPlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					Tree.CODEC.fieldOf("tree").forGetter(p -> p.tree)
			).apply(instance, ConfigChunkRarityPlacement::new));

	private final Tree tree;

	public ConfigChunkRarityPlacement(Tree tree) {
		this.tree = tree;
	}

	@Override
	protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		CropLiteConfig config = CropLiteConfig.get();
		if (!config.naturalFruitTreeGeneration) {
			return false;
		}

		Holder<Biome> biome = context.getLevel().getBiome(origin);
		if (!tree.allowsBiome(biome, config)) {
			return false;
		}

		int chunksPerSpawn = tree.chunksPerSpawn(config);
		if (chunksPerSpawn <= 0) {
			return false;
		}
		if (chunksPerSpawn == 1) {
			return true;
		}

		return passesDeterministicChunkRarity(context.getLevel().getSeed(), origin, tree, chunksPerSpawn);
	}

	/**
	 * Exactly one in {@code n} chunks (on average), stable for a given world seed.
	 * Evaluated on the decoration origin (chunk corner before {@code in_square}).
	 */
	static boolean passesDeterministicChunkRarity(long worldSeed, BlockPos origin, Tree tree, int n) {
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		long hash = worldSeed;
		hash ^= (long) chunkX * 341873128712L;
		hash ^= (long) chunkZ * 132897987541L;
		hash ^= (long) (tree.ordinal() + 1) * 0x9E3779B97F4A7C15L;
		hash ^= (hash >>> 30);
		hash *= 0xBF58476D1CE4E5B9L;
		hash ^= (hash >>> 27);
		hash *= 0x94D049BB133111EBL;
		hash ^= (hash >>> 31);
		return Math.floorMod(hash, n) == 0;
	}

	@Override
	public PlacementModifierType<?> type() {
		return ModPlacementModifiers.CONFIG_CHUNK_RARITY;
	}

	public enum Tree implements StringRepresentable {
		PEACH("peach"),
		LEMON("lemon"),
		BANANA("banana");

		public static final Codec<Tree> CODEC = StringRepresentable.fromEnum(Tree::values);

		private final String name;

		Tree(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		int chunksPerSpawn(CropLiteConfig config) {
			return switch (this) {
				case PEACH -> config.peachTreeChunksPerSpawn;
				case LEMON -> config.lemonTreeChunksPerSpawn;
				case BANANA -> config.bananaTreeChunksPerSpawn;
			};
		}

		/** Placement-time climate gate (mirrors biome selectors in {@link ModWorldgen}). */
		boolean allowsBiome(Holder<Biome> biome, CropLiteConfig config) {
			return switch (this) {
				case PEACH -> BiomeSpawnCategory.allowsPeachTree(biome, config);
				case LEMON -> BiomeSpawnCategory.allowsLemonTree(biome, config);
				case BANANA -> BiomeSpawnCategory.allowsBananaTree(biome, config);
			};
		}
	}
}
