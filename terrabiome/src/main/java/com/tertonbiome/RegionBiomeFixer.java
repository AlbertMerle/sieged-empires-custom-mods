package com.tertonbiome;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.border.WorldBorder;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Background biome repair: scans generated chunks for illegal zone biomes and
 * replaces them slowly on the server thread (one or few chunks per batch with
 * pauses) so players are not hit with lag spikes.
 */
public final class RegionBiomeFixer {
	private static final int REGION_SIZE = 256;
	private static final int PROGRESS_EVERY_CHUNKS = 64;

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Terrabiome-Fixer");
		thread.setDaemon(true);
		return thread;
	});

	private static final AtomicBoolean running = new AtomicBoolean(false);
	private static volatile boolean cancelRequested = false;
	private static volatile FixProgress progress = FixProgress.idle();

	private RegionBiomeFixer() {}

	public static boolean isRunning() {
		return running.get();
	}

	public static String statusSummary() {
		return progress.summary();
	}

	public static boolean stop(Consumer<Component> feedback) {
		if (!running.get()) {
			feedback.accept(Component.literal("No Terrabiome fix is running."));
			return false;
		}
		cancelRequested = true;
		feedback.accept(Component.literal("Terrabiome fix stop requested — finishing current chunk batch…"));
		return true;
	}

	public static boolean startRegionFix(ServerLevel level, int regionX, int regionZ, Consumer<Component> feedback) {
		int minX = regionX * REGION_SIZE;
		int minZ = regionZ * REGION_SIZE;
		int maxX = minX + REGION_SIZE - 1;
		int maxZ = minZ + REGION_SIZE - 1;
		String label = "region " + regionX + "," + regionZ;
		return startBoundsFix(level, boundsFor(minX, minZ, maxX, maxZ), label, feedback);
	}

	public static boolean startZStripFix(ServerLevel level, int fromZ, int toZ, Consumer<Component> feedback) {
		int minZ = Math.min(fromZ, toZ);
		int maxZ = Math.max(fromZ, toZ);
		WorldBorder border = level.getWorldBorder();
		int minX = (int) Math.ceil(border.getMinX());
		int maxX = (int) Math.floor(border.getMaxX());
		String label = "Z " + minZ + " to " + maxZ;
		return startBoundsFix(level, boundsFor(minX, minZ, maxX, maxZ), label, feedback);
	}

	private static BlockBounds boundsFor(int minX, int minZ, int maxX, int maxZ) {
		return new BlockBounds(
			minX,
			SurfaceBiomeBounds.minY(),
			minZ,
			maxX,
			SurfaceBiomeBounds.maxY(),
			maxZ
		);
	}

	private static boolean startBoundsFix(
		ServerLevel level,
		BlockBounds bounds,
		String label,
		Consumer<Component> feedback
	) {
		if (!running.compareAndSet(false, true)) {
			return false;
		}
		if (!level.dimension().equals(Level.OVERWORLD)) {
			running.set(false);
			feedback.accept(Component.literal("Terrabiome fix only works in the overworld."));
			return false;
		}

		cancelRequested = false;
		ChunkSpan span = ChunkSpan.fromBounds(bounds);
		if (span.totalChunks() <= 0) {
			running.set(false);
			feedback.accept(Component.literal("Terrabiome fix: no chunks in range."));
			return false;
		}

		TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
		progress = FixProgress.started(label, span.totalChunks());
		MinecraftServer server = level.getServer();
		feedback.accept(Component.literal(
			"Terrabiome: queued background fix for " + label
				+ " (" + span.totalChunks() + " chunk(s), Y " + bounds.minY() + "…" + bounds.maxY()
				+ ", " + config.fixChunksPerBatch + " chunk(s)/batch, " + config.fixBatchDelayMs + "ms pause)…"
		));

		WORKER.execute(() -> runJob(server, level, bounds, span, label, feedback));
		return true;
	}

	private static void runJob(
		MinecraftServer server,
		ServerLevel level,
		BlockBounds bounds,
		ChunkSpan span,
		String label,
		Consumer<Component> feedback
	) {
		MutableInt totalChanged = new MutableInt(0);
		MutableInt skipped = new MutableInt(0);
		int processed = 0;
		boolean cancelled = false;

		try {
			TerratonicbiomesConfig config = TerratonicbiomesConfig.get();
			ZoneBiomeEnforcer.BiomeFinder finder = key -> level.registryAccess()
				.lookupOrThrow(Registries.BIOME)
				.get(key)
				.orElse(null);
			BoundingBox region = bounds.toBoundingBox();

			for (int index = 0; index < span.totalChunks(); index += config.fixChunksPerBatch) {
				if (cancelRequested) {
					cancelled = true;
					break;
				}

				int batchStart = index;
				int batchEnd = Math.min(index + config.fixChunksPerBatch, span.totalChunks());
				BatchResult result = runBatchOnServer(server, level, span, bounds, region, finder, batchStart, batchEnd);
				processed += result.processed();
				totalChanged.add(result.changed());
				skipped.add(result.skipped());
				progress = progress.withCounts(processed, totalChanged.intValue(), skipped.intValue());

				if (processed % PROGRESS_EVERY_CHUNKS == 0 || batchEnd >= span.totalChunks()) {
					int percent = span.totalChunks() > 0 ? processed * 100 / span.totalChunks() : 100;
					int finalProcessed = processed;
					int finalPercent = percent;
					int finalChanged = totalChanged.intValue();
					server.execute(() -> feedback.accept(Component.literal(
						"Terrabiome fix " + label + ": " + finalPercent + "% (" + finalProcessed + "/"
							+ span.totalChunks() + " chunks, " + finalChanged + " sample(s) replaced)"
					)));
				}

				if (batchEnd < span.totalChunks()) {
					Thread.sleep(Math.max(0, config.fixBatchDelayMs));
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			cancelled = true;
			server.execute(() -> feedback.accept(Component.literal("Terrabiome fix interrupted.")));
		} catch (Exception e) {
			Terratonicbiomes.LOGGER.error("Terrabiome background fix failed", e);
			server.execute(() -> feedback.accept(Component.literal("Terrabiome fix failed: " + e.getMessage())));
		} finally {
			running.set(false);
			cancelRequested = false;
			int finalProcessed = processed;
			int finalChanged = totalChanged.intValue();
			int finalSkipped = skipped.intValue();
			boolean finalCancelled = cancelled;
			progress = FixProgress.finished(label, finalProcessed, finalChanged, finalSkipped, finalCancelled);
			server.execute(() -> feedback.accept(Component.literal(
				finalCancelled
					? "Terrabiome fix stopped — processed " + finalProcessed + " chunk(s), replaced "
						+ finalChanged + " sample(s), skipped " + finalSkipped + "."
					: "Terrabiome fix complete — processed " + finalProcessed + " chunk(s), replaced "
						+ finalChanged + " sample(s), skipped " + finalSkipped + "."
			)));
		}
	}

	private static BatchResult runBatchOnServer(
		MinecraftServer server,
		ServerLevel level,
		ChunkSpan span,
		BlockBounds bounds,
		BoundingBox region,
		ZoneBiomeEnforcer.BiomeFinder finder,
		int batchStart,
		int batchEnd
	) throws Exception {
		CompletableFuture<BatchResult> future = new CompletableFuture<>();
		server.execute(() -> {
			try {
				future.complete(processBatch(level, span, bounds, region, finder, batchStart, batchEnd));
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
		});
		return future.get(120, TimeUnit.SECONDS);
	}

	private static BatchResult processBatch(
		ServerLevel level,
		ChunkSpan span,
		BlockBounds bounds,
		BoundingBox region,
		ZoneBiomeEnforcer.BiomeFinder finder,
		int batchStart,
		int batchEnd
	) {
		Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
		int processed = 0;
		int changed = 0;
		int skipped = 0;
		List<ChunkAccess> changedChunks = new ArrayList<>();

		for (int index = batchStart; index < batchEnd; index++) {
			int chunkX = span.chunkX(index);
			int chunkZ = span.chunkZ(index);
			ChunkResult<ChunkAccess> chunkResult = level.getChunkSource()
				.getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true)
				.join();
			ChunkAccess chunk = chunkResult.orElse(null);
			if (chunk == null) {
				skipped++;
				continue;
			}

			processed++;
			MutableInt chunkChanged = new MutableInt(0);
			BiomeResolver resolver = makeResolver(chunkChanged, chunk, region, finder);
			chunk.fillBiomesFromNoise(resolver, sampler);
			if (chunkChanged.intValue() > 0) {
				chunk.markUnsaved();
				changedChunks.add(chunk);
				changed += chunkChanged.intValue();
			}
		}

		if (!changedChunks.isEmpty()) {
			level.getChunkSource().chunkMap.resendBiomesForChunks(changedChunks);
		}
		return new BatchResult(processed, changed, skipped);
	}

	private static BiomeResolver makeResolver(
		MutableInt count,
		ChunkAccess chunk,
		BoundingBox region,
		ZoneBiomeEnforcer.BiomeFinder finder
	) {
		return (quartX, quartY, quartZ, sampler) -> {
			int blockX = QuartPos.toBlock(quartX);
			int blockY = QuartPos.toBlock(quartY);
			int blockZ = QuartPos.toBlock(quartZ);
			Holder<Biome> current = chunk.getNoiseBiome(quartX, quartY, quartZ);
			if (!region.isInside(blockX, blockY, blockZ) || SurfaceBiomeBounds.isUnderground(blockY)) {
				return current;
			}
			if (!ZoneBiomeEnforcer.isIllegal(current, blockX, blockZ)) {
				return current;
			}
			count.increment();
			return ZoneBiomeEnforcer.pickRandomReplacement(current, blockX, blockY, blockZ, finder);
		};
	}

	private record BatchResult(int processed, int changed, int skipped) {}

	private record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		BoundingBox toBoundingBox() {
			return BoundingBox.fromCorners(
				new BlockPos(quantize(minX), quantize(minY), quantize(minZ)),
				new BlockPos(quantize(maxX), quantize(maxY), quantize(maxZ))
			);
		}

		private static int quantize(int blockCoord) {
			return QuartPos.toBlock(QuartPos.fromBlock(blockCoord));
		}
	}

	private record ChunkSpan(int minChunkX, int minChunkZ, int widthChunks, int heightChunks) {
		static ChunkSpan fromBounds(BlockBounds bounds) {
			int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
			int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
			int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
			int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
			return new ChunkSpan(
				minChunkX,
				minChunkZ,
				maxChunkX - minChunkX + 1,
				maxChunkZ - minChunkZ + 1
			);
		}

		int totalChunks() {
			return widthChunks * heightChunks;
		}

		int chunkX(int index) {
			return minChunkX + index % widthChunks;
		}

		int chunkZ(int index) {
			return minChunkZ + index / widthChunks;
		}
	}

	private record FixProgress(
		String label,
		int totalChunks,
		int processedChunks,
		int replacedSamples,
		int skippedChunks,
		boolean active
	) {
		static FixProgress idle() {
			return new FixProgress("idle", 0, 0, 0, 0, false);
		}

		static FixProgress started(String label, int totalChunks) {
			return new FixProgress(label, totalChunks, 0, 0, 0, true);
		}

		static FixProgress finished(String label, int processed, int replaced, int skipped, boolean cancelled) {
			return new FixProgress(
				cancelled ? label + " (stopped)" : label + " (done)",
				processed,
				processed,
				replaced,
				skipped,
				false
			);
		}

		FixProgress withCounts(int processed, int replaced, int skipped) {
			return new FixProgress(label, totalChunks, processed, replaced, skipped, active);
		}

		String summary() {
			if (!active && totalChunks == 0 && processedChunks == 0) {
				return "Terrabiome fix: idle";
			}
			int percent = totalChunks > 0 ? processedChunks * 100 / totalChunks : 100;
			return "Terrabiome fix: "
				+ label
				+ (active ? " running " : " ")
				+ percent + "% ("
				+ processedChunks + "/" + totalChunks + " chunks, "
				+ replacedSamples + " replaced, "
				+ skippedChunks + " skipped)";
		}
	}
}
