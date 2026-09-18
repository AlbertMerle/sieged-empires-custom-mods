package com.siegedempires.invasion;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BannerManager;
import com.siegedempires.config.ModSettings;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.diplomacy.DiplomacyActions;
import com.siegedempires.model.ChunkPosition;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.network.DiplomacyBuilder;
import com.siegedempires.network.ModNetworking;
import com.siegedempires.permission.PvpPolicyChecker;
import com.siegedempires.util.CantAffordFeedback;
import com.siegedempires.util.InventoryHelper;
import com.siegedempires.util.TitleHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active invasions, raid boss bars, sounds, war-banner lifecycle, and cooldowns.
 */
public final class InvasionManager {
	private static final Component SIEGE_BOSS_TITLE = Component.literal("Active Siege");
	private static final Component INVASION_BOSS_TITLE = Component.literal("Active Invasion");
	private static final Component WAR_FLAG_BOSS_TITLE = Component.literal("War Flag");
	private static final String WAR_BANNER_TAG = "siegedempires_war_banner";
	private static final int TITLE_STAY_TICKS = 10 * 20;

	private static final Map<String, ActiveInvasion> ACTIVE = new ConcurrentHashMap<>();
	private static final Map<String, Long> COOLDOWN_UNTIL_MS = new ConcurrentHashMap<>();

	private InvasionManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (ACTIVE.isEmpty()) {
				return;
			}
			for (Iterator<Map.Entry<String, ActiveInvasion>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
				ActiveInvasion invasion = it.next().getValue();
				if (!invasion.tick(server)) {
					it.remove();
					if (!invasion.endingFromBannerDeath()) {
						if (invasion.hasWarBannerChunk()) {
							invasion.resolveInvaderVictory(server);
						}
						endInvasion(invasion, server);
					}
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				onPlayerJoin(handler.player, server));

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			ActiveInvasion invasion = findInvasionByWarBanner(entity);
			if (invasion == null) {
				return InteractionResult.PASS;
			}
			invasion.handleWarBannerAttack(serverPlayer, entity);
			return InteractionResult.FAIL;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()) {
				return InteractionResult.PASS;
			}
			return findInvasionByWarBanner(entity) != null ? InteractionResult.FAIL : InteractionResult.PASS;
		});

		PvpPolicyChecker.register((attacker, victim, claimTown) ->
				isTownBeingInvaded(claimTown) ? Boolean.TRUE : null);
	}

	public static String startInvasion(ServerPlayer starter, DiplomacyActions.ManagedEntity invader,
	                                   DiplomacyActions.ManagedEntity invaded) {
		if (starter == null || invader == null || invaded == null) {
			return "Invalid invasion target!";
		}

		String key = invasionKey(invader, invaded);
		if (ACTIVE.containsKey(key)) {
			return "An invasion is already in progress against them!";
		}
		if (isOnCooldown(key)) {
			return "You must wait before invading them again!";
		}
		for (ActiveInvasion existing : ACTIVE.values()) {
			if (existing.invaderRef().equals(invader.ref())) {
				return "Your faction already has an active invasion!";
			}
			if (existing.invadedRef().equals(invaded.ref())) {
				return invaded.entityName() + " is already being invaded!";
			}
		}

		MinecraftServer server = starter.level().getServer();
		if (server == null) {
			return "Server unavailable!";
		}

		int online = DiplomacyBuilder.countOnline(invaded.entityType(), invaded.entityId(), server);
		int required = ModSettings.get().invasionMinOnlinePlayers;
		if (online < required) {
			return "At least " + required + " players must be online in "
					+ invaded.entityName() + " to invade!";
		}

		int price = ModSettings.get().invasionPrice;
		if (price > 0 && !InventoryHelper.hasEnoughGold(starter, price)) {
			CantAffordFeedback.playSound(starter);
			return "Can't Afford This!";
		}
		if (price > 0) {
			InventoryHelper.removeGold(starter, price);
		}

		ActiveInvasion invasion = new ActiveInvasion(invader, invaded);
		ACTIVE.put(key, invasion);
		invasion.begin(server);

		giveWarBanner(starter, invader, invaded);
		refreshDiplomacyManagers(server, invader, invaded);

		return null;
	}

	private static void refreshDiplomacyManagers(MinecraftServer server,
	                                           DiplomacyActions.ManagedEntity invader,
	                                           DiplomacyActions.ManagedEntity invaded) {
		ModNetworking.sendDiplomacyToFactionManagers(invader.entityType(), invader.entityId(), server);
		ModNetworking.sendDiplomacyToFactionManagers(invaded.entityType(), invaded.entityId(), server);
	}

	public static boolean hasActiveInvasionFor(String entityType, String entityId) {
		String ref = DiplomacyRecord.ref(entityType, entityId);
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (invasion.invaderRef().equals(ref) || invasion.invadedRef().equals(ref)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isActiveInvasion(String invaderRef, String invadedRef) {
		return ACTIVE.containsKey(invaderRef + "->" + invadedRef);
	}

	/** True if either side is currently invading the other. */
	public static boolean hasActiveInvasionBetween(String factionRefA, String factionRefB) {
		if (factionRefA == null || factionRefB == null) {
			return false;
		}
		return isActiveInvasion(factionRefA, factionRefB) || isActiveInvasion(factionRefB, factionRefA);
	}

	/**
	 * Ends every active invasion involving {@code factionRef} (as invader or invaded)
	 * and clears related cooldown entries. Used when an empire or town is deleted.
	 */
	public static void cancelInvasionsInvolving(String factionRef) {
		if (factionRef == null || factionRef.isEmpty()) {
			return;
		}
		MinecraftServer server = BannerManager.getServer();
		if (server == null) {
			return;
		}
		List<ActiveInvasion> toCancel = new ArrayList<>();
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (factionRef.equals(invasion.invaderRef()) || factionRef.equals(invasion.invadedRef())) {
				toCancel.add(invasion);
			}
		}
		for (ActiveInvasion invasion : toCancel) {
			ACTIVE.remove(invasion.key());
			endInvasion(invasion, server);
		}
		COOLDOWN_UNTIL_MS.keySet().removeIf(key -> {
			int sep = key.indexOf("->");
			if (sep <= 0) {
				return false;
			}
			String left = key.substring(0, sep);
			String right = key.substring(sep + 2);
			return factionRef.equals(left) || factionRef.equals(right);
		});
	}

	public static boolean isTownBeingInvaded(TownData town) {
		if (town == null) {
			return false;
		}
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (isSiegeTargetTown(town, invasion)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code playerId} belongs to an invading town/empire and {@code claimTown}
	 * is the town under attack (war-banner town). Everyone in the invading town, or
	 * everyone in the invading empire, gets privileges there — not in sibling towns
	 * of the defender's empire.
	 */
	public static boolean isInvaderInInvadedTerritory(UUID playerId, TownData claimTown) {
		if (playerId == null || claimTown == null) {
			return false;
		}
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (!isSiegeTargetTown(claimTown, invasion)) {
				continue;
			}
			if (belongsToFaction(playerId, invasion.invaderRef())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The town currently under siege for this invasion.
	 * <ul>
	 *   <li>War banner placed → that banner's town (empire or town wars)</li>
	 *   <li>No banner yet, town-targeted invasion → the invaded town</li>
	 *   <li>No banner yet, empire-targeted invasion → none (place banner to open the siege)</li>
	 * </ul>
	 */
	static boolean isSiegeTargetTown(TownData claimTown, ActiveInvasion invasion) {
		if (claimTown == null || invasion == null) {
			return false;
		}
		ChunkPosition banner = invasion.warBannerChunk();
		if (banner != null) {
			TownData bannerTown = TownDataManager.getInstance().getTownAtChunk(
					banner.getX(), banner.getZ(), banner.getDimension());
			return bannerTown != null && bannerTown.getId().equals(claimTown.getId());
		}
		if (invasion.invadedRef().startsWith(DiplomacyRecord.TYPE_TOWN + ":")) {
			return townMatchesInvadedRef(claimTown, invasion.invadedRef());
		}
		return false;
	}

	public static boolean isWarBannerChunk(BlockPos pos, String dimension, TownData claimTown) {
		if (pos == null || dimension == null || claimTown == null) {
			return false;
		}
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (!townMatchesInvadedRef(claimTown, invasion.invadedRef())) {
				continue;
			}
			ChunkPosition banner = invasion.warBannerChunk();
			if (banner == null) {
				continue;
			}
			if (banner.getX() == cx && banner.getZ() == cz && dimension.equals(banner.getDimension())) {
				return true;
			}
		}
		return false;
	}

	public static boolean isInWarBannerTntRadius(BlockPos pos, String dimension, TownData claimTown) {
		if (pos == null || dimension == null || claimTown == null) {
			return false;
		}
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (!townMatchesInvadedRef(claimTown, invasion.invadedRef())) {
				continue;
			}
			ChunkPosition banner = invasion.warBannerChunk();
			if (banner == null || !dimension.equals(banner.getDimension())) {
				continue;
			}
			if (Math.abs(banner.getX() - cx) <= 1 && Math.abs(banner.getZ() - cz) <= 1) {
				return true;
			}
		}
		return false;
	}

	public static boolean canPlaceWarBanner(ServerPlayer player, BlockPos placementPos, ItemStack stack) {
		return warBannerPlaceDenyReason(player, placementPos, stack) == null;
	}

	/** Reason the war banner cannot be placed, or {@code null} if placement is allowed. */
	public static String warBannerPlaceDenyReason(ServerPlayer player, BlockPos placementPos, ItemStack stack) {
		String invaderRef = BannerHelper.getInvaderRefFromStack(stack);
		String invadedRef = BannerHelper.getInvadedRefFromStack(stack);
		if (invaderRef == null || invadedRef == null) {
			return "War banners can only be placed in enemy territory during an active invasion!";
		}
		ActiveInvasion invasion = ACTIVE.get(invaderRef + "->" + invadedRef);
		if (invasion == null) {
			return "War banners can only be placed in enemy territory during an active invasion!";
		}
		if (invasion.hasWarBanner()) {
			return "Your War Flag is already planted!";
		}
		if (!belongsToFaction(player.getUUID(), invaderRef)) {
			return "War banners can only be placed in enemy territory during an active invasion!";
		}
		if (!isInvadedTerritory(placementPos, (ServerLevel) player.level(), invadedRef)) {
			return "War banners can only be placed in enemy territory during an active invasion!";
		}
		return null;
	}

	/**
	 * Places the war banner as a damageable armor-stand entity (not a banner block).
	 *
	 * @return {@code true} if the entity was spawned and the item should be consumed
	 */
	public static boolean placeWarBannerEntity(ServerPlayer player, BlockPos placementPos, ItemStack stack) {
		if (!canPlaceWarBanner(player, placementPos, stack)) {
			return false;
		}
		String invaderRef = BannerHelper.getInvaderRefFromStack(stack);
		String invadedRef = BannerHelper.getInvadedRefFromStack(stack);
		ActiveInvasion invasion = ACTIVE.get(invaderRef + "->" + invadedRef);
		if (invasion == null) {
			return false;
		}

		ServerLevel level = (ServerLevel) player.level();
		ArmorStand stand = new ArmorStand(level, placementPos.getX() + 0.5,
				placementPos.getY(), placementPos.getZ() + 0.5);
		stand.setYRot(player.getYRot());
		stand.setNoGravity(true);
		stand.setInvisible(true);
		stand.setNoBasePlate(true);
		stand.setShowArms(false);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.addTag(WAR_BANNER_TAG);
		stand.setCustomName(Component.literal("War Flag").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		stand.setCustomNameVisible(true);
		stand.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));

		if (!level.addFreshEntity(stand)) {
			return false;
		}

		ChunkPosition chunk = new ChunkPosition(
				placementPos.getX() >> 4,
				placementPos.getZ() >> 4,
				level.dimension().identifier().toString());
		invasion.onWarBannerPlaced(stand, chunk, level.getServer());
		return true;
	}

	private static void giveWarBanner(ServerPlayer starter, DiplomacyActions.ManagedEntity invader,
	                                  DiplomacyActions.ManagedEntity invaded) {
		var lookup = starter.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack banner = BannerHelper.createWarBannerItem(invader, invaded, lookup);
		banner.setCount(1);
		if (!starter.getInventory().add(banner)) {
			starter.drop(banner, false);
		}
	}

	private static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
		syncBossBars(player);
		stripExpiredWarBannerItems(player);
	}

	private static void syncBossBars(ServerPlayer player) {
		for (ActiveInvasion invasion : ACTIVE.values()) {
			invasion.syncPlayerBossBar(player);
		}
	}

	private static ActiveInvasion findInvasionByWarBanner(Entity entity) {
		if (!(entity instanceof ArmorStand) || !entity.entityTags().contains(WAR_BANNER_TAG)) {
			return null;
		}
		UUID id = entity.getUUID();
		for (ActiveInvasion invasion : ACTIVE.values()) {
			if (id.equals(invasion.warBannerEntityId())) {
				return invasion;
			}
		}
		return null;
	}

	private static void endInvasion(ActiveInvasion invasion, MinecraftServer server) {
		invasion.cleanup(server);
		long cooldownMs = ModSettings.get().invasionCooldownMillis();
		long until = System.currentTimeMillis() + cooldownMs;
		// Invader cannot re-invade the same target yet.
		COOLDOWN_UNTIL_MS.put(invasion.key(), until);
		// Invaded cannot invade the invader during/after for the same cooldown window.
		COOLDOWN_UNTIL_MS.put(invasion.invadedRef() + "->" + invasion.invaderRef(), until);
		removeWarBannerItems(server, invasion.invaderRef(), invasion.invadedRef());
		removePlacedWarBannerBlocks(server, invasion.invadedRef());
		refreshDiplomacyManagers(server, invasion.invader(), invasion.invaded());
	}

	private static void removeWarBannerItems(MinecraftServer server, String invaderRef, String invadedRef) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			stripWarBannerItems(player, invaderRef, invadedRef);
		}
	}

	private static void stripWarBannerItems(ServerPlayer player, String invaderRef, String invadedRef) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (BannerHelper.isWarBannerFor(stack, invaderRef, invadedRef)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void stripExpiredWarBannerItems(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!BannerHelper.isWarBanner(stack)) {
				continue;
			}
			String invaderRef = BannerHelper.getInvaderRefFromStack(stack);
			String invadedRef = BannerHelper.getInvadedRefFromStack(stack);
			if (invaderRef == null || invadedRef == null || !isActiveInvasion(invaderRef, invadedRef)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	/** Removes leftover war-banner blocks from older placement logic. */
	private static void removePlacedWarBannerBlocks(MinecraftServer server, String invadedRef) {
		Set<String> townIds = invadedTownIds(invadedRef);
		for (ServerLevel level : server.getAllLevels()) {
			for (String townId : townIds) {
				TownData town = TownDataManager.getInstance().getTown(townId);
				if (town == null) {
					continue;
				}
				for (var chunkPos : town.getClaimedChunks()) {
					if (!chunkPos.getDimension().equals(level.dimension().identifier().toString())) {
						continue;
					}
					LevelChunk chunk = level.getChunk(chunkPos.getX(), chunkPos.getZ());
					removeWarBannerBlocksInChunk(chunk, level, invadedRef);
				}
			}
		}
	}

	private static void removeWarBannerBlocksInChunk(LevelChunk chunk, ServerLevel level, String invadedRef) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof BannerBlockEntity banner)) {
				continue;
			}
			BannerHelper.BannerBlockEntityAccess access = (BannerHelper.BannerBlockEntityAccess) banner;
			if (!BannerHelper.isWarBannerType(access.siegedempires$getBannerType())) {
				continue;
			}
			String bannerInvadedRef = access.siegedempires$getInvadedRef();
			if (bannerInvadedRef != null && bannerInvadedRef.equals(invadedRef)) {
				level.removeBlock(banner.getBlockPos(), false);
			}
		}
	}

	public static boolean isInvadedTerritory(BlockPos pos, ServerLevel level, String invadedRef) {
		TownData town = TownDataManager.getInstance().getTownAtChunk(
				pos.getX() >> 4, pos.getZ() >> 4, level.dimension().identifier().toString());
		if (town == null) {
			return false;
		}
		return townMatchesInvadedRef(town, invadedRef);
	}

	static boolean townMatchesInvadedRef(TownData town, String invadedRef) {
		if (invadedRef.startsWith(DiplomacyRecord.TYPE_TOWN + ":")) {
			String townId = invadedRef.substring(DiplomacyRecord.TYPE_TOWN.length() + 1);
			return townId.equals(town.getId());
		}
		if (invadedRef.startsWith(DiplomacyRecord.TYPE_EMPIRE + ":")) {
			String empireId = invadedRef.substring(DiplomacyRecord.TYPE_EMPIRE.length() + 1);
			return empireId.equals(town.getEmpireId());
		}
		return false;
	}

	static Set<String> invadedTownIds(String invadedRef) {
		Set<String> ids = new HashSet<>();
		if (invadedRef.startsWith(DiplomacyRecord.TYPE_TOWN + ":")) {
			ids.add(invadedRef.substring(DiplomacyRecord.TYPE_TOWN.length() + 1));
			return ids;
		}
		if (invadedRef.startsWith(DiplomacyRecord.TYPE_EMPIRE + ":")) {
			String empireId = invadedRef.substring(DiplomacyRecord.TYPE_EMPIRE.length() + 1);
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null && empire.getMemberTownIds() != null) {
				ids.addAll(empire.getMemberTownIds());
			}
			TownData warTown = TownDataManager.getInstance().getWarTownForEmpire(empireId);
			if (warTown != null) {
				ids.add(warTown.getId());
			}
			for (TownData wt : TownDataManager.getInstance().getWarTownsForEmpire(empireId)) {
				ids.add(wt.getId());
			}
		}
		return ids;
	}

	static boolean belongsToFaction(UUID playerId, String factionRef) {
		if (factionRef.startsWith(DiplomacyRecord.TYPE_EMPIRE + ":")) {
			String empireId = factionRef.substring(DiplomacyRecord.TYPE_EMPIRE.length() + 1);
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null && empire.getEmperorUuid().equals(playerId)) {
				return true;
			}
			TownData town = TownDataManager.getInstance().getPlayerTown(playerId);
			return town != null && empireId.equals(town.getEmpireId());
		}
		if (factionRef.startsWith(DiplomacyRecord.TYPE_TOWN + ":")) {
			String townId = factionRef.substring(DiplomacyRecord.TYPE_TOWN.length() + 1);
			TownData town = TownDataManager.getInstance().getPlayerTown(playerId);
			return town != null && townId.equals(town.getId());
		}
		return false;
	}

	static List<ServerPlayer> factionPlayers(MinecraftServer server, String factionRef) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (belongsToFaction(player.getUUID(), factionRef)) {
				players.add(player);
			}
		}
		return players;
	}

	private static String invasionKey(DiplomacyActions.ManagedEntity invader,
	                                  DiplomacyActions.ManagedEntity invaded) {
		return invader.ref() + "->" + invaded.ref();
	}

	private static boolean isOnCooldown(String key) {
		Long until = COOLDOWN_UNTIL_MS.get(key);
		return until != null && System.currentTimeMillis() < until;
	}

	private static final class ActiveInvasion {
		private final String key;
		private final String invaderRef;
		private final String invadedRef;
		private final DiplomacyActions.ManagedEntity invader;
		private final DiplomacyActions.ManagedEntity invaded;
		private final int totalTicks;
		private int ticksRemaining;
		private final ServerBossEvent siegeBossBar;
		private final ServerBossEvent invasionBossBar;
		private final ServerBossEvent warFlagBossBar;
		private UUID warBannerEntityId;
		private ChunkPosition warBannerChunk;
		private int warBannerMaxHp;
		private float warBannerHp;
		private boolean endingFromBannerDeath;

		private ActiveInvasion(DiplomacyActions.ManagedEntity invader, DiplomacyActions.ManagedEntity invaded) {
			this.key = invasionKey(invader, invaded);
			this.invaderRef = invader.ref();
			this.invadedRef = invaded.ref();
			this.invader = invader;
			this.invaded = invaded;
			this.totalTicks = ModSettings.get().invasionDurationTicks();
			this.ticksRemaining = totalTicks;
			this.siegeBossBar = new ServerBossEvent(
					UUID.randomUUID(), SIEGE_BOSS_TITLE,
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
			this.invasionBossBar = new ServerBossEvent(
					UUID.randomUUID(), INVASION_BOSS_TITLE,
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
			this.warFlagBossBar = new ServerBossEvent(
					UUID.randomUUID(), WAR_FLAG_BOSS_TITLE,
					BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
			siegeBossBar.setProgress(1.0F);
			invasionBossBar.setProgress(1.0F);
			warFlagBossBar.setProgress(1.0F);
			warFlagBossBar.setVisible(false);
		}

		private String key() {
			return key;
		}

		private String invaderRef() {
			return invaderRef;
		}

		private String invadedRef() {
			return invadedRef;
		}

		private DiplomacyActions.ManagedEntity invader() {
			return invader;
		}

		private DiplomacyActions.ManagedEntity invaded() {
			return invaded;
		}

		private UUID warBannerEntityId() {
			return warBannerEntityId;
		}

		private ChunkPosition warBannerChunk() {
			return warBannerChunk;
		}

		private boolean hasWarBanner() {
			return warBannerEntityId != null;
		}

		private boolean hasWarBannerChunk() {
			return warBannerChunk != null;
		}

		private boolean endingFromBannerDeath() {
			return endingFromBannerDeath;
		}

		private void begin(MinecraftServer server) {
			playInvasionSounds(server);
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				siegeBossBar.addPlayer(player);
			}
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				invasionBossBar.addPlayer(player);
			}
		}

		private void playInvasionSounds(MinecraftServer server) {
			var goatHorn = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value();
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						goatHorn, SoundSource.PLAYERS, 1.0F, 0.65F);
			}
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
			}
		}

		private void onWarBannerPlaced(ArmorStand stand, ChunkPosition chunk, MinecraftServer server) {
			this.warBannerEntityId = stand.getUUID();
			this.warBannerChunk = chunk;
			this.warBannerMaxHp = ModSettings.get().warBannerHealth;
			this.warBannerHp = warBannerMaxHp;
			warFlagBossBar.setProgress(1.0F);
			warFlagBossBar.setVisible(true);
			syncWarFlagBossBar(server);
		}

		private void handleWarBannerAttack(ServerPlayer attacker, Entity bannerEntity) {
			if (warBannerEntityId == null || endingFromBannerDeath) {
				return;
			}
			ItemStack weapon = attacker.getMainHandItem();
			if (!weapon.is(ItemTags.SWORDS)) {
				attacker.sendSystemMessage(Component.literal("The War Flag can only be damaged with a sword!")
						.withStyle(ChatFormatting.RED));
				return;
			}

			float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
			float strength = attacker.getAttackStrengthScale(0.5F);
			damage *= 0.2F + strength * strength * 0.8F;
			if (damage <= 0.0F) {
				damage = 1.0F;
			}

			warBannerHp = Math.max(0.0F, warBannerHp - damage);
			warFlagBossBar.setProgress(warBannerMaxHp <= 0 ? 0.0F : warBannerHp / warBannerMaxHp);

			ServerLevel level = (ServerLevel) attacker.level();
			level.playSound(null, bannerEntity.getX(), bannerEntity.getY(), bannerEntity.getZ(),
					SoundEvents.ARMOR_STAND_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.broadcastEntityEvent(bannerEntity, (byte) 32);

			if (warBannerHp <= 0.0F) {
				onWarBannerDestroyed(level.getServer(), bannerEntity);
			}
		}

		private void onWarBannerDestroyed(MinecraftServer server, Entity bannerEntity) {
			if (endingFromBannerDeath) {
				return;
			}
			endingFromBannerDeath = true;

			ServerLevel bannerLevel = (ServerLevel) bannerEntity.level();
			bannerEntity.discard();
			warBannerEntityId = null;
			warFlagBossBar.setProgress(0.0F);

			damageInvadersInClaims(server, bannerLevel);
			notifyDefendersVictory(server);
			notifyInvadersDefeat(server);

			ACTIVE.remove(key);
			endInvasion(this, server);
		}

		private void resolveInvaderVictory(MinecraftServer server) {
			captureBannerChunk(server);
			notifyInvadersVictory(server);
			notifyDefendersDefeat(server);
		}

		private void captureBannerChunk(MinecraftServer server) {
			if (warBannerChunk == null) {
				return;
			}
			TownData winnerTown = resolveWinnerTown();
			if (winnerTown == null) {
				return;
			}
			ServerLevel level = null;
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.dimension().identifier().toString().equals(warBannerChunk.getDimension())) {
					level = candidate;
					break;
				}
			}
			TownDataManager.getInstance().transferClaimChunk(warBannerChunk, winnerTown, level);
		}

		private TownData resolveWinnerTown() {
			if (DiplomacyRecord.TYPE_EMPIRE.equals(invader.entityType())) {
				EmpireData empire = EmpireDataManager.getInstance().getEmpire(invader.entityId());
				if (empire == null) {
					return null;
				}
				TownData sourceTown = getCapturedSourceTown();
				if (sourceTown != null) {
					return TownDataManager.getInstance().getOrCreateWarTown(empire, sourceTown);
				}
				return TownDataManager.getInstance().getOrCreateWarTown(empire);
			}
			return TownDataManager.getInstance().getTown(invader.entityId());
		}

		private TownData getCapturedSourceTown() {
			if (warBannerChunk == null) {
				return null;
			}
			TownData owner = TownDataManager.getInstance().getTownAtChunk(
					warBannerChunk.getX(), warBannerChunk.getZ(), warBannerChunk.getDimension());
			if (owner == null || owner.isWarTown()) {
				return null;
			}
			return owner;
		}

		private void damageInvadersInClaims(MinecraftServer server, ServerLevel bannerLevel) {
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				if (player.level() != bannerLevel) {
					continue;
				}
				if (!isInvadedTerritory(player.blockPosition(), bannerLevel, invadedRef)) {
					continue;
				}
				player.hurtServer(bannerLevel, player.damageSources().generic(), 1.0F);
			}
		}

		private void notifyDefendersVictory(MinecraftServer server) {
			var goatHorn = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value();
			Component title = Component.literal("You Defended Your Land!").withStyle(ChatFormatting.GREEN);
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						goatHorn, SoundSource.PLAYERS, 1.0F, 1.0F);
				TitleHelper.showTitle(player, title, TITLE_STAY_TICKS);
			}
		}

		private void notifyInvadersDefeat(MinecraftServer server) {
			Component title = Component.literal("Your Invasion has Failed!").withStyle(ChatFormatting.RED);
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1.0F, 0.5F);
				TitleHelper.showTitle(player, title, TITLE_STAY_TICKS);
			}
		}

		private void notifyInvadersVictory(MinecraftServer server) {
			var goatHorn = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value();
			Component title = Component.literal("Your Invasion has Succeeded!").withStyle(ChatFormatting.GREEN);
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						goatHorn, SoundSource.PLAYERS, 1.0F, 1.0F);
				TitleHelper.showTitle(player, title, TITLE_STAY_TICKS);
			}
		}

		private void notifyDefendersDefeat(MinecraftServer server) {
			Component title = Component.literal("Your Land Has Fallen!").withStyle(ChatFormatting.RED);
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1.0F, 0.5F);
				TitleHelper.showTitle(player, title, TITLE_STAY_TICKS);
			}
		}

		private boolean tick(MinecraftServer server) {
			if (endingFromBannerDeath) {
				return false;
			}
			ticksRemaining--;
			float progress = Math.max(0.0F, (float) ticksRemaining / totalTicks);
			siegeBossBar.setProgress(progress);
			invasionBossBar.setProgress(progress);
			syncFactionBossBars(server);
			if (hasWarBanner()) {
				syncWarFlagBossBar(server);
			}
			return ticksRemaining > 0;
		}

		private void syncFactionBossBars(MinecraftServer server) {
			Set<UUID> siegePlayers = new HashSet<>();
			Set<UUID> invasionPlayers = new HashSet<>();
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				siegePlayers.add(player.getUUID());
			}
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				invasionPlayers.add(player.getUUID());
			}

			for (ServerPlayer player : new ArrayList<>(siegeBossBar.getPlayers())) {
				if (!siegePlayers.contains(player.getUUID())) {
					siegeBossBar.removePlayer(player);
				}
			}
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				if (!siegeBossBar.getPlayers().contains(player)) {
					siegeBossBar.addPlayer(player);
				}
			}

			for (ServerPlayer player : new ArrayList<>(invasionBossBar.getPlayers())) {
				if (!invasionPlayers.contains(player.getUUID())) {
					invasionBossBar.removePlayer(player);
				}
			}
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				if (!invasionBossBar.getPlayers().contains(player)) {
					invasionBossBar.addPlayer(player);
				}
			}
		}

		private void syncWarFlagBossBar(MinecraftServer server) {
			if (!hasWarBanner() || !warFlagBossBar.isVisible()) {
				warFlagBossBar.removeAllPlayers();
				return;
			}
			Set<UUID> viewers = new HashSet<>();
			for (ServerPlayer player : factionPlayers(server, invaderRef)) {
				viewers.add(player.getUUID());
				if (!warFlagBossBar.getPlayers().contains(player)) {
					warFlagBossBar.addPlayer(player);
				}
			}
			for (ServerPlayer player : factionPlayers(server, invadedRef)) {
				viewers.add(player.getUUID());
				if (!warFlagBossBar.getPlayers().contains(player)) {
					warFlagBossBar.addPlayer(player);
				}
			}
			for (ServerPlayer player : new ArrayList<>(warFlagBossBar.getPlayers())) {
				if (!viewers.contains(player.getUUID())) {
					warFlagBossBar.removePlayer(player);
				}
			}
		}

		private void syncPlayerBossBar(ServerPlayer player) {
			if (belongsToFaction(player.getUUID(), invaderRef)) {
				siegeBossBar.addPlayer(player);
				invasionBossBar.removePlayer(player);
			} else if (belongsToFaction(player.getUUID(), invadedRef)) {
				invasionBossBar.addPlayer(player);
				siegeBossBar.removePlayer(player);
			}
			if (hasWarBanner() && warFlagBossBar.isVisible()
					&& (belongsToFaction(player.getUUID(), invaderRef)
					|| belongsToFaction(player.getUUID(), invadedRef))) {
				warFlagBossBar.addPlayer(player);
			}
		}

		private void cleanup(MinecraftServer server) {
			removeWarBannerEntity(server);
			siegeBossBar.removeAllPlayers();
			invasionBossBar.removeAllPlayers();
			warFlagBossBar.removeAllPlayers();
			warFlagBossBar.setVisible(false);
		}

		private void removeWarBannerEntity(MinecraftServer server) {
			if (warBannerEntityId == null) {
				return;
			}
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(warBannerEntityId);
				if (entity != null) {
					entity.discard();
					break;
				}
			}
			warBannerEntityId = null;
		}
	}
}
