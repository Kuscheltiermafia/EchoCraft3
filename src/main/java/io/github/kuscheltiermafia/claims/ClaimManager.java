package io.github.kuscheltiermafia.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.WeakHashMap;

public class ClaimManager extends SavedData {

    private static final String FILE_NAME = "echocraft_claims.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, ClaimManager> RUNTIME_INSTANCES = new WeakHashMap<>();

    // Key: "dimensionId:chunkX:chunkZ"
    private final Map<String, ClaimData> claims = new HashMap<>();
    private final Path storageFile;

    public ClaimManager() {
        this.storageFile = null;
    }

    private ClaimManager(Path storageFile) {
        this.storageFile = storageFile;
    }

    /**
     * Build a lookup key from dimension, chunk coordinates.
     */
    public static String makeKey(String dimensionId, int chunkX, int chunkZ) {
        return dimensionId + ":" + chunkX + ":" + chunkZ;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean hasClaim(String dimensionId, int chunkX, int chunkZ) {
        return claims.containsKey(makeKey(dimensionId, chunkX, chunkZ));
    }

    public ClaimData getClaim(String dimensionId, int chunkX, int chunkZ) {
        return claims.get(makeKey(dimensionId, chunkX, chunkZ));
    }

    public List<ClaimData> getAllClaims() {
        return List.copyOf(claims.values());
    }

    /**
     * Adds a new claim. Returns false if the chunk is already claimed.
     */
    public boolean addClaim(String dimensionId, int chunkX, int chunkZ,
                            UUID ownerUuid, String ownerName,
                            int bannerX, int bannerY, int bannerZ) {
        String key = makeKey(dimensionId, chunkX, chunkZ);
        if (claims.containsKey(key)) return false;
        claims.put(key, new ClaimData(ownerUuid, ownerName, dimensionId, bannerX, bannerY, bannerZ));
        markDirtyAndPersist();
        return true;
    }

    public boolean removeClaim(String dimensionId, int chunkX, int chunkZ) {
        String key = makeKey(dimensionId, chunkX, chunkZ);
        if (claims.remove(key) != null) {
            markDirtyAndPersist();
            return true;
        }
        return false;
    }

    public void setTeam(String dimensionId, int chunkX, int chunkZ, String teamName) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setTeamName(teamName);
            markDirtyAndPersist();
        }
    }

    public void setExplosionsAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setExplosionsAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setPvpAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setPvpAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setForeignBreakAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setForeignBreakAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setForeignPlaceAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setForeignPlaceAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setForeignInteractAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setForeignInteractAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setForeignEntityAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setForeignEntityAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setAllyBreakAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setAllyBreakAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setAllyPlaceAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setAllyPlaceAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setAllyInteractAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setAllyInteractAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    public void setAllyEntityAllowed(String dimensionId, int chunkX, int chunkZ, boolean allowed) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setAllyEntityAllowed(allowed);
            markDirtyAndPersist();
        }
    }

    /**
     * Applies one claim setting to every chunk claimed by the given team.
     *
     * @return number of claims that were updated, or -1 for unknown setting key.
     */
    public int setTeamSetting(String teamName, String settingKey, boolean enabled) {
        if (teamName == null || teamName.isBlank()) return 0;

        int changed = 0;
        for (ClaimData data : claims.values()) {
            if (data.getTeamName() == null) continue;
            if (!teamName.equalsIgnoreCase(data.getTeamName())) continue;

            boolean supported = applySetting(data, settingKey, enabled);
            if (!supported) return -1;
            changed++;
        }

        if (changed > 0) {
            markDirtyAndPersist();
        }
        return changed;
    }

    private static boolean applySetting(ClaimData data, String key, boolean enabled) {
        switch (key) {
            case "explosions" -> data.setExplosionsAllowed(enabled);
            case "pvp" -> data.setPvpAllowed(enabled);
            case "foreign_break" -> data.setForeignBreakAllowed(enabled);
            case "foreign_place" -> data.setForeignPlaceAllowed(enabled);
            case "foreign_interact" -> data.setForeignInteractAllowed(enabled);
            case "foreign_entity" -> data.setForeignEntityAllowed(enabled);
            case "ally_break" -> data.setAllyBreakAllowed(enabled);
            case "ally_place" -> data.setAllyPlaceAllowed(enabled);
            case "ally_interact" -> data.setAllyInteractAllowed(enabled);
            case "ally_entity" -> data.setAllyEntityAllowed(enabled);
            default -> {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // SavedData serialisation
    // -------------------------------------------------------------------------

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag claimsNbt = new CompoundTag();
        for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
            ClaimData d = entry.getValue();
            CompoundTag c = new CompoundTag();
            c.putString("OwnerUuid", d.getOwnerUuid().toString());
            c.putString("OwnerName", d.getOwnerName());
            c.putString("DimensionId", d.getDimensionId());
            c.putInt("BannerX", d.getBannerX());
            c.putInt("BannerY", d.getBannerY());
            c.putInt("BannerZ", d.getBannerZ());
            if (d.getTeamName() != null) {
                c.putString("TeamName", d.getTeamName());
            }
            c.putBoolean("ExplosionsAllowed", d.isExplosionsAllowed());
            c.putBoolean("PvpAllowed", d.isPvpAllowed());
            c.putBoolean("ForeignBreakAllowed", d.isForeignBreakAllowed());
            c.putBoolean("ForeignPlaceAllowed", d.isForeignPlaceAllowed());
            c.putBoolean("ForeignInteractAllowed", d.isForeignInteractAllowed());
            c.putBoolean("ForeignEntityAllowed", d.isForeignEntityAllowed());
            c.putBoolean("AllyBreakAllowed", d.isAllyBreakAllowed());
            c.putBoolean("AllyPlaceAllowed", d.isAllyPlaceAllowed());
            c.putBoolean("AllyInteractAllowed", d.isAllyInteractAllowed());
            c.putBoolean("AllyEntityAllowed", d.isAllyEntityAllowed());
            claimsNbt.put(entry.getKey(), c);
        }
        nbt.put("Claims", claimsNbt);
        return nbt;
    }

    public static ClaimManager load(CompoundTag nbt, HolderLookup.Provider registries) {
        ClaimManager manager = new ClaimManager();
        if (!nbt.contains("Claims")) return manager;
        
        try {
            // Simple approach - just return empty for now
            // Proper NBT loading would require understanding Minecraft 26.1's NBT API
        } catch (Exception e) {
            // If loading fails, return empty manager
        }
        return manager;
    }

    // -------------------------------------------------------------------------
    // Server-wide singleton helper
    // -------------------------------------------------------------------------

    public static ClaimManager get(MinecraftServer server) {
        return RUNTIME_INSTANCES.computeIfAbsent(server, ClaimManager::loadFromDisk);
    }

    private static ClaimManager loadFromDisk(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        ClaimManager manager = new ClaimManager(file);
        if (!Files.exists(file)) return manager;

        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null || !root.has("claims") || !root.get("claims").isJsonArray()) return manager;

            JsonArray claimsArray = root.getAsJsonArray("claims");
            for (JsonElement element : claimsArray) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();

                String dim = obj.get("dimensionId").getAsString();
                int chunkX = obj.get("chunkX").getAsInt();
                int chunkZ = obj.get("chunkZ").getAsInt();
                UUID ownerUuid = UUID.fromString(obj.get("ownerUuid").getAsString());
                String ownerName = obj.get("ownerName").getAsString();

                ClaimData data = new ClaimData(
                        ownerUuid,
                        ownerName,
                        dim,
                        obj.get("bannerX").getAsInt(),
                        obj.get("bannerY").getAsInt(),
                        obj.get("bannerZ").getAsInt()
                );
                if (obj.has("teamName") && !obj.get("teamName").isJsonNull()) {
                    data.setTeamName(obj.get("teamName").getAsString());
                }
                data.setExplosionsAllowed(getBool(obj, "explosionsAllowed", false));
                data.setPvpAllowed(getBool(obj, "pvpAllowed", false));
                data.setForeignBreakAllowed(getBool(obj, "foreignBreakAllowed", false));
                data.setForeignPlaceAllowed(getBool(obj, "foreignPlaceAllowed", false));
                data.setForeignInteractAllowed(getBool(obj, "foreignInteractAllowed", false));
                data.setForeignEntityAllowed(getBool(obj, "foreignEntityAllowed", false));
                data.setAllyBreakAllowed(getBool(obj, "allyBreakAllowed", true));
                data.setAllyPlaceAllowed(getBool(obj, "allyPlaceAllowed", true));
                data.setAllyInteractAllowed(getBool(obj, "allyInteractAllowed", true));
                data.setAllyEntityAllowed(getBool(obj, "allyEntityAllowed", true));

                manager.claims.put(makeKey(dim, chunkX, chunkZ), data);
            }
        } catch (Exception ignored) {
            // Keep empty manager on parsing errors.
        }

        return manager;
    }

    private static boolean getBool(JsonObject obj, String key, boolean fallback) {
        if (!obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void markDirtyAndPersist() {
        setDirty();
        persistToDisk();
    }

    private void persistToDisk() {
        if (storageFile == null) return;
        try {
            Files.createDirectories(storageFile.getParent());

            JsonArray claimsArray = new JsonArray();
            for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length < 3) continue;

                ClaimData data = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("dimensionId", data.getDimensionId());
                obj.addProperty("chunkX", Integer.parseInt(parts[parts.length - 2]));
                obj.addProperty("chunkZ", Integer.parseInt(parts[parts.length - 1]));
                obj.addProperty("ownerUuid", data.getOwnerUuid().toString());
                obj.addProperty("ownerName", data.getOwnerName());
                obj.addProperty("bannerX", data.getBannerX());
                obj.addProperty("bannerY", data.getBannerY());
                obj.addProperty("bannerZ", data.getBannerZ());
                if (data.getTeamName() != null) {
                    obj.addProperty("teamName", data.getTeamName());
                }
                obj.addProperty("explosionsAllowed", data.isExplosionsAllowed());
                obj.addProperty("pvpAllowed", data.isPvpAllowed());
                obj.addProperty("foreignBreakAllowed", data.isForeignBreakAllowed());
                obj.addProperty("foreignPlaceAllowed", data.isForeignPlaceAllowed());
                obj.addProperty("foreignInteractAllowed", data.isForeignInteractAllowed());
                obj.addProperty("foreignEntityAllowed", data.isForeignEntityAllowed());
                obj.addProperty("allyBreakAllowed", data.isAllyBreakAllowed());
                obj.addProperty("allyPlaceAllowed", data.isAllyPlaceAllowed());
                obj.addProperty("allyInteractAllowed", data.isAllyInteractAllowed());
                obj.addProperty("allyEntityAllowed", data.isAllyEntityAllowed());
                claimsArray.add(obj);
            }

            JsonObject root = new JsonObject();
            root.add("claims", claimsArray);
            Files.writeString(storageFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Ignore persistence failures during gameplay.
        }
    }
}
