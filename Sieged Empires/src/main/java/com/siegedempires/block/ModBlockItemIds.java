package com.siegedempires.block;

import com.siegedempires.Siegedempires;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;

public final class ModBlockItemIds {

    public static final BlockItemId LOCKSMITHING_TABLE = create("locksmithing_table");

    private ModBlockItemIds() {}

    private static BlockItemId create(String name) {
        // Must use Identifier overload — create(String, String) puts BOTH in the minecraft namespace!
        Identifier id = Siegedempires.id(name);
        return BlockItemId.create(id, id);
    }
}
