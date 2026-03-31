package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;

public class ClaimExplosionProtectionHandler {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel world : server.getAllLevels()) {
                onEndWorldTick(world);
            }
        });
    }

    private static void onEndWorldTick(ServerLevel world) {
        ClaimManager claims = ClaimManager.get(world.getServer());
        restoreClaimBanners(world, claims);

        for (Entity entity : world.getAllEntities()) {
            if (!isExplosionEntity(entity)) continue;

            BlockPos pos = entity.blockPosition();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = world.dimension().toString();
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null || claim.isExplosionsAllowed()) continue;

            if (!shouldTriggerNow(entity)) continue;

            entity.discard();
            world.sendParticles(
                    ParticleTypes.FIREWORK,
                    pos.getX() + 0.5,
                    pos.getY() + 0.4,
                    pos.getZ() + 0.5,
                    70,
                    1.0,
                    0.8,
                    1.0,
                    0.15
            );
            world.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5,
                    pos.getY() + 0.4,
                    pos.getZ() + 0.5,
                    35,
                    0.8,
                    0.6,
                    0.8,
                    0.08
            );
            world.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private static void restoreClaimBanners(ServerLevel world, ClaimManager claims) {
        String dimId = world.dimension().toString();
        for (ClaimData claim : claims.getAllClaims()) {
            if (!dimId.equals(claim.getDimensionId())) continue;

            BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
            if (!world.getBlockState(bannerPos).is(Blocks.WHITE_BANNER) && !world.getBlockState(bannerPos).is(Blocks.WHITE_WALL_BANNER)) {
                removeVanillaBannerDrops(world, bannerPos);
                world.setBlock(bannerPos, Blocks.WHITE_BANNER.defaultBlockState(), 3);
            }
        }
    }

    private static void removeVanillaBannerDrops(ServerLevel world, BlockPos bannerPos) {
        AABB box = new AABB(bannerPos).inflate(0.8);
        for (ItemEntity itemEntity : world.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!itemEntity.getItem().is(Items.WHITE_BANNER)) continue;
            if (ModItems.isClaimBanner(itemEntity.getItem())) continue;
            itemEntity.discard();
        }
    }

    private static boolean shouldTriggerNow(Entity entity) {
        if (entity instanceof PrimedTnt tnt) {
            return tnt.getFuse() <= 1;
        }
        if (entity.getType() == EntityType.CREEPER) {
            return isCreeperAtFuseEnd(entity);
        }
        if (entity.getType() == EntityType.TNT_MINECART) {
            try {
                var method = entity.getClass().getMethod("getFuse");
                Object fuseValue = method.invoke(entity);
                if (fuseValue instanceof Integer fuse) {
                    return fuse <= 1 || entity.isOnFire();
                }
            } catch (Exception ignored) {
                // Fall back to immediate confetti behavior if mappings differ.
            }
            return true;
        }
        // Other explosive entities do not expose a clean fuse API; trigger immediately.
        return true;
    }

    private static boolean isCreeperAtFuseEnd(Entity entity) {
        try {
            var getSwelling = entity.getClass().getMethod("getSwelling", float.class);
            Object value = getSwelling.invoke(entity, 1.0f);
            if (value instanceof Float swelling) return swelling >= 1.0f;
        } catch (Exception ignored) {
            // Try no-arg variant next.
        }
        try {
            var getSwelling = entity.getClass().getMethod("getSwelling");
            Object value = getSwelling.invoke(entity);
            if (value instanceof Float swelling) return swelling >= 1.0f;
            if (value instanceof Integer swellInt) return swellInt >= 1;
        } catch (Exception ignored) {
            // Fallback below.
        }
        try {
            var getSwellDir = entity.getClass().getMethod("getSwellDir");
            Object value = getSwellDir.invoke(entity);
            if (value instanceof Integer dir) return dir > 0;
        } catch (Exception ignored) {
            // Conservative fallback.
        }
        return false;
    }

    private static boolean isExplosionEntity(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.TNT
                || type == EntityType.TNT_MINECART
                || type == EntityType.CREEPER
                || type == EntityType.WITHER_SKULL
                || type == EntityType.WITHER
                || type == EntityType.END_CRYSTAL;
    }
}

