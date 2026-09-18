package com.siegedempires.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siegedempires.banner.BannerHelper;
import com.siegedempires.banner.BannerManager;
import com.siegedempires.data.EmpireDataManager;
import com.siegedempires.data.TownDataManager;
import com.siegedempires.model.EmpireData;
import com.siegedempires.model.TownData;
import com.siegedempires.util.CantAffordFeedback;
import com.siegedempires.util.ChatAnnouncements;
import com.siegedempires.util.FactionEditCost;
import com.siegedempires.util.InventoryHelper;
import com.siegedempires.util.NameValidator;
import com.siegedempires.util.PlayerPrefixManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class EmpireCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("empire")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("description", StringArgumentType.string())
                                        .then(Commands.argument("bannerData", StringArgumentType.greedyString())
                                                .executes(EmpireCommand::createEmpire)))))
                .then(Commands.literal("join")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(EmpireCommand::joinEmpire)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("town", StringArgumentType.string())
                                .executes(EmpireCommand::inviteTown)))
                .then(Commands.literal("disannex")
                        .then(Commands.argument("town", StringArgumentType.string())
                                .executes(EmpireCommand::disannexTown)))
                .then(Commands.literal("setpublic")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(EmpireCommand::setPublic)))
                .then(Commands.literal("togglepublic")
                        .executes(EmpireCommand::togglePublic))
                .then(Commands.literal("stepdown")
                        .then(Commands.argument("player", StringArgumentType.string())
                                .executes(EmpireCommand::stepDown)))
                .then(Commands.literal("delete")
                        .executes(EmpireCommand::deleteEmpire))
                .then(Commands.literal("info")
                        .executes(EmpireCommand::showInfo)));
    }
private static int createEmpire(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can create empires!"));
            return 0;
        }

        String empireName = StringArgumentType.getString(context, "name").trim().replace("_", " ");
        String description = StringArgumentType.getString(context, "description").replace("_", " ");
        String bannerData = StringArgumentType.getString(context, "bannerData");

        // Banner data format: "title;baseColor;pattern1;pattern2;..."
        String[] parts = bannerData.split(";", -1);
        if (parts.length < 2) {
            source.sendFailure(Component.literal("Invalid banner data!"));
            return 0;
        }

        String title = parts[0];
        String baseColor = parts[1];
        List<String> patterns = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                patterns.add(parts[i]);
            }
        }

        String error = tryCreateEmpire(player, empireName, description, title, baseColor, patterns);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    public static String tryCreateEmpire(ServerPlayer player, String empireName, String description,
            String title, String baseColor, List<String> patterns) {
        return tryCreateEmpire(player, empireName, description, title, baseColor, patterns, null);
    }

    /**
     * Shared empire creation used by {@code /empire create} and the GUI network payload.
     * @return error message, or null on success
     */
    public static String tryCreateEmpire(ServerPlayer player, String empireName, String description,
            String title, String baseColor, List<String> patterns, String bannerPixels) {
        if (!title.equals("Emperor") && !title.equals("Empress")) {
            return "Title must be 'Emperor' or 'Empress'!";
        }

        TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
        if (town == null) {
            return "Only a town Monarch can create an empire!";
        }

        if (town.getEmpireId() != null && !town.getEmpireId().isEmpty()) {
            return "Your town is already in an empire!";
        }

        String validationError = NameValidator.getValidationError(empireName);
        if (validationError != null) {
            return validationError;
        }

        if (EmpireDataManager.getInstance().empireNameExists(empireName)) {
            return "An empire with this name already exists!";
        }

        if (!InventoryHelper.hasEnoughGold(player, 8)) {
            CantAffordFeedback.playSound(player);
            return "Can't Afford This!";
        }

        InventoryHelper.removeGold(player, 8);

        String desc = description == null ? "" : description.trim();
        if (desc.equalsIgnoreCase("none") || desc.equalsIgnoreCase("null")) {
            desc = "";
        }

        List<String> patternList = patterns != null ? patterns : List.of();
        String color = baseColor == null || baseColor.isEmpty() ? "white" : baseColor;

        EmpireData empire = EmpireDataManager.getInstance().createEmpire(
                empireName.trim(), desc, player.getUUID(), player.getName().getString(),
                title, town.getId(), patternList, color, bannerPixels);

        if (empire == null) {
            return "Failed to create empire! Name may already exist.";
        }

        town.setEmpireId(empire.getId());
        String previousPixels = town.getBannerPixels();
        BannerHelper.preserveOwnBannerAndAdoptEmpire(town, empire);
        TownDataManager.getInstance().saveTown(town);

        BannerManager.syncBannersForTown(BannerManager.getServer(), town, previousPixels);
        PlayerPrefixManager.refresh(player);
        PlayerPrefixManager.refreshForTown(town);

        ChatAnnouncements.empireCreated(player, empire.getName(), title);
        return null;
    }

    /**
     * Update empire name and banner; charges {@link FactionEditCost}.
     * @return error message, or null on success
     */
    public static String tryUpdateEmpire(ServerPlayer player, String newName,
            String bannerBaseColor, List<String> bannerPatterns, String bannerPixels) {
        EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
        if (empire == null) {
            return "Only the Emperor or Empress can edit the empire!";
        }

        int cost = FactionEditCost.costForEmpire(empire);
        if (!InventoryHelper.hasEnoughGold(player, cost)) {
            CantAffordFeedback.playSound(player);
            return "Can't Afford This!";
        }

        String error = EmpireDataManager.getInstance().updateEmpireNameAndBanner(
                player.getUUID(), newName, bannerBaseColor, bannerPatterns, bannerPixels);
        if (error != null) {
            return error;
        }

        InventoryHelper.removeGold(player, cost);
        return null;
    }

    private static int joinEmpire(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can join empires!"));
            return 0;
        }

        String empireName = StringArgumentType.getString(context, "name").trim().replace("_", " ");
        TownData town = TownDataManager.getInstance().getMonarchTown(player.getUUID());
        if (town == null) {
            source.sendFailure(Component.literal("Only a town Monarch can join an empire!"));
            return 0;
        }

        EmpireData empire = EmpireDataManager.getInstance().getEmpire(
                com.siegedempires.util.NameValidator.toId(empireName));
        if (empire == null) {
            source.sendFailure(Component.literal("That empire does not exist!"));
            return 0;
        }

        String error = EmpireDataManager.getInstance().joinEmpire(empire.getId(), town);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int inviteTown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can invite towns to empires!"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "town").trim().replace("_", " ");
        String error = EmpireDataManager.getInstance().inviteTownByEmperor(player.getUUID(), townName);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int disannexTown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can disannex towns from empires!"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "town").trim().replace("_", " ");
        TownData town = TownDataManager.getInstance().getTownByName(townName);
        if (town == null) {
            source.sendFailure(Component.literal("That town does not exist!"));
            return 0;
        }

        String error = EmpireDataManager.getInstance().disannexTown(player.getUUID(), town.getId());
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int setPublic(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can change empire settings!"));
            return 0;
        }

        boolean value = BoolArgumentType.getBool(context, "value");
        String error = EmpireDataManager.getInstance().setEmpirePublic(player.getUUID(), value);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int togglePublic(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can change empire settings!"));
            return 0;
        }

        EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
        if (empire == null) {
            source.sendFailure(Component.literal("Only the Emperor or Empress can change empire settings!"));
            return 0;
        }

        String error = EmpireDataManager.getInstance().toggleEmpirePublic(player.getUUID());
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    private static int stepDown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can step down as Emperor!"));
            return 0;
        }

        String targetName = StringArgumentType.getString(context, "player").trim().replace("_", " ");
        EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
        if (empire == null) {
            source.sendFailure(Component.literal("Only the Emperor or Empress can step down!"));
            return 0;
        }

        TownData successorTown = null;
        java.util.UUID successorUuid = null;
        for (String townId : empire.getMemberTownIds()) {
            TownData town = TownDataManager.getInstance().getTown(townId);
            if (town == null || town.isWarTown()) {
                continue;
            }
            if (town.getMonarchName() != null
                    && town.getMonarchName().equalsIgnoreCase(targetName)) {
                successorTown = town;
                successorUuid = town.getMonarchUuid();
                break;
            }
            if (town.getMonarchUuid() != null
                    && town.getMemberName(town.getMonarchUuid()).equalsIgnoreCase(targetName)) {
                successorTown = town;
                successorUuid = town.getMonarchUuid();
                break;
            }
        }

        if (successorTown == null || successorUuid == null) {
            source.sendFailure(Component.literal(
                    "That player is not a Monarch of a town in your empire!"));
            return 0;
        }

        String error = EmpireDataManager.getInstance().transferEmpire(player.getUUID(), successorUuid);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }

        EmpireData updated = EmpireDataManager.getInstance().getEmpire(empire.getId());
        String newName = updated != null ? updated.getEmperorName() : targetName;
        source.sendSuccess(() -> Component.literal(
                "You have given the imperial crown to " + newName + "!"), false);
        return 1;
    }

    private static int deleteEmpire(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can delete empires!"));
            return 0;
        }

        EmpireData empire = EmpireDataManager.getInstance().getEmpireByEmperor(player.getUUID());
        if (empire == null) {
            source.sendFailure(Component.literal("Only the Emperor or Empress can delete the empire!"));
            return 0;
        }

        String empireName = empire.getName();
        String error = EmpireDataManager.getInstance().deleteEmpire(player.getUUID());
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "The Empire of " + empireName + " has been dissolved. Member towns are independent again."),
                false);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can view empire info!"));
            return 0;
        }

        TownData town = TownDataManager.getInstance().getPlayerTown(player.getUUID());
        if (town == null) {
            source.sendFailure(Component.literal("You are not in a town!"));
            return 0;
        }

        if (town.getEmpireId() == null || town.getEmpireId().isEmpty()) {
            source.sendFailure(Component.literal("Your town is not in an empire!"));
            return 0;
        }

        EmpireData empire = EmpireDataManager.getInstance().getEmpire(town.getEmpireId());
        if (empire == null) {
            source.sendFailure(Component.literal("Your empire could not be found!"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "=== Empire of " + empire.getName() + " ==="), false);
        source.sendSuccess(() -> Component.literal(
                "Description: " + (empire.getDescription() != null && !empire.getDescription().isEmpty()
                        ? empire.getDescription() : "none")), false);
        source.sendSuccess(() -> Component.literal(
                empire.getEmperorTitle() + ": " + empire.getEmperorName()), false);
        source.sendSuccess(() -> Component.literal(
                "Capital: " + empire.getCapitalTownId()), false);
        source.sendSuccess(() -> Component.literal(
                "Member Towns: " + empire.getMemberTownIds().size()), false);

        return 1;
    }
}