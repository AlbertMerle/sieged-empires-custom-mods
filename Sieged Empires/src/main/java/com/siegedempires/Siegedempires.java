package com.siegedempires;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BannerManager;
import com.siegedempires.block.ModBlockEntities;
import com.siegedempires.block.ModBlocks;
import com.siegedempires.block.ModMenuTypes;
import com.siegedempires.claim.FlanClaimBridge;
import com.siegedempires.claim.RestrictedZoneTracker;
import com.siegedempires.claim.TownClaimTracker;
import com.siegedempires.command.ModCommands;
import com.siegedempires.data.DataStorage;
import com.siegedempires.data.PendingCrownManager;
import com.siegedempires.data.PlayerMailManager;
import com.siegedempires.data.DiplomacyDataManager;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.data.WartownManagementContext;
import com.siegedempires.invasion.InvasionManager;
import com.siegedempires.item.ModItems;
import com.siegedempires.lock.LockDataManager;
import com.siegedempires.lock.LockInteractionHandler;
import com.siegedempires.lock.LockSystemInitializer;
import com.siegedempires.network.ModNetworking;
import com.siegedempires.session.SessionGateManager;
import com.siegedempires.permission.BlockPermissionProfile;
import com.siegedempires.permission.BlockPermissions;
import com.siegedempires.permission.EntityPermissions;
import com.siegedempires.permission.ClaimRole;
import com.siegedempires.permission.PermissionManager;
import com.siegedempires.permission.PermissionType;
import com.siegedempires.permission.PvpProtectionInitializer;
import com.siegedempires.permission.TownPvpWindowManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.BlockEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Siegedempires implements ModInitializer {
	public static final String MOD_ID = "siegedempires";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Sieged Empires");

		DataStorage.initialize();
		PlayerMailManager.initialize();
		PendingCrownManager.initialize();
		ModNetworking.registerPayloads();
		ModNetworking.registerServerReceivers();
		ModCommands.register();
		TownClaimTracker.register();
		RestrictedZoneTracker.register();

		// Lock system registration - blocks must init before items (BlockItem depends on Block)
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModMenuTypes.initialize();
		ModItems.initialize();
		com.siegedempires.recipe.ModRecipes.initialize();
		com.siegedempires.sound.ModSounds.initialize();
		LockDataManager.load();
		com.siegedempires.config.ModSettings.load();
		LockSystemInitializer.register();

		registerClaimInteractionEvents();
		TownPvpWindowManager.register();
		PvpProtectionInitializer.register();
		InvasionManager.register();
		SessionGateManager.register();
		com.siegedempires.guide.GuideBook.register();
		com.siegedempires.gold.GoldBarPlacement.register();
		com.siegedempires.gold.GoldCoinPlacement.register();
		com.siegedempires.banner.BoatBannerManager.register();
		com.siegedempires.banner.BannerManager.register();
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shippy-ships")) {
			com.siegedempires.compat.shippyships.ShippyShipsBoatBannerCompat.register();
			LOGGER.info("Shippy Ships detected — ship banners claim on first town-member boarding (vanilla boats excluded)");
		}
		// Sync lock data to players when they join
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ModNetworking.syncLocksToPlayer(handler.player);
			com.siegedempires.util.PlayerPrefixManager.refresh(handler.player);
			server.execute(() -> ModNetworking.sendMail(handler.player));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				WartownManagementContext.clear(handler.player.getUUID()));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TownDataManager.getInstance().loadAllTowns();
			EmpireDataManager.getInstance().loadAllEmpires();
			DiplomacyDataManager.getInstance().loadAll();
			com.siegedempires.banner.BannerManager.setServer(server);
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("squaremap")) {
				com.siegedempires.compat.squaremap.SquaremapCompat.register();
			}
			LOGGER.info("Sieged Empires ready");
		});

		LOGGER.info("Sieged Empires initialized");
	}

	private void registerClaimInteractionEvents() {
		// Break
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel))
				return true;
			// Door halves / double chests share one lock — treat any related pos as locked
			boolean locked = false;
			for (BlockPos related : LockInteractionHandler.relatedPositions(state, pos)) {
				if (LockDataManager.isLocked(serverLevel, related)) {
					locked = true;
					break;
				}
			}
			if (!PermissionManager.allowed(serverPlayer, pos, PermissionType.BREAK, state.getBlock(), locked))
				return false;
			// Drop lock item if somehow break is allowed while locked (future explosion paths)
			if (locked) {
				LockInteractionHandler.onBlockBreak(serverLevel, pos, serverPlayer);
			}
			return true;
		});

		// Use / place — lock checking for containers/doors is handled by LockInteractionHandler
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			BlockPos clickedPos = hit.getBlockPos();
			BlockState clickedState = level.getBlockState(clickedPos);

			BlockPos placementPos = clickedState.canBeReplaced()
				? clickedPos
				: clickedPos.relative(hit.getDirection());

			// Claim banners: client + server (client must block vanilla prediction).
			if (held.getItem() instanceof BlockItem && BannerHelper.isClaimBanner(held)) {
				return BannerManager.handleClaimBannerUse(player, level, placementPos, held);
			}

			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
				return InteractionResult.PASS;

			// TNT minecart is not a BlockItem — allow invaders in war-banner radius only.
			String heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
			if ("tnt_minecart".equals(heldId)) {
				com.siegedempires.model.TownData townAt = TownDataManager.getInstance().getTownAtChunk(
						placementPos.getX() >> 4, placementPos.getZ() >> 4,
						level.dimension().identifier().toString());
				if (townAt != null && !PermissionManager.invaderMayPlaceTntMinecart(serverPlayer, placementPos)) {
					return InteractionResult.FAIL;
				}
				return InteractionResult.PASS;
			}

			// Block placement (primary path in MC 26.2 — runs before BlockEvents.USE_ITEM_ON)
			if (held.getItem() instanceof BlockItem blockItem) {
				if (BannerHelper.isWarBanner(held)) {
					String deny = InvasionManager.warBannerPlaceDenyReason(serverPlayer, placementPos, held);
					if (deny != null) {
						serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(deny));
						return InteractionResult.FAIL;
					}
					if (InvasionManager.placeWarBannerEntity(serverPlayer, placementPos, held)) {
						held.shrink(1);
						return InteractionResult.SUCCESS;
					}
					return InteractionResult.FAIL;
				}

				boolean locked = level instanceof ServerLevel sl && LockDataManager.isLocked(sl, placementPos);
				if (!PermissionManager.allowed(serverPlayer, placementPos, PermissionType.PLACE,
					blockItem.getBlock(), locked)) {
					return InteractionResult.FAIL;
				}
				return InteractionResult.PASS;
			}

			Block block = clickedState.getBlock();
			BlockPermissionProfile profile = BlockPermissions.classify(block);
			if (profile == BlockPermissionProfile.DEFAULT_CITIZEN_ALLOWED)
				return InteractionResult.PASS;
			if (!PermissionManager.allowed(serverPlayer, clickedPos, PermissionType.INTERACT, block, false))
				return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});

		// Place (secondary hook on BlockState.useItemOn — return null to defer to vanilla)
		BlockEvents.USE_ITEM_ON.register((stack, state, level, pos, player, hand, hit) -> {
			// Client-only belt for claim banners (UseBlockCallback is primary; server handled there).
			if (BannerHelper.isClaimBanner(stack) && level.isClientSide()) {
				BlockPos placementPos = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
				InteractionResult result = BannerManager.handleClaimBannerUse(player, level, placementPos, stack);
				return result == InteractionResult.PASS ? null : result;
			}

			if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
				|| !(stack.getItem() instanceof BlockItem blockItem))
				return null;
			// War banners spawn as entities via UseBlockCallback — never place as blocks.
			if (BannerHelper.isWarBanner(stack)) {
				return InteractionResult.FAIL;
			}
			if (BannerHelper.isClaimBanner(stack)) {
				BlockPos placementPos = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
				return BannerManager.handleClaimBannerUse(player, level, placementPos, stack);
			}
			BlockPos placementPos = state.canBeReplaced() ? pos : pos.relative(hit.getDirection());
			ServerLevel serverLevel = (ServerLevel) level;
			boolean locked = LockDataManager.isLocked(serverLevel, placementPos);
			return PermissionManager.allowed(serverPlayer, placementPos, PermissionType.PLACE,
				blockItem.getBlock(), locked) ? null : InteractionResult.FAIL;
		});

		// Entity interactions (boats/minecarts)
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide())
				return InteractionResult.PASS;
			BlockPermissionProfile profile = EntityPermissions.classify(entity);
			if (profile == null)
				return InteractionResult.PASS;
			net.minecraft.core.BlockPos entityPos = entity.blockPosition();
			String dimension = serverPlayer.level().dimension().identifier().toString();
			int x = entityPos.getX() >> 4, z = entityPos.getZ() >> 4;
			com.siegedempires.model.TownData town = TownDataManager.getInstance().getTownAtChunk(x, z, dimension);
			if (town == null)
				return InteractionResult.PASS;
			ClaimRole role = PermissionManager.roleAt(serverPlayer, entityPos);
			if (role == ClaimRole.EMPEROR || role == ClaimRole.MONARCH || role == ClaimRole.LORD) {
				return InteractionResult.PASS;
			}
			return profile.allows(role == ClaimRole.OUTSIDER, PermissionType.INTERACT)
				? InteractionResult.PASS : InteractionResult.FAIL;
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
