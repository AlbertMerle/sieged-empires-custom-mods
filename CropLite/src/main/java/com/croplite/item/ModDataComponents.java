package com.croplite.item;

import com.croplite.CropLite;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModDataComponents {
	private ModDataComponents() {
	}

	public static final DataComponentType<HasGarlicComponent> HAS_GARLIC = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			CropLite.id("has_garlic"),
			DataComponentType.<HasGarlicComponent>builder()
					.persistent(HasGarlicComponent.CODEC)
					.networkSynchronized(HasGarlicComponent.STREAM_CODEC)
					.build());

	public static void initialize() {
	}
}
