package io.github.kuscheltiermafia.events;

import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import io.github.kuscheltiermafia.users.UserSettingsManager;
import io.github.kuscheltiermafia.util.TextPalette;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers all claim-protection events:
 *  - Placing a Claim Banner item on a block surface claims the chunk;
 *    the banner block stays in the world as the claim marker.
 *  - Owner (or op) breaking that banner block removes the claim and returns the item.
 *  - Block break / interact / PvP in claimed chunks is denied for non-members.
 */
public class ClaimProtectionHandler {
    private static final Map<UUID, String> LAST_TEAM_TERRITORY = new HashMap<>();

    public static void register() {
        registerBannerPlace();
        registerBlockBreakProtection();
        registerUseBlockProtection();
        registerUseEntityProtection();
        registerPvpProtection();
        registerTerritoryActionbars();
    }

    // -------------------------------------------------------------------------
    // Place Claim Banner to claim the chunk
    // -------------------------------------------------------------------------

    private static void registerBannerPlace() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(world instanceof ServerLevel serverWorld)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            ItemStack stack = player.getMainHandItem();
            if (!ModItems.isClaimBanner(stack)) return InteractionResult.PASS;

            Direction side = hitResult.getDirection();
            // Cannot attach a banner to the underside of a block
            if (side == Direction.DOWN) return InteractionResult.FAIL;

            // Position where the banner block will be placed
            BlockPos bannerPos = hitResult.getBlockPos().relative(side);

            // Ensure the target position is free (check if air)
            if (!serverWorld.getBlockState(bannerPos).isAir()) {
                player.sendSystemMessage(TextPalette.white("No room to place the Claim Banner here."));
                return InteractionResult.FAIL;
            }

            int chunkX = bannerPos.getX() >> 4;
            int chunkZ = bannerPos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            TeamManager teams = TeamManager.get(serverWorld.getServer());
            TeamData playerTeam = teams.getTeamForPlayer(player.getUUID());
            if (playerTeam == null) {
                player.sendSystemMessage(TextPalette.white("You must be in a team to place a Claim Banner."));
                return InteractionResult.FAIL;
            }
            ClaimData existing = claims.getClaim(dimId, chunkX, chunkZ);

            if (existing != null) {
                sendActionbar(player, TextPalette.join(
                        TextPalette.white("This chunk is already claimed by: "),
                        resolveClaimTeamDisplay(existing, serverWorld)
                ));
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
            claims.setTeam(dimId, chunkX, chunkZ, playerTeam.getName());
            // Use another claim of the same team as template so new claims inherit current team rules.
            claims.cloneSettingsFromExistingTeamClaim(playerTeam.getName(), dimId, chunkX, chunkZ);

            if (!player.isCreative()) {
                stack.shrink(1);
            }

            player.sendSystemMessage(TextPalette.join(
                    TextPalette.white("Chunk claimed! Break the "),
                    TextPalette.yellow("banner"),
                    TextPalette.white(" to remove the claim.")
            ));
            return InteractionResult.SUCCESS;
        });
    }

    // -------------------------------------------------------------------------
    // Block-break protection — owner breaking the claim banner unclaims the chunk
    // -------------------------------------------------------------------------

    private static void registerBlockBreakProtection() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, _state, _blockEntity) -> {
            if (!(world instanceof ServerLevel serverWorld)) return true;

            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return true;

            AccessRelation relation = relation(player, claim, serverWorld);
            if (relation == AccessRelation.TEAM) {
                BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
                if (pos.equals(bannerPos)) {
                    if (!canManageClaim(player, claim, serverWorld)) {
                        player.sendSystemMessage(TextPalette.white("Only the claim owner, team Moderator, or team Leader can remove this claim."));
                        return false;
                    }
                    // Remove block without vanilla loot drop
                    serverWorld.destroyBlock(pos, false);
                    // Return the claim banner to the player (or drop it)
                    Containers.dropItemStack(serverWorld,
                            pos.getX(), pos.getY(), pos.getZ(),
                            ModItems.createClaimBannerStack());
                    // Remove the claim data
                    claims.removeClaim(dimId, chunkX, chunkZ);
                    player.sendSystemMessage(TextPalette.white("Claim removed. Banner returned."));
                    return false; // cancel vanilla break (block already gone)
                }
                return true;
            }

            if (relation == AccessRelation.ALLY && claim.isAllyBreakAllowed()) return true;

            if (claim.isForeignBreakAllowed()) return true;

            sendClaimDenyActionbar(player, claim, serverWorld);
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
            if (ModItems.isClaimBanner(player.getItemInHand(hand))) return InteractionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return InteractionResult.PASS;

            AccessRelation relation = relation(player, claim, serverWorld);
            if (relation == AccessRelation.TEAM) return InteractionResult.PASS;

            BlockState clickedState = serverWorld.getBlockState(pos);
            if (isAlwaysAllowedUtilityBlock(clickedState)) {
                return InteractionResult.PASS;
            }

            if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
                if (relation == AccessRelation.ALLY && claim.isAllyPlaceAllowed()) return InteractionResult.PASS;
                if (relation == AccessRelation.FOREIGN && claim.isForeignPlaceAllowed()) return InteractionResult.PASS;
                sendClaimDenyActionbar(player, claim, serverWorld);
                return InteractionResult.FAIL;
            }

            if (relation == AccessRelation.ALLY && claim.isAllyInteractAllowed()) return InteractionResult.PASS;
            if (relation == AccessRelation.FOREIGN && claim.isForeignInteractAllowed()) return InteractionResult.PASS;

            sendClaimDenyActionbar(player, claim, serverWorld);
            return InteractionResult.FAIL;
        });
    }

    private static void registerUseEntityProtection() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(world instanceof ServerLevel serverWorld)) return InteractionResult.PASS;
            if (entity instanceof Player && entity.getUUID().equals(player.getUUID())) return InteractionResult.PASS;

            BlockPos pos = entity.blockPosition();
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return InteractionResult.PASS;

            AccessRelation relation = relation(player, claim, serverWorld);
            if (relation == AccessRelation.TEAM) return InteractionResult.PASS;
            if (relation == AccessRelation.ALLY && claim.isAllyEntityAllowed()) return InteractionResult.PASS;
            if (relation == AccessRelation.FOREIGN && claim.isForeignEntityAllowed()) return InteractionResult.PASS;

            sendClaimDenyActionbar(player, claim, serverWorld);
            return InteractionResult.FAIL;
        });
    }

    // -------------------------------------------------------------------------
    // PvP protection in claimed chunks
    // -------------------------------------------------------------------------

    private static void registerPvpProtection() {
        AttackEntityCallback.EVENT.register((player, world, _hand, target, _hitResult) -> {
            if (!(world instanceof ServerLevel serverWorld)) return InteractionResult.PASS;

            int chunkX = target.blockPosition().getX() >> 4;
            int chunkZ = target.blockPosition().getZ() >> 4;
            String dimId = serverWorld.dimension().toString();
            ClaimManager claims = ClaimManager.get(serverWorld.getServer());
            ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
            if (claim == null) return InteractionResult.PASS;

            AccessRelation relation = relation(player, claim, serverWorld);
            if (relation == AccessRelation.TEAM) return InteractionResult.PASS;

            if (target instanceof Player) {
                if (claim.isPvpAllowed()) return InteractionResult.PASS;
                if (relation == AccessRelation.ALLY && claim.isAllyEntityAllowed()) return InteractionResult.PASS;
                if (relation == AccessRelation.FOREIGN && claim.isForeignEntityAllowed()) return InteractionResult.PASS;

                sendClaimDenyActionbar(player, claim, serverWorld);
                return InteractionResult.FAIL;
            }

            if (relation == AccessRelation.ALLY && claim.isAllyEntityAllowed()) return InteractionResult.PASS;
            if (relation == AccessRelation.FOREIGN && claim.isForeignEntityAllowed()) return InteractionResult.PASS;

            sendClaimDenyActionbar(player, claim, serverWorld);
            return InteractionResult.FAIL;
        });
    }

    private static void registerTerritoryActionbars() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            UserSettingsManager userSettings = UserSettingsManager.get(server);
            for (ServerLevel world : server.getAllLevels()) {
                ClaimManager claims = ClaimManager.get(server);
                for (net.minecraft.server.level.ServerPlayer player : world.players()) {
                    UUID uuid = player.getUUID();
                    String previousTeam = LAST_TEAM_TERRITORY.get(uuid);
                    String currentTeam = currentClaimTeam(player, claims);
                    if ((previousTeam == null && currentTeam == null)
                            || (previousTeam != null && previousTeam.equalsIgnoreCase(currentTeam))) {
                        continue;
                    }

                    if (userSettings.isTerritoryNotificationEnabled(uuid)) {
                        TeamManager teams = TeamManager.get(server);
                        if (previousTeam != null && (currentTeam == null || !previousTeam.equalsIgnoreCase(currentTeam))) {
                            sendActionbar(player, TextPalette.join(
                                    TextPalette.white("You are now leaving "),
                                    teams.getDisplayComponent(previousTeam),
                                    TextPalette.white(" territory")
                            ));
                        }
                        if (currentTeam != null && (previousTeam == null || !currentTeam.equalsIgnoreCase(previousTeam))) {
                            sendActionbar(player, TextPalette.join(
                                    TextPalette.white("You are now entering "),
                                    teams.getDisplayComponent(currentTeam),
                                    TextPalette.white(" territory")
                            ));
                        }
                    }

                    if (currentTeam == null) {
                        LAST_TEAM_TERRITORY.remove(uuid);
                    } else {
                        LAST_TEAM_TERRITORY.put(uuid, currentTeam);
                    }
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AccessRelation relation(Player player, ClaimData claim, ServerLevel world) {
        String claimTeam = claim.getTeamName();
        if (claimTeam == null) return AccessRelation.FOREIGN;

        TeamManager teams = TeamManager.get(world.getServer());
        TeamData actorTeam = teams.getTeamForPlayer(player.getUUID());
        if (actorTeam == null) return AccessRelation.FOREIGN;
        if (actorTeam.getName().equalsIgnoreCase(claimTeam)) return AccessRelation.TEAM;
        if (teams.areAllies(claimTeam, actorTeam.getName())) return AccessRelation.ALLY;
        return AccessRelation.FOREIGN;
    }

    private static boolean canManageClaim(Player player, ClaimData claim, ServerLevel world) {
        UUID uuid = player.getUUID();
        if (claim.getTeamName() == null) return false;
        TeamManager teams = TeamManager.get(world.getServer());
        return teams.canManageClaims(claim.getTeamName(), uuid);
    }

    private enum AccessRelation {
        TEAM,
        ALLY,
        FOREIGN
    }

    private static String currentClaimTeam(net.minecraft.server.level.ServerPlayer player, ClaimManager claims) {
        BlockPos pos = player.blockPosition();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String dimId = player.level().dimension().toString();
        ClaimData claim = claims.getClaim(dimId, chunkX, chunkZ);
        return claim != null ? claim.getTeamName() : null;
    }

    private static void sendClaimDenyActionbar(Player player, ClaimData claim, ServerLevel world) {
        UserSettingsManager settings = UserSettingsManager.get(world.getServer());
        if (!settings.isClaimDenyNotificationEnabled(player.getUUID())) {
            return;
        }
        sendActionbar(player, TextPalette.join(
                TextPalette.red("You can't do that in "),
                resolveClaimTeamDisplay(claim, world),
                TextPalette.red(" territory")
        ));
    }

    private static Component resolveClaimTeamDisplay(ClaimData claim, ServerLevel world) {
        if (claim.getTeamName() == null || claim.getTeamName().isBlank()) {
            return TextPalette.white(claim.getOwnerName());
        }
        TeamManager teams = TeamManager.get(world.getServer());
        return teams.getDisplayComponent(claim.getTeamName());
    }

    private static void sendActionbar(Player player, Component message) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
            return;
        }
        player.sendSystemMessage(message);
    }

    private static boolean isAlwaysAllowedUtilityBlock(BlockState state) {
        return state.getBlock() instanceof CraftingTableBlock
                || state.getBlock() instanceof SmithingTableBlock
                || state.getBlock() instanceof AnvilBlock;
    }

    private static void showClaimInfo(Player player, ClaimData claim, int chunkX, int chunkZ) {
        player.sendSystemMessage(TextPalette.yellow("=== Claim Info ==="));
        player.sendSystemMessage(TextPalette.join(TextPalette.yellow("Chunk: "), TextPalette.white(chunkX + ", " + chunkZ)));
        if (player.level() instanceof ServerLevel serverLevel) {
            player.sendSystemMessage(TextPalette.join(
                    TextPalette.yellow("Owner: "),
                    TextPalette.player(claim.getOwnerName(), claim.getOwnerUuid(), serverLevel.getServer())
            ));
        } else {
            player.sendSystemMessage(TextPalette.join(TextPalette.yellow("Owner: "), TextPalette.white(claim.getOwnerName())));
        }
        player.sendSystemMessage(TextPalette.join(
                TextPalette.yellow("Team: "),
                player.level() instanceof ServerLevel serverLevel && claim.getTeamName() != null
                        ? TeamManager.get(serverLevel.getServer()).getDisplayComponent(claim.getTeamName())
                        : TextPalette.white(claim.getTeamName() != null ? claim.getTeamName() : "None")
        ));
        player.sendSystemMessage(TextPalette.join(
                TextPalette.yellow("Banner: "),
                TextPalette.white(claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ())
        ));
    }
}

