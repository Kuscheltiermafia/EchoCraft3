package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/**
 * Registers all claim-protection events:
 *  - Right-clicking any block while holding a Claim Banner item claims the chunk.
 *  - Block break, block interaction in claimed chunks require ownership / team membership.
 *  - PvP damage in a claimed chunk is blocked for non-owners / non-team-members.
 */
public class ClaimProtectionHandler {

    public static void register() {
        registerBannerClaim();
        registerBlockBreakProtection();
        registerUseBlockProtection();
        registerPvpProtection();
    }

    // -------------------------------------------------------------------------
    // Claim via Claim Banner item right-click
    // -------------------------------------------------------------------------

    private static void registerBannerClaim() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

            ItemStack stack = player.getMainHandStack();
            if (!stack.isOf(ModItems.CLAIM_BANNER)) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            ChunkPos chunkPos = new ChunkPos(pos);
            String dimId = serverWorld.getRegistryKey().getValue().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData existing = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

            if (existing == null) {
                boolean success = claims.addClaim(
                        dimId, chunkPos.x, chunkPos.z,
                        player.getUuid(), player.getGameProfile().getName(),
                        pos.getX(), pos.getY(), pos.getZ()
                );
                if (success) {
                    if (!player.isCreative()) {
                        stack.decrement(1);
                    }
                    player.sendMessage(Text.literal(
                            "§aChunk claimed! Use §6/claim info §ato manage this claim."), false);
                } else {
                    player.sendMessage(Text.literal("§cThis chunk is already claimed."), false);
                }
            } else if (existing.getOwnerUuid().equals(player.getUuid())) {
                showClaimInfo(player, existing, chunkPos);
            } else {
                player.sendMessage(Text.literal(
                        "§cThis chunk is claimed by §e" + existing.getOwnerName() + "§c."), false);
            }
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

            if (isAllowed(player, claim, serverWorld)) return true;

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

            // Claim banner item interaction is handled in the first handler above.
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
        player.sendMessage(Text.literal("§eOrigin: §f" + claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ()), false);
    }
}

