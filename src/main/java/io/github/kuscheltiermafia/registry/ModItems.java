package io.github.kuscheltiermafia.registry;

import io.github.kuscheltiermafia.EchoCraft3;
import io.github.kuscheltiermafia.items.ClaimBannerItem;
import io.github.kuscheltiermafia.items.DamageShieldItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    /**
     * A durability-based damage-absorbing item.
     * Uses ITEM_MODEL to appear as a totem of undying for vanilla clients.
     */
    public static final Item DAMAGE_SHIELD = Registry.register(
            Registries.ITEM,
            Identifier.of(EchoCraft3.MOD_ID, "damage_shield"),
            new DamageShieldItem(
                    new Item.Settings()
                            .maxDamage(1024)
                            .component(DataComponentTypes.ITEM_MODEL,
                                    Identifier.of("minecraft", "totem_of_undying"))
            )
    );

    /**
     * Placed on a block surface to claim the chunk; breaking the placed banner
     * removes the claim and returns this item (reusable). Vanilla clients see it
     * as a white banner via ITEM_MODEL.
     * Recipe: 6 wool (any colour) + 2 iron ingots + 1 stick.
     */
    public static final Item CLAIM_BANNER = Registry.register(
            Registries.ITEM,
            Identifier.of(EchoCraft3.MOD_ID, "claim_banner"),
            new ClaimBannerItem(
                    new Item.Settings()
                            .maxCount(1)
                            .component(DataComponentTypes.ITEM_MODEL,
                                    Identifier.of("minecraft", "white_banner"))
            )
    );

    public static void register() {
        // Static initialisation triggers the fields above.
        EchoCraft3.LOGGER.info("Registering EchoCraft3 items");
    }
}

