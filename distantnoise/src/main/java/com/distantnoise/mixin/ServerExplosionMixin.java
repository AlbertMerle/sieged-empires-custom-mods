package com.distantnoise.mixin;

import com.distantnoise.network.ModNetworking;
import com.distantnoise.sound.DistantNoiseKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Whenever any server explosion detonates (creeper, TNT, minecart TNT, crystal, bed, anchor, fireball, etc.),
 * relay a distant muffled explosion boom to far players.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	public abstract Vec3 center();

	@Inject(method = "explode", at = @At("RETURN"))
	private void distantnoise$afterExplode(CallbackInfoReturnable<Integer> cir) {
		Vec3 pos = this.center();
		ModNetworking.broadcast(
				this.level,
				DistantNoiseKind.TNT,
				pos.x,
				pos.y,
				pos.z
		);
	}
}
