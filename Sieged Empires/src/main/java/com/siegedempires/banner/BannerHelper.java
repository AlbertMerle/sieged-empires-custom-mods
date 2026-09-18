package com.siegedempires.banner;

import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.DiplomacyRecord;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BannerHelper {
	public static final String CUSTOM_DATA_KEY = "siegedempires";
	public static final String TOWN_KEY = "town";
	public static final String EMPIRE_KEY = "empire";
	public static final String TYPE_KEY = "type";
	public static final String CITIZEN_KEY = "citizen";
	public static final String PLACER_KEY = "placer";
	public static final String INVADER_REF_KEY = "invader_ref";
	public static final String INVADED_REF_KEY = "invaded_ref";
	/** 800-char hex {@link CustomBannerDesign} on item/block custom data. */
	public static final String BANNER_PIXELS_KEY = "banner_pixels";

	/**
	 * Set just before a claim-banner place attempt so the block entity can record
	 * who placed it (for inventory refunds). Cleared when consumed.
	 */
	private static final ThreadLocal<java.util.UUID> PENDING_BANNER_PLACER = new ThreadLocal<>();

	public static void setPendingBannerPlacer(java.util.UUID placerId) {
		PENDING_BANNER_PLACER.set(placerId);
	}

	public static void clearPendingBannerPlacer() {
		PENDING_BANNER_PLACER.remove();
	}

	/** Returns and clears the pending placer, or {@code null}. */
	public static java.util.UUID pollPendingBannerPlacer() {
		java.util.UUID id = PENDING_BANNER_PLACER.get();
		PENDING_BANNER_PLACER.remove();
		return id;
	}

	private static final String[] COLOR_NAMES = {
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	private static final String[] PATTERN_NAMES = {
			"square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
			"stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center",
			"stripe_middle", "stripe_downright", "stripe_downleft", "small_stripes",
			"cross", "straight_cross", "triangle_bottom", "triangle_top", "triangles_bottom",
			"triangles_top", "diagonal_left", "diagonal_right", "diagonal_up_left",
			"diagonal_up_right", "circle", "rhombus", "half_vertical", "half_horizontal",
			"half_vertical_right", "half_horizontal_bottom", "border", "curly_border",
			"creeper", "gradient", "gradient_up", "bricks", "skull", "flower", "mojang",
			"piglin", "globe", "flow", "guster"
	};

	private static final Map<String, ResourceKey<BannerPattern>> PATTERN_KEYS = new HashMap<>();
	private static final Map<ResourceKey<BannerPattern>, String> PATTERN_NAMES_BY_KEY = new HashMap<>();
	private static final Map<String, DyeColor> COLOR_MAP = new HashMap<>();

	/** Parsed town/empire banner design from an item stack (FZMM / vanilla banners). */
	public record ParsedBanner(String baseColor, List<String> patterns) {}

	static {
		registerPattern("square_bottom_left", BannerPatterns.SQUARE_BOTTOM_LEFT);
		registerPattern("square_bottom_right", BannerPatterns.SQUARE_BOTTOM_RIGHT);
		registerPattern("square_top_left", BannerPatterns.SQUARE_TOP_LEFT);
		registerPattern("square_top_right", BannerPatterns.SQUARE_TOP_RIGHT);
		registerPattern("stripe_bottom", BannerPatterns.STRIPE_BOTTOM);
		registerPattern("stripe_top", BannerPatterns.STRIPE_TOP);
		registerPattern("stripe_left", BannerPatterns.STRIPE_LEFT);
		registerPattern("stripe_right", BannerPatterns.STRIPE_RIGHT);
		registerPattern("stripe_center", BannerPatterns.STRIPE_CENTER);
		registerPattern("stripe_middle", BannerPatterns.STRIPE_MIDDLE);
		registerPattern("stripe_downright", BannerPatterns.STRIPE_DOWNRIGHT);
		registerPattern("stripe_downleft", BannerPatterns.STRIPE_DOWNLEFT);
		registerPattern("small_stripes", BannerPatterns.STRIPE_SMALL);
		registerPattern("cross", BannerPatterns.CROSS);
		registerPattern("straight_cross", BannerPatterns.STRAIGHT_CROSS);
		registerPattern("triangle_bottom", BannerPatterns.TRIANGLE_BOTTOM);
		registerPattern("triangle_top", BannerPatterns.TRIANGLE_TOP);
		registerPattern("triangles_bottom", BannerPatterns.TRIANGLES_BOTTOM);
		registerPattern("triangles_top", BannerPatterns.TRIANGLES_TOP);
		registerPattern("diagonal_left", BannerPatterns.DIAGONAL_LEFT);
		registerPattern("diagonal_right", BannerPatterns.DIAGONAL_RIGHT);
		registerPattern("diagonal_up_left", BannerPatterns.DIAGONAL_LEFT_MIRROR);
		registerPattern("diagonal_up_right", BannerPatterns.DIAGONAL_RIGHT_MIRROR);
		registerPattern("circle", BannerPatterns.CIRCLE_MIDDLE);
		registerPattern("rhombus", BannerPatterns.RHOMBUS_MIDDLE);
		registerPattern("half_vertical", BannerPatterns.HALF_VERTICAL);
		registerPattern("half_horizontal", BannerPatterns.HALF_HORIZONTAL);
		registerPattern("half_vertical_right", BannerPatterns.HALF_VERTICAL_MIRROR);
		registerPattern("half_horizontal_bottom", BannerPatterns.HALF_HORIZONTAL_MIRROR);
		registerPattern("border", BannerPatterns.BORDER);
		registerPattern("curly_border", BannerPatterns.CURLY_BORDER);
		registerPattern("creeper", BannerPatterns.CREEPER);
		registerPattern("gradient", BannerPatterns.GRADIENT);
		registerPattern("gradient_up", BannerPatterns.GRADIENT_UP);
		registerPattern("bricks", BannerPatterns.BRICKS);
		registerPattern("skull", BannerPatterns.SKULL);
		registerPattern("flower", BannerPatterns.FLOWER);
		registerPattern("mojang", BannerPatterns.MOJANG);
		registerPattern("piglin", BannerPatterns.PIGLIN);
		registerPattern("globe", BannerPatterns.GLOBE);
		registerPattern("flow", BannerPatterns.FLOW);
		registerPattern("guster", BannerPatterns.GUSTER);

		registerColor("white", DyeColor.WHITE);
		registerColor("orange", DyeColor.ORANGE);
		registerColor("magenta", DyeColor.MAGENTA);
		registerColor("light_blue", DyeColor.LIGHT_BLUE);
		registerColor("yellow", DyeColor.YELLOW);
		registerColor("lime", DyeColor.LIME);
		registerColor("pink", DyeColor.PINK);
		registerColor("gray", DyeColor.GRAY);
		registerColor("light_gray", DyeColor.LIGHT_GRAY);
		registerColor("cyan", DyeColor.CYAN);
		registerColor("purple", DyeColor.PURPLE);
		registerColor("blue", DyeColor.BLUE);
		registerColor("brown", DyeColor.BROWN);
		registerColor("green", DyeColor.GREEN);
		registerColor("red", DyeColor.RED);
		registerColor("black", DyeColor.BLACK);
	}

	private BannerHelper() {
	}

	private static void registerPattern(String name, ResourceKey<BannerPattern> key) {
		PATTERN_KEYS.put(name, key);
		PATTERN_NAMES_BY_KEY.put(key, name);
	}

	private static void registerColor(String name, DyeColor color) {
		COLOR_MAP.put(name, color);
	}

	public static String[] getColorNames() {
		return COLOR_NAMES;
	}

	public static String[] getPatternNames() {
		return PATTERN_NAMES;
	}

	/**
	 * Serialize a banner pattern key for town/empire JSON storage.
	 * Vanilla ({@code minecraft:}) patterns use the path only; mod/datapack patterns
	 * use {@code namespace:path} so they round-trip correctly.
	 */
	public static String toStorageName(ResourceKey<BannerPattern> key) {
		if (key == null) {
			return "stripe_bottom";
		}
		Identifier id = key.identifier();
		if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
			return id.getPath();
		}
		return id.toString();
	}

	/**
	 * All banner patterns available in the loom picker: known vanilla patterns first
	 * (stable order), then every other registered pattern (datapack/mod) sorted by id.
	 * Falls back to the static vanilla list when registry access is unavailable.
	 */
	public static List<String> getSelectablePatternNames(HolderGetter<BannerPattern> lookup) {
		if (!(lookup instanceof Registry<BannerPattern> registry)) {
			return List.of(PATTERN_NAMES);
		}
		List<String> result = new ArrayList<>();
		Set<ResourceKey<BannerPattern>> added = new HashSet<>();
		for (String name : PATTERN_NAMES) {
			ResourceKey<BannerPattern> key = PATTERN_KEYS.get(name);
			if (key != null && registry.get(key).isPresent()) {
				result.add(name);
				added.add(key);
			}
		}
		registry.listElements()
				.filter(holder -> !added.contains(holder.key()))
				.sorted(Comparator.comparing(holder -> holder.key().identifier()))
				.forEach(holder -> result.add(toStorageName(holder.key())));
		return result;
	}

	public static int getColorRgb(String colorName) {
		DyeColor color = COLOR_MAP.getOrDefault(colorName, DyeColor.WHITE);
		return 0xFF000000 | color.getTextureDiffuseColor();
	}

	/**
	 * Returns the dye color name that appears most often on the town flag.
	 * Prefers custom {@link CustomBannerDesign} pixels when present; otherwise
	 * base color plus each pattern layer color.
	 */
	public static String getDominantFlagColorName(TownData town) {
		if (town == null) {
			return "white";
		}
		CustomBannerDesign pixels = CustomBannerDesign.decode(town.getBannerPixels());
		if (pixels != null) {
			return pixels.dominantColorName();
		}
		Map<String, Integer> counts = new HashMap<>();
		String base = town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white";
		counts.merge(base, 1, Integer::sum);
		if (town.getBannerPatterns() != null) {
			for (String entry : town.getBannerPatterns()) {
				String[] parts = entry.split(":", 2);
				if (parts.length == 2) {
					counts.merge(parts[0], 1, Integer::sum);
				}
			}
		}
		return counts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse("white");
	}

	/**
	 * Resolve the stored custom design, or synthesize a solid fill from the base dye.
	 * Does not mutate the town.
	 */
	public static CustomBannerDesign resolveDesign(TownData town) {
		if (town == null) {
			return CustomBannerDesign.solid("white");
		}
		return CustomBannerDesign.fromStoredOrSolid(town.getBannerPixels(), town.getBannerBaseColor());
	}

	public static CustomBannerDesign resolveDesign(EmpireData empire) {
		if (empire == null) {
			return CustomBannerDesign.solid("white");
		}
		return CustomBannerDesign.fromStoredOrSolid(empire.getBannerPixels(), empire.getBannerBaseColor());
	}

	/**
	 * Ensure {@code town.bannerPixels} is populated (solid base if missing) and return it.
	 */
	public static String ensureBannerPixels(TownData town) {
		if (town == null) {
			return CustomBannerDesign.solid("white").encode();
		}
		if (CustomBannerDesign.isValidEncoded(town.getBannerPixels())) {
			return town.getBannerPixels();
		}
		String encoded = CustomBannerDesign.solid(
				town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white").encode();
		town.setBannerPixels(encoded);
		return encoded;
	}

	public static String ensureBannerPixels(EmpireData empire) {
		if (empire == null) {
			return CustomBannerDesign.solid("white").encode();
		}
		if (CustomBannerDesign.isValidEncoded(empire.getBannerPixels())) {
			return empire.getBannerPixels();
		}
		String encoded = CustomBannerDesign.solid(
				empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white").encode();
		empire.setBannerPixels(encoded);
		return encoded;
	}

	/**
	 * Snapshot the town's current flag into {@code savedBanner*} (once), then copy the
	 * empire design onto the town's active banner fields. Claim banners, map flags,
	 * Give Banner, and world banners all read the active fields — so they show empire
	 * while the town remains a member. The snapshot is restored on leave / dissolve.
	 */
	public static void preserveOwnBannerAndAdoptEmpire(TownData town, EmpireData empire) {
		if (town == null || empire == null) {
			return;
		}
		if (!town.hasSavedOwnBanner()) {
			ensureBannerPixels(town);
			List<String> patterns = town.getBannerPatterns() != null
					? new ArrayList<>(town.getBannerPatterns())
					: new ArrayList<>();
			town.setSavedBannerPatterns(patterns);
			town.setSavedBannerBaseColor(
					town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white");
			town.setSavedBannerPixels(town.getBannerPixels());
		}
		adoptEmpireBanner(town, empire);
	}

	/** Copy empire flag onto the town's active banner fields (does not touch saved*). */
	public static void adoptEmpireBanner(TownData town, EmpireData empire) {
		if (town == null || empire == null) {
			return;
		}
		ensureBannerPixels(empire);
		List<String> patterns = empire.getBannerPatterns() != null
				? new ArrayList<>(empire.getBannerPatterns())
				: new ArrayList<>();
		town.setBannerPatterns(patterns);
		town.setBannerBaseColor(
				empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white");
		town.setBannerPixels(empire.getBannerPixels());
	}

	/**
	 * Restore the town's own flag from {@code savedBanner*} and clear the snapshot.
	 * @return previous active {@code bannerPixels} before restore (for inventory sync),
	 *         or {@code null} if nothing was saved
	 */
	public static String restoreOwnBannerAfterLeavingEmpire(TownData town) {
		if (town == null || !town.hasSavedOwnBanner()) {
			return null;
		}
		String previousPixels = town.getBannerPixels();
		List<String> patterns = town.getSavedBannerPatterns() != null
				? new ArrayList<>(town.getSavedBannerPatterns())
				: new ArrayList<>();
		String base = town.getSavedBannerBaseColor() != null && !town.getSavedBannerBaseColor().isEmpty()
				? town.getSavedBannerBaseColor()
				: "white";
		String pixels = CustomBannerDesign.isValidEncoded(town.getSavedBannerPixels())
				? town.getSavedBannerPixels()
				: CustomBannerDesign.solid(base).encode();
		town.setBannerPatterns(patterns);
		town.setBannerBaseColor(base);
		town.setBannerPixels(pixels);
		town.clearSavedOwnBanner();
		return previousPixels;
	}

	public static DyeColor parseColor(String colorName) {
		return COLOR_MAP.getOrDefault(colorName, DyeColor.WHITE);
	}

	public static String getColorName(DyeColor color) {
		if (color == null) {
			return "white";
		}
		String serialized = color.getSerializedName();
		return COLOR_MAP.containsKey(serialized) ? serialized : "white";
	}

	public static ResourceKey<BannerPattern> parsePatternKey(String patternName) {
		if (patternName == null || patternName.isEmpty()) {
			return BannerPatterns.STRIPE_BOTTOM;
		}
		ResourceKey<BannerPattern> known = PATTERN_KEYS.get(patternName);
		if (known != null) {
			return known;
		}
		Identifier id = patternName.indexOf(':') >= 0
				? Identifier.tryParse(patternName)
				: Identifier.withDefaultNamespace(patternName);
		if (id != null) {
			return ResourceKey.create(Registries.BANNER_PATTERN, id);
		}
		return BannerPatterns.STRIPE_BOTTOM;
	}

	public static String getPatternName(ResourceKey<BannerPattern> key) {
		if (key == null) {
			return "stripe_bottom";
		}
		String known = PATTERN_NAMES_BY_KEY.get(key);
		if (known != null) {
			return known;
		}
		return toStorageName(key);
	}

	/**
	 * Extract SE banner storage ({@code baseColor} + {@code color:pattern} layers)
	 * from a banner or shield item stack (e.g. after FZMM editing).
	 */
	public static ParsedBanner parseBannerStack(ItemStack stack) {
		String baseColor = "white";
		List<String> patterns = new ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return new ParsedBanner(baseColor, patterns);
		}
		if (stack.getItem() instanceof BannerItem bannerItem) {
			baseColor = getColorName(bannerItem.getColor());
		}
		DyeColor baseComponent = stack.get(DataComponents.BASE_COLOR);
		if (baseComponent != null) {
			baseColor = getColorName(baseComponent);
		}
		BannerPatternLayers layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		for (BannerPatternLayers.Layer layer : layers.layers()) {
			String colorName = getColorName(layer.color());
			String patternName = layer.pattern().unwrapKey()
					.map(BannerHelper::getPatternName)
					.orElse("stripe_bottom");
			patterns.add(colorName + ":" + patternName);
		}
		return new ParsedBanner(baseColor, patterns);
	}

	public static BannerPatternLayers buildPatternLayers(List<String> patternStrings, HolderGetter<BannerPattern> patternLookup) {
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (String entry : patternStrings) {
			String[] parts = entry.split(":", 2);
			if (parts.length != 2) {
				continue;
			}
			DyeColor color = parseColor(parts[0]);
			ResourceKey<BannerPattern> patternKey = parsePatternKey(parts[1]);
			builder.addIfRegistered(patternLookup, patternKey, color);
		}
		return builder.build();
	}

	public static ItemStack createBannerStack(String baseColorName, List<String> patternStrings, HolderGetter<BannerPattern> patternLookup) {
		DyeColor baseColor = parseColor(baseColorName);
		ItemStack stack = new ItemStack(Items.BANNER.pick(baseColor));
		BannerPatternLayers layers = buildPatternLayers(patternStrings, patternLookup);
		if (!layers.equals(BannerPatternLayers.EMPTY)) {
			stack.set(DataComponents.BANNER_PATTERNS, layers);
		}
		return stack;
	}

	public static ItemStack createBannerStack(TownData town, HolderGetter<BannerPattern> patternLookup) {
		String baseColor = town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white";
		List<String> patterns = CustomBannerDesign.isValidEncoded(town.getBannerPixels())
				? List.of()
				: town.getBannerPatterns();
		ItemStack stack = createBannerStack(baseColor, patterns, patternLookup);
		setBannerPixelsOnStack(stack, town.getBannerPixels());
		return stack;
	}

	public static ItemStack createBannerStack(EmpireData empire, HolderGetter<BannerPattern> patternLookup) {
		String baseColor = empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white";
		List<String> patterns = CustomBannerDesign.isValidEncoded(empire.getBannerPixels())
				? List.of()
				: empire.getBannerPatterns();
		ItemStack stack = createBannerStack(baseColor, patterns, patternLookup);
		setBannerPixelsOnStack(stack, empire.getBannerPixels());
		return stack;
	}

	/**
	 * True for claim / war / land-tool banners that must not be wiped by
	 * {@link #convertInventoryBanners}.
	 */
	public static boolean isSpecialSiegedBanner(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return getBannerType(readSiegedEmpiresData(stack)) != null;
	}

	public static boolean isConvertibleBanner(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& stack.getItem() instanceof BannerItem
				&& !isSpecialSiegedBanner(stack);
	}

	/**
	 * Replace every convertible banner stack in the player's inventory with
	 * the given design, keeping slot and count. Returns how many items were converted.
	 * When {@code bannerPixels} is valid, stacks carry paint data (empty pattern layers)
	 * so placed world banners render the custom cloth.
	 */
	public static int convertInventoryBanners(ServerPlayer player, String baseColorName,
											  List<String> patternStrings) {
		return convertInventoryBanners(player, baseColorName, patternStrings, null);
	}

	public static int convertInventoryBanners(ServerPlayer player, String baseColorName,
											  List<String> patternStrings, String bannerPixels) {
		return convertInventoryBanners(player, baseColorName, patternStrings, bannerPixels, null, null);
	}

	/**
	 * Convert plain inventory banners to the faction design and tag them with
	 * town/empire ids (no {@link BannerType}) so a later name/banner edit can
	 * refresh every SE-menu-issued decorative banner.
	 */
	public static int convertInventoryBanners(ServerPlayer player, String baseColorName,
											  List<String> patternStrings, String bannerPixels,
											  String townId, String empireId) {
		if (player == null) {
			return 0;
		}
		HolderGetter<BannerPattern> patterns = player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		boolean usePixels = CustomBannerDesign.isValidEncoded(bannerPixels);
		List<String> layers = usePixels ? List.of() : patternStrings;
		int converted = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!isConvertibleBanner(stack)) {
				continue;
			}
			int count = stack.getCount();
			ItemStack replacement = createBannerStack(baseColorName, layers, patterns);
			if (usePixels) {
				setBannerPixelsOnStack(replacement, bannerPixels);
			}
			if ((townId != null && !townId.isEmpty()) || (empireId != null && !empireId.isEmpty())) {
				tagFactionDisplayBanner(replacement, townId, empireId);
			}
			replacement.setCount(count);
			inventory.setItem(slot, replacement);
			converted += count;
		}
		return converted;
	}

	/**
	 * Tag a decorative SE menu banner with town/empire ownership without a
	 * placement {@link BannerType} (so it is not treated as claim/war/etc.).
	 */
	public static void tagFactionDisplayBanner(ItemStack stack, String townId, String empireId) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null) {
			data = new CompoundTag();
		}
		if (townId != null && !townId.isEmpty()) {
			data.putString(TOWN_KEY, townId);
		}
		if (empireId != null && !empireId.isEmpty()) {
			data.putString(EMPIRE_KEY, empireId);
		}
		writeSiegedEmpiresData(stack, data);
	}

	public static void setBannerMetadata(ItemStack stack, String townId, String empireId, BannerType type) {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString(TOWN_KEY, townId != null ? townId : "");
		data.putString(EMPIRE_KEY, empireId != null ? empireId : "");
		data.putString(TYPE_KEY, type != null ? type.getId() : "");
		root.put(CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	/**
	 * Attach or clear freeform {@code banner_pixels} on a banner stack.
	 * Creates the {@code siegedempires} custom-data compound when missing so
	 * converted/decorative banners can carry paint without claim metadata.
	 */
	public static void setBannerPixelsOnStack(ItemStack stack, String bannerPixels) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null) {
			data = new CompoundTag();
		}
		if (CustomBannerDesign.isValidEncoded(bannerPixels)) {
			data.putString(BANNER_PIXELS_KEY, bannerPixels);
		} else {
			data.remove(BANNER_PIXELS_KEY);
			if (data.isEmpty()) {
				stack.remove(DataComponents.CUSTOM_DATA);
				return;
			}
		}
		writeSiegedEmpiresData(stack, data);
	}

	public static String getBannerPixels(CompoundTag data) {
		if (data == null || !data.contains(BANNER_PIXELS_KEY)) {
			return null;
		}
		String pixels = data.getString(BANNER_PIXELS_KEY).orElse("");
		return CustomBannerDesign.isValidEncoded(pixels) ? pixels : null;
	}

	public static String getBannerPixelsFromStack(ItemStack stack) {
		return getBannerPixels(readSiegedEmpiresData(stack));
	}

	/**
	 * Same as {@link #setBannerMetadata} but also stores the citizen UUID
	 * that the {@link BannerType#CITIZEN_GIVE_LAND} banner will grant
	 * autonomy to, plus the placer UUID for feedback messages.
	 */
	public static void setCitizenGiveLandMetadata(ItemStack stack, String townId, String empireId,
	                                              String citizenUuid, String placerUuid) {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString(TOWN_KEY, townId != null ? townId : "");
		data.putString(EMPIRE_KEY, empireId != null ? empireId : "");
		data.putString(TYPE_KEY, BannerType.CITIZEN_GIVE_LAND.getId());
		data.putString(CITIZEN_KEY, citizenUuid != null ? citizenUuid : "");
		data.putString(PLACER_KEY, placerUuid != null ? placerUuid : "");
		root.put(CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	/** @return the citizen UUID stored on a CITIZEN_GIVE_LAND banner, or {@code null}. */
	public static String getCitizenId(CompoundTag data) {
		if (data == null || !data.contains(CITIZEN_KEY)) {
			return null;
		}
		String id = data.getString(CITIZEN_KEY).orElse("");
		return id.isEmpty() ? null : id;
	}

	/** @return the citizen UUID stored on the stack, or {@code null}. */
	public static String getCitizenIdFromStack(ItemStack stack) {
		return getCitizenId(readSiegedEmpiresData(stack));
	}

	/**
	 * Build the metadata for a Land Revoke banner: stores town, empire, and
	 * the placer's UUID so {@link BannerManager} can DM the placer after the
	 * revocation runs. No target player is recorded on the banner.
	 */
	public static void setLandRevokeMetadata(ItemStack stack, String townId, String empireId, String placerUuid) {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString(TOWN_KEY, townId != null ? townId : "");
		data.putString(EMPIRE_KEY, empireId != null ? empireId : "");
		data.putString(TYPE_KEY, BannerType.LAND_REVOKE.getId());
		data.putString(PLACER_KEY, placerUuid != null ? placerUuid : "");
		root.put(CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	/** @return the placer UUID stored on a LAND_REVOKE banner, or {@code null}. */
	public static String getPlacerId(CompoundTag data) {
		if (data == null || !data.contains(PLACER_KEY)) {
			return null;
		}
		String id = data.getString(PLACER_KEY).orElse("");
		return id.isEmpty() ? null : id;
	}

	/** @return the placer UUID stored on the stack, or {@code null}. */
	public static String getPlacerIdFromStack(ItemStack stack) {
		return getPlacerId(readSiegedEmpiresData(stack));
	}

	public static CompoundTag readSiegedEmpiresData(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return null;
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(CUSTOM_DATA_KEY)) {
			return null;
		}
		return root.getCompound(CUSTOM_DATA_KEY).orElse(null);
	}

	public static void writeSiegedEmpiresData(ItemStack stack, CompoundTag data) {
		CompoundTag root = new CompoundTag();
		root.put(CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	public static String getTownId(CompoundTag data) {
		if (data == null || !data.contains(TOWN_KEY)) {
			return null;
		}
		String townId = data.getString(TOWN_KEY).orElse("");
		return townId.isEmpty() ? null : townId;
	}

	public static String getEmpireId(CompoundTag data) {
		if (data == null || !data.contains(EMPIRE_KEY)) {
			return null;
		}
		String empireId = data.getString(EMPIRE_KEY).orElse("");
		return empireId.isEmpty() ? null : empireId;
	}

	/**
	 * Refresh the visual design on an inventory banner that belongs to {@code town},
	 * preserving claim/war/etc. metadata and custom names.
	 */
	public static ItemStack applyTownDesignToItem(ItemStack stack, TownData town,
												  HolderGetter<BannerPattern> patternLookup) {
		if (stack == null || stack.isEmpty() || town == null || !(stack.getItem() instanceof BannerItem)) {
			return stack;
		}
		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null || !town.getId().equals(getTownId(data))) {
			return stack;
		}
		return replaceItemDesign(stack, town.getBannerBaseColor(), town.getBannerPatterns(),
				town.getBannerPixels(), patternLookup);
	}

	/**
	 * Refresh inventory banners tagged with an empire id (decorative Give Banner stacks).
	 */
	public static ItemStack applyEmpireDesignToItem(ItemStack stack, EmpireData empire,
													HolderGetter<BannerPattern> patternLookup) {
		if (stack == null || stack.isEmpty() || empire == null || !(stack.getItem() instanceof BannerItem)) {
			return stack;
		}
		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null || !empire.getId().equals(getEmpireId(data))) {
			return stack;
		}
		// Claim / land tools stay town-linked; still refresh their cloth from empire when tagged.
		return replaceItemDesign(stack, empire.getBannerBaseColor(), empire.getBannerPatterns(),
				empire.getBannerPixels(), patternLookup);
	}

	/**
	 * Legacy Give Banner stacks may only carry matching {@code banner_pixels} with no town id.
	 */
	public static ItemStack applyDesignIfMatchingPixels(ItemStack stack, String oldPixels,
														String baseColor, List<String> patterns,
														String newPixels,
														HolderGetter<BannerPattern> patternLookup) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BannerItem)) {
			return stack;
		}
		if (!CustomBannerDesign.isValidEncoded(oldPixels)) {
			return stack;
		}
		String current = getBannerPixelsFromStack(stack);
		if (!oldPixels.equals(current)) {
			return stack;
		}
		// Skip special typed banners unless they already matched pixels (claim banners etc.).
		return replaceItemDesign(stack, baseColor, patterns, newPixels, patternLookup);
	}

	private static ItemStack replaceItemDesign(ItemStack stack, String baseColorName,
											   List<String> patternStrings, String bannerPixels,
											   HolderGetter<BannerPattern> patternLookup) {
		CompoundTag preserved = readSiegedEmpiresData(stack);
		var customName = stack.get(DataComponents.CUSTOM_NAME);
		int count = stack.getCount();
		boolean usePixels = CustomBannerDesign.isValidEncoded(bannerPixels);
		List<String> layers = usePixels ? List.of() : (patternStrings != null ? patternStrings : List.of());
		String base = baseColorName != null && !baseColorName.isEmpty() ? baseColorName : "white";
		ItemStack replacement = createBannerStack(base, layers, patternLookup);
		if (usePixels) {
			setBannerPixelsOnStack(replacement, bannerPixels);
		}
		if (preserved != null) {
			CompoundTag copy = preserved.copy();
			if (usePixels) {
				copy.putString(BANNER_PIXELS_KEY, bannerPixels);
			} else {
				copy.remove(BANNER_PIXELS_KEY);
			}
			writeSiegedEmpiresData(replacement, copy);
		}
		if (customName != null) {
			replacement.set(DataComponents.CUSTOM_NAME, customName);
		}
		replacement.setCount(count);
		return replacement;
	}

	public static BannerType getBannerType(CompoundTag data) {
		if (data == null || !data.contains(TYPE_KEY)) {
			return null;
		}
		return BannerType.fromId(data.getString(TYPE_KEY).orElse(""));
	}

	public static void applyTownDesign(BannerBlockEntity blockEntity, TownData town, ServerLevel level) {
		HolderGetter<BannerPattern> patternLookup = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		BannerBlockEntityAccess access = (BannerBlockEntityAccess) blockEntity;
		access.siegedempires$setTownId(town.getId());
		access.siegedempires$setFreshlyPlaced(false);

		boolean usePixels = CustomBannerDesign.isValidEncoded(town.getBannerPixels());
		// Prefer freeform pixels (paint UI). Vanilla pattern layers only when pixels absent.
		// Do NOT swap the banner block colour when using pixels — setBlock would recreate
		// the BE and wipe SiegedEmpiresBannerPixels / town id.
		if (usePixels) {
			access.siegedempires$setBannerPixels(town.getBannerPixels());
			access.siegedempires$setPatterns(BannerPatternLayers.EMPTY);
		} else {
			access.siegedempires$setBannerPixels(null);
			access.siegedempires$setPatterns(buildPatternLayers(town.getBannerPatterns(), patternLookup));
			DyeColor desiredBaseColor = parseColor(
					town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white");
			if (blockEntity.getBaseColor() != desiredBaseColor) {
				var pos = blockEntity.getBlockPos();
				replaceBannerBaseColor(blockEntity, level, desiredBaseColor);
				if (level.getBlockEntity(pos) instanceof BannerBlockEntity replacement) {
					blockEntity = replacement;
					access = (BannerBlockEntityAccess) blockEntity;
					access.siegedempires$setTownId(town.getId());
					access.siegedempires$setFreshlyPlaced(false);
					access.siegedempires$setPatterns(buildPatternLayers(town.getBannerPatterns(), patternLookup));
				}
			}
		}

		blockEntity.setChanged();
		BlockState state = blockEntity.getBlockState();
		level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_ALL);
		// NBT-only updates still need an explicit BE packet so already-loaded clients
		// receive SiegedEmpiresBannerPixels (chunk blockChanged alone can race).
		var packet = blockEntity.getUpdatePacket();
		if (packet != null) {
			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().closerThan(blockEntity.getBlockPos(), 128.0)) {
					player.connection.send(packet);
				}
			}
		}
	}

	public static void resetToVanillaWhiteBanner(BannerBlockEntity blockEntity, ServerLevel level) {
		BannerBlockEntityAccess access = (BannerBlockEntityAccess) blockEntity;
		access.siegedempires$setPatterns(BannerPatternLayers.EMPTY);
		access.siegedempires$setBannerPixels(null);
		access.siegedempires$setTownId(null);
		access.siegedempires$setBannerType(null);
		access.siegedempires$setFreshlyPlaced(false);
		replaceBannerBaseColor(blockEntity, level, DyeColor.WHITE);
		blockEntity.setChanged();
		level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_ALL);
	}

	public static ItemStack createVanillaWhiteBannerStack(int count) {
		ItemStack stack = new ItemStack(Items.BANNER.pick(DyeColor.WHITE));
		stack.setCount(count);
		return stack;
	}

	public static ItemStack resetItemIfTownBanner(ItemStack stack, String townId) {
		if (stack.isEmpty() || townId == null) {
			return stack;
		}

		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null) {
			return stack;
		}

		String itemTownId = getTownId(data);
		if (!townId.equals(itemTownId)) {
			return stack;
		}

		return createVanillaWhiteBannerStack(stack.getCount());
	}

	public static ItemStack migrateItemTownId(ItemStack stack, String oldTownId, String newTownId) {
		if (stack.isEmpty() || oldTownId == null || newTownId == null || oldTownId.equals(newTownId)) {
			return stack;
		}

		CompoundTag data = readSiegedEmpiresData(stack);
		if (data == null) {
			return stack;
		}

		String itemTownId = getTownId(data);
		if (!oldTownId.equals(itemTownId)) {
			return stack;
		}

		ItemStack copy = stack.copy();
		CompoundTag copyData = readSiegedEmpiresData(copy);
		if (copyData != null) {
			copyData.putString(TOWN_KEY, newTownId);
			writeSiegedEmpiresData(copy, copyData);
		}
		return copy;
	}

	public static boolean hasTownMetadata(ItemStack stack) {
		return readSiegedEmpiresData(stack) != null && getTownId(readSiegedEmpiresData(stack)) != null;
	}

	private static void replaceBannerBaseColor(BannerBlockEntity blockEntity, ServerLevel level, DyeColor color) {
		BlockState currentState = blockEntity.getBlockState();
		Block currentBlock = currentState.getBlock();
		BlockState newState;

		if (currentBlock instanceof WallBannerBlock) {
			newState = Blocks.WALL_BANNER.pick(color).defaultBlockState()
					.setValue(WallBannerBlock.FACING, currentState.getValue(WallBannerBlock.FACING));
		} else if (currentBlock instanceof AbstractBannerBlock) {
			newState = Blocks.BANNER.pick(color).defaultBlockState()
					.setValue(net.minecraft.world.level.block.BannerBlock.ROTATION, currentState.getValue(net.minecraft.world.level.block.BannerBlock.ROTATION));
		} else {
			return;
		}

		level.setBlock(blockEntity.getBlockPos(), newState, Block.UPDATE_ALL);
	}

	public static boolean isBannerBlock(Block block) {
		return block instanceof AbstractBannerBlock;
	}

	public static final Component CLAIM_BANNER_NAME = Component.literal("CLAIM BANNER").withColor(0x55FF55);

	public static final Component WAR_BANNER_NAME = Component.literal("WAR BANNER!")
			.withColor(0xFF5555)
			.withStyle(net.minecraft.ChatFormatting.BOLD);

	public static ItemStack createClaimBannerItem(TownData town, HolderGetter<BannerPattern> patternLookup) {
		ItemStack stack = createBannerStack(town, patternLookup);
		setBannerMetadata(stack, town.getId(), town.getEmpireId(), BannerType.CLAIM);
		setBannerPixelsOnStack(stack, town.getBannerPixels());
		stack.set(DataComponents.CUSTOM_NAME, CLAIM_BANNER_NAME);
		return stack;
	}

	/**
	 * Build the CitizenGiveLandBanner item that a Lord or Monarch receives
	 * when they confirm the "Give Citizen Land" flow.
	 *
	 * <p>The banner carries the town design (base color + patterns) so it
	 * matches the rest of the town, and is tagged with the town ID, empire
	 * ID, and citizen UUID via {@link #setCitizenGiveLandMetadata}. The
	 * item name is set to "{citizenName} Land Claim" per project spec.
	 */
	public static ItemStack createCitizenGiveLandBannerItem(TownData town, java.util.UUID citizenUuid,
														   String citizenName, java.util.UUID placerUuid,
														   HolderGetter<BannerPattern> patternLookup) {
		ItemStack stack = createBannerStack(town, patternLookup);
		setCitizenGiveLandMetadata(stack, town.getId(), town.getEmpireId(),
			citizenUuid != null ? citizenUuid.toString() : "",
			placerUuid != null ? placerUuid.toString() : "");
		setBannerPixelsOnStack(stack, town.getBannerPixels());
		stack.set(DataComponents.CUSTOM_NAME,
			Component.literal((citizenName == null ? "Citizen" : citizenName) + " Land Claim")
				.withColor(0xFF55));
		return stack;
	}

	/**
	 * Build the Land Revoke Banner that a Lord or Monarch receives when
	 * they confirm the "Create Land Revoke Banner" flow.
	 *
	 * <p>Per project spec the banner does NOT record a target player. It
	 * stores only the town ID, empire ID, and the placer's UUID (the placer
	 * UUID lets {@link BannerManager} send a private chat message back to
	 * the placer reporting which chunk was revoked and who owned it).
	 *
	 * <p>The item is named "Land Revoke Banner" in purple (0xAA00AA).
	 */
	public static ItemStack createLandRevokeBannerItem(TownData town, java.util.UUID placerUuid,
													   HolderGetter<BannerPattern> patternLookup) {
		ItemStack stack = createBannerStack(town, patternLookup);
		setLandRevokeMetadata(stack, town.getId(), town.getEmpireId(),
			placerUuid != null ? placerUuid.toString() : "");
		setBannerPixelsOnStack(stack, town.getBannerPixels());
		stack.set(DataComponents.CUSTOM_NAME,
			Component.literal("Land Revoke Banner").withColor(0xAA00AA));
		return stack;
	}

	/** Light-blue item name color for Restricted Land banners (matches GUI button). */
	public static final int RESTRICTED_BANNER_NAME_COLOR = 0x55FFFF;

	/**
	 * Plain light-blue banner that marks a claimed town chunk as a Restricted Zone
	 * when placed. Must be used on a chunk the town already owns.
	 */
	public static ItemStack createRestrictedLandBannerItem(TownData town,
														   HolderGetter<BannerPattern> patternLookup) {
		ItemStack stack = createBannerStack("light_blue", List.of(), patternLookup);
		setBannerMetadata(stack, town.getId(), town.getEmpireId(), BannerType.RESTRICTED);
		stack.set(DataComponents.CUSTOM_NAME,
			Component.literal("RESTRICTED LAND BANNER").withColor(RESTRICTED_BANNER_NAME_COLOR));
		return stack;
	}

	public static ItemStack createWarBannerItem(com.siegedempires.diplomacy.DiplomacyActions.ManagedEntity invader,
	                                            com.siegedempires.diplomacy.DiplomacyActions.ManagedEntity invaded,
	                                            HolderGetter<BannerPattern> patternLookup) {
		ItemStack stack;
		if (DiplomacyRecord.TYPE_EMPIRE.equals(invader.entityType())) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(invader.entityId());
			if (empire != null) {
				stack = createBannerStack(
						empire.getBannerBaseColor() != null ? empire.getBannerBaseColor() : "white",
						empire.getBannerPatterns(), patternLookup);
			} else {
				stack = createVanillaWhiteBannerStack(1);
			}
		} else {
			TownData town = TownDataManager.getInstance().getTown(invader.entityId());
			stack = town != null ? createBannerStack(town, patternLookup) : createVanillaWhiteBannerStack(1);
		}
		setWarBannerMetadata(stack, invader.ref(), invaded.ref());
		if (DiplomacyRecord.TYPE_EMPIRE.equals(invader.entityType())) {
			EmpireData empire = EmpireDataManager.getInstance().getEmpire(invader.entityId());
			if (empire != null) {
				setBannerPixelsOnStack(stack, empire.getBannerPixels());
			}
		} else {
			TownData town = TownDataManager.getInstance().getTown(invader.entityId());
			if (town != null) {
				setBannerPixelsOnStack(stack, town.getBannerPixels());
			}
		}
		stack.set(DataComponents.CUSTOM_NAME, WAR_BANNER_NAME);
		return stack;
	}

	public static void setWarBannerMetadata(ItemStack stack, String invaderRef, String invadedRef) {
		CompoundTag root = new CompoundTag();
		CompoundTag data = new CompoundTag();
		data.putString(TYPE_KEY, BannerType.WAR.getId());
		data.putString(INVADER_REF_KEY, invaderRef != null ? invaderRef : "");
		data.putString(INVADED_REF_KEY, invadedRef != null ? invadedRef : "");
		root.put(CUSTOM_DATA_KEY, data);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, root);
	}

	public static boolean isWarBanner(ItemStack stack) {
		CompoundTag data = readSiegedEmpiresData(stack);
		BannerType type = getBannerType(data);
		return type == BannerType.WAR;
	}

	public static boolean isClaimBanner(ItemStack stack) {
		CompoundTag data = readSiegedEmpiresData(stack);
		BannerType type = getBannerType(data);
		return type == BannerType.CLAIM;
	}

	public static boolean isWarBannerType(String typeId) {
		return BannerType.WAR.getId().equals(typeId);
	}

	public static boolean isWarBannerFor(ItemStack stack, String invaderRef, String invadedRef) {
		if (!isWarBanner(stack)) {
			return false;
		}
		String stackInvader = getInvaderRefFromStack(stack);
		String stackInvaded = getInvadedRefFromStack(stack);
		return invaderRef.equals(stackInvader) && invadedRef.equals(stackInvaded);
	}

	public static String getInvaderRef(CompoundTag data) {
		if (data == null || !data.contains(INVADER_REF_KEY)) {
			return null;
		}
		String ref = data.getString(INVADER_REF_KEY).orElse("");
		return ref.isEmpty() ? null : ref;
	}

	public static String getInvadedRef(CompoundTag data) {
		if (data == null || !data.contains(INVADED_REF_KEY)) {
			return null;
		}
		String ref = data.getString(INVADED_REF_KEY).orElse("");
		return ref.isEmpty() ? null : ref;
	}

	public static String getInvaderRefFromStack(ItemStack stack) {
		return getInvaderRef(readSiegedEmpiresData(stack));
	}

	public static String getInvadedRefFromStack(ItemStack stack) {
		return getInvadedRef(readSiegedEmpiresData(stack));
	}

	public interface BannerBlockEntityAccess {
		void siegedempires$setPatterns(BannerPatternLayers patterns);

		String siegedempires$getTownId();

		void siegedempires$setTownId(String townId);

		String siegedempires$getBannerType();

		void siegedempires$setBannerType(String bannerType);

		boolean siegedempires$isFreshlyPlaced();

		void siegedempires$setFreshlyPlaced(boolean freshlyPlaced);

		/** @return citizen UUID stored on a CITIZEN_GIVE_LAND banner, or {@code null}. */
		String siegedempires$getCitizenId();

		void siegedempires$setCitizenId(String citizenId);

		/** @return placer UUID stored on a LAND_REVOKE banner, or {@code null}. */
		String siegedempires$getPlacerId();

		void siegedempires$setPlacerId(String placerId);

		String siegedempires$getInvaderRef();

		void siegedempires$setInvaderRef(String invaderRef);

		String siegedempires$getInvadedRef();

		void siegedempires$setInvadedRef(String invadedRef);

		/** Freeform 20×40 dye grid (800-char hex), or {@code null}. */
		String siegedempires$getBannerPixels();

		void siegedempires$setBannerPixels(String bannerPixels);
	}
}
