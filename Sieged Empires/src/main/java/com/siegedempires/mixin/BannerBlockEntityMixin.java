package com.siegedempires.mixin;

import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BannerManager;
import com.siegedempires.banner.BannerType;
import com.siegedempires.banner.CustomBannerDesign;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerBlockEntity.class)
public abstract class BannerBlockEntityMixin implements BannerHelper.BannerBlockEntityAccess {
	@Shadow
	private BannerPatternLayers patterns;

	@Unique
	private String siegedempires$townId;

	@Unique
	private String siegedempires$bannerType;

	@Unique
	private boolean siegedempires$freshlyPlaced;

	@Unique
	private String siegedempires$citizenId;

	@Unique
	private String siegedempires$placerId;

	@Unique
	private String siegedempires$invaderRef;

	@Unique
	private String siegedempires$invadedRef;

	@Unique
	private String siegedempires$bannerPixels;

	@Inject(method = "applyImplicitComponents", at = @At("TAIL"))
	private void siegedempires$readItemMetadata(DataComponentGetter components, CallbackInfo ci) {
		CustomData customData = components.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return;
		}

		CompoundTag data = customData.copyTag().getCompound(BannerHelper.CUSTOM_DATA_KEY).orElse(null);
		if (data == null) {
			return;
		}

		siegedempires$townId = BannerHelper.getTownId(data);
		BannerType type = BannerHelper.getBannerType(data);
		siegedempires$bannerType = type != null ? type.getId() : null;
		siegedempires$freshlyPlaced = (type == BannerType.CLAIM
			|| type == BannerType.CITIZEN_GIVE_LAND
			|| type == BannerType.LAND_REVOKE
			|| type == BannerType.RESTRICTED
			|| type == BannerType.WAR);
		siegedempires$citizenId = BannerHelper.getCitizenId(data);
		siegedempires$placerId = BannerHelper.getPlacerId(data);
		if ((siegedempires$placerId == null || siegedempires$placerId.isEmpty())
				&& type == BannerType.CLAIM) {
			java.util.UUID pending = BannerHelper.pollPendingBannerPlacer();
			if (pending != null) {
				siegedempires$placerId = pending.toString();
			}
		} else {
			BannerHelper.clearPendingBannerPlacer();
		}
		siegedempires$invaderRef = BannerHelper.getInvaderRef(data);
		siegedempires$invadedRef = BannerHelper.getInvadedRef(data);
		siegedempires$bannerPixels = BannerHelper.getBannerPixels(data);

		BannerManager.onBannerLoaded((BannerBlockEntity) (Object) this);
	}

	@Inject(method = "collectImplicitComponents", at = @At("TAIL"))
	private void siegedempires$writeItemMetadata(DataComponentMap.Builder components, CallbackInfo ci) {
		if (!siegedempires$hasAnyMetadata()) {
			return;
		}
		CompoundTag data = new CompoundTag();
		if (siegedempires$townId != null && !siegedempires$townId.isEmpty()) {
			data.putString(BannerHelper.TOWN_KEY, siegedempires$townId);
		}
		if (siegedempires$bannerType != null && !siegedempires$bannerType.isEmpty()) {
			data.putString(BannerHelper.TYPE_KEY, siegedempires$bannerType);
		}
		if (siegedempires$citizenId != null && !siegedempires$citizenId.isEmpty()) {
			data.putString(BannerHelper.CITIZEN_KEY, siegedempires$citizenId);
		}
		if (siegedempires$placerId != null && !siegedempires$placerId.isEmpty()) {
			data.putString(BannerHelper.PLACER_KEY, siegedempires$placerId);
		}
		if (siegedempires$invaderRef != null && !siegedempires$invaderRef.isEmpty()) {
			data.putString(BannerHelper.INVADER_REF_KEY, siegedempires$invaderRef);
		}
		if (siegedempires$invadedRef != null && !siegedempires$invadedRef.isEmpty()) {
			data.putString(BannerHelper.INVADED_REF_KEY, siegedempires$invadedRef);
		}
		if (CustomBannerDesign.isValidEncoded(siegedempires$bannerPixels)) {
			data.putString(BannerHelper.BANNER_PIXELS_KEY, siegedempires$bannerPixels);
		}
		if (data.isEmpty()) {
			return;
		}
		CompoundTag root = new CompoundTag();
		root.put(BannerHelper.CUSTOM_DATA_KEY, data);
		components.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	@Unique
	private boolean siegedempires$hasAnyMetadata() {
		return (siegedempires$townId != null && !siegedempires$townId.isEmpty())
				|| (siegedempires$bannerType != null && !siegedempires$bannerType.isEmpty())
				|| CustomBannerDesign.isValidEncoded(siegedempires$bannerPixels)
				|| (siegedempires$citizenId != null && !siegedempires$citizenId.isEmpty())
				|| (siegedempires$placerId != null && !siegedempires$placerId.isEmpty())
				|| (siegedempires$invaderRef != null && !siegedempires$invaderRef.isEmpty())
				|| (siegedempires$invadedRef != null && !siegedempires$invadedRef.isEmpty());
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void siegedempires$onLoad(ValueInput input, CallbackInfo ci) {
		siegedempires$townId = input.getString("SiegedEmpiresTown").orElse(null);
		if (siegedempires$townId != null && siegedempires$townId.isEmpty()) {
			siegedempires$townId = null;
		}
		siegedempires$bannerType = input.getString("SiegedEmpiresBannerType").orElse(null);
		if (siegedempires$bannerType != null && siegedempires$bannerType.isEmpty()) {
			siegedempires$bannerType = null;
		}
		siegedempires$citizenId = input.getString("SiegedEmpiresCitizen").orElse(null);
		if (siegedempires$citizenId != null && siegedempires$citizenId.isEmpty()) {
			siegedempires$citizenId = null;
		}
		siegedempires$placerId = input.getString("SiegedEmpiresPlacer").orElse(null);
		if (siegedempires$placerId != null && siegedempires$placerId.isEmpty()) {
			siegedempires$placerId = null;
		}
		siegedempires$invaderRef = input.getString("SiegedEmpiresInvaderRef").orElse(null);
		if (siegedempires$invaderRef != null && siegedempires$invaderRef.isEmpty()) {
			siegedempires$invaderRef = null;
		}
		siegedempires$invadedRef = input.getString("SiegedEmpiresInvadedRef").orElse(null);
		if (siegedempires$invadedRef != null && siegedempires$invadedRef.isEmpty()) {
			siegedempires$invadedRef = null;
		}
		siegedempires$bannerPixels = input.getString("SiegedEmpiresBannerPixels").orElse(null);
		if (siegedempires$bannerPixels != null
				&& !CustomBannerDesign.isValidEncoded(siegedempires$bannerPixels)) {
			siegedempires$bannerPixels = null;
		}

		if (siegedempires$townId != null && !siegedempires$townId.isEmpty()) {
			BannerManager.onBannerLoaded((BannerBlockEntity) (Object) this);
		} else if (BannerType.WAR.getId().equals(siegedempires$bannerType)) {
			BannerManager.onBannerLoaded((BannerBlockEntity) (Object) this);
		}
	}

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void siegedempires$onSave(ValueOutput output, CallbackInfo ci) {
		output.putString("SiegedEmpiresTown", siegedempires$townId != null ? siegedempires$townId : "");
		output.putString("SiegedEmpiresBannerType", siegedempires$bannerType != null ? siegedempires$bannerType : "");
		output.putString("SiegedEmpiresCitizen", siegedempires$citizenId != null ? siegedempires$citizenId : "");
		output.putString("SiegedEmpiresPlacer", siegedempires$placerId != null ? siegedempires$placerId : "");
		output.putString("SiegedEmpiresInvaderRef", siegedempires$invaderRef != null ? siegedempires$invaderRef : "");
		output.putString("SiegedEmpiresInvadedRef", siegedempires$invadedRef != null ? siegedempires$invadedRef : "");
		output.putString("SiegedEmpiresBannerPixels",
				siegedempires$bannerPixels != null ? siegedempires$bannerPixels : "");
	}

	@Override
	public void siegedempires$setPatterns(BannerPatternLayers newPatterns) {
		this.patterns = newPatterns;
	}

	@Override
	public String siegedempires$getTownId() {
		return siegedempires$townId;
	}

	@Override
	public void siegedempires$setTownId(String townId) {
		this.siegedempires$townId = townId;
	}

	@Override
	public String siegedempires$getBannerType() {
		return siegedempires$bannerType;
	}

	@Override
	public void siegedempires$setBannerType(String bannerType) {
		this.siegedempires$bannerType = bannerType;
	}

	@Override
	public boolean siegedempires$isFreshlyPlaced() {
		return siegedempires$freshlyPlaced;
	}

	@Override
	public void siegedempires$setFreshlyPlaced(boolean freshlyPlaced) {
		this.siegedempires$freshlyPlaced = freshlyPlaced;
	}

	@Override
	public String siegedempires$getCitizenId() {
		return siegedempires$citizenId;
	}

	@Override
	public void siegedempires$setCitizenId(String citizenId) {
		this.siegedempires$citizenId = citizenId;
	}

	@Override
	public String siegedempires$getPlacerId() {
		return siegedempires$placerId;
	}

	@Override
	public void siegedempires$setPlacerId(String placerId) {
		this.siegedempires$placerId = placerId;
	}

	@Override
	public String siegedempires$getInvaderRef() {
		return siegedempires$invaderRef;
	}

	@Override
	public void siegedempires$setInvaderRef(String invaderRef) {
		this.siegedempires$invaderRef = invaderRef;
	}

	@Override
	public String siegedempires$getInvadedRef() {
		return siegedempires$invadedRef;
	}

	@Override
	public void siegedempires$setInvadedRef(String invadedRef) {
		this.siegedempires$invadedRef = invadedRef;
	}

	@Override
	public String siegedempires$getBannerPixels() {
		return siegedempires$bannerPixels;
	}

	@Override
	public void siegedempires$setBannerPixels(String bannerPixels) {
		this.siegedempires$bannerPixels =
				CustomBannerDesign.isValidEncoded(bannerPixels) ? bannerPixels : null;
	}
}
