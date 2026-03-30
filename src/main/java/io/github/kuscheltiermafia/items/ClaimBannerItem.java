package io.github.kuscheltiermafia.items;

import net.minecraft.item.Item;

/**
 * The Claim Banner item. Players right-click any block while holding this to
 * claim the chunk they are standing in. The item is consumed on a successful
 * claim. Vanilla clients see it as a white banner (via ITEM_MODEL component).
 */
public class ClaimBannerItem extends Item {

    public ClaimBannerItem(Settings settings) {
        super(settings);
    }
}
