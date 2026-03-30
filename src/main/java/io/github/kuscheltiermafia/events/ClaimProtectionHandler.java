package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/**
 * Registers all claim-protection events:
 *  - Right-clicking a banner claims the chunk.
 *  - Block break, block place (via UseBlockCallback with block-item), and
 *    block interaction in claimed chunks require ownership / team membership.
 *  - PvP damage in a claimed chunk requires ownership / team membership of
 *    the ATTACKER.
 */
public class ClaimProtectionHandler {

    public static void register() {
        registerBannerClaim();
        registerBlockBreakProtection();
        registerUseBlockProtection();
        registerPvpProtection();
    }

    // -------------------------------------------------------------------------
    // Claim via banner right-click
    // -------------------------------------------------------------------------

    private static void registerBannerClaim() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            if (!(world.getBlockState(pos).getBlock() instanceof AbstractBannerBlock)) {
                return ActionResult.PASS;
            }

            // It's a banner – handle claim logic
            ChunkPos chunkPos = new ChunkPos(pos);
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData existing = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

            if (existing == null) {
                // Unclaimed – claim it
                boolean success = claims.addClaim(
                        dimId, chunkPos.x, chunkPos.z,
                        player.getUuid(), player.getGameProfile().getName(),
                        pos.getX(), pos.getY(), pos.getZ()
                );
                if (success) {
                    player.sendMessage(Text.literal(
                            "§aChunk claimed! Use §6/claim info §ato manage this claim."), false);
                } else {
                    player.sendMessage(Text.literal("§cThis chunk is already claimed."), false);
                }
                return ActionResult.SUCCESS; // override vanilla banner interaction
            }

            if (existing.getOwnerUuid().equals(player.getUuid())) {
                // Owner re-clicking: show info
                showClaimInfo(player, existing, chunkPos);
                return ActionResult.SUCCESS;
            }

            // Someone else's claim
            player.sendMessage(Text.literal("§cThis chunk is claimed by §e" + existing.getOwnerName() + "§c."), false);
            return ActionResult.SUCCESS;
        });
    }

    // -------------------------------------------------------------------------
    // Block-break protection
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
                // Owner breaking their own banner – remove the claim
                BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
                if (pos.equals(bannerPos)) {
                    claims.removeClaim(dimId, chunkPos.x, chunkPos.z);
                    player.sendMessage(Text.literal("§6Claim removed."), true);
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

            BlockPos pos = hitResult.getBlockPos();
            // Banner claims are handled in the first UseBlockCallback handler;
            // for other blocks, check claim protection.
            if (world.getBlockState(pos).getBlock() instanceof AbstractBannerBlock) {
                return ActionResult.PASS; // handled by the banner-claim handler
            }

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
