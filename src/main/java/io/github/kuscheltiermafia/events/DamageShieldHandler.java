package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Damage Shield item: absorbs incoming damage based on remaining durability.
 * 1 durability point = 1 damage point absorbed.
 */
public class DamageShieldHandler {
    private static final String REPAIR_LOCK_KEY = "damage_shield_min_damage";
    private static final String VIRTUAL_DAMAGE_KEY = "damage_shield_virtual_damage";
    private static final int FALLBACK_MAX_DURABILITY = 256;

    /** Guards against re-entrant calls when we apply residual damage. */
    private static final Set<UUID> processing = new HashSet<>();

    private record MendingData(Holder<Enchantment> enchantment, int level) {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                enforceNoRepair(player);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) return true;
            if (!(source.getEntity() instanceof ServerPlayer attacker)) return true;
            if (attacker.getUUID().equals(player.getUUID())) return true;
            if (amount <= 0.0f) return true;
            UUID id = player.getUUID();
            if (processing.contains(id)) return true; // prevent recursion

            float remaining = amount;
            while (remaining > 0.0001f) {
                ItemStack shield = findDamageShield(player);
                if (shield == null) {
                    break;
                }
                enforceNoRepair(shield);

                int maxDurability = getShieldMaxDurability(shield);
                int currentDamage = getShieldDamage(shield);
                int durability = maxDurability - currentDamage;
                if (durability <= 0) {
                    continue;
                }

                float absorbed = Math.min(remaining, (float) durability);
                if (absorbed <= 0.0f) {
                    break;
                }

                int attemptedDurabilityLoss = Math.max(1, (int) Math.ceil(absorbed));
                int durabilityLoss = Math.min(durability, calculateDurabilityLoss(player, shield, attemptedDurabilityLoss));

                MendingData mendingData = findMending(shield);
                if (mendingData != null && durabilityLoss > 0) {
                    dropMendingBook(player, mendingData);
                    stripMendingFromDamageShield(shield);
                }

                int newDamage = currentDamage + durabilityLoss;
                if (newDamage >= maxDurability) {
                    breakShield(player, shield);
                } else {
                    setShieldDamage(shield, newDamage);
                    setRepairLock(shield, newDamage);
                }

                remaining -= absorbed;
            }

            if (remaining <= 0.0001f) {
                return false; // fully absorbed, potentially by multiple shields
            }

            // Apply the residual damage without triggering this handler again
            processing.add(id);
            try {
                player.hurt(source, remaining);
            } finally {
                processing.remove(id);
            }
            return false; // original event canceled; residual applied manually
        });
    }

    private static ItemStack findDamageShield(Player player) {
        ItemStack offhand = player.getOffhandItem();
        enforceNoRepair(offhand);
        if (ModItems.isDamageShield(offhand) && hasDurabilityLeft(offhand)) {
            return offhand;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) continue;
            ItemStack equipped = player.getItemBySlot(slot);
            enforceNoRepair(equipped);
            if (ModItems.isDamageShield(equipped) && hasDurabilityLeft(equipped)) {
                return equipped;
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            enforceNoRepair(stack);
            if (ModItems.isDamageShield(stack) && hasDurabilityLeft(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean hasDurabilityLeft(ItemStack stack) {
        int max = getShieldMaxDurability(stack);
        if (max <= 0) return false;
        return getShieldDamage(stack) < max;
    }

    private static int getShieldMaxDurability(ItemStack stack) {
        int nativeMax = stack.getMaxDamage();
        return nativeMax > 0 ? nativeMax : FALLBACK_MAX_DURABILITY;
    }

    private static int getShieldDamage(ItemStack stack) {
        if (stack.getMaxDamage() > 0) {
            return stack.getDamageValue();
        }
        return getVirtualDamage(stack);
    }

    private static void setShieldDamage(ItemStack stack, int value) {
        int clamped = Math.max(0, Math.min(value, getShieldMaxDurability(stack)));
        if (stack.getMaxDamage() > 0) {
            stack.setDamageValue(clamped);
            updateShieldLore(stack);
            return;
        }
        setVirtualDamage(stack, clamped);
        updateShieldLore(stack);
    }

    private static int calculateDurabilityLoss(ServerPlayer player, ItemStack shield, int absorbedDamage) {
        int unbreakingLevel = getUnbreakingLevel(shield);
        if (unbreakingLevel <= 0) return absorbedDamage;

        int loss = 0;
        for (int i = 0; i < absorbedDamage; i++) {
            // Vanilla-like behavior: chance to consume durability is 1/(level+1)
            if (player.getRandom().nextInt(unbreakingLevel + 1) == 0) {
                loss++;
            }
        }
        return Math.max(1, loss);
    }

    private static int getUnbreakingLevel(ItemStack shield) {
        int level = 0;
        for (var entry : shield.getEnchantments().entrySet()) {
            String key = entry.getKey().unwrapKey().map(Object::toString).orElse("");
            if (key.contains("unbreaking")) {
                level = entry.getIntValue();
                break;
            }
        }
        return level;
    }

    public static void sanitizeDamageShieldEnchantments(ServerPlayer player) {
        stripMendingFromDamageShield(player.getMainHandItem());
        stripMendingFromDamageShield(player.getOffhandItem());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            stripMendingFromDamageShield(player.getInventory().getItem(i));
        }
    }

    public static boolean isMending(Holder<Enchantment> enchantment) {
        String key = enchantment.unwrapKey().map(Object::toString).orElse("");
        return key.contains("mending");
    }

    public static boolean stripMendingFromDamageShield(ItemStack stack) {
        if (!ModItems.isDamageShield(stack)) return false;

        ItemEnchantments enchantments = stack.getEnchantments();
        boolean hasMending = false;
        for (var entry : enchantments.entrySet()) {
            if (isMending(entry.getKey())) {
                hasMending = true;
                break;
            }
        }
        if (!hasMending) return false;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (isMending(entry.getKey())) continue;
            mutable.set(entry.getKey(), entry.getIntValue());
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return true;
    }

    private static MendingData findMending(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (isMending(entry.getKey())) {
                return new MendingData(entry.getKey(), Math.max(1, entry.getIntValue()));
            }
        }
        return null;
    }

    private static void dropMendingBook(ServerPlayer player, MendingData mendingData) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(book.getEnchantments());
        mutable.set(mendingData.enchantment(), mendingData.level());
        EnchantmentHelper.setEnchantments(book, mutable.toImmutable());
        ItemEntity dropped = player.drop(book, false);
        if (dropped != null) {
            dropped.setNoPickUpDelay();
        }
    }

    private static void enforceNoRepair(Player player) {
        ejectDamageShieldFromAnvil(player);
        enforceNoRepair(player.getMainHandItem());
        enforceNoRepair(player.getOffhandItem());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) continue;
            enforceNoRepair(player.getItemBySlot(slot));
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            enforceNoRepair(player.getInventory().getItem(i));
        }
    }

    private static void ejectDamageShieldFromAnvil(Player player) {
        if (!(player.containerMenu instanceof AnvilMenu anvil)) return;

        ItemStack main = anvil.getSlot(0).getItem();
        ItemStack repair = anvil.getSlot(1).getItem();
        if (!ModItems.isDamageShield(main) || !repair.is(Items.GOLD_INGOT)) return;

        ItemStack droppedMain = main.copy();
        ItemStack droppedRepair = repair.copy();
        anvil.getSlot(0).set(ItemStack.EMPTY);
        anvil.getSlot(1).set(ItemStack.EMPTY);

        boolean changed = true;
        if (!droppedMain.isEmpty()) {
            ItemEntity entity = player.drop(droppedMain, false);
            if (entity != null) entity.setNoPickUpDelay();
        }
        if (!droppedRepair.isEmpty()) {
            ItemEntity entity = player.drop(droppedRepair, false);
            if (entity != null) entity.setNoPickUpDelay();
        }

        if (changed) {
            anvil.broadcastChanges();
        }
    }

    private static void enforceNoRepair(ItemStack stack) {
        if (!ModItems.isDamageShield(stack)) return;

        int current = getShieldDamage(stack);
        int locked = getRepairLock(stack);
        if (locked < 0) {
            setRepairLock(stack, current);
            return;
        }

        if (current < locked) {
            setShieldDamage(stack, locked);
            return;
        }

        if (current > locked) {
            setRepairLock(stack, current);
        }

        updateShieldLore(stack);
    }

    private static void updateShieldLore(ItemStack stack) {
        if (!ModItems.isDamageShield(stack)) return;

        int max = getShieldMaxDurability(stack);
        if (max <= 0) return;
        int absorbed = Math.max(0, Math.min(getShieldDamage(stack), max));

        float ratio = (float) absorbed / (float) max;
        int percent = Math.round(ratio * 100.0f);
        int rgb = getLoreColorForDamagePercent(percent);

        MutableComponent line = Component.literal(String.valueOf(absorbed))
                .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)).withItalic(false))
                .append(Component.literal("/" + max + " Damage absorbed.").withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)));

        stack.set(DataComponents.LORE, new ItemLore(java.util.List.of(line)));
    }

    private static int getLoreColorForDamagePercent(int percent) {
        if (percent <= 20) return 0x00AA00;      // dark green
        if (percent <= 40) return 0x55FF55;      // green
        if (percent <= 60) return 0xFFFF55;      // yellow
        if (percent <= 80) return 0xFFAA00;      // orange
        return 0xFF5555;                         // red
    }

    private static void breakShield(ServerPlayer player, ItemStack shield) {
        shield.setCount(0);
        var level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0f, 0.9f);
        level.sendParticles(
                ParticleTypes.CRIT,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                20,
                0.35,
                0.35,
                0.35,
                0.02
        );
        level.sendParticles(
                ParticleTypes.SMOKE,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                10,
                0.2,
                0.2,
                0.2,
                0.01
        );
    }

    private static int getRepairLock(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return -1;
        return customData.copyTag().getInt(REPAIR_LOCK_KEY).orElse(-1);
    }

    private static int getVirtualDamage(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(VIRTUAL_DAMAGE_KEY).orElse(0);
    }

    private static void setRepairLock(ItemStack stack, int minDamage) {
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                : new CompoundTag();
        tag.putInt(REPAIR_LOCK_KEY, Math.max(0, minDamage));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void setVirtualDamage(ItemStack stack, int damage) {
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                : new CompoundTag();
        tag.putInt(VIRTUAL_DAMAGE_KEY, Math.max(0, damage));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
