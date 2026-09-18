package com.voxmapsync.server.webmap;

import com.voxmapsync.VoxelMapSync;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates hierarchical quadtree pyramid downscale tiles (-1, -2, -3, -4, -5...).
 * Allows instant, low-bandwidth zooming out to see the entire 30,000 x 30,000 world.
 */
public final class WebPyramidDownscaler {
	private static final Pattern TILE_FILE_PATTERN = Pattern.compile("^(-?\\d+)_(-?\\d+)\\.png$");

	private WebPyramidDownscaler() {
	}

	/**
	 * Rebuilds all pyramid downscale zoom levels from zoom 0 down to -maxZoomOut.
	 */
	public static void rebuildFullPyramid(Path dimTilesDir, int maxZoomOut) {
		if (dimTilesDir == null || !Files.exists(dimTilesDir) || maxZoomOut <= 0) {
			return;
		}

		for (int zoom = -1; zoom >= -maxZoomOut; zoom--) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			int childZoom = zoom + 1;
			Path childDir = dimTilesDir.resolve(Integer.toString(childZoom));
			Path parentDir = dimTilesDir.resolve(Integer.toString(zoom));

			if (!Files.isDirectory(childDir)) {
				break;
			}

			Set<Long> parentCoords = new HashSet<>();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(childDir, "*.png")) {
				for (Path childFile : stream) {
					if (Thread.currentThread().isInterrupted()) {
						return;
					}
					Matcher matcher = TILE_FILE_PATTERN.matcher(childFile.getFileName().toString());
					if (matcher.matches()) {
						int cx = Integer.parseInt(matcher.group(1));
						int cz = Integer.parseInt(matcher.group(2));
						int px = Math.floorDiv(cx, 2);
						int pz = Math.floorDiv(cz, 2);
						long key = (((long) px) << 32) | (pz & 0xFFFFFFFFL);
						parentCoords.add(key);
					}
				}
			} catch (IOException e) {
				VoxelMapSync.LOGGER.warn("Failed to scan child tiles in {}", childDir, e);
				break;
			}

			if (parentCoords.isEmpty()) {
				break;
			}

			try {
				Files.createDirectories(parentDir);
			} catch (IOException ignored) {
			}

			for (long key : parentCoords) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}
				int parentX = (int) (key >> 32);
				int parentZ = (int) key;
				int originChildX = parentX * 2;
				int originChildZ = parentZ * 2;

				BufferedImage parentImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = parentImage.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g.setComposite(java.awt.AlphaComposite.Src);

				boolean anyChild = false;
				for (int ox = 0; ox < 2; ox++) {
					for (int oz = 0; oz < 2; oz++) {
						int cx = originChildX + ox;
						int cz = originChildZ + oz;
						Path childPath = childDir.resolve(cx + "_" + cz + ".png");
						if (Files.exists(childPath)) {
							try {
								BufferedImage childTile = ImageIO.read(childPath.toFile());
								if (childTile != null) {
									g.drawImage(childTile, ox * 128, oz * 128, 128, 128, null);
									childTile.flush();
									anyChild = true;
								}
							} catch (Exception ignored) {
							}
						}
					}
				}
				g.dispose();

				if (anyChild) {
					Path parentFile = parentDir.resolve(parentX + "_" + parentZ + ".png");
					try {
						ImageIO.write(parentImage, "PNG", parentFile.toFile());
					} catch (IOException e) {
						VoxelMapSync.LOGGER.warn("Failed to write pyramid tile {}", parentFile, e);
					}
				}
				parentImage.flush();
			}
		}
	}

	/**
	 * Updates the parent overview tiles up to maxZoomOut.
	 */
	public static void updatePyramid(Path dimTilesDir, int baseRegionX, int baseRegionZ, BufferedImage baseImage, int maxZoomOut) {
		int currentChildX = baseRegionX;
		int currentChildZ = baseRegionZ;
		BufferedImage currentChildImg = baseImage;

		for (int zoom = -1; zoom >= -maxZoomOut; zoom--) {
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			int parentZoom = zoom;
			int parentX = Math.floorDiv(currentChildX, 2);
			int parentZ = Math.floorDiv(currentChildZ, 2);

			Path parentDir = dimTilesDir.resolve(Integer.toString(parentZoom));
			Path parentFile = parentDir.resolve(parentX + "_" + parentZ + ".png");

			// Child zoom level dir
			int childZoom = zoom + 1;
			Path childDir = dimTilesDir.resolve(Integer.toString(childZoom));

			BufferedImage parentImage = loadOrCreateParent(parentFile);
			Graphics2D g = parentImage.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setComposite(java.awt.AlphaComposite.Src);

			// Draw 4 quadrants: (2*parentX + ox, 2*parentZ + oz)
			int originChildX = parentX * 2;
			int originChildZ = parentZ * 2;

			for (int ox = 0; ox < 2; ox++) {
				for (int oz = 0; oz < 2; oz++) {
					int cx = originChildX + ox;
					int cz = originChildZ + oz;
					int drawX = ox * 128;
					int drawY = oz * 128;

					BufferedImage childTile = null;
					boolean isDirect = false;
					if (cx == currentChildX && cz == currentChildZ && currentChildImg != null) {
						childTile = currentChildImg;
						isDirect = true;
					} else {
						Path childPath = childDir.resolve(cx + "_" + cz + ".png");
						if (Files.exists(childPath)) {
							try {
								childTile = ImageIO.read(childPath.toFile());
							} catch (Exception ignored) {
							}
						}
					}

					if (childTile != null) {
						g.drawImage(childTile, drawX, drawY, 128, 128, null);
						if (!isDirect) {
							childTile.flush();
						}
					}
				}
			}
			g.dispose();

			try {
				Files.createDirectories(parentDir);
				ImageIO.write(parentImage, "PNG", parentFile.toFile());
			} catch (IOException e) {
				VoxelMapSync.LOGGER.warn("Failed to write pyramid tile {}", parentFile, e);
				parentImage.flush();
				break;
			}

			if (currentChildImg != null && currentChildImg != baseImage) {
				currentChildImg.flush();
			}

			currentChildX = parentX;
			currentChildZ = parentZ;
			currentChildImg = parentImage;
		}

		if (currentChildImg != null && currentChildImg != baseImage) {
			currentChildImg.flush();
		}
	}

	private static BufferedImage loadOrCreateParent(Path file) {
		if (Files.exists(file)) {
			try {
				BufferedImage img = ImageIO.read(file.toFile());
				if (img != null && img.getWidth() == 256 && img.getHeight() == 256) {
					return img;
				}
			} catch (Exception ignored) {
			}
		}
		return new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
	}
}
