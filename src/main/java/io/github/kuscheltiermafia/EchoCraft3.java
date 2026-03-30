package io.github.kuscheltiermafia;

import io.github.kuscheltiermafia.commands.ClaimCommand;
import io.github.kuscheltiermafia.commands.TeamCommand;
import io.github.kuscheltiermafia.events.ClaimProtectionHandler;
import io.github.kuscheltiermafia.events.DamageShieldHandler;
import io.github.kuscheltiermafia.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoCraft3 implements ModInitializer {
	public static final String MOD_ID = "echocraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initialising EchoCraft3");

		// Register items
		ModItems.register();

		// Register event handlers
		DamageShieldHandler.register();
		ClaimProtectionHandler.register();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TeamCommand.register(dispatcher, registryAccess);
			ClaimCommand.register(dispatcher, registryAccess);
		});
	}
}