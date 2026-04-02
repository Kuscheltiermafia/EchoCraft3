package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.registry.ModItems;
import io.github.kuscheltiermafia.users.UserSettingsManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Gives every player a Claim Banner the very first time they join the world.
 * The one-time delivery is tracked persistently via {@link UserSettingsManager}.
 */
public class FirstJoinHandler {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UserSettingsManager settings = UserSettingsManager.get(server);

            if (!settings.hasReceivedStarterKit(player.getUUID())) {
                ItemStack banner = ModItems.createClaimBannerStack();
                if (!player.getInventory().add(banner)) {
                    player.drop(banner, false);
                }
                settings.markStarterKitReceived(player.getUUID());
            }
        });
    }
}
