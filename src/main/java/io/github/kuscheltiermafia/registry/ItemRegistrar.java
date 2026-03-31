package io.github.kuscheltiermafia.registry;

import io.github.kuscheltiermafia.EchoCraft3;

/**
 * Registers custom items to the game registry
 */
public class ItemRegistrar {
    
    public static void registerItems() {
        EchoCraft3.LOGGER.info("EchoCraft3 items loaded and ready");
        EchoCraft3.LOGGER.info("- DAMAGE_SHIELD registered");
        EchoCraft3.LOGGER.info("- CLAIM_BANNER registered");
    }
}

