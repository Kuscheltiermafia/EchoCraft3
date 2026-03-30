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
     * A one-use item that claims the chunk when the player right-clicks any block.
     * Uses ITEM_MODEL to appear as a white banner for vanilla clients.
     * Recipe: 6 wool (any colour) + 1 stick + 2 iron ingots.
     */
    public static final Item CLAIM_BANNER = Registry.register(
            Registries.ITEM,
            Identifier.of(EchoCraft3.MOD_ID, "claim_banner"),
            new ClaimBannerItem(
                    new Item.Settings()
                            .maxCount(16)
                            .component(DataComponentTypes.ITEM_MODEL,
                                    Identifier.of("minecraft", "white_banner"))
            )
    );

    public static void register() {
        // Static initialisation triggers the fields above.
        EchoCraft3.LOGGER.info("Registering EchoCraft3 items");
    }
}

