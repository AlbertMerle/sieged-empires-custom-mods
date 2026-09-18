package com.weaponsmodaddon.gun;

import ckathode.weaponmod.WMDamageSources;
import ckathode.weaponmod.WMUtil;
import com.weaponsmodaddon.headshot.HeadshotContext;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Client-aim delayed hitscan for muskets / flintlocks.
 * Travel time: 1 second per 100 blocks (min 1 tick at close range).
 */
public final class GunHitscan {
	/** Musket effective ray length — beyond typical distant-player band. */
	public static final double MAX_RANGE = 256.0;
	private static final double TICKS_PER_100_BLOCKS = 20.0;

	private static final Map<UUID, PendingShot> PENDING = new ConcurrentHashMap<>();

	private GunHitscan() {
	}

	public static void schedule(ServerPlayer shooter, ItemStack weapon, float yaw, float pitch) {
		ServerLevel level = (ServerLevel) shooter.level();
		Vec3 origin = shooter.getEyePosition();
		Vec3 direction = Vec3.directionFromRotation(pitch, yaw).normalize();
		double distance = rayDistance(level, shooter, origin, direction, MAX_RANGE);
		int delayTicks = Math.max(1, (int) Math.round(distance * TICKS_PER_100_BLOCKS / 100.0));

		PENDING.put(
				shooter.getUUID(),
				new PendingShot(origin, direction, weapon.copy(), delayTicks));
	}

	public static void tickServer(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, PendingShot>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, PendingShot> entry = it.next();
			PendingShot shot = entry.getValue();
			shot.ticksLeft--;
			if (shot.ticksLeft > 0) {
				continue;
			}
			it.remove();
			ServerPlayer shooter = server.getPlayerList().getPlayer(entry.getKey());
			if (shooter == null || !shooter.isAlive()) {
				continue;
			}
			resolve(shooter, shot);
		}
	}

	private static void resolve(ServerPlayer shooter, PendingShot shot) {
		ServerLevel level = (ServerLevel) shooter.level();
		Vec3 end = shot.origin.add(shot.direction.scale(MAX_RANGE));
		HitResult hit = raycast(level, shooter, shot.origin, end);
		if (hit.getType() != HitResult.Type.ENTITY) {
			return;
		}
		EntityHitResult entityHit = (EntityHitResult) hit;
		Entity target = entityHit.getEntity();
		if (!canHit(target, shooter)) {
			return;
		}

		var damageSource = level.damageSources().source(WMDamageSources.WEAPON, shooter, shooter);
		float damage = GunDamage.compute(shot.weapon);
		HeadshotContext.prepare(entityHit, HeadshotContext.Kind.GUN);
		try {
			WMUtil.hurtOrSimulate(target, damageSource, damage);
		} finally {
			HeadshotContext.clear();
		}
	}

	@Nullable
	private static HitResult raycast(ServerLevel level, Entity shooter, Vec3 start, Vec3 end) {
		ClipContext blockCtx = new ClipContext(
				start,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				shooter);
		HitResult blockHit = level.clip(blockCtx);
		Vec3 entityEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : end;

		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				shooter,
				start,
				entityEnd,
				shooter.getBoundingBox().expandTowards(entityEnd.subtract(start)).inflate(1.0),
				entity -> canHit(entity, shooter),
				0.0);

		if (entityHit != null) {
			if (blockHit.getType() != HitResult.Type.MISS) {
				double blockDist = start.distanceToSqr(blockHit.getLocation());
				double entityDist = start.distanceToSqr(entityHit.getLocation());
				if (entityDist < blockDist) {
					return entityHit;
				}
				return blockHit;
			}
			return entityHit;
		}
		return blockHit;
	}

	private static double rayDistance(ServerLevel level, Entity shooter, Vec3 origin, Vec3 direction, double maxRange) {
		Vec3 end = origin.add(direction.scale(maxRange));
		HitResult hit = raycast(level, shooter, origin, end);
		if (hit.getType() == HitResult.Type.MISS) {
			return maxRange;
		}
		return Mth.clamp(origin.distanceTo(hit.getLocation()), 0.0, maxRange);
	}

	private static boolean canHit(Entity entity, Entity shooter) {
		if (!entity.isPickable() || entity.isSpectator()) {
			return false;
		}
		if (entity == shooter) {
			return false;
		}
		if (entity instanceof Player target
				&& shooter instanceof Player attacker
				&& !attacker.canHarmPlayer(target)) {
			return false;
		}
		return true;
	}

	private static final class PendingShot {
		final Vec3 origin;
		final Vec3 direction;
		final ItemStack weapon;
		int ticksLeft;

		PendingShot(Vec3 origin, Vec3 direction, ItemStack weapon, int ticksLeft) {
			this.origin = origin;
			this.direction = direction;
			this.weapon = weapon;
			this.ticksLeft = ticksLeft;
		}
	}
}
