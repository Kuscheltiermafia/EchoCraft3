package io.github.kuscheltiermafia.users;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum LocatorBarColor {
    BLACK("Black", ChatFormatting.BLACK),
    DARK_BLUE("Dark Blue", ChatFormatting.DARK_BLUE),
    DARK_GREEN("Dark Green", ChatFormatting.DARK_GREEN),
    DARK_AQUA("Dark Aqua", ChatFormatting.DARK_AQUA),
    DARK_RED("Dark Red", ChatFormatting.DARK_RED),
    DARK_PURPLE("Dark Purple", ChatFormatting.DARK_PURPLE),
    GRAY("Gray", ChatFormatting.GRAY),
    DARK_GRAY("Dark Gray", ChatFormatting.DARK_GRAY),
    RED("Red", ChatFormatting.RED),
    GOLD("Gold", ChatFormatting.GOLD),
    YELLOW("Yellow", ChatFormatting.YELLOW),
    GREEN("Green", ChatFormatting.GREEN),
    AQUA("Aqua", ChatFormatting.AQUA),
    BLUE("Blue", ChatFormatting.BLUE),
    LIGHT_PURPLE("Light Purple", ChatFormatting.LIGHT_PURPLE),
    WHITE("White", ChatFormatting.WHITE);

    private final String displayName;
    private final ChatFormatting formatting;

    LocatorBarColor(String displayName, ChatFormatting formatting) {
        this.displayName = displayName;
        this.formatting = formatting;
    }

    public String displayName() {
        return displayName;
    }

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ChatFormatting formatting() {
        return formatting;
    }

    public static LocatorBarColor fromToken(String raw) {
        if (raw == null || raw.isBlank()) return WHITE;
        try {
            return LocatorBarColor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WHITE;
        }
    }

    public static boolean isHexToken(String raw) {
        return normalizeHex(raw) != null;
    }

    public static String normalizeHex(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (value.length() != 7) return null;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lowerHex = c >= 'a' && c <= 'f';
            boolean upperHex = c >= 'A' && c <= 'F';
            if (!digit && !lowerHex && !upperHex) return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static Integer parseHexRgb(String raw) {
        String normalized = normalizeHex(raw);
        if (normalized == null) return null;
        try {
            return Integer.parseInt(normalized.substring(1), 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

