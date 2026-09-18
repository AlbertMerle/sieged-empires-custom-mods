package com.siegedempires.client.lock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.siegedempires.Siegedempires;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.Set;

/**
 * Draws {@code lock_overlay_door_bottom.png} as a face-aligned decal on locked blocks.
 * Uses the text (unlit) pipeline so shaders do not illuminate the overlay, and sits
 * 2 texels off the face so it does not clip into chests / barrels / doors.
 */
public final class LockOverlayRenderer {

	private static final Identifier TEXTURE =
		Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "textures/block/lock_overlay_door_bottom.png");
	/** 2 pixels at 16 px/block — keep the decal clear of the model surface. */
	private static final float FACE_OFFSET = 2.0F / 16.0F;
	private static final double MAX_DIST_SQ = 48.0 * 48.0;

	private LockOverlayRenderer() {}

	public static void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(LockOverlayRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null || mc.player == null) return;

		String dimension = level.dimension().identifier().toString();
		Set<BlockPos> locked = ClientLockCache.getLockedPositions(dimension);
		if (locked.isEmpty()) return;

		CameraRenderState camera = context.levelState().cameraRenderState;
		var camPos = camera.pos;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();

		for (BlockPos pos : locked) {
			if (camPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DIST_SQ) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) continue;

			if (state.getBlock() instanceof DoorBlock
				&& state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
				continue;
			}
			if (state.getBlock() instanceof ChestBlock
				&& state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(
				pos.getX() - camPos.x,
				pos.getY() - camPos.y,
				pos.getZ() - camPos.z
			);

			// Text pipeline: flat textured overlay (no entity normals / shader lighting).
			collector.submitCustomGeometry(poseStack, RenderTypes.text(TEXTURE),
				(p, buffer) -> drawForBlock(p, buffer, level, pos, state));

			poseStack.popPose();
		}
	}

	private static void drawForBlock(PoseStack.Pose pose, VertexConsumer buffer,
	                                 Level level, BlockPos pos, BlockState state) {
		VoxelShape shape = state.getShape(level, pos, CollisionContext.empty());
		if (shape.isEmpty()) return;

		AABB bounds = shape.bounds();
		int light = LightCoordsUtil.getLightCoords(level, pos);

		if (state.getBlock() instanceof DoorBlock) {
			drawPanelFaces(pose, buffer, bounds, light);
			return;
		}
		if (state.getBlock() instanceof FenceGateBlock) {
			drawPanelFaces(pose, buffer, bounds, light);
			return;
		}
		if (state.getBlock() instanceof ChestBlock) {
			AABB chestBounds = expandChestBounds(state, bounds);
			if (chestBounds != null) {
				drawFace(pose, buffer, chestBounds, state.getValue(ChestBlock.FACING), light);
			}
			return;
		}
		if (state.getBlock() instanceof BarrelBlock) {
			if (state.getValue(BarrelBlock.OPEN)) return;
			Direction facing = state.getValue(BarrelBlock.FACING);
			drawFace(pose, buffer, bounds, facing, light);
			drawFace(pose, buffer, bounds, facing.getOpposite(), light);
		}
	}

	/** Door / fence gate: both large faces of the thin panel (front + back). */
	private static void drawPanelFaces(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int light) {
		double dx = box.maxX - box.minX;
		double dz = box.maxZ - box.minZ;
		if (dx <= dz) {
			drawFace(pose, buffer, box, Direction.WEST, light);
			drawFace(pose, buffer, box, Direction.EAST, light);
		} else {
			drawFace(pose, buffer, box, Direction.NORTH, light);
			drawFace(pose, buffer, box, Direction.SOUTH, light);
		}
	}

	/**
	 * Double chests: one overlay spanning both halves, anchored on the LEFT block.
	 */
	private static AABB expandChestBounds(BlockState state, AABB local) {
		ChestType type = state.getValue(ChestBlock.TYPE);
		if (type == ChestType.SINGLE) {
			return local;
		}
		if (type == ChestType.RIGHT) {
			return null;
		}
		Direction connected = ChestBlock.getConnectedDirection(state);
		return switch (connected) {
			case EAST -> new AABB(local.minX, local.minY, local.minZ, local.maxX + 1.0, local.maxY, local.maxZ);
			case WEST -> new AABB(local.minX - 1.0, local.minY, local.minZ, local.maxX, local.maxY, local.maxZ);
			case SOUTH -> new AABB(local.minX, local.minY, local.minZ, local.maxX, local.maxY, local.maxZ + 1.0);
			case NORTH -> new AABB(local.minX, local.minY, local.minZ - 1.0, local.maxX, local.maxY, local.maxZ);
			default -> local;
		};
	}

	/** Full-face textured quad on one side of an AABB, offset outward along the face normal. */
	private static void drawFace(PoseStack.Pose pose, VertexConsumer buffer, AABB box, Direction face, int light) {
		float minX = (float) box.minX;
		float minY = (float) box.minY;
		float minZ = (float) box.minZ;
		float maxX = (float) box.maxX;
		float maxY = (float) box.maxY;
		float maxZ = (float) box.maxZ;

		float x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;

		switch (face) {
			case NORTH -> {
				float z = minZ - FACE_OFFSET;
				x0 = minX; y0 = minY; z0 = z;
				x1 = maxX; y1 = minY; z1 = z;
				x2 = maxX; y2 = maxY; z2 = z;
				x3 = minX; y3 = maxY; z3 = z;
			}
			case SOUTH -> {
				float z = maxZ + FACE_OFFSET;
				x0 = maxX; y0 = minY; z0 = z;
				x1 = minX; y1 = minY; z1 = z;
				x2 = minX; y2 = maxY; z2 = z;
				x3 = maxX; y3 = maxY; z3 = z;
			}
			case WEST -> {
				float x = minX - FACE_OFFSET;
				x0 = x; y0 = minY; z0 = maxZ;
				x1 = x; y1 = minY; z1 = minZ;
				x2 = x; y2 = maxY; z2 = minZ;
				x3 = x; y3 = maxY; z3 = maxZ;
			}
			case EAST -> {
				float x = maxX + FACE_OFFSET;
				x0 = x; y0 = minY; z0 = minZ;
				x1 = x; y1 = minY; z1 = maxZ;
				x2 = x; y2 = maxY; z2 = maxZ;
				x3 = x; y3 = maxY; z3 = minZ;
			}
			case UP -> {
				float y = maxY + FACE_OFFSET;
				x0 = minX; y0 = y; z0 = minZ;
				x1 = maxX; y1 = y; z1 = minZ;
				x2 = maxX; y2 = y; z2 = maxZ;
				x3 = minX; y3 = y; z3 = maxZ;
			}
			default -> { // DOWN
				float y = minY - FACE_OFFSET;
				x0 = minX; y0 = y; z0 = maxZ;
				x1 = maxX; y1 = y; z1 = maxZ;
				x2 = maxX; y2 = y; z2 = minZ;
				x3 = minX; y3 = y; z3 = minZ;
			}
		}

		Matrix4f mat = pose.pose();
		vert(buffer, mat, x0, y0, z0, 0F, 1F, light);
		vert(buffer, mat, x1, y1, z1, 1F, 1F, light);
		vert(buffer, mat, x2, y2, z2, 1F, 0F, light);
		vert(buffer, mat, x3, y3, z3, 0F, 0F, light);
	}

	private static void vert(VertexConsumer buffer, Matrix4f mat,
	                         float x, float y, float z, float u, float v, int light) {
		buffer.addVertex(mat, x, y, z)
			.setColor(255, 255, 255, 255)
			.setUv(u, v)
			.setLight(light);
	}
}
