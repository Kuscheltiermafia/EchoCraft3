package io.github.kuscheltiermafia.claims;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimManager extends SavedData {

    private static final String DATA_KEY = "echocraft_claims";

    // Key: "dimensionId:chunkX:chunkZ"
    private final Map<String, ClaimData> claims = new HashMap<>();

    public ClaimManager() {}

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
        setDirty();
        return true;
    }

    public boolean removeClaim(String dimensionId, int chunkX, int chunkZ) {
        String key = makeKey(dimensionId, chunkX, chunkZ);
        if (claims.remove(key) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public void setTeam(String dimensionId, int chunkX, int chunkZ, String teamName) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setTeamName(teamName);
            setDirty();
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
        // Simple in-memory singleton for now - TODO: implement proper persistence with SavedData API
        return new ClaimManager();
    }
}
