package io.github.kuscheltiermafia.util;

import io.github.kuscheltiermafia.users.UserSettingsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public final class TextPalette {
    private TextPalette() {}

    public static MutableComponent white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    public static MutableComponent yellow(String text) {
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    public static MutableComponent red(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }

    public static MutableComponent status(boolean allowed) {
        return Component.literal(allowed ? "Allow" : "Disallow")
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static MutableComponent player(String name, UUID uuid, MinecraftServer server) {
        UserSettingsManager settings = UserSettingsManager.get(server);
        Integer rgb = settings.getLocatorColorRgb(uuid);
        if (rgb != null) {
            TextColor color = TextColor.fromRgb(rgb);
            if (color != null) {
                return Component.literal(name).withStyle(style -> style.withColor(color));
            }
        }
        return Component.literal(name)
                .withStyle(settings.getLocatorColorPreset(uuid).formatting());
    }

    public static MutableComponent join(Component... parts) {
        MutableComponent out = Component.empty();
        for (Component part : parts) {
            out.append(part);
        }
        return out;
    }
}

