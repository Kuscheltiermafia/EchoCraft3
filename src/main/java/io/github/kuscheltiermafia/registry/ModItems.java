package io.github.kuscheltiermafia.registry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class ModItems {

    private static final String CUSTOM_DATA_KEY = "echocraft_item";
    public static final String DAMAGE_SHIELD_MARKER = "damage_shield";
    public static final String CLAIM_BANNER_MARKER = "claim_banner";

    // Keep as a no-op to preserve existing init flow.
    public static void registerItems() {}

    public static ItemStack createDamageShieldStack() {
        ItemStack stack = new ItemStack(Items.SHIELD);
        markStack(stack, DAMAGE_SHIELD_MARKER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Damage Shield"));
        return stack;
    }

    public static ItemStack createClaimBannerStack() {
        ItemStack stack = new ItemStack(Items.WHITE_BANNER);
        markStack(stack, CLAIM_BANNER_MARKER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Claim Banner"));
        return stack;
    }

    public static boolean isDamageShield(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME) && "Damage Shield".equals(stack.getHoverName().getString())) {
            return stack.is(Items.SHIELD);
        }
        return isMarkedStack(stack, Items.SHIELD, DAMAGE_SHIELD_MARKER);
    }

    public static boolean isClaimBanner(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME) && "Claim Banner".equals(stack.getHoverName().getString())) {
            return stack.is(Items.WHITE_BANNER);
        }
        return isMarkedStack(stack, Items.WHITE_BANNER, CLAIM_BANNER_MARKER);
    }

    private static boolean isMarkedStack(ItemStack stack, Item expectedBaseItem, String marker) {
        if (!stack.is(expectedBaseItem)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        return marker.equals(data.copyTag().getString(CUSTOM_DATA_KEY).orElse(""));
    }

    private static void markStack(ItemStack stack, String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString(CUSTOM_DATA_KEY, marker);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
