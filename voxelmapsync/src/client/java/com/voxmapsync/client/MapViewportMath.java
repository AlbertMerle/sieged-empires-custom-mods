package com.voxmapsync.client;

import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;

import java.lang.reflect.Field;
import java.util.List;

/** Shared viewport math for VoxelMap world-map region requests and claim overlays. */
public final class MapViewportMath {
	private static Field mapCenterXField;
	private static Field mapCenterZField;
	private static Field centerXField;
	private static Field guiToMapField;
	private static Field oldNorthField;
	private static Field topField;
	private static Field bottomField;
	private static boolean fieldsResolved;
	private static boolean fieldsFailed;

	private MapViewportMath() {
	}

	public record RegionBounds(int left, int right, int top, int bottom) {
		public int minBlockX() {
			return left * 256;
		}

		public int maxBlockX() {
			return (right + 1) * 256;
		}

		public int minBlockZ() {
			return top * 256;
		}

		public int maxBlockZ() {
			return (bottom + 1) * 256;
		}

		public boolean intersectsChunk(int chunkX, int chunkZ) {
			int x1 = chunkX << 4;
			int z1 = chunkZ << 4;
			int x2 = x1 + 16;
			int z2 = z1 + 16;
			return x2 > minBlockX() && x1 < maxBlockX() && z2 > minBlockZ() && z1 < maxBlockZ();
		}
	}

	public static RegionBounds regionBounds(GuiPersistentMap screen, int marginRegions) {
		if (!resolveFields()) {
			return null;
		}
		try {
			float mapCenterX = mapCenterXField.getFloat(screen);
			float mapCenterZ = mapCenterZField.getFloat(screen);
			int centerX = centerXField.getInt(screen);
			int top = topField.getInt(screen);
			int bottom = bottomField.getInt(screen);
			int centerY = (bottom - top) / 2;
			float guiToMap = guiToMapField.getFloat(screen);
			boolean oldNorth = oldNorthField.getBoolean(screen);

			int left;
			int right;
			int regionTop;
			int regionBottom;
			if (oldNorth) {
				left = (int) Math.floor((mapCenterZ - centerY * guiToMap) / 256.0F);
				right = (int) Math.floor((mapCenterZ + centerY * guiToMap) / 256.0F);
				regionTop = (int) Math.floor((-mapCenterX - centerX * guiToMap) / 256.0F);
				regionBottom = (int) Math.floor((-mapCenterX + centerX * guiToMap) / 256.0F);
			} else {
				left = (int) Math.floor((mapCenterX - centerX * guiToMap) / 256.0F);
				right = (int) Math.floor((mapCenterX + centerX * guiToMap) / 256.0F);
				regionTop = (int) Math.floor((mapCenterZ - centerY * guiToMap) / 256.0F);
				regionBottom = (int) Math.floor((mapCenterZ + centerY * guiToMap) / 256.0F);
			}
			return new RegionBounds(
					left - marginRegions,
					right + marginRegions,
					regionTop - marginRegions,
					regionBottom + marginRegions);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static boolean resolveFields() {
		if (fieldsResolved) {
			return true;
		}
		if (fieldsFailed) {
			return false;
		}
		try {
			Class<?> clazz = GuiPersistentMap.class;
			mapCenterXField = clazz.getDeclaredField("mapCenterX");
			mapCenterZField = clazz.getDeclaredField("mapCenterZ");
			centerXField = clazz.getDeclaredField("centerX");
			guiToMapField = clazz.getDeclaredField("guiToMap");
			oldNorthField = clazz.getDeclaredField("oldNorth");
			topField = clazz.getDeclaredField("top");
			bottomField = clazz.getDeclaredField("bottom");
			for (Field f : List.of(
					mapCenterXField, mapCenterZField, centerXField,
					guiToMapField, oldNorthField, topField, bottomField)) {
				f.setAccessible(true);
			}
			fieldsResolved = true;
			return true;
		} catch (ReflectiveOperationException e) {
			fieldsFailed = true;
			return false;
		}
	}
}
