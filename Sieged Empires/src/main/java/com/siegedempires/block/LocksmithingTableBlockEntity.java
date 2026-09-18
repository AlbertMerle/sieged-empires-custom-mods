package com.siegedempires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class LocksmithingTableBlockEntity extends BaseContainerBlockEntity {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int SLOT_COUNT = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private String currentPassword = "";

    public LocksmithingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOCKSMITHING_TABLE, pos, state);
    }

    public String getPassword() { return currentPassword; }
    public void setPassword(String p) { currentPassword = p; setChanged(); }

    public ItemStack getInput() { return items.get(INPUT_SLOT); }
    public ItemStack getOutput() { return items.get(OUTPUT_SLOT); }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("gui.siegedempires.locksmithing_table");
    }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> list) { this.items = list; }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return new LocksmithingTableMenu(id, inv, this);
    }

    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putString("Password", currentPassword);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        currentPassword = input.getString("Password").orElse("");
    }
}