package io.github.kuscheltiermafia.users;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class UserSettingsManager {
    private static final String FILE_NAME = "echocraft_user_settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, UserSettingsManager> RUNTIME_INSTANCES = new WeakHashMap<>();

    private static final String DEFAULT_COLOR_TOKEN = LocatorBarColor.WHITE.token();

    private final Map<UUID, String> locatorColors = new HashMap<>();
    private final Map<UUID, Boolean> territoryNotifications = new HashMap<>();
    private final Map<UUID, Boolean> claimDenyNotifications = new HashMap<>();
    private final Set<UUID> starterKitReceived = new HashSet<>();
    private final Path storageFile;

    private UserSettingsManager(Path storageFile) {
        this.storageFile = storageFile;
    }

    public static UserSettingsManager get(MinecraftServer server) {
        return RUNTIME_INSTANCES.computeIfAbsent(server, UserSettingsManager::loadFromDisk);
    }

    public String getLocatorColorToken(UUID uuid) {
        String raw = locatorColors.get(uuid);
        if (raw == null || raw.isBlank()) return DEFAULT_COLOR_TOKEN;
        if (LocatorBarColor.isHexToken(raw)) {
            return LocatorBarColor.normalizeHex(raw);
        }
        return LocatorBarColor.fromToken(raw).token();
    }

    public LocatorBarColor getLocatorColorPreset(UUID uuid) {
        return LocatorBarColor.fromToken(getLocatorColorToken(uuid));
    }

    public Integer getLocatorColorRgb(UUID uuid) {
        return LocatorBarColor.parseHexRgb(getLocatorColorToken(uuid));
    }

    public void setLocatorColor(UUID uuid, LocatorBarColor color) {
        locatorColors.put(uuid, color.token());
        persistToDisk();
    }

    public boolean setLocatorHexColor(UUID uuid, String rawHex) {
        String normalized = LocatorBarColor.normalizeHex(rawHex);
        if (normalized == null) return false;
        locatorColors.put(uuid, normalized);
        persistToDisk();
        return true;
    }

    public boolean isTerritoryNotificationEnabled(UUID uuid) {
        return territoryNotifications.getOrDefault(uuid, true);
    }

    public void setTerritoryNotificationEnabled(UUID uuid, boolean enabled) {
        territoryNotifications.put(uuid, enabled);
        persistToDisk();
    }

    public boolean isClaimDenyNotificationEnabled(UUID uuid) {
        return claimDenyNotifications.getOrDefault(uuid, true);
    }

    public void setClaimDenyNotificationEnabled(UUID uuid, boolean enabled) {
        claimDenyNotifications.put(uuid, enabled);
        persistToDisk();
    }

    public boolean hasReceivedStarterKit(UUID uuid) {
        return starterKitReceived.contains(uuid);
    }

    public void markStarterKitReceived(UUID uuid) {
        starterKitReceived.add(uuid);
        persistToDisk();
    }

    private static UserSettingsManager loadFromDisk(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        UserSettingsManager manager = new UserSettingsManager(file);
        if (!Files.exists(file)) return manager;

        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null || !root.has("locatorColors") || !root.get("locatorColors").isJsonObject()) {
                return manager;
            }

            JsonObject colors = root.getAsJsonObject("locatorColors");
            for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    String token = entry.getValue().getAsString();
                    if (LocatorBarColor.isHexToken(token)) {
                        manager.locatorColors.put(uuid, LocatorBarColor.normalizeHex(token));
                    } else {
                        manager.locatorColors.put(uuid, LocatorBarColor.fromToken(token).token());
                    }
                } catch (Exception ignored) {
                    // Ignore malformed entries and keep gameplay stable.
                }
            }

            if (root.has("territoryNotifications") && root.get("territoryNotifications").isJsonObject()) {
                JsonObject notifications = root.getAsJsonObject("territoryNotifications");
                for (Map.Entry<String, JsonElement> entry : notifications.entrySet()) {
                    try {
                        manager.territoryNotifications.put(UUID.fromString(entry.getKey()), entry.getValue().getAsBoolean());
                    } catch (Exception ignored) {
                        // Ignore malformed entries and keep gameplay stable.
                    }
                }
            }

            if (root.has("claimDenyNotifications") && root.get("claimDenyNotifications").isJsonObject()) {
                JsonObject notifications = root.getAsJsonObject("claimDenyNotifications");
                for (Map.Entry<String, JsonElement> entry : notifications.entrySet()) {
                    try {
                        manager.claimDenyNotifications.put(UUID.fromString(entry.getKey()), entry.getValue().getAsBoolean());
                    } catch (Exception ignored) {
                        // Ignore malformed entries and keep gameplay stable.
                    }
                }
            }

            if (root.has("starterKitReceived") && root.get("starterKitReceived").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("starterKitReceived")) {
                    try {
                        manager.starterKitReceived.add(UUID.fromString(element.getAsString()));
                    } catch (Exception ignored) {
                        // Ignore malformed entries and keep gameplay stable.
                    }
                }
            }
        } catch (Exception ignored) {
            // Keep defaults if reading/parsing fails.
        }

        return manager;
    }

    private void persistToDisk() {
        try {
            Files.createDirectories(storageFile.getParent());

            JsonObject root = new JsonObject();
            JsonObject colors = new JsonObject();
            for (Map.Entry<UUID, String> entry : locatorColors.entrySet()) {
                String token = entry.getValue();
                if (LocatorBarColor.isHexToken(token)) {
                    colors.addProperty(entry.getKey().toString(), LocatorBarColor.normalizeHex(token));
                } else {
                    colors.addProperty(entry.getKey().toString(), LocatorBarColor.fromToken(token).token());
                }
            }
            root.add("locatorColors", colors);

            JsonObject territory = new JsonObject();
            for (Map.Entry<UUID, Boolean> entry : territoryNotifications.entrySet()) {
                territory.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("territoryNotifications", territory);

            JsonObject deny = new JsonObject();
            for (Map.Entry<UUID, Boolean> entry : claimDenyNotifications.entrySet()) {
                deny.addProperty(entry.getKey().toString(), entry.getValue());
            }
            root.add("claimDenyNotifications", deny);

            JsonArray starterKit = new JsonArray();
            for (UUID uuid : starterKitReceived) {
                starterKit.add(uuid.toString());
            }
            root.add("starterKitReceived", starterKit);

            Files.writeString(storageFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Persistence errors should not break commands.
        }
    }
}

