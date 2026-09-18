package com.distantnoise.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Non-collidable plant / crop / flower / leaf-litter detection for player grass-walk SFX.
 * Includes Croplite crops (extend {@link CropBlock}) and other empty-collision croplite plants.
 */
public final class PlantVegetation {
	private PlantVegetation() {
	}

	/** True if the player is intersecting rustle plants, or the stepped-on block is one (e.g. leaf litter). */
	public static boolean isInRustlePlants(Player player, BlockState steppedOn) {
		if (isRustlePlant(steppedOn, player.level(), BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ()))) {
			return true;
		}
		return isTouchingRustlePlant(player);
	}

	public static boolean isTouchingRustlePlant(LivingEntity entity) {
		Level level = entity.level();
		AABB box = entity.getBoundingBox().deflate(0.01);
		int minX = Mth.floor(box.minX);
		int minY = Mth.floor(box.minY);
		int minZ = Mth.floor(box.minZ);
		int maxX = Mth.floor(box.maxX);
		int maxY = Mth.floor(box.maxY);
		int maxZ = Mth.floor(box.maxZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					if (isRustlePlant(level.getBlockState(pos), level, pos)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean isTouchingRustlePlant(Player player) {
		return isTouchingRustlePlant((LivingEntity) player);
	}

	public static boolean isRustlePlant(BlockState state, Level level, BlockPos pos) {
		if (state.isAir()) {
			return false;
		}
		if (state.is(Blocks.LEAF_LITTER)) {
			return true;
		}
		if (state.getBlock() instanceof VegetationBlock || state.getBlock() instanceof CropBlock) {
			return hasNoEntityCollision(state, level, pos);
		}
		if (state.is(BlockTags.CROPS) || state.is(BlockTags.FLOWERS)) {
			return hasNoEntityCollision(state, level, pos);
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (id != null && "croplite".equals(id.getNamespace())) {
			return hasNoEntityCollision(state, level, pos);
		}
		return false;
	}

	private static boolean hasNoEntityCollision(BlockState state, Level level, BlockPos pos) {
		VoxelShape shape = state.getCollisionShape(level, pos);
		return shape.isEmpty();
	}
}
