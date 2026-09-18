package com.siegedempires.banner;

import com.siegedempires.Siegedempires;
import com.siegedempires.boat.VanillaBoatHealth;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Town/empire banner for Shippy Ships vessels only (not vanilla {@code minecraft:boat}).
 * <p>
 * Claim happens only when a town member first boards a finished ship: town/empire data
 * is saved on the vessel and never replaced. Later boardings (any town) leave the flag
 * alone. The visual armor-stand is restored from that saved data when missing
 * (chunk load / render).
 */
public final class BoatBannerManager {
	public static final String BOAT_BANNER_TAG = "siegedempires_boat_banner";

	public static final AttachmentType<BoatBannerClaim> BANNER_CLAIM = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Siegedempires.MOD_ID, "boat_banner_claim"),
			BoatBannerClaim.CODEC);

	/** Feet attachment above the boat deck; banner head mesh sits ~2 blocks higher. */
	private static final double BANNER_RIDE_HEIGHT_EXTRA = 0.35;

	/** Local Y for mast attachment {@code (0, y, 0)} from hitbox bottom-center (F3+B). */
	public static final double CARAVEL_BANNER_Y = 21.0;
	public static final double COG_BANNER_Y = 5.0;
	public static final double SAILBOAT_BANNER_Y = 3.0;
	public static final double DEFAULT_SHIP_BANNER_Y = 5.0;

	/** Soft hook for Shippy Ships construction status; vanilla boats never claim. */
	private static Predicate<AbstractBoat> claimAllowed = boat -> false;

	private BoatBannerManager() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isBoatBanner(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				isBoatBanner(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Soft hook for Shippy Ships: allow finished vessels only (never vanilla boats). */
	public static void setClaimAllowed(Predicate<AbstractBoat> predicate) {
		claimAllowed = predicate != null ? predicate : boat -> false;
	}

	/** True when this vessel may receive a town/empire flag (Shippy Ships only). */
	public static boolean allowsShipBanner(AbstractBoat boat) {
		return VanillaBoatHealth.isShippyShip(boat) && claimAllowed.test(boat);
	}

	public static boolean isBoatBanner(Entity entity) {
		if (!(entity instanceof ArmorStand stand)) {
			return false;
		}
		// Custom name syncs to clients; scoreboard tags often do not.
		Component name = stand.getCustomName();
		if (name != null && BOAT_BANNER_TAG.equals(name.getString())) {
			return true;
		}
		if (entity.entityTags().contains(BOAT_BANNER_TAG)) {
			return true;
		}
		// Client fallback before name/tag packets arrive — still need mast positioning.
		if (!entity.level().isClientSide()) {
			return false;
		}
		return looksLikeBoatBannerStand(stand);
	}

	/**
	 * Boat-banner stand riding a Shippy Ships vessel (not a vanilla boat).
	 * Client uses this to swap the head banner for the waving {@code flag.gltf} model.
	 */
	public static boolean isShipBoatBanner(Entity entity) {
		if (!isBoatBanner(entity)) {
			return false;
		}
		Entity vehicle = entity.getVehicle();
		return vehicle != null && VanillaBoatHealth.isShippyShip(vehicle);
	}

	private static boolean looksLikeBoatBannerStand(ArmorStand stand) {
		if (!stand.isInvisible() || stand.showBasePlate()) {
			return false;
		}
		if (!(stand.getVehicle() instanceof AbstractBoat)) {
			return false;
		}
		ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
		return !head.isEmpty()
				&& head.getItem() instanceof BannerItem
				&& !BannerHelper.isWarBanner(head);
	}

	public static boolean hasBoatBanner(AbstractBoat boat) {
		return findBoatBanner(boat) != null;
	}

	public static boolean hasBannerClaim(AbstractBoat boat) {
		return boat.hasAttached(BANNER_CLAIM);
	}

	public static BoatBannerClaim getBannerClaim(AbstractBoat boat) {
		return boat.getAttached(BANNER_CLAIM);
	}

	public static ArmorStand findBoatBanner(AbstractBoat boat) {
		for (Entity passenger : boat.getPassengers()) {
			if (isBoatBanner(passenger) && passenger instanceof ArmorStand stand) {
				return stand;
			}
		}
		return null;
	}

	public static int nonBannerPassengerCount(AbstractBoat boat) {
		int count = 0;
		for (Entity passenger : boat.getPassengers()) {
			if (!isBoatBanner(passenger)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * First town-member boarding claims the boat permanently. If already claimed,
	 * only restores the visual stand from saved data — never changes ownership.
	 */
	public static void onPlayerEnter(AbstractBoat boat, Player player) {
		if (boat.level().isClientSide() || !(boat.level() instanceof ServerLevel level)) {
			return;
		}
		if (!boat.isAlive() || !allowsShipBanner(boat)) {
			return;
		}

		if (hasBannerClaim(boat)) {
			ensureBannerVisual(boat);
			return;
		}

		BoatBannerClaim claim = createClaimForPlayer(player);
		if (claim == null) {
			return;
		}

		boat.setAttached(BANNER_CLAIM, claim);
		spawnBannerStand(boat, level, claim);
	}

	/**
	 * Respawn the armor-stand flag from persisted claim data when the stand is missing
	 * (e.g. after chunk load or accidental dismount discard). Never runs on vanilla boats.
	 */
	public static void ensureBannerVisual(AbstractBoat boat) {
		if (boat.level().isClientSide() || !(boat.level() instanceof ServerLevel level)) {
			return;
		}
		if (!boat.isAlive() || !allowsShipBanner(boat)) {
			return;
		}
		BoatBannerClaim claim = getBannerClaim(boat);
		if (claim == null || hasBoatBanner(boat)) {
			return;
		}
		spawnBannerStand(boat, level, claim);
	}

	private static BoatBannerClaim createClaimForPlayer(Player player) {
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		if (town == null) {
			return null;
		}

		String townId = town.getId() != null ? town.getId() : "";
		String empireId = town.getEmpireId() != null ? town.getEmpireId() : "";
		String baseColor = "white";
		List<String> patterns = List.of();

		if (!empireId.isEmpty()) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(empireId);
			if (empire != null) {
				baseColor = empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white";
				patterns = empire.getBannerPatterns() != null
						? List.copyOf(empire.getBannerPatterns())
						: List.of();
				return new BoatBannerClaim(townId, empireId, baseColor, patterns);
			}
		}

		baseColor = town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white";
		patterns = town.getBannerPatterns() != null
				? List.copyOf(town.getBannerPatterns())
				: List.of();
		return new BoatBannerClaim(townId, empireId, baseColor, patterns);
	}

	private static void spawnBannerStand(AbstractBoat boat, ServerLevel level, BoatBannerClaim claim) {
		ItemStack banner = createBannerStack(claim, level);
		if (banner.isEmpty()) {
			return;
		}

		ArmorStand stand = new ArmorStand(level, boat.getX(), boat.getY(), boat.getZ());
		stand.setYRot(boat.getYRot());
		stand.setNoGravity(true);
		stand.setInvisible(true);
		stand.setNoBasePlate(true);
		stand.setShowArms(false);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		// Synced marker so client mixins can pin the mast height (tags alone often miss the client).
		stand.setCustomName(Component.literal(BOAT_BANNER_TAG));
		stand.setCustomNameVisible(false);
		stand.addTag(BOAT_BANNER_TAG);
		stand.setItemSlot(EquipmentSlot.HEAD, banner);

		if (!level.addFreshEntity(stand)) {
			return;
		}
		if (!stand.startRiding(boat, true, false)) {
			stand.discard();
		}
	}

	private static ItemStack createBannerStack(BoatBannerClaim claim, ServerLevel level) {
		HolderGetter<BannerPattern> patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<String> patternList = claim.patterns() != null ? claim.patterns() : List.of();
		String base = claim.baseColor() != null ? claim.baseColor() : "white";
		return BannerHelper.createBannerStack(base, new ArrayList<>(patternList), patterns);
	}

	public static Vec3 boatBannerAttachmentPoint(AbstractBoat boat, float rideHeight) {
		return new Vec3(0.0, rideHeight + BANNER_RIDE_HEIGHT_EXTRA, 0.0)
				.yRot(-boat.getYRot() * ((float) Math.PI / 180.0F));
	}

	/**
	 * Mast attachment for Shippy Ships: local {@code (0, y, 0)} from hitbox bottom-center
	 * (Caravel 21, Cog 5, Sailboat 3), rotated into world space by ship pitch/roll/yaw.
	 */
	public static Vec3 shipBannerAttachmentPoint(AbstractBoat ship) {
		return shipBannerAttachmentPoint(ship, 0.0F, 0.0F, ship.getYRot());
	}

	/**
	 * @param pitch ship pitch in radians (Shippy Ships {@code getPitch()})
	 * @param roll  ship roll in radians (Shippy Ships {@code getRoll()})
	 * @param yRot  ship yaw in degrees
	 */
	public static Vec3 shipBannerAttachmentPoint(AbstractBoat ship, float pitch, float roll, float yRot) {
		return new Vec3(0.0, shipBannerY(ship), 0.0)
				.xRot(-pitch)
				.zRot(roll)
				.yRot(-yRot * ((float) Math.PI / 180.0F));
	}

	public static double shipBannerY(AbstractBoat ship) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(ship.getType());
		if (id == null) {
			return DEFAULT_SHIP_BANNER_Y;
		}
		String path = id.getPath();
		if (path.contains("caravel")) {
			return CARAVEL_BANNER_Y;
		}
		if (path.contains("cog")) {
			return COG_BANNER_Y;
		}
		if (path.contains("sailboat")) {
			return SAILBOAT_BANNER_Y;
		}
		return DEFAULT_SHIP_BANNER_Y;
	}

	/** Discard banner passengers when the boat is removed or they dismount. */
	public static void discardBoatBanners(AbstractBoat boat) {
		for (Entity passenger : List.copyOf(boat.getPassengers())) {
			if (isBoatBanner(passenger)) {
				passenger.stopRiding();
				passenger.discard();
			}
		}
	}

	public static void discardIfDismountedBoatBanner(Entity entity) {
		if (isBoatBanner(entity) && !entity.isPassenger() && !entity.level().isClientSide()) {
			entity.discard();
		}
	}
}
