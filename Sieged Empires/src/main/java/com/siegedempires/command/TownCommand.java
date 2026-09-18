package com.siegedempires.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.TownData;
import com.siegedempires.util.CantAffordFeedback;
import com.siegedempires.util.ChatAnnouncements;
import com.siegedempires.util.FactionEditCost;
import com.siegedempires.util.InventoryHelper;
import com.siegedempires.util.NameValidator;
import com.siegedempires.util.PlayerPrefixManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TownCommand {
	
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("town")
				.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.string())
								.then(Commands.argument("description", StringArgumentType.string())
										.then(Commands.argument("bannerData", StringArgumentType.greedyString())
												.executes(TownCommand::createTown)))))
				.then(Commands.literal("info")
						.executes(TownCommand::showInfo))
				.then(Commands.literal("join")
						.then(Commands.argument("name", StringArgumentType.string())
								.executes(TownCommand::joinTown)))
				.then(Commands.literal("leave")
						.executes(TownCommand::leaveTown))
				.then(Commands.literal("invite")
						.then(Commands.argument("player", StringArgumentType.string())
								.executes(TownCommand::invitePlayer)))
				.then(Commands.literal("givecitizenland")
					.then(Commands.argument("player", StringArgumentType.string())
							.executes(TownCommand::giveCitizenLandBanner)))
				.then(Commands.literal("revokelandall")
					.then(Commands.argument("player", StringArgumentType.string())
							.executes(TownCommand::revokeAllLand)))
				.then(Commands.literal("revokelandbanner")
					.executes(TownCommand::giveLandRevokeBanner))
				.then(Commands.literal("evict")
						.then(Commands.argument("player", StringArgumentType.string())
								.executes(TownCommand::evictPlayer)))
				.then(Commands.literal("setpublic")
						.then(Commands.argument("value", BoolArgumentType.bool())
								.executes(TownCommand::setPublic)))
				.then(Commands.literal("togglepublic")
						.executes(TownCommand::togglePublic))
				.then(Commands.literal("trust")
						.then(Commands.argument("player", StringArgumentType.string())
								.executes(TownCommand::trustCitizen)))
				.then(Commands.literal("makelord")
						.then(Commands.argument("player", StringArgumentType.string())
								.executes(TownCommand::makeLord)))
				.then(Commands.literal("stepdown")
						.then(Commands.argument("player", StringArgumentType.string())
								.executes(TownCommand::stepDown)))
				.then(Commands.literal("buyclaimbanner")
						.executes(ctx -> buyClaimBanner(ctx, 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
								.executes(ctx -> buyClaimBanner(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
				.then(Commands.literal("makerestrictedland")
						.executes(TownCommand::makeRestrictedLandBanner))
				.then(Commands.literal("delete")
						.executes(TownCommand::deleteTown)));
	}
	
	private static int createTown(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can create towns!"));
			return 0;
		}
		
		String townName = StringArgumentType.getString(context, "name").trim().replace("_", " ");
		String description = StringArgumentType.getString(context, "description").replace("_", " ");
		String bannerData = StringArgumentType.getString(context, "bannerData");

		String[] parts = bannerData.split(";", -1);
		if (parts.length < 1 || parts[0].isEmpty()) {
			source.sendFailure(Component.literal("Invalid banner data!"));
			return 0;
		}

		String monarchTitle = parts[0];
		String baseColor = parts.length > 1 ? parts[1] : "white";
		List<String> patterns = new ArrayList<>();
		for (int i = 2; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				patterns.add(parts[i]);
			}
		}

		String error = tryCreateTown(player, townName, description, monarchTitle, baseColor, patterns);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	public static String tryCreateTown(ServerPlayer player, String townName, String description,
			String monarchTitle, String baseColor, List<String> patterns) {
		return tryCreateTown(player, townName, description, monarchTitle, baseColor, patterns, null);
	}

	/**
	 * Shared town creation used by {@code /town create} and the GUI network payload.
	 * @return error message, or null on success
	 */
	public static String tryCreateTown(ServerPlayer player, String townName, String description,
			String monarchTitle, String baseColor, List<String> patterns, String bannerPixels) {
		if (!monarchTitle.equals("King") && !monarchTitle.equals("Queen")) {
			return "Monarch title must be 'King' or 'Queen'!";
		}
		
		if (TownDataManager.getInstance().hasTown(player.getUUID())) {
			return "You are already in a town!";
		}
		
		String validationError = NameValidator.getValidationError(townName);
		if (validationError != null) {
			return validationError;
		}
		
		if (TownDataManager.getInstance().townNameExists(townName)) {
			return "A town with this name already exists!";
		}
		
		int createCost = com.siegedempires.config.ModSettings.get().createTownCost;
		if (!InventoryHelper.hasEnoughGold(player, createCost)) {
			CantAffordFeedback.playSound(player);
			return "Can't Afford This!";
		}
		
		InventoryHelper.removeGold(player, createCost);
		
		TownData town = TownDataManager.getInstance().createTown(
				townName.trim(),
				player.getUUID(),
				player.getName().getString()
		);
		
		if (town == null) {
			return "Failed to create town!";
		}

		town.setMonarchTitle(monarchTitle);

		String desc = description == null ? "" : description.trim();
		if (!desc.isEmpty() && !desc.equalsIgnoreCase("null") && !desc.equalsIgnoreCase("none")) {
			town.setDescription(desc);
		}

		String color = baseColor == null || baseColor.isEmpty() ? "white" : baseColor;
		town.setBannerBaseColor(color);
		List<String> patternList = new ArrayList<>();
		if (patterns != null) {
			for (String p : patterns) {
				if (p != null && !p.isEmpty()) {
					patternList.add(p);
				}
			}
		}
		town.setBannerPatterns(patternList);
		if (com.siegedempires.banner.CustomBannerDesign.isValidEncoded(bannerPixels)) {
			town.setBannerPixels(bannerPixels);
		} else {
			com.siegedempires.banner.BannerHelper.ensureBannerPixels(town);
		}

		TownDataManager.getInstance().saveTown(town);
		giveClaimBanners(player, town);
		PlayerPrefixManager.refresh(player);

		String type = town.isNation() ? "Nation" : "Town";
		ChatAnnouncements.townOrNationCreated(player, type, town.getName());
		return null;
	}

	public static final int RENAME_COST = FactionEditCost.BASE_COST;

	/**
	 * Shared town rename used by the Manage Town GUI network payload.
	 * @return error message, or null on success
	 */
	public static String tryRenameTown(ServerPlayer player, String newName) {
		return tryUpdateTown(player, newName, null, null, null, true);
	}

	/**
	 * Update town name and banner; charges {@link FactionEditCost}.
	 * @param nameOnly when true, keep existing banner (legacy rename payload)
	 */
	public static String tryUpdateTown(ServerPlayer player, String newName,
			String bannerBaseColor, List<String> bannerPatterns, String bannerPixels,
			boolean nameOnly) {
		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			return "Only the Monarch can edit the town!";
		}

		int cost = FactionEditCost.costForTown(town);
		if (!InventoryHelper.hasEnoughGold(player, cost)) {
			CantAffordFeedback.playSound(player);
			return "Can't Afford This!";
		}

		String error;
		if (nameOnly) {
			error = TownDataManager.getInstance().renameTown(player, newName);
		} else {
			String base = bannerBaseColor;
			List<String> patterns = bannerPatterns;
			String pixels = bannerPixels;
			if (base == null || base.isEmpty()) {
				base = town.getBannerBaseColor() != null ? town.getBannerBaseColor() : "white";
			}
			if (patterns == null) {
				patterns = town.getBannerPatterns();
			}
			if (pixels == null || pixels.isEmpty()) {
				pixels = town.getBannerPixels();
			}
			error = TownDataManager.getInstance().updateTownNameAndBanner(
					player, newName, base, patterns, pixels);
		}
		if (error != null) {
			return error;
		}

		InventoryHelper.removeGold(player, cost);
		return null;
	}
	
	private static void giveClaimBanners(ServerPlayer player, TownData town) {
		var patternLookup = player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack banner = BannerHelper.createClaimBannerItem(town, patternLookup);
		int remaining = 18;
		while (remaining > 0) {
			ItemStack stack = banner.copy();
			int count = Math.min(remaining, stack.getMaxStackSize());
			stack.setCount(count);
			remaining -= count;
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
	}

	/** Gold bars (or coin equivalent) charged per claim banner. */
	public static final int CLAIM_BANNER_COST_PER = 1;
	private static final int RESTRICTED_LAND_COST = 2;

	private static int buyClaimBanner(CommandContext<CommandSourceStack> context, int count) {
		CommandSourceStack source = context.getSource();
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can buy claim banners!"));
			return 0;
		}

		int amount = Math.max(1, Math.min(16, count));
		TownData town = TownDataManager.getInstance().getManageableTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("You are not in a town!"));
			return 0;
		}

		boolean isMonarch = town.getMonarchUuid().equals(player.getUUID());
		boolean isLord = town.isLord(player.getUUID());
		if (!isMonarch && !isLord) {
			source.sendFailure(Component.literal("Only the monarch or a lord can buy claim banners!"));
			return 0;
		}

		int totalCost = amount * CLAIM_BANNER_COST_PER;
		if (!InventoryHelper.hasEnoughGold(player, totalCost)) {
			CantAffordFeedback.playSound(player);
			source.sendFailure(Component.literal("Can't Afford This!"));
			return 0;
		}

		InventoryHelper.removeGold(player, totalCost);
		var patternLookup = player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack template = BannerHelper.createClaimBannerItem(town, patternLookup);
		int remaining = amount;
		while (remaining > 0) {
			ItemStack stack = template.copy();
			int stackCount = Math.min(remaining, stack.getMaxStackSize());
			stack.setCount(stackCount);
			remaining -= stackCount;
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		return amount;
	}

	private static int makeRestrictedLandBanner(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can create Restricted Land banners!"));
			return 0;
		}

		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can create Restricted Land banners!"));
			return 0;
		}

		if (!InventoryHelper.hasEnoughGold(player, RESTRICTED_LAND_COST)) {
			CantAffordFeedback.playSound(player);
			source.sendFailure(Component.literal("Can't Afford This!"));
			return 0;
		}

		InventoryHelper.removeGold(player, RESTRICTED_LAND_COST);
		var patternLookup = player.level().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		ItemStack banner = BannerHelper.createRestrictedLandBannerItem(town, patternLookup);
		banner.setCount(1);
		if (!player.getInventory().add(banner)) {
			player.drop(banner, false);
		}
		return 1;
	}

	private static int showInfo(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can use this command!"));
			return 0;
		}
		
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		
		if (town == null) {
			source.sendFailure(Component.literal("You are not in a town!"));
			return 0;
		}
		
		String type = town.isNation() ? "Nation" : "Town";
		source.sendSuccess(() -> Component.literal(
			"=== " + type + " of " + town.getName() + " ===\n" +
			"Description: " + town.getDescription() + "\n" +
			"Monarch: " + town.getMonarchName() + "\n" +
			"Chunks: " + town.getChunkCount() + "/100" + (town.isNation() ? " (Nation)" : "") + "\n" +
			"Members: " + town.getMembers().size()
		), false);

		return 1;
	}
	
	private static int joinTown(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can join towns!"));
			return 0;
		}

		String townName = StringArgumentType.getString(context, "name").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getTownByName(townName);

		if (town == null) {
			source.sendFailure(Component.literal("Town not found!"));
			return 0;
		}

		if (!TownDataManager.getInstance().canJoinTown(player.getUUID(), town)) {
			source.sendFailure(Component.literal(
					TownDataManager.getInstance().getJoinFailureMessage(player.getUUID(), town)));
			return 0;
		}

		if (TownDataManager.getInstance().joinTown(player, townName)) {
			return 1;
		}

		source.sendFailure(Component.literal("Failed to join town!"));
		return 0;
	}

	private static int leaveTown(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can use this command!"));
			return 0;
		}
		
		TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
		
		if (town == null) {
			source.sendFailure(Component.literal("You are not in a town!"));
			return 0;
		}
		
		if (town.getMonarchUuid().equals(player.getUUID())) {
			source.sendFailure(Component.literal("You cannot leave as the Monarch! Transfer leadership first."));
			return 0;
		}

		if (TownDataManager.getInstance().leaveTown(player.getUUID())) {
			return 1;
		}

		source.sendFailure(Component.literal("Failed to leave town!"));
		return 0;
	}

	private static int invitePlayer(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can invite to towns!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		String error = TownDataManager.getInstance().invitePlayer(player, targetName);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	/**
	 * `/town givecitizenland <player>` -- gives the calling Lord/Monarch a
	 * CitizenGiveLandBanner that, when placed, grants the chunk to the named
	 * citizen. The banner item carries the citizens UUID and removes itself
	 * once the chunk grant is recorded.
	 */
	private static int giveCitizenLandBanner(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can give citizen land!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		String error = TownDataManager.getInstance().giveCitizenGiveLandBanner(player, targetName);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	/**
	 * `/town revokelandall <player>` -- remove every chunk grant the named
	 * citizen currently holds inside the caller's town.
	 */
	private static int revokeAllLand(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can revoke land!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getManageableTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("You are not in a town!"));
			return 0;
		}
		java.util.UUID targetUuid = TownDataManager.getInstance().findMemberByName(town, targetName);
		if (targetUuid == null) {
			for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
				if (online.getName().getString().equalsIgnoreCase(targetName)) {
					targetUuid = online.getUUID();
					break;
				}
			}
		}
		if (targetUuid == null || !town.getMembers().containsKey(targetUuid)) {
			source.sendFailure(Component.literal("That player is not in your town!"));
			return 0;
		}
		String error = TownDataManager.getInstance().revokeAllLandForCitizen(player, targetUuid);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	/**
	 * `/town revokelandbanner` -- give the caller a Land Revoke Banner
	 * (purple "Land Revoke Banner") that, when placed, revokes the
	 * currently owning citizen from the chunk where it sits.
	 */
	private static int giveLandRevokeBanner(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can create a Land Revoke Banner!"));
			return 0;
		}

		String error = TownDataManager.getInstance().giveLandRevokeBanner(player);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	private static int evictPlayer(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can evict from towns!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can evict players!"));
			return 0;
		}

		java.util.UUID targetUuid = findMemberUuid(town, targetName);
		if (targetUuid == null) {
			source.sendFailure(Component.literal("That player is not in your town!"));
			return 0;
		}

		String error = TownDataManager.getInstance().evictPlayer(player, targetUuid);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static java.util.UUID findMemberUuid(TownData town, String targetName) {
		for (var entry : town.getMembers().entrySet()) {
			if (town.getMemberName(entry.getKey()).equalsIgnoreCase(targetName)) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static int setPublic(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can change town settings!"));
			return 0;
		}

		boolean value = BoolArgumentType.getBool(context, "value");
		String error = TownDataManager.getInstance().setTownPublic(player, value);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static int togglePublic(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can change town settings!"));
			return 0;
		}

		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can change town settings!"));
			return 0;
		}

		String error = TownDataManager.getInstance().toggleTownPublic(player);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static int deleteTown(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can delete towns!"));
			return 0;
		}

		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can delete the town!"));
			return 0;
		}

		String townName = town.getName();
		String error = TownDataManager.getInstance().deleteTown(player);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static int trustCitizen(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can trust citizens!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can trust citizens!"));
			return 0;
		}

		java.util.UUID targetUuid = findMemberUuid(town, targetName);
		if (targetUuid == null) {
			source.sendFailure(Component.literal("That player is not in your town!"));
			return 0;
		}

		String error = TownDataManager.getInstance().trustCitizen(player, targetUuid);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static int makeLord(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can make lords!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can make a citizen a Lord!"));
			return 0;
		}

		java.util.UUID targetUuid = findMemberUuid(town, targetName);
		if (targetUuid == null) {
			source.sendFailure(Component.literal("That player is not in your town!"));
			return 0;
		}

		String error = TownDataManager.getInstance().makeLord(player, targetUuid);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		return 1;
	}

	private static int stepDown(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();

		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can step down as Monarch!"));
			return 0;
		}

		String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
		TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
		if (town == null) {
			source.sendFailure(Component.literal("Only the Monarch can step down!"));
			return 0;
		}

		java.util.UUID targetUuid = findMemberUuid(town, targetName);
		if (targetUuid == null) {
			source.sendFailure(Component.literal("That player is not in your town!"));
			return 0;
		}

		String error = TownDataManager.getInstance().transferMonarchy(player, targetUuid);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(
				"You have given the crown to " + town.getMonarchName() + "!"), false);
		return 1;
	}
}