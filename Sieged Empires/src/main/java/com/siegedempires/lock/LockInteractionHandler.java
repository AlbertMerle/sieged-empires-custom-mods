package com.siegedempires.lock;

import com.siegedempires.item.KeyItem;
import com.siegedempires.item.LockItem;
import com.siegedempires.item.LockpickItem;
import com.siegedempires.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public final class LockInteractionHandler {

	private LockInteractionHandler() {}

	/** Handle right-click on a block. Called from UseBlockCallback. */
	public static InteractionResult onUseBlock(Player player, Level level,
	                                           InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl))
			return InteractionResult.PASS;

		ItemStack held = sp.getItemInHand(hand);
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		boolean lockable = LockDataManager.isLockable(blockId);
		boolean locked = isAnyLocked(sl, relatedPositions(state, pos));

		// --- Place Lock ---
		if (held.getItem() instanceof LockItem && lockable) {
			if (locked) {
				msg(sp, "Already locked!", true);
				return InteractionResult.FAIL;
			}
			String pw = LockItem.getPassword(held);
			if (pw.isEmpty()) {
				msg(sp, "Set a password first at a Locksmithing Table!", true);
				return InteractionResult.FAIL;
			}
			for (BlockPos p : relatedPositions(state, pos)) {
				LockDataManager.lock(sl, p, pw, sp.getUUID().toString());
			}
			if (!sp.isCreative()) held.shrink(1);
			msg(sp, "Locked!", false);
			com.siegedempires.network.ModNetworking.broadcastLockSync(sl);
			return InteractionResult.SUCCESS;
		}

		// --- Lockpick ---
		if (held.getItem() instanceof LockpickItem && locked) {
			if (LockpickSessionManager.isActive(sp)) {
				msg(sp, Component.translatable("lock.siegedempires.already_lockpicking").getString(), true);
				return InteractionResult.FAIL;
			}
			Component deny = LockpickAccess.denyReason(sp, pos);
			if (deny != null) {
				sp.sendSystemMessage(deny.copy().withStyle(ChatFormatting.RED));
				return InteractionResult.FAIL;
			}
			if (LockpickSessionManager.start(sp, sl, relatedPositions(state, pos), hand, pos, state)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.FAIL;
		}

		// --- Use Key (grants temporary access, does NOT remove lock) ---
		if (held.getItem() instanceof KeyItem && locked) {
			if (LockDataManager.checkKey(sl, primaryLockPos(sl, relatedPositions(state, pos)),
				KeyItem.getPassword(held))) {
				msg(sp, "Key accepted!", false);
				return InteractionResult.PASS;
			}
			msg(sp, "Key does not match!", true);
			return InteractionResult.FAIL;
		}

		// --- Block locked interaction while lockpicking or without a matching key ---
		if (LockpickSessionManager.isActive(sp)) {
			msg(sp, Component.translatable("lock.siegedempires.already_lockpicking").getString(), true);
			return InteractionResult.FAIL;
		}

		if (locked && !isMatchingKeyInHand(sp, sl, relatedPositions(state, pos))) {
			msg(sp, "Locked!", true);
			return InteractionResult.FAIL;
		}

		return InteractionResult.PASS;
	}

	/**
	 * Drop lock item + clear data when a locked block is destroyed by a
	 * non-mining path (explosions use {@link LockExplosionHandler} instead).
	 * Player mining of locked blocks is denied by PermissionManager.
	 */
	public static boolean onBlockBreak(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		List<BlockPos> related = relatedPositions(state, pos);
		if (!isAnyLocked(level, related)) return true;

		String pw = null;
		for (BlockPos p : related) {
			String found = LockDataManager.getPassword(level, p);
			if (found != null) pw = found;
		}
		if (pw != null) {
			ItemStack s = new ItemStack(ModItems.LOCK);
			LockItem.setPassword(s, pw);
			level.addFreshEntity(new ItemEntity(level,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s));
		}
		for (BlockPos p : related) {
			LockDataManager.unlock(level, p);
		}
		com.siegedempires.network.ModNetworking.broadcastLockSync(level);
		return true;
	}

	/** All block positions that share one logical lock (door halves, double chests). */
	public static List<BlockPos> relatedPositions(BlockState state, BlockPos pos) {
		List<BlockPos> list = new ArrayList<>(2);
		list.add(pos);
		if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
			if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				list.add(pos.above());
			} else {
				list.add(pos.below());
			}
		} else if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
			ChestType type = state.getValue(ChestBlock.TYPE);
			if (type != ChestType.SINGLE) {
				Direction connected = ChestBlock.getConnectedDirection(state);
				list.add(pos.relative(connected));
			}
		}
		return list;
	}

	private static boolean isAnyLocked(ServerLevel level, List<BlockPos> positions) {
		for (BlockPos p : positions) {
			if (LockDataManager.isLocked(level, p)) return true;
		}
		return false;
	}

	private static BlockPos primaryLockPos(ServerLevel level, List<BlockPos> positions) {
		for (BlockPos p : positions) {
			if (LockDataManager.isLocked(level, p)) return p;
		}
		return positions.getFirst();
	}

	private static boolean isMatchingKeyInHand(ServerPlayer sp, ServerLevel sl, List<BlockPos> positions) {
		BlockPos lockPos = primaryLockPos(sl, positions);
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack s = sp.getItemInHand(h);
			if (s.getItem() instanceof KeyItem
				&& LockDataManager.checkKey(sl, lockPos, KeyItem.getPassword(s))) {
				return true;
			}
		}
		return false;
	}

	private static void msg(ServerPlayer p, String text, boolean error) {
		p.sendSystemMessage(Component.literal(text)
			.withStyle(error ? ChatFormatting.RED : ChatFormatting.GREEN));
	}
}
