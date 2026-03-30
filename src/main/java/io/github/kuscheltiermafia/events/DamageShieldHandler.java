package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            UUID id = player.getUuid();
            if (processing.contains(id)) return true; // prevent recursion

            ItemStack shield = findDamageShield(player);
            if (shield == null) return true; // no shield, allow normal damage

            int durability = shield.getMaxDamage() - shield.getDamage();
            if (durability <= 0) return true; // broken shield

            int absorbed = (int) Math.min(amount, durability);
            float remaining = amount - absorbed;

            // Reduce shield durability manually
            int newDamage = shield.getDamage() + absorbed;
            if (newDamage >= shield.getMaxDamage()) {
                shield.setCount(0); // item breaks
                player.sendMessage(Text.literal("§6Your Damage Shield has broken!"), true);
            } else {
                shield.setDamage(newDamage);
            }

            player.sendMessage(
                    Text.literal("§6Damage Shield absorbed §c" + absorbed + " §6damage!"),
                    true
            );

            if (remaining <= 0) {
                return false; // fully absorbed
            }

            // Apply the residual damage without triggering this handler again
            processing.add(id);
            try {
                player.damage(player.getServerWorld(), source, remaining);
            } finally {
                processing.remove(id);
            }
            return false; // original event cancelled; residual applied manually
        });
    }

    private static ItemStack findDamageShield(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ModItems.DAMAGE_SHIELD) && stack.getDamage() < stack.getMaxDamage()) {
                return stack;
            }
        }
        return null;
    }
}
