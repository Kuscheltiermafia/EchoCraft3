package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Damage Shield item: absorbs incoming damage based on remaining durability.
 * 1 durability point = 1 damage point absorbed.
 */
public class DamageShieldHandler {

    /** Guards against re-entrant calls when we apply residual damage. */
    private static final Set<UUID> processing = new HashSet<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, _source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            UUID id = player.getUUID();
            if (processing.contains(id)) return true; // prevent recursion

            ItemStack shield = findDamageShield(player);
            if (shield == null) return true; // no shield, allow normal damage

            int maxDurability = shield.getMaxDamage();
            int currentDamage = shield.getDamageValue();
            int durability = maxDurability - currentDamage;
            if (durability <= 0) return true; // broken shield

            int absorbed = (int) Math.min(amount, durability);
            float remaining = amount - absorbed;

            // Reduce shield durability manually
            int newDamage = currentDamage + absorbed;
            if (newDamage >= maxDurability) {
                shield.setCount(0); // item breaks
                player.sendSystemMessage(Component.literal("§6Your Damage Shield has broken!"));
            } else {
                shield.setDamageValue(newDamage);
            }

            player.sendSystemMessage(
                    Component.literal("§6Damage Shield absorbed §c" + absorbed + " §6damage!")
            );

            if (remaining <= 0) {
                return false; // fully absorbed
            }

            // Apply the residual damage without triggering this handler again
            processing.add(id);
            try {
                player.hurt(player.damageSources().magic(), remaining);
            } finally {
                processing.remove(id);
            }
            return false; // original event cancelled; residual applied manually
        });
    }

    private static ItemStack findDamageShield(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ModItems.isDamageShield(stack) && stack.getDamageValue() < stack.getMaxDamage()) {
                return stack;
            }
        }
        return null;
    }
}
