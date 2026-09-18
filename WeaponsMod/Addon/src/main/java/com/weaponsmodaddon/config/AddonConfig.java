package com.weaponsmodaddon.config;

import ckathode.weaponmod.item.ItemMusket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.weaponsmodaddon.ModItemTags;
import com.weaponsmodaddon.WeaponsModAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/weaponsmodaddon.json} — gun reload/damage tuning and headshot rules.
 * <p>
 * Base musket bullet damage is 20; multipliers scale that base. Enchantment extra damage stays additive.
 */
public final class AddonConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static AddonConfig INSTANCE = new AddonConfig();

	private static final Identifier BLUNDERBUSS_ID = Identifier.fromNamespaceAndPath("weaponmod", "blunderbuss");
	private static final Identifier MORTAR_ID = Identifier.fromNamespaceAndPath("weaponmod", "mortar");

	/** Plain musket / bayonetted musket (not scoped). */
	public GunTuning musket = GunTuning.musketDefaults();
	/** Flintlock pistol. */
	public GunTuning flintlock = GunTuning.flintlockDefaults();
	/** Addon scoped musket / scoped bayonets. */
	public GunTuning scopedMusket = GunTuning.scopedDefaults();
	/** Blunderbuss. */
	public GunTuning blunderbuss = GunTuning.blunderbussDefaults();
	/** Hand mortar ({@code weaponmod:mortar}). */
	public GunTuning mortar = GunTuning.mortarDefaults();

	@SerializedName("player-headshot")
	public boolean playerHeadshot = true;

	/** Player head band tuning (top fraction, blindness, damage). */
	public HeadshotTuning headshot = HeadshotTuning.defaults();

	@SerializedName("animal-headshot-front")
	public boolean animalHeadshotFront = true;

	@SerializedName("animal-headshot-front-fraction")
	public double animalHeadshotFrontFraction = 0.30;

	@SerializedName("animal-headshot-front-enabled")
	public EntityTagMatcher animalHeadshotFrontEnabled = new EntityTagMatcher();

	@SerializedName("one-shot-animals")
	public boolean oneShotAnimals = false;

	@SerializedName("one-shot-animals-enabled")
	public EntityTagMatcher oneShotAnimalsEnabled = new EntityTagMatcher();

	@SerializedName("double-damage")
	public boolean doubleDamage = false;

	@SerializedName("double-damage-enabled")
	public EntityTagMatcher doubleDamageEnabled = new EntityTagMatcher();

	/** Max blocks other players can hear gun reload audio (silent beyond). */
	@SerializedName("reload-sound-max-range")
	public double reloadSoundMaxRange = 12.0;

	private AddonConfig() {
	}

	public static AddonConfig get() {
		return INSTANCE;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("weaponsmodaddon.json");
		AddonConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, AddonConfig.class);
			} catch (IOException e) {
				WeaponsModAddon.LOGGER.warn("Failed to read {}", path, e);
			}
		}

		INSTANCE = loaded != null ? loaded : new AddonConfig();
		INSTANCE.sanitize();
		INSTANCE.save(path);
		WeaponsModAddon.LOGGER.info(
				"Gun tuning: musket {}s/{}x, flintlock {}s/{}x, scoped {}s/{}x, blunderbuss {}s/{}x, mortar {}s/{}x; "
						+ "player-headshot={}, animal-headshot-front={}, one-shot-animals={}, double-damage={} ({})",
				INSTANCE.musket.reloadSeconds, INSTANCE.musket.damageMultiplier,
				INSTANCE.flintlock.reloadSeconds, INSTANCE.flintlock.damageMultiplier,
				INSTANCE.scopedMusket.reloadSeconds, INSTANCE.scopedMusket.damageMultiplier,
				INSTANCE.blunderbuss.reloadSeconds, INSTANCE.blunderbuss.damageMultiplier,
				INSTANCE.mortar.reloadSeconds, INSTANCE.mortar.damageMultiplier,
				INSTANCE.playerHeadshot,
				INSTANCE.animalHeadshotFront,
				INSTANCE.oneShotAnimals,
				INSTANCE.doubleDamage,
				path);
	}

	private void sanitize() {
		if (musket == null) {
			musket = GunTuning.musketDefaults();
		}
		if (flintlock == null) {
			flintlock = GunTuning.flintlockDefaults();
		}
		if (scopedMusket == null) {
			scopedMusket = GunTuning.scopedDefaults();
		}
		if (blunderbuss == null) {
			blunderbuss = GunTuning.blunderbussDefaults();
		}
		if (mortar == null) {
			mortar = GunTuning.mortarDefaults();
		}
		if (headshot == null) {
			headshot = HeadshotTuning.defaults();
		}
		if (animalHeadshotFrontEnabled == null) {
			animalHeadshotFrontEnabled = new EntityTagMatcher();
		}
		if (oneShotAnimalsEnabled == null) {
			oneShotAnimalsEnabled = new EntityTagMatcher();
		}
		if (doubleDamageEnabled == null) {
			doubleDamageEnabled = new EntityTagMatcher();
		}

		// Legacy: headshot.enabled → player-headshot
		if (headshot.legacyEnabled != null) {
			playerHeadshot = headshot.legacyEnabled;
		}

		musket.sanitize();
		flintlock.sanitize();
		scopedMusket.sanitize();
		blunderbuss.sanitize();
		mortar.sanitize();
		headshot.sanitize();
		animalHeadshotFrontEnabled.sanitize();
		oneShotAnimalsEnabled.sanitize();
		doubleDamageEnabled.sanitize();
		animalHeadshotFrontFraction = Math.min(1.0, Math.max(0.05, animalHeadshotFrontFraction));
		reloadSoundMaxRange = Math.min(32.0, Math.max(2.0, reloadSoundMaxRange));
	}

	private void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
				writer.write('\n');
			}
		} catch (IOException e) {
			WeaponsModAddon.LOGGER.warn("Failed to write {}", path, e);
		}
	}

	/** Reload duration in ticks, or {@code -1} if this stack is not tuned by the addon. */
	public int reloadTicksFor(ItemStack stack) {
		GunTuning tuning = tuningFor(stack);
		return tuning == null ? -1 : tuning.reloadTicks();
	}

	/** Damage multiplier vs stock musket bullet (20). Unknown guns → 1.0. */
	public float damageMultiplierFor(@Nullable ItemStack stack) {
		GunTuning tuning = tuningFor(stack);
		return tuning == null ? 1.0f : (float) tuning.damageMultiplier;
	}

	public boolean isFlintlock(@Nullable ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(ModItemTags.PISTOLS);
	}

	@Nullable
	private GunTuning tuningFor(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		if (stack.is(ModItemTags.SCOPED_MUSKETS)) {
			return scopedMusket;
		}
		if (stack.is(ModItemTags.PISTOLS)) {
			return flintlock;
		}
		if (stack.getItem() instanceof ItemMusket) {
			return musket;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (BLUNDERBUSS_ID.equals(id)) {
			return blunderbuss;
		}
		if (MORTAR_ID.equals(id)) {
			return mortar;
		}
		return null;
	}

	public static final class GunTuning {
		/** Hold-to-reload duration in seconds (converted to ticks at 20 tps). */
		public double reloadSeconds;
		/** Multiplier on base musket bullet damage (20). */
		public double damageMultiplier;

		public GunTuning() {
		}

		public GunTuning(double reloadSeconds, double damageMultiplier) {
			this.reloadSeconds = reloadSeconds;
			this.damageMultiplier = damageMultiplier;
		}

		static GunTuning musketDefaults() {
			return new GunTuning(16.0, 1.0);
		}

		static GunTuning flintlockDefaults() {
			return new GunTuning(6.0, 0.6);
		}

		static GunTuning scopedDefaults() {
			return new GunTuning(16.0, 1.5);
		}

		static GunTuning blunderbussDefaults() {
			return new GunTuning(10.0, 1.0);
		}

		static GunTuning mortarDefaults() {
			return new GunTuning(16.0, 1.0);
		}

		void sanitize() {
			reloadSeconds = Math.max(0.05, reloadSeconds);
			damageMultiplier = Math.max(0.0, damageMultiplier);
		}

		int reloadTicks() {
			return Math.max(1, (int) Math.round(reloadSeconds * 20.0));
		}
	}

	public static final class HeadshotTuning {
		/** @deprecated use root {@link AddonConfig#playerHeadshot}; read for migration only */
		@Deprecated
		@SerializedName("enabled")
		public @Nullable Boolean legacyEnabled;

		/** Top fraction of the player AABB treated as the head (0.25 = top 25%). */
		public double topHitboxFraction = 0.25;
		/** Blindness duration on player and mob headshots. */
		public double blindnessSeconds = 15.0;
		/** Damage multiplier for arrow/gun headshots with a helmet, and melee headshots without a helmet. */
		public double damageMultiplier = 2.0;
		/** Gun headshot with no helmet → lethal (very high damage). */
		public boolean gunNoHelmetLethal = true;

		public HeadshotTuning() {
		}

		static HeadshotTuning defaults() {
			return new HeadshotTuning();
		}

		void sanitize() {
			topHitboxFraction = Math.min(1.0, Math.max(0.05, topHitboxFraction));
			blindnessSeconds = Math.max(0.0, blindnessSeconds);
			damageMultiplier = Math.max(0.0, damageMultiplier);
		}
	}
}
