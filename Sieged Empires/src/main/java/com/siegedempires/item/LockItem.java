package com.siegedempires.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class LockItem extends Item {

    public static final int MAX_PW = 40;
    private static final String NBT_KEY = "SiegedPassword";

    public LockItem(Properties p) { super(p); }

    public static String getPassword(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag().getString(NBT_KEY).orElse("") : "";
    }

    public static void setPassword(ItemStack stack, String pw) {
        if (pw.length() > MAX_PW) pw = pw.substring(0, MAX_PW);
        CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
            ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
            : new CompoundTag();
        tag.putString(NBT_KEY, pw);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    public static boolean hasPassword(ItemStack stack) {
        return !getPassword(stack).isEmpty();
    }
}