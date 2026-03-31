package io.github.kuscheltiermafia.registry;

import io.github.kuscheltiermafia.EchoCraft3;
import io.github.kuscheltiermafia.items.ClaimBannerItem;
import io.github.kuscheltiermafia.items.DamageShieldItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public class ModItems {

    public static final Item DAMAGE_SHIELD = register(
            "damage_shield",
            DamageShieldItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static final Item CLAIM_BANNER = register(
            "claim_banner",
            ClaimBannerItem::new,
            new Item.Properties().stacksTo(1)
    );

    public static void registerItems() {}

    public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
        // Create the item key.
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EchoCraft3.MOD_ID, name));

        // Create the item instance.
        T item = itemFactory.apply(settings.setId(itemKey));

        // Register the item.
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        return item;
    }
}
