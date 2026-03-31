package io.github.kuscheltiermafia.mixin;

import io.github.kuscheltiermafia.events.DamageShieldHandler;
import io.github.kuscheltiermafia.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(EnchantmentHelper.class)
public class EnchantmentSanitizeMixin {

    @Inject(method = "setEnchantments", at = @At("TAIL"))
    private static void echocraft$sanitizeAfterSet(ItemStack stack, ItemEnchantments enchantments, CallbackInfo ci) {
        if (!ModItems.isDamageShield(stack)) return;
        DamageShieldHandler.stripMendingFromDamageShield(stack);
    }

    @Inject(method = "updateEnchantments", at = @At("TAIL"))
    private static void echocraft$sanitizeAfterUpdate(ItemStack stack, Consumer<ItemEnchantments.Mutable> editor, CallbackInfoReturnable<ItemEnchantments> cir) {
        if (!ModItems.isDamageShield(stack)) return;
        DamageShieldHandler.stripMendingFromDamageShield(stack);
    }
}

