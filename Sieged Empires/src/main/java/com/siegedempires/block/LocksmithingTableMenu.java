package com.siegedempires.block;

import com.siegedempires.item.KeyItem;
import com.siegedempires.item.LockItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class LocksmithingTableMenu extends AbstractContainerMenu {

	public static final int INPUT_SLOT_ID = 0;
	public static final int OUTPUT_SLOT_ID = 1;

	@Nullable
	private final LocksmithingTableBlockEntity blockEntity;
	private final ContainerLevelAccess access;
	/** Client uses this; server prefers the block entity when present. */
	private final Container craftSlots;
	private String pendingRename = "";
	private String pendingPassword = "";

	/** Used by MenuType registry (client-side). */
	public LocksmithingTableMenu(int containerId, Inventory playerInv) {
		super(ModMenuTypes.LOCKSMITHING_TABLE, containerId);
		this.blockEntity = null;
		this.access = ContainerLevelAccess.NULL;
		this.craftSlots = new SimpleContainer(2);
		addSlots(playerInv, this.craftSlots);
	}

	/** Used by getMenuProvider on server-side. */
	public LocksmithingTableMenu(int containerId, Inventory playerInv,
	                             LocksmithingTableBlockEntity entity) {
		super(ModMenuTypes.LOCKSMITHING_TABLE, containerId);
		this.blockEntity = entity;
		this.access = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());
		this.craftSlots = entity;
		this.pendingPassword = entity.getPassword() != null ? entity.getPassword() : "";
		addSlots(playerInv, entity);
		createResult();
	}

	private void addSlots(Inventory playerInv, Container table) {
		// Spaced layout matching locksmithingtablegui.png (176x186)
		this.addSlot(new Slot(table, INPUT_SLOT_ID, 27, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof KeyItem || stack.getItem() instanceof LockItem;
			}

			@Override
			public void setChanged() {
				super.setChanged();
				slotsChanged(table);
			}
		});
		this.addSlot(new Slot(table, OUTPUT_SLOT_ID, 133, 36) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player player, ItemStack stack) {
				Slot input = LocksmithingTableMenu.this.getSlot(INPUT_SLOT_ID);
				ItemStack inputStack = input.getItem();
				int taken = stack.getCount();
				if (!inputStack.isEmpty() && taken > 0) {
					inputStack.shrink(taken);
					if (inputStack.isEmpty()) {
						input.set(ItemStack.EMPTY);
					} else {
						input.set(inputStack);
					}
				}
				super.onTake(player, stack);
				createResult();
			}
		});

		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 9; col++)
				this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
		for (int col = 0; col < 9; col++)
			this.addSlot(new Slot(playerInv, col, 8 + col * 18, 162));
	}

	@Nullable
	public LocksmithingTableBlockEntity getBlockEntity() {
		return blockEntity;
	}

	public ItemStack getInputStack() {
		return getSlot(INPUT_SLOT_ID).getItem();
	}

	public String getPendingPassword() {
		return pendingPassword;
	}

	public String getPendingRename() {
		return pendingRename;
	}

	/** Called from the GUI (client prediction) and C2S packet (server authority). */
	public void applyClientFields(String rename, String password) {
		this.pendingRename = rename != null ? rename : "";
		if (password != null && password.length() > LockItem.MAX_PW) {
			password = password.substring(0, LockItem.MAX_PW);
		}
		this.pendingPassword = password != null ? password : "";
		if (blockEntity != null) {
			blockEntity.setPassword(this.pendingPassword);
		}
		createResult();
	}

	@Override
	public void slotsChanged(Container container) {
		createResult();
	}

	private void createResult() {
		ItemStack input = getSlot(INPUT_SLOT_ID).getItem();
		if (input.isEmpty()
			|| (!(input.getItem() instanceof KeyItem) && !(input.getItem() instanceof LockItem))) {
			getSlot(OUTPUT_SLOT_ID).set(ItemStack.EMPTY);
			return;
		}

		ItemStack result = input.copy();
		result.setCount(input.getCount());
		if (result.getItem() instanceof KeyItem) {
			KeyItem.setPassword(result, pendingPassword);
		} else {
			LockItem.setPassword(result, pendingPassword);
		}
		String rename = pendingRename.trim();
		if (!rename.isEmpty()) {
			result.set(DataComponents.CUSTOM_NAME, Component.literal(rename));
		} else {
			result.remove(DataComponents.CUSTOM_NAME);
		}
		getSlot(OUTPUT_SLOT_ID).set(result);
		this.broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			if (index == OUTPUT_SLOT_ID) {
				if (!this.moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
				slot.onTake(player, stack);
			} else if (index == INPUT_SLOT_ID) {
				if (!this.moveItemStackTo(stack, 2, 38, true)) return ItemStack.EMPTY;
			} else if (stack.getItem() instanceof KeyItem || stack.getItem() instanceof LockItem) {
				if (!this.moveItemStackTo(stack, INPUT_SLOT_ID, INPUT_SLOT_ID + 1, false))
					return ItemStack.EMPTY;
			} else {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
			else slot.setChanged();
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModBlocks.LOCKSMITHING_TABLE);
	}
}
