package com.siegedempires.client.mixin;

import java.util.List;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("children")
	List<GuiEventListener> siegedempires$getChildren();

	@Accessor("narratables")
	List<NarratableEntry> siegedempires$getNarratables();

	@Accessor("renderables")
	List<Renderable> siegedempires$getRenderables();
}
