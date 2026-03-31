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
