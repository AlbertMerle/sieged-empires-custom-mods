package com.voxmapsync.client.mixin;

import com.mamiyaotaru.voxelmap.persistent.GuiPersistentMap;
import com.voxmapsync.client.ClientMapSession;
import com.voxmapsync.client.MapViewportMath;
import com.voxmapsync.client.MapViewportMath.RegionBounds;
import com.voxmapsync.client.ViewportRegionRequester;
import com.voxmapsync.client.claims.ClaimOverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Sieged Empires claim fills after VoxelMap region textures, still in map-space.
 * Also drives viewport region requests for empty/missing/partial tiles.
 */
@Mixin(GuiPersistentMap.class)
public class GuiPersistentMapMixin {
	@Inject(
			method = "extractRenderState",
			at = @At(
					value = "FIELD",
					target = "Lcom/mamiyaotaru/voxelmap/MapSettingsManager;worldBorder:Z",
					opcode = org.objectweb.asm.Opcodes.GETFIELD
			)
	)
	private void voxelmapsync$drawClaims(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		RegionBounds viewport = MapViewportMath.regionBounds((GuiPersistentMap) (Object) this, 1);
		ClaimOverlayRenderer.drawInMapSpace(graphics, mc.font, dim, viewport);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void voxelmapsync$mapOpen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ClientMapSession.setWorldMapOpen(true);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void voxelmapsync$mapClosed(CallbackInfo ci) {
		ClientMapSession.setWorldMapOpen(false);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void voxelmapsync$requestViewport(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ViewportRegionRequester.tick((GuiPersistentMap) (Object) this);
	}
}
