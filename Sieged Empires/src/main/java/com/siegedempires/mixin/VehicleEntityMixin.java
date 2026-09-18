package com.siegedempires.mixin;

import com.siegedempires.boat.VanillaBoatHealth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's regenerating damage meter for boats/rafts with persistent HP
 * ({@link com.siegedempires.config.ModSettings#vanillaBoatHealth}, default 30).
 * <p>
 * Invulnerability / hurt-flash helpers live on {@link net.minecraft.world.entity.Entity},
 * not {@link VehicleEntity}, so they are reached via public APIs instead of {@code @Shadow}.
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleEntityMixin {
	@Shadow
	protected abstract void destroy(ServerLevel level, DamageSource source);

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void siegedempires$vanillaBoatHealth(
			ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		VehicleEntity self = (VehicleEntity) (Object) this;
		if (!VanillaBoatHealth.isVanillaBoat(self)) {
			return;
		}
		AbstractBoat boat = (AbstractBoat) self;

		if (boat.isRemoved()) {
			cir.setReturnValue(true);
			return;
		}
		if (isInvulnerableToBasePublic(boat, source)) {
			cir.setReturnValue(false);
			return;
		}

		boat.setHurtDir(-boat.getHurtDir());
		boat.setHurtTime(10);
		boat.hurtMarked = true;
		boat.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());

		boolean creativePlayer = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
		if (creativePlayer) {
			boat.discard();
			cir.setReturnValue(true);
			return;
		}

		float remaining = VanillaBoatHealth.damage(boat, damage);
		if (remaining <= 0.0F) {
			this.destroy(level, source);
		}
		cir.setReturnValue(true);
	}

	/** Mirrors {@code Entity.isInvulnerableToBase} using only public members. */
	private static boolean isInvulnerableToBasePublic(AbstractBoat boat, DamageSource source) {
		if (boat.isInvulnerable()
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& !source.isCreativePlayer()) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FIRE) && boat.fireImmune()) {
			return true;
		}
		return source.is(DamageTypeTags.IS_FALL) && boat.is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
	}
}
