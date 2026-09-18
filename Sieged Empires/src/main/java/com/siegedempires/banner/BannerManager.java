package com.siegedempires.banner;

import com.siegedempires.claim.FlanClaimBridge;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.util.TitleHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BannerManager {
	public static final String CHUNK_ALREADY_CLAIMED_MESSAGE = "This chunk has already been claimed!";
	/** How long the success-flash banner stays placed (2 seconds). */
	public static final int CLAIM_FLASH_TICKS = 40;

	private static MinecraftServer server;
	private static final List<PendingClaimBannerRemoval> PENDING_CLAIM_BANNER_REMOVALS = new ArrayList<>();

	private record PendingClaimBannerRemoval(String dimension, BlockPos pos, long removeAtTick) {
	}

	private BannerManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BannerManager::tickClaimBannerRemovals);
	}

	public static void setServer(MinecraftServer serverInstance) {
		server = serverInstance;
	}

	public static MinecraftServer getServer() {
		return server;
	}

	public static void onBannerLoaded(BannerBlockEntity blockEntity) {
		if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
			return;
		}

		BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) blockEntity;
		BannerType type = BannerType.fromId(access.siegedempires$getBannerType());
		if (type == BannerType.WAR) {
			if (access.siegedempires$isFreshlyPlaced()) {
				access.siegedempires$setFreshlyPlaced(false);
				performWarBannerPlace(level, blockEntity, access);
			}
			return;
		}

		String townId = access.siegedempires$getTownId();
		if (townId == null || townId.isEmpty()) {
			return;
		}

		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			BannerHelper.resetToVanillaWhiteBanner(blockEntity, level);
			return;
		}

		if (type == BannerType.CITIZEN_GIVE_LAND && access.siegedempires$isFreshlyPlaced()) {
			access.siegedempires$setFreshlyPlaced(false);
			performCitizenGrant(level, blockEntity, town);
			return;
		}

		if (type == BannerType.LAND_REVOKE && access.siegedempires$isFreshlyPlaced()) {
			access.siegedempires$setFreshlyPlaced(false);
			performLandRevoke(level, blockEntity, town, access.siegedempires$getPlacerId());
			return;
		}

		if (type == BannerType.RESTRICTED && access.siegedempires$isFreshlyPlaced()) {
			access.siegedempires$setFreshlyPlaced(false);
			performRestrictedMark(level, blockEntity, town);
			return;
		}

		if (type == BannerType.CLAIM && access.siegedempires$isFreshlyPlaced()) {
			access.siegedempires$setFreshlyPlaced(false);
			performClaim(level, blockEntity, town);
			return;
		}

		BannerHelper.applyTownDesign(blockEntity, town, level);
	}

	private static void performWarBannerPlace(ServerLevel level, BannerBlockEntity blockEntity,
	                                          BannerHelper.BannerBlockEntityAccess access) {
		// War banners are entities now; remove any block that slipped through.
		level.removeBlock(blockEntity.getBlockPos(), false);
	}

	/**
	 * HARD pre-check before a claim banner may leave the player's inventory.
	 * Returns a deny message, or {@code null} if the chunk is free to claim.
	 * Covers: already claimed by any town (including this one), adjacency, missing town.
	 */
	public static String claimBannerPlaceDenyReason(ItemStack stack, int chunkX, int chunkZ, String dimension) {
		if (!BannerHelper.isClaimBanner(stack)) {
			return "Not a claim banner!";
		}
		String townId = BannerHelper.getTownId(BannerHelper.readSiegedEmpiresData(stack));
		if (townId == null) {
			return "This claim banner is invalid!";
		}
		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return "This claim banner's town no longer exists!";
		}
		ChunkPosition chunk = new ChunkPosition(chunkX, chunkZ, dimension);

		// Already claimed by this town or any other — banner must NOT be taken.
		TownData existingOwner = TownDataManager.getInstance().getTownAtChunk(chunkX, chunkZ, dimension);
		if (existingOwner != null || town.getClaimedChunks().contains(chunk)) {
			return CHUNK_ALREADY_CLAIMED_MESSAGE;
		}
		if (!isClaimAdjacentOrFirst(town, chunk)) {
			return "Claims must touch your existing land!";
		}
		return null;
	}

	public static String claimBannerPlaceDenyReason(ServerLevel level, BlockPos placementPos, ItemStack stack) {
		ChunkPos chunkPos = ChunkPos.containing(placementPos);
		return claimBannerPlaceDenyReason(stack, chunkPos.x(), chunkPos.z(),
				level.dimension().identifier().toString());
	}

	/**
	 * Claim-banner use/place on client and server. Client must block vanilla prediction
	 * (otherwise the item is consumed locally even when the server denies the claim).
	 * Server performs the actual claim and is the only side that shrinks the stack.
	 *
	 * <p><strong>MC 26.2:</strong> a client {@link InteractionResult#FAIL} does not send a
	 * use packet ({@code consumesAction() == false}), so deny feedback (title/chat) must
	 * render on the client when town data is available locally. When town data is absent
	 * (dedicated remote client), return {@link InteractionResult#SUCCESS} so the server
	 * can deny and send titles.
	 */
	public static InteractionResult handleClaimBannerUse(
			net.minecraft.world.entity.player.Player player,
			net.minecraft.world.level.Level level,
			BlockPos placementPos,
			ItemStack held) {
		int chunkX = placementPos.getX() >> 4;
		int chunkZ = placementPos.getZ() >> 4;
		String dimension = level.dimension().identifier().toString();
		String deny = claimBannerPlaceDenyReason(held, chunkX, chunkZ, dimension);

		if (level.isClientSide()) {
			if (!canEvaluateClaimLocally(held)) {
				// Remote client — no town index; block vanilla and let the server decide.
				return InteractionResult.SUCCESS;
			}
			if (deny != null) {
				showClaimDenyFeedbackClient(deny);
				return InteractionResult.FAIL;
			}
			// Block vanilla banner placement/consumption; server will claim and shrink.
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.FAIL;
		}

		if (deny != null) {
			if (CHUNK_ALREADY_CLAIMED_MESSAGE.equals(deny)) {
				playClaimAlreadyClaimedFeedback(serverPlayer);
			}
			serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(deny));
			return InteractionResult.FAIL;
		}

		if (tryClaimFromBanner(serverPlayer, placementPos, held)) {
			if (!serverPlayer.getAbilities().instabuild) {
				held.shrink(1);
			}
			playClaimSuccessFeedback(serverLevel, serverPlayer, placementPos, held);
			return InteractionResult.SUCCESS;
		}

		playClaimAlreadyClaimedFeedback(serverPlayer);
		serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(CHUNK_ALREADY_CLAIMED_MESSAGE));
		return InteractionResult.FAIL;
	}

	/**
	 * Claims the chunk for the banner's town without placing a banner block.
	 * Caller must shrink the held item <em>only</em> when this returns {@code true}.
	 */
	public static boolean tryClaimFromBanner(ServerPlayer player, BlockPos placementPos, ItemStack stack) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		String deny = claimBannerPlaceDenyReason(level, placementPos, stack);
		if (deny != null) {
			return false;
		}
		String townId = BannerHelper.getTownId(BannerHelper.readSiegedEmpiresData(stack));
		TownData town = TownDataManager.getInstance().getTown(townId);
		if (town == null) {
			return false;
		}
		ChunkPos chunkPos = ChunkPos.containing(placementPos);
		String dimension = level.dimension().identifier().toString();
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);

		// Second hard check immediately before mutating claim data.
		TownData existingOwner = TownDataManager.getInstance()
				.getTownAtChunk(chunkPos.x(), chunkPos.z(), dimension);
		if (existingOwner != null || town.getClaimedChunks().contains(chunk)) {
			return false;
		}

		town.getClaimedChunks().add(chunk);
		town.checkNationStatus();
		TownDataManager.getInstance().saveTown(town);
		FlanClaimBridge.claimChunk(town, chunk, level);
		return true;
	}

	/** Red small title when a claim banner is used on land that is already claimed. */
	private static void playClaimAlreadyClaimedFeedback(ServerPlayer player) {
		TitleHelper.showSmallTitle(player,
				Component.literal(CHUNK_ALREADY_CLAIMED_MESSAGE).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				CLAIM_FLASH_TICKS);
	}

	/** True when this JVM has loaded town data (integrated server / runClient). */
	private static boolean canEvaluateClaimLocally(ItemStack stack) {
		String townId = BannerHelper.getTownId(BannerHelper.readSiegedEmpiresData(stack));
		return townId != null && TownDataManager.getInstance().getTown(townId) != null;
	}

	private static void showClaimDenyFeedbackClient(String deny) {
		if (CHUNK_ALREADY_CLAIMED_MESSAGE.equals(deny)) {
			ClaimBannerClientFeedback.showAlreadyClaimed();
		}
	}

	/** Brief banner flash, placement sound, and green title after a successful claim. */
	private static void playClaimSuccessFeedback(
			ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack sourceStack) {
		String townId = BannerHelper.getTownId(BannerHelper.readSiegedEmpiresData(sourceStack));
		TownData town = townId != null ? TownDataManager.getInstance().getTown(townId) : null;
		if (town == null) {
			return;
		}

		BlockState placedState = level.getBlockState(pos);
		if (placedState.canBeReplaced() && sourceStack.getItem() instanceof BlockItem blockItem) {
			placedState = blockItem.getBlock().defaultBlockState();
			if (level.setBlock(pos, placedState, Block.UPDATE_ALL)) {
				BlockEntity blockEntity = level.getBlockEntity(pos);
				if (blockEntity instanceof BannerBlockEntity banner) {
					// Town design only — no CLAIM type so performClaim is not re-triggered.
					BannerHelper.applyTownDesign(banner, town, level);
				}
			} else {
				placedState = level.getBlockState(pos);
			}
		}

		level.playSound(null, pos, placedState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
		TitleHelper.showTitle(player,
				Component.literal("Chunk Claimed!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
				CLAIM_FLASH_TICKS);
		scheduleClaimBannerRemoval(level, pos, CLAIM_FLASH_TICKS);
	}

	private static void scheduleClaimBannerRemoval(ServerLevel level, BlockPos pos, int delayTicks) {
		MinecraftServer tickServer = level.getServer();
		if (tickServer == null) {
			return;
		}
		PENDING_CLAIM_BANNER_REMOVALS.add(new PendingClaimBannerRemoval(
				level.dimension().identifier().toString(),
				pos.immutable(),
				tickServer.getTickCount() + delayTicks));
	}

	private static void tickClaimBannerRemovals(MinecraftServer server) {
		if (PENDING_CLAIM_BANNER_REMOVALS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		Iterator<PendingClaimBannerRemoval> iterator = PENDING_CLAIM_BANNER_REMOVALS.iterator();
		while (iterator.hasNext()) {
			PendingClaimBannerRemoval pending = iterator.next();
			if (now < pending.removeAtTick()) {
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				if (level.dimension().identifier().toString().equals(pending.dimension())) {
					level.removeBlock(pending.pos(), false);
					break;
				}
			}
			iterator.remove();
		}
	}

	/**
	 * Fallback if a claim-banner block somehow still places (should not happen —
	 * UseBlockCallback owns claim banners). Refunds and removes; never silently consumes.
	 */
	private static void performClaim(ServerLevel level, BannerBlockEntity blockEntity, TownData town) {
		BlockPos pos = blockEntity.getBlockPos();
		ChunkPos chunkPos = ChunkPos.containing(pos);
		String dimension = level.dimension().identifier().toString();
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);
		BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) blockEntity;

		TownData existingOwner = TownDataManager.getInstance()
				.getTownAtChunk(chunkPos.x(), chunkPos.z(), dimension);
		if (existingOwner != null || town.getClaimedChunks().contains(chunk)) {
			notifyPlacer(level, access.siegedempires$getPlacerId(), CHUNK_ALREADY_CLAIMED_MESSAGE);
			refundClaimBanner(level, pos, town, access.siegedempires$getPlacerId());
			level.removeBlock(pos, false);
			return;
		}

		if (!isClaimAdjacentOrFirst(town, chunk)) {
			notifyPlacer(level, access.siegedempires$getPlacerId(), "Claims must touch your existing land!");
			refundClaimBanner(level, pos, town, access.siegedempires$getPlacerId());
			level.removeBlock(pos, false);
			return;
		}

		town.getClaimedChunks().add(chunk);
		town.checkNationStatus();
		TownDataManager.getInstance().saveTown(town);
		FlanClaimBridge.claimChunk(town, chunk, level);
		level.removeBlock(pos, false);
	}

	/**
	 * Return a claim banner to the placer's inventory when a place attempt fails.
	 * Creative players never lose the item on place, so they are not given another.
	 * Falls back to a world drop only if no online placer can take the item.
	 */
	private static void refundClaimBanner(ServerLevel level, BlockPos pos, TownData town,
	                                      String placerUuidStr) {
		var patternLookup = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack refund = BannerHelper.createClaimBannerItem(town, patternLookup);

		ServerPlayer placer = resolvePlacer(level, pos, placerUuidStr);
		if (placer != null) {
			if (placer.getAbilities().instabuild) {
				return;
			}
			if (!placer.getInventory().add(refund)) {
				placer.drop(refund, false);
			}
			return;
		}
		Block.popResource(level, pos, refund);
	}

	private static @org.jspecify.annotations.Nullable ServerPlayer resolvePlacer(
			ServerLevel level, BlockPos pos, String placerUuidStr) {
		if (level.getServer() != null && placerUuidStr != null && !placerUuidStr.isEmpty()) {
			try {
				ServerPlayer byId = level.getServer().getPlayerList()
						.getPlayer(UUID.fromString(placerUuidStr));
				if (byId != null) {
					return byId;
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
		Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0, false);
		return nearest instanceof ServerPlayer serverPlayer ? serverPlayer : null;
	}

	/**
	 * Claim banners must touch an existing town chunk on at least one side
	 * (4-neighbor: ±1 chunk X or ±1 chunk Z, same dimension), unless this is
	 * the town's first claim ever (no claimed chunks yet).
	 */
	private static boolean isClaimAdjacentOrFirst(TownData town, ChunkPosition chunk) {
		Set<ChunkPosition> claimed = town.getClaimedChunks();
		if (claimed == null || claimed.isEmpty()) {
			return true;
		}
		String dimension = chunk.getDimension();
		int x = chunk.getX();
		int z = chunk.getZ();
		for (ChunkPosition existing : claimed) {
			if (existing == null || !dimension.equals(existing.getDimension())) {
				continue;
			}
			int dx = Math.abs(existing.getX() - x);
			int dz = Math.abs(existing.getZ() - z);
			if ((dx == 1 && dz == 0) || (dx == 0 && dz == 1)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Handle a freshly placed CITIZEN_GIVE_LAND banner. Grants the chunk
	 * where the banner sits to the citizen UUID stored in the banner's NBT,
	 * then removes the banner so only the chunk ownership remains.
	 *
	 * <p>The banner must be placed in a chunk that the town already claims;
	 * otherwise the grant is rejected and the banner is removed silently.
	 * Restricted Zones cannot be granted — the placer is notified and the
	 * banner is refunded.
	 */
	private static void performCitizenGrant(ServerLevel level, BannerBlockEntity blockEntity, TownData town) {
		BlockPos pos = blockEntity.getBlockPos();
		ChunkPos chunkPos = ChunkPos.containing(pos);
		String dimension = level.dimension().identifier().toString();
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);
		BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) blockEntity;
		String placerUuidStr = access.siegedempires$getPlacerId();
		String citizenUuidStr = access.siegedempires$getCitizenId();

		if (town.getRestrictedChunks().contains(chunk)) {
			notifyPlacer(level, placerUuidStr, "You cannot give a Citizen a Restricted Zone!");
			refundCitizenGiveLandBanner(level, pos, town, citizenUuidStr, placerUuidStr);
			level.removeBlock(pos, false);
			return;
		}

		// The town must already claim this chunk; we don't grant land in
		// unclaimed wilderness.
		if (!town.getClaimedChunks().contains(chunk)) {
			level.removeBlock(pos, false);
			return;
		}

		if (citizenUuidStr == null || citizenUuidStr.isEmpty()) {
			level.removeBlock(pos, false);
			return;
		}

		try {
			java.util.UUID citizenUuid = java.util.UUID.fromString(citizenUuidStr);
			// Only grant if the citizen is actually a member of the town.
			if (town.getMembers().containsKey(citizenUuid)) {
				town.getCitizenOwnedChunks()
					.computeIfAbsent(citizenUuid, k -> new HashSet<>())
					.add(chunk);
				TownDataManager.getInstance().saveTown(town);
			}
		} catch (IllegalArgumentException ignored) {
			// Malformed UUID; treat as failed grant.
		}

		// The banner always breaks itself, matching the project spec.
		level.removeBlock(pos, false);
	}

	private static void refundCitizenGiveLandBanner(ServerLevel level, BlockPos pos, TownData town,
	                                                String citizenUuidStr, String placerUuidStr) {
		java.util.UUID citizenUuid = null;
		java.util.UUID placerUuid = null;
		try {
			if (citizenUuidStr != null && !citizenUuidStr.isEmpty()) {
				citizenUuid = java.util.UUID.fromString(citizenUuidStr);
			}
		} catch (IllegalArgumentException ignored) {
		}
		try {
			if (placerUuidStr != null && !placerUuidStr.isEmpty()) {
				placerUuid = java.util.UUID.fromString(placerUuidStr);
			}
		} catch (IllegalArgumentException ignored) {
		}
		String citizenName = citizenUuid != null ? town.getMemberName(citizenUuid) : "Citizen";
		var patternLookup = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack refund = BannerHelper.createCitizenGiveLandBannerItem(
				town, citizenUuid, citizenName, placerUuid, patternLookup);
		Block.popResource(level, pos, refund);
	}

	private static void notifyPlacer(ServerLevel level, String placerUuidStr, String message) {
		if (placerUuidStr == null || placerUuidStr.isEmpty() || level.getServer() == null) {
			return;
		}
		try {
			java.util.UUID placerUuid = java.util.UUID.fromString(placerUuidStr);
			ServerPlayer placer = level.getServer().getPlayerList().getPlayer(placerUuid);
			if (placer != null) {
				if (CHUNK_ALREADY_CLAIMED_MESSAGE.equals(message)) {
					playClaimAlreadyClaimedFeedback(placer);
				}
				placer.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
			}
		} catch (IllegalArgumentException ignored) {
		}
	}

	/**
	 * Mark an already-claimed town chunk as a Restricted Zone. Placement
	 * outside owned claims refunds the banner item. The banner always removes
	 * itself after the attempt.
	 */
	private static void performRestrictedMark(ServerLevel level, BannerBlockEntity blockEntity, TownData town) {
		BlockPos pos = blockEntity.getBlockPos();
		ChunkPos chunkPos = ChunkPos.containing(pos);
		String dimension = level.dimension().identifier().toString();
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);

		if (!town.getClaimedChunks().contains(chunk)) {
			var patternLookup = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			ItemStack refund = BannerHelper.createRestrictedLandBannerItem(town, patternLookup);
			Block.popResource(level, pos, refund);
			level.removeBlock(pos, false);
			return;
		}

		if (town.getRestrictedChunks().add(chunk)) {
			TownDataManager.getInstance().saveTown(town);
		}

		level.removeBlock(pos, false);
	}

	/**
	 * Handle a freshly placed LAND_REVOKE banner. Look up the citizen who
	 * currently owns the chunk (if any) and remove them from the
	 * {@code citizenOwnedChunks} map, then break the banner. A private
	 * chat message is sent to the placer with details about which chunk
	 * was revoked and who previously owned it (or that nobody owned it).
	 */
	private static void performLandRevoke(ServerLevel level, BannerBlockEntity blockEntity, TownData town,
										   String placerUuidStr) {
		BlockPos pos = blockEntity.getBlockPos();
		ChunkPos chunkPos = ChunkPos.containing(pos);
		String dimension = level.dimension().identifier().toString();
		ChunkPosition chunk = new ChunkPosition(chunkPos.x(), chunkPos.z(), dimension);

		// Find whichever citizen currently owns the chunk.
		java.util.UUID previousOwner = null;
		for (var entry : town.getCitizenOwnedChunks().entrySet()) {
			if (entry.getValue() != null && entry.getValue().contains(chunk)) {
				previousOwner = entry.getKey();
				entry.getValue().remove(chunk);
				if (entry.getValue().isEmpty()) {
					town.getCitizenOwnedChunks().remove(previousOwner);
				}
				break;
			}
		}

		if (previousOwner != null) {
			TownDataManager.getInstance().saveTown(town);
		}

		// Always break the banner, matching the project spec.
		level.removeBlock(pos, false);
	}

	public static void syncBannersForTown(MinecraftServer serverInstance, TownData town) {
		syncBannersForTown(serverInstance, town, null);
	}

	/**
	 * Refresh world + online-inventory banners for a town design change.
	 * {@code previousBannerPixels} also rematches legacy Give Banner stacks that
	 * only stored paint data without a town id.
	 */
	public static void syncBannersForTown(MinecraftServer serverInstance, TownData town,
										  String previousBannerPixels) {
		if (serverInstance == null || town == null) {
			return;
		}

		for (ServerLevel level : serverInstance.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk -> updateBannersInChunk(chunk, town));
		}

		var patternLookup = serverInstance.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
			updateTownBannerItemsInInventory(player, town, previousBannerPixels, patternLookup);
		}
	}

	public static void syncBannersForEmpire(MinecraftServer serverInstance, EmpireData empire,
											String previousBannerPixels) {
		if (serverInstance == null || empire == null) {
			return;
		}
		var patternLookup = serverInstance.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
			updateEmpireBannerItemsInInventory(player, empire, previousBannerPixels, patternLookup);
		}
		for (String townId : empire.getMemberTownIds()) {
			TownData town = TownDataManager.getInstance().getTown(townId);
			if (town != null) {
				syncBannersForTown(serverInstance, town, previousBannerPixels);
			}
		}
	}

	private static void updateTownBannerItemsInInventory(ServerPlayer player, TownData town,
														 String previousBannerPixels,
														 HolderGetter<BannerPattern> patternLookup) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			ItemStack updated = BannerHelper.applyTownDesignToItem(stack, town, patternLookup);
			if (updated == stack && previousBannerPixels != null) {
				updated = BannerHelper.applyDesignIfMatchingPixels(stack, previousBannerPixels,
						town.getBannerBaseColor(), town.getBannerPatterns(), town.getBannerPixels(),
						patternLookup);
			}
			if (updated != stack) {
				player.getInventory().setItem(slot, updated);
			}
		}
	}

	private static void updateEmpireBannerItemsInInventory(ServerPlayer player, EmpireData empire,
														   String previousBannerPixels,
														   HolderGetter<BannerPattern> patternLookup) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			ItemStack updated = BannerHelper.applyEmpireDesignToItem(stack, empire, patternLookup);
			if (updated == stack && previousBannerPixels != null) {
				updated = BannerHelper.applyDesignIfMatchingPixels(stack, previousBannerPixels,
						empire.getBannerBaseColor(), empire.getBannerPatterns(), empire.getBannerPixels(),
						patternLookup);
			}
			if (updated != stack) {
				player.getInventory().setItem(slot, updated);
			}
		}
	}

	public static void resetBannersForDeletedTown(MinecraftServer serverInstance, String townId) {
		if (serverInstance == null || townId == null || townId.isEmpty()) {
			return;
		}

		for (ServerLevel level : serverInstance.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk -> resetBannersInChunk(chunk, townId, level));
		}

		for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
			resetBannerItemsInInventory(player, townId);
		}
	}

	public static void migrateTownId(MinecraftServer serverInstance, String oldTownId, String newTownId) {
		if (serverInstance == null || oldTownId == null || newTownId == null || oldTownId.equals(newTownId)) {
			return;
		}

		for (ServerLevel level : serverInstance.getAllLevels()) {
			level.getChunkSource().chunkMap.forEachBlockTickingChunk(
					chunk -> migrateBannersInChunk(chunk, oldTownId, newTownId, level));
		}

		for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
			migrateBannerItemsInInventory(player, oldTownId, newTownId);
		}
	}

	private static void updateBannersInChunk(LevelChunk chunk, TownData town) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof BannerBlockEntity banner)) {
				continue;
			}

			BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) banner;
			if (!town.getId().equals(access.siegedempires$getTownId())) {
				continue;
			}

			if (!(banner.getLevel() instanceof ServerLevel level)) {
				continue;
			}

			BannerHelper.applyTownDesign(banner, town, level);
		}
	}

	private static void resetBannersInChunk(LevelChunk chunk, String townId, ServerLevel level) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof BannerBlockEntity banner)) {
				continue;
			}

			BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) banner;
			if (!townId.equals(access.siegedempires$getTownId())) {
				continue;
			}

			BannerHelper.resetToVanillaWhiteBanner(banner, level);
		}
	}

	private static void resetBannerItemsInInventory(ServerPlayer player, String townId) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			ItemStack reset = BannerHelper.resetItemIfTownBanner(stack, townId);
			if (reset != stack) {
				player.getInventory().setItem(slot, reset);
			}
		}
	}

	private static void migrateBannersInChunk(LevelChunk chunk, String oldTownId, String newTownId, ServerLevel level) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof BannerBlockEntity banner)) {
				continue;
			}

			BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) banner;
			if (!oldTownId.equals(access.siegedempires$getTownId())) {
				continue;
			}

			access.siegedempires$setTownId(newTownId);
			banner.setChanged();
			level.sendBlockUpdated(banner.getBlockPos(), banner.getBlockState(), banner.getBlockState(), Block.UPDATE_ALL);
		}
	}

	private static void migrateBannerItemsInInventory(ServerPlayer player, String oldTownId, String newTownId) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			ItemStack migrated = BannerHelper.migrateItemTownId(stack, oldTownId, newTownId);
			if (migrated != stack) {
				player.getInventory().setItem(slot, migrated);
			}
		}
	}
}
