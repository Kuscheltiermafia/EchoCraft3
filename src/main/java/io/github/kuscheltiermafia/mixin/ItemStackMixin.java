package io.github.kuscheltiermafia.mixin;

import io.github.kuscheltiermafia.events.DamageShieldHandler;
import io.github.kuscheltiermafia.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
    private void echocraft$blockMendingOnDamageShield(Holder<Enchantment> enchantment, int level, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        if (ModItems.isDamageShield(self) && DamageShieldHandler.isMending(enchantment)) {
            ci.cancel();
        }
    }
}

