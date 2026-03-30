package io.github.kuscheltiermafia.claims;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimManager extends PersistentState {

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
        markDirty();
        return true;
    }

    public boolean removeClaim(String dimensionId, int chunkX, int chunkZ) {
        String key = makeKey(dimensionId, chunkX, chunkZ);
        if (claims.remove(key) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public void setTeam(String dimensionId, int chunkX, int chunkZ, String teamName) {
        ClaimData data = claims.get(makeKey(dimensionId, chunkX, chunkZ));
        if (data != null) {
            data.setTeamName(teamName);
            markDirty();
        }
    }

    // -------------------------------------------------------------------------
    // PersistentState serialisation
    // -------------------------------------------------------------------------

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound claimsNbt = new NbtCompound();
        for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
            ClaimData d = entry.getValue();
            NbtCompound c = new NbtCompound();
            c.putUuid("OwnerUuid", d.getOwnerUuid());
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

    public static ClaimManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        ClaimManager manager = new ClaimManager();
        NbtCompound claimsNbt = nbt.getCompound("Claims");
        for (String key : claimsNbt.getKeys()) {
            NbtCompound c = claimsNbt.getCompound(key);
            UUID ownerUuid = c.getUuid("OwnerUuid");
            String ownerName = c.getString("OwnerName");
            String dimensionId = c.getString("DimensionId");
            int bannerX = c.getInt("BannerX");
            int bannerY = c.getInt("BannerY");
            int bannerZ = c.getInt("BannerZ");
            ClaimData data = new ClaimData(ownerUuid, ownerName, dimensionId, bannerX, bannerY, bannerZ);
            if (c.contains("TeamName")) {
                data.setTeamName(c.getString("TeamName"));
            }
            manager.claims.put(key, data);
        }
        return manager;
    }

    // -------------------------------------------------------------------------
    // Server-wide singleton helper
    // -------------------------------------------------------------------------

    private static final PersistentState.Type<ClaimManager> TYPE = new PersistentState.Type<>(
            ClaimManager::new,
            ClaimManager::fromNbt,
            null
    );

    public static ClaimManager get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        PersistentStateManager stateManager = world.getPersistentStateManager();
        return stateManager.getOrCreate(TYPE, DATA_KEY);
    }
}
