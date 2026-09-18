package com.siegedempires.lock;

import com.siegedempires.config.ModSettings;
import com.siegedempires.item.LockpickItem;
import com.siegedempires.item.ModItems;
import com.siegedempires.network.ModNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LockpickSessionManager {
	private static final float SOUND_VOLUME = 1.0F;
	private static final float SOUND_PITCH = 1.0F;
	private static final double MAX_DISTANCE_SQR = 5.0 * 5.0;
	private static final Map<UUID, LockpickSession> SESSIONS = new ConcurrentHashMap<>();

	private LockpickSessionManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SESSIONS.isEmpty()) {
				return;
			}
			for (Iterator<Map.Entry<UUID, LockpickSession>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
				LockpickSession session = it.next().getValue();
				if (!session.tick()) {
					it.remove();
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				cancelSession(handler.getPlayer(), false));
	}

	public static boolean isActive(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	public static boolean start(ServerPlayer player, ServerLevel level, List<BlockPos> targets,
	                            InteractionHand hand, BlockPos anchor, BlockState state) {
		if (SESSIONS.containsKey(player.getUUID())) {
			return false;
		}

		if (!isAnyLocked(level, targets)) {
			return false;
		}

		int totalTicks = ModSettings.get().lockpickDurationTicks();
		Component title = lockpickTitle(state);
		ServerBossEvent bossBar = new ServerBossEvent(
				UUID.randomUUID(),
				title,
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		bossBar.setProgress(1.0F);
		bossBar.addPlayer(player);

		LockpickSession session = new LockpickSession(
				player, level, new ArrayList<>(targets), hand, anchor, bossBar, totalTicks);
		SESSIONS.put(player.getUUID(), session);
		session.playLockpickSound();
		return true;
	}

	public static void cancelSession(ServerPlayer player, boolean notify) {
		LockpickSession session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			session.cleanup();
			if (notify) {
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_cancelled")
						.withStyle(ChatFormatting.RED));
			}
		}
	}

	private static Component lockpickTitle(BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return Component.translatable("lock.siegedempires.lockpicking_door");
		}
		if (state.getBlock() instanceof ChestBlock) {
			return Component.translatable("lock.siegedempires.lockpicking_chest");
		}
		String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getKey(state.getBlock()).getPath();
		if (blockId.contains("chest") || blockId.contains("barrel")) {
			return Component.translatable("lock.siegedempires.lockpicking_chest");
		}
		if (blockId.contains("door") || blockId.contains("trapdoor") || blockId.contains("fence_gate")) {
			return Component.translatable("lock.siegedempires.lockpicking_door");
		}
		return Component.translatable("lock.siegedempires.lockpicking");
	}

	private static boolean isAnyLocked(ServerLevel level, List<BlockPos> positions) {
		for (BlockPos pos : positions) {
			if (LockDataManager.isLocked(level, pos)) {
				return true;
			}
		}
		return false;
	}

	private static final class LockpickSession {
		private final ServerPlayer player;
		private final ServerLevel level;
		private final List<BlockPos> targets;
		private final InteractionHand hand;
		private final BlockPos anchor;
		private final ServerBossEvent bossBar;
		private final int totalTicks;
		private int ticksRemaining;
		private int soundTicks;

		private LockpickSession(ServerPlayer player, ServerLevel level, List<BlockPos> targets,
		                        InteractionHand hand, BlockPos anchor, ServerBossEvent bossBar, int totalTicks) {
			this.player = player;
			this.level = level;
			this.targets = targets;
			this.hand = hand;
			this.anchor = anchor;
			this.bossBar = bossBar;
			this.totalTicks = totalTicks;
			this.ticksRemaining = totalTicks;
			this.soundTicks = 20;
		}

		private boolean tick() {
			if (!player.isAlive() || player.hasDisconnected() || player.level() != level) {
				cleanup();
				return false;
			}

			if (player.position().distanceToSqr(Vec3.atCenterOf(anchor)) > MAX_DISTANCE_SQR) {
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_cancelled")
						.withStyle(ChatFormatting.RED));
				cleanup();
				return false;
			}

			if (!isLookingAtTarget()) {
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_cancelled")
						.withStyle(ChatFormatting.RED));
				cleanup();
				return false;
			}

			if (!isAnyLocked(level, targets)) {
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_cancelled")
						.withStyle(ChatFormatting.RED));
				cleanup();
				return false;
			}

			ticksRemaining--;
			bossBar.setProgress(Math.max(0.0F, (float) ticksRemaining / totalTicks));

			soundTicks--;
			if (soundTicks <= 0) {
				playLockpickSound();
				soundTicks = 20;
			}

			if (ticksRemaining <= 0) {
				complete();
				return false;
			}

			return true;
		}

		private void complete() {
			cleanup();

			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);

			if (!player.isCreative()) {
				consumeLockpick();
				dropIronNugget();
			}

			int successPercent = ModSettings.get().lockpickSuccessPercent;
			boolean success = level.getRandom().nextInt(100) < successPercent;
			if (success) {
				for (BlockPos pos : targets) {
					LockDataManager.unlock(level, pos);
				}
				ModNetworking.broadcastLockSync(level);
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_success")
						.withStyle(ChatFormatting.GREEN));
			} else {
				player.sendSystemMessage(Component.translatable("lock.siegedempires.lockpick_failed")
						.withStyle(ChatFormatting.RED));
			}
		}

		private void consumeLockpick() {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof LockpickItem) {
				held.shrink(1);
				return;
			}

			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (stack.getItem() == ModItems.LOCKPICK) {
					stack.shrink(1);
					return;
				}
			}
		}

		private void dropIronNugget() {
			ItemEntity nugget = new ItemEntity(level,
					anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5,
					new ItemStack(Items.IRON_NUGGET));
			nugget.setDefaultPickUpDelay();
			level.addFreshEntity(nugget);
		}

		private boolean isLookingAtTarget() {
			HitResult hit = player.pick(5.0, 0.0F, false);
			if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) {
				return false;
			}
			return targets.contains(blockHit.getBlockPos());
		}

		private void playLockpickSound() {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SHELF_MULTI_SWAP, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
		}

		private void cleanup() {
			bossBar.removeAllPlayers();
		}
	}
}
