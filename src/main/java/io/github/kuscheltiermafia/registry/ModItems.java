package io.github.kuscheltiermafia.registry;

import io.github.kuscheltiermafia.EchoCraft3;
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

    public static void register() {
        // Static initialisation triggers the field above.
        EchoCraft3.LOGGER.info("Registering EchoCraft3 items");
    }
}
