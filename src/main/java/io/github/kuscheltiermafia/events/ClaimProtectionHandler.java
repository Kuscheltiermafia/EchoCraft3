package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.UUID;

/**
 * Registers all claim-protection events:
 *  - Placing a Claim Banner item on a block surface claims the chunk;
 *    the banner block stays in the world as the claim marker.
 *  - Owner (or op) breaking that banner block removes the claim and returns the item.
 *  - Block break / interact / PvP in claimed chunks is denied for non-members.
 */
public class ClaimProtectionHandler {

    public static void register() {
        registerBannerPlace();
        registerBlockBreakProtection();
        registerUseBlockProtection();
        registerPvpProtection();
    }

    // -------------------------------------------------------------------------
    // Place Claim Banner to claim the chunk
    // -------------------------------------------------------------------------

    private static void registerBannerPlace() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world instanceof ServerLevel serverWorld) {
                // only run on server
            } else {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.CLAIM_BANNER)) return InteractionResult.PASS;

            Direction side = hitResult.getDirection();
            // Cannot attach a banner to the underside of a block
            if (side == Direction.DOWN) return InteractionResult.FAIL;

            // Position where the banner block will be placed
            BlockPos bannerPos = hitResult.getBlockPos().relative(side);

            // Ensure the target position is free (check if air)
            if (!serverWorld.getBlockState(bannerPos).isAir()) {
                player.sendSystemMessage(Component.literal("§cNo room to place the Claim Banner here."));
                return InteractionResult.FAIL;
            }

            int chunkX = bannerPos.getX() >> 4;
            int chunkZ = bannerPos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData existing = claims.getClaim(dimId, chunkX, chunkZ);

            if (existing != null) {
                if (existing.getOwnerUuid().equals(player.getUUID())) {
                    showClaimInfo(player, existing, chunkX, chunkZ);
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§cThis chunk is already claimed by §e" + existing.getOwnerName() + "§c."));
                }
                return InteractionResult.FAIL;
            }

            // Place the banner block (standing on UP-face, wall banner otherwise)
            BlockState bannerState;
            if (side == Direction.UP) {
                // Convert player yaw to one of 16 banner rotation slots (0 = south, increases clockwise)
                int rotation = Mth.floor(player.getXRot() / 360.0f * 16.0f + 0.5f) & 15;
                bannerState = Blocks.WHITE_BANNER.defaultBlockState()
                        .setValue(BannerBlock.ROTATION, rotation);
            } else {
                bannerState = Blocks.WHITE_WALL_BANNER.defaultBlockState()
                        .setValue(WallBannerBlock.FACING, side);
            }
            serverWorld.setBlock(bannerPos, bannerState, 3);

            // Register claim anchored at the banner's position
            claims.addClaim(
                    dimId, chunkX, chunkZ,
                    player.getUUID(), player.getName().getString(),
                    bannerPos.getX(), bannerPos.getY(), bannerPos.getZ()
            );

            if (!player.isCreative()) {
                stack.shrink(1);
            }

            player.sendSystemMessage(Component.literal(
                    "§aChunk claimed! Break the banner to remove the claim."));
            return InteractionResult.SUCCESS;
        });
    }

    // -------------------------------------------------------------------------
    // Block-break protection — owner breaking the claim banner unclaims the chunk
    // -------------------------------------------------------------------------

    private static void registerBlockBreakProtection() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel serverWorld)) return true;

            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return true;

            if (isAllowed(player, claim, serverWorld)) {
                BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
                if (pos.equals(bannerPos)) {
                    // Remove block without vanilla loot drop
                    serverWorld.destroyBlock(pos, false);
                    // Return the claim banner to the player (or drop it)
                    Containers.dropItemStack(serverWorld,
                            pos.getX(), pos.getY(), pos.getZ(),
                            new ItemStack(ModItems.CLAIM_BANNER));
                    // Remove the claim data
                    claims.removeClaim(dimId, chunkX, chunkZ);
                    player.sendSystemMessage(Component.literal("§6Claim removed. Banner returned."));
                    return false; // cancel vanilla break (block already gone)
                }
                return true;
            }

            player.sendSystemMessage(Component.literal("§cYou cannot break blocks in this claimed chunk!"));
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Block-use (interact / place) protection
    // -------------------------------------------------------------------------

    private static void registerUseBlockProtection() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerLevel serverWorld)) return InteractionResult.PASS;

            // Claim banner placement is handled in the first handler above
            if (player.getItemInHand(hand).is(ModItems.CLAIM_BANNER)) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return InteractionResult.PASS;

            if (isAllowed(player, claim, serverWorld)) return InteractionResult.PASS;

            player.sendSystemMessage(Component.literal("§cYou cannot interact with blocks in this claimed chunk!"));
            return InteractionResult.FAIL;
        });
    }

    // -------------------------------------------------------------------------
    // PvP protection in claimed chunks
    // -------------------------------------------------------------------------

    private static void registerPvpProtection() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (!(target instanceof Player)) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel serverWorld)) return InteractionResult.PASS;

            int chunkX = target.blockPosition().getX() >> 4;
            int chunkZ = target.blockPosition().getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return InteractionResult.PASS;

            if (isAllowed(player, claim, serverWorld)) return InteractionResult.PASS;

            player.sendSystemMessage(Component.literal("§cYou cannot attack players in this claimed chunk!"));
            return InteractionResult.FAIL;
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isAllowed(Player player, ClaimData claim, ServerLevel world) {
        UUID uuid = player.getUUID();
        if (claim.getOwnerUuid().equals(uuid)) return true;
        // TODO: Add op check back when API is found
        String teamName = claim.getTeamName();
        if (teamName != null) {
            TeamManager teams = TeamManager.get(world.getServer());
            return teams.isMember(teamName, uuid);
        }
        return false;
    }

    private static void showClaimInfo(Player player, ClaimData claim, int chunkX, int chunkZ) {
        player.sendSystemMessage(Component.literal("§6=== Claim Info ==="));
        player.sendSystemMessage(Component.literal("§eChunk: §f" + chunkX + ", " + chunkZ));
        player.sendSystemMessage(Component.literal("§eOwner: §f" + claim.getOwnerName()));
        player.sendSystemMessage(Component.literal("§eTeam: §f" + (claim.getTeamName() != null ? claim.getTeamName() : "none")));
        player.sendSystemMessage(Component.literal("§eBanner: §f" + claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ()));
    }
}

