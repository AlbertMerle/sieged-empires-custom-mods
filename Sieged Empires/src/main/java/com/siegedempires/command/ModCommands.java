package com.siegedempires.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public class ModCommands {
	
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TownCommand.register(dispatcher);
			EmpireCommand.register(dispatcher);
			DiplomacyCommand.register(dispatcher);
		});
	}
}