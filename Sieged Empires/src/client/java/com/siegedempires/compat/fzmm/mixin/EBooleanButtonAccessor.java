package com.siegedempires.compat.fzmm.mixin;

import fzmm.zailer.me.client.gui.components.extend.component.EBooleanButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code enabledText}/{@code disabledText} are final; FZMM's {@code updateMessage()}
 * always restores them after {@code enabledIgnoreCallback}. Mutate both to Finished!
 * so preview updates cannot revive Item:Banner/Shield.
 */
@Mixin(EBooleanButton.class)
public interface EBooleanButtonAccessor {
	@Accessor("enabledText")
	@Mutable
	void siegedempires$setEnabledText(Component text);

	@Accessor("disabledText")
	@Mutable
	void siegedempires$setDisabledText(Component text);
}
