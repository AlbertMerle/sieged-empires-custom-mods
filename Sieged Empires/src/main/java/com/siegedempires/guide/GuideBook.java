package com.siegedempires.guide;

import com.mojang.serialization.Codec;
import com.siegedempires.Siegedempires;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.config.ModSettings;
import com.siegedempires.item.ModItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Gives the Sieged Empire Guide once on a player's first join to a world/server,
 * and refreshes its pages from wiki/config whenever the book is opened.
 * <p>
 * Receipt is stored as a Fabric persistent player attachment (survives logout).
 * Losing or throwing the book does <strong>not</strong> cause another give.
 * <p>
 * Item id: {@code siegedempires:guide} (creative + {@code /give}).
 * Legacy written-book guides with the custom flag still open the custom GUI.
 */
public final class GuideBook {
	public static final String TITLE = "Sieged Empire Guide";
	public static final String AUTHOR = "Sieged Empires";

	private static final String GUIDE_FLAG = "guide_book";
	/** Legacy non-persistent flag (player CUSTOM_DATA); migrated once if present. */
	private static final String RECEIVED_FLAG = "received_guide_book";

	/**
	 * Persisted on the player entity (playerdata). True after the first-join give.
	 */
	public static final AttachmentType<Boolean> RECEIVED_GUIDE = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "received_guide_book"),
			Codec.BOOL);

	private GuideBook() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				tryGiveOnFirstJoin(handler.player));

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!isGuideBook(stack)) {
				return InteractionResult.PASS;
			}
			ModSettings.load();
			applyContent(stack);
			return InteractionResult.PASS;
		});
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(ModItems.GUIDE);
		markAsGuideBook(stack);
		applyContent(stack);
		return stack;
	}

	public static void applyContent(ItemStack stack) {
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(TITLE));
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, GuideBookContent.createContent());
	}

	public static boolean isGuideBook(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.is(ModItems.GUIDE)) {
			return true;
		}
		// Legacy first-join books were vanilla written books with a custom NBT flag.
		if (!stack.is(Items.WRITTEN_BOOK)) {
			return false;
		}
		CompoundTag data = BannerHelper.readSiegedEmpiresData(stack);
		return data != null && data.getBooleanOr(GUIDE_FLAG, false);
	}

	private static void markAsGuideBook(ItemStack stack) {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putBoolean(GUIDE_FLAG, true);
		root.put(BannerHelper.CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	private static void tryGiveOnFirstJoin(ServerPlayer player) {
		if (hasReceivedGuide(player)) {
			return;
		}
		ItemStack book = create();
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		markReceivedGuide(player);
		Siegedempires.LOGGER.info("Gave {} the Sieged Empire Guide (first join)", player.getName().getString());
	}

	private static boolean hasReceivedGuide(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(RECEIVED_GUIDE))) {
			return true;
		}
		// One-time migrate from the old non-persisting CUSTOM_DATA flag if somehow set.
		if (hasLegacyReceivedFlag(player)) {
			markReceivedGuide(player);
			return true;
		}
		return false;
	}

	private static boolean hasLegacyReceivedFlag(ServerPlayer player) {
		CustomData customData = player.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return false;
		}
		CompoundTag root = customData.copyTag();
		CompoundTag data = root.getCompound(BannerHelper.CUSTOM_DATA_KEY).orElse(null);
		return data != null && data.getBooleanOr(RECEIVED_FLAG, false);
	}

	private static void markReceivedGuide(ServerPlayer player) {
		player.setAttached(RECEIVED_GUIDE, true);
	}
}
