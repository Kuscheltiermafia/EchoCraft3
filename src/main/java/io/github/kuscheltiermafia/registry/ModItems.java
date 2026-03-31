package io.github.kuscheltiermafia.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class ModItems {
    public static final String WARDEN_SHIELD_NAME = "Warden Shield";
    private static final int FALLBACK_MAX_DURABILITY = 336;

    private static final String CUSTOM_DATA_KEY = "echocraft_item";
    public static final String DAMAGE_SHIELD_MARKER = "damage_shield";
    public static final String CLAIM_BANNER_MARKER = "claim_banner";

    // Keep as a no-op to preserve existing init flow.
    public static void registerItems() {}

    public static ItemStack createDamageShieldStack() {
        ItemStack stack = new ItemStack(Items.GOLDEN_NAUTILUS_ARMOR);
        markStack(stack, DAMAGE_SHIELD_MARKER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(WARDEN_SHIELD_NAME).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.AQUA)));
        int max = stack.getMaxDamage() > 0 ? stack.getMaxDamage() : FALLBACK_MAX_DURABILITY;
        Component line = Component.literal("0")
                .withStyle(style -> style.withColor(TextColor.fromRgb(0x00FF00)).withItalic(false))
                .append(Component.literal("/" + max + " Damage absorbed.").withStyle(style -> style.withColor(0xFFFFFF).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(line)));
        return stack;
    }

    public static ItemStack createClaimBannerStack() {
        ItemStack stack = new ItemStack(Items.WHITE_BANNER);
        markStack(stack, CLAIM_BANNER_MARKER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Claim Banner").withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.AQUA)));
        return stack;
    }

    public static boolean isDamageShield(ItemStack stack) {
        if (!stack.is(Items.GOLDEN_NAUTILUS_ARMOR)) return false;
        if (isMarkedStack(stack, Items.GOLDEN_NAUTILUS_ARMOR, DAMAGE_SHIELD_MARKER)) return true;

        if (!stack.has(DataComponents.CUSTOM_NAME)) return false;
        return Component.literal(WARDEN_SHIELD_NAME).withStyle(Style.EMPTY.withItalic(false)).equals(stack.getHoverName());
    }

    public static boolean isClaimBanner(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME) && Component.literal("Claim Banner").withStyle(Style.EMPTY.withItalic(false)).equals(stack.getHoverName())) {
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
