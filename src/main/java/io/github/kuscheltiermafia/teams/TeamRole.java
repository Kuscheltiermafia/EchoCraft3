package io.github.kuscheltiermafia.teams;

public enum TeamRole {
    MEMBER,
    MODERATOR,
    LEADER;

    public static TeamRole fromString(String raw) {
        try {
            return TeamRole.valueOf(raw.toUpperCase());
        } catch (Exception ignored) {
            return MEMBER;
        }
    }

    public String displayName() {
        return switch (this) {
            case MEMBER -> "Member";
            case MODERATOR -> "Moderator";
            case LEADER -> "Leader";
        };
    }
}

