package io.github.kuscheltiermafia.mixin;

import io.github.kuscheltiermafia.events.DamageShieldHandler;
import io.github.kuscheltiermafia.registry.ModItems;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {

    @Inject(method = "createResult", at = @At("TAIL"))
    private void echocraft$stripMendingFromAnvilResult(CallbackInfo ci) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack result = self.getSlot(2).getItem();
        if (!ModItems.isDamageShield(result)) return;
        DamageShieldHandler.stripMendingFromDamageShield(result);
    }
}

