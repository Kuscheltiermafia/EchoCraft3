package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

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
            if (world.isClient) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            ItemStack stack = player.getMainHandStack();
            if (!stack.isOf(ModItems.CLAIM_BANNER)) return ActionResult.PASS;

            Direction side = hitResult.getSide();
            // Cannot attach a banner to the underside of a block
            if (side == Direction.DOWN) return ActionResult.FAIL;

            // Position where the banner block will be placed
            BlockPos bannerPos = hitResult.getBlockPos().offset(side);

            // Ensure the target position is free
            if (!serverWorld.getBlockState(bannerPos).isReplaceable()) {
                player.sendMessage(Text.literal("§cNo room to place the Claim Banner here."), true);
                return ActionResult.FAIL;
            }

            ChunkPos chunkPos = new ChunkPos(bannerPos);
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData existing = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

            if (existing != null) {
                if (existing.getOwnerUuid().equals(player.getUuid())) {
                    showClaimInfo(player, existing, chunkPos);
                } else {
                    player.sendMessage(Text.literal(
                            "§cThis chunk is already claimed by §e" + existing.getOwnerName() + "§c."), false);
                }
                return ActionResult.FAIL;
            }

            // Place the banner block (standing on UP-face, wall banner otherwise)
            BlockState bannerState;
            if (side == Direction.UP) {
                // Convert player yaw to one of 16 banner rotation slots (0 = south, increases clockwise)
                int rotation = MathHelper.floor(player.getYaw() / 360.0f * 16.0f + 0.5f) & 15;
                bannerState = Blocks.WHITE_BANNER.getDefaultState()
                        .with(BannerBlock.ROTATION, rotation);
            } else {
                bannerState = Blocks.WHITE_WALL_BANNER.getDefaultState()
                        .with(WallBannerBlock.FACING, side);
            }
            serverWorld.setBlockState(bannerPos, bannerState);

            // Register claim anchored at the banner's position
            claims.addClaim(
                    dimId, chunkPos.x, chunkPos.z,
                    player.getUuid(), player.getGameProfile().getName(),
                    bannerPos.getX(), bannerPos.getY(), bannerPos.getZ()
            );

            if (!player.isCreative()) {
                stack.decrement(1);
            }

            player.sendMessage(Text.literal(
                    "§aChunk claimed! Break the banner to remove the claim."), false);
            return ActionResult.SUCCESS;
        });
    }

    // -------------------------------------------------------------------------
    // Block-break protection — owner breaking the claim banner unclaims the chunk
    // -------------------------------------------------------------------------

    private static void registerBlockBreakProtection() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient) return true;
            if (!(world instanceof ServerWorld serverWorld)) return true;

            ChunkPos chunkPos = new ChunkPos(pos);
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);
            if (claim == null) return true;

            if (isAllowed(player, claim, serverWorld)) {
                BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
                if (pos.equals(bannerPos)) {
                    // Remove block without vanilla loot drop
                    serverWorld.removeBlock(pos, false);
                    // Return the claim banner to the player (or drop it)
                    ItemScatterer.spawn(serverWorld,
                            pos.getX(), pos.getY(), pos.getZ(),
                            new ItemStack(ModItems.CLAIM_BANNER));
                    // Remove the claim data
                    claims.removeClaim(dimId, chunkPos.x, chunkPos.z);
                    player.sendMessage(Text.literal("§6Claim removed. Banner returned."), true);
                    return false; // cancel vanilla break (block already gone)
                }
                return true;
            }

            player.sendMessage(Text.literal("§cYou cannot break blocks in this claimed chunk!"), true);
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Block-use (interact / place) protection
    // -------------------------------------------------------------------------

    private static void registerUseBlockProtection() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            // Claim banner placement is handled in the first handler above
            if (player.getStackInHand(hand).isOf(ModItems.CLAIM_BANNER)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            ChunkPos chunkPos = new ChunkPos(pos);
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);
            if (claim == null) return ActionResult.PASS;

            if (isAllowed(player, claim, serverWorld)) return ActionResult.PASS;

            player.sendMessage(Text.literal("§cYou cannot interact with blocks in this claimed chunk!"), true);
            return ActionResult.FAIL;
        });
    }

    // -------------------------------------------------------------------------
    // PvP protection in claimed chunks
    // -------------------------------------------------------------------------

    private static void registerPvpProtection() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(target instanceof PlayerEntity)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            ChunkPos chunkPos = new ChunkPos(target.getBlockPos());
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);
            if (claim == null) return ActionResult.PASS;

            if (isAllowed(player, claim, serverWorld)) return ActionResult.PASS;

            player.sendMessage(Text.literal("§cYou cannot attack players in this claimed chunk!"), true);
            return ActionResult.FAIL;
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isAllowed(PlayerEntity player, ClaimData claim, ServerWorld world) {
        UUID uuid = player.getUuid();
        if (claim.getOwnerUuid().equals(uuid)) return true;
        if (player instanceof ServerPlayerEntity sp && sp.hasPermissionLevel(2)) return true;
        String teamName = claim.getTeamName();
        if (teamName != null) {
            TeamManager teams = TeamManager.get(world.getServer());
            return teams.isMember(teamName, uuid);
        }
        return false;
    }

    private static void showClaimInfo(PlayerEntity player, ClaimData claim, ChunkPos chunkPos) {
        player.sendMessage(Text.literal("§6=== Claim Info ==="), false);
        player.sendMessage(Text.literal("§eChunk: §f" + chunkPos.x + ", " + chunkPos.z), false);
        player.sendMessage(Text.literal("§eOwner: §f" + claim.getOwnerName()), false);
        player.sendMessage(Text.literal("§eTeam: §f" + (claim.getTeamName() != null ? claim.getTeamName() : "none")), false);
        player.sendMessage(Text.literal("§eBanner: §f" + claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ()), false);
    }
}

