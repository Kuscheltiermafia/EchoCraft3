package io.github.kuscheltiermafia.mixin;

import io.github.kuscheltiermafia.events.DamageShieldHandler;
import io.github.kuscheltiermafia.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {

    @Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"), cancellable = true)
    private static void echocraft$removeMendingForDamageShield(
            int cost,
            ItemStack stack,
            Stream<Holder<Enchantment>> availableEnchantments,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir
    ) {
        if (!ModItems.isDamageShield(stack)) return;
        List<EnchantmentInstance> filtered = cir.getReturnValue().stream()
                .filter(instance -> !DamageShieldHandler.isMending(instance.enchantment()))
                .toList();
        cir.setReturnValue(filtered);
    }
}

