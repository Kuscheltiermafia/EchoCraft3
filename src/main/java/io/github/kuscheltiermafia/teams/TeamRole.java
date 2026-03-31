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
}

