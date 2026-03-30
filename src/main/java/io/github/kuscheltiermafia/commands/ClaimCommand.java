package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

/**
 * /claim info         – shows claim info for the chunk the player is standing in
 * /claim team <name>  – links the current chunk's claim to a team
 * /claim remove       – removes the current chunk's claim (owner or op only)
 */
public class ClaimCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {
        dispatcher.register(
                CommandManager.literal("claim")
                        .then(CommandManager.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
                        .then(CommandManager.literal("team")
                                .then(CommandManager.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> linkTeam(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(CommandManager.literal("remove")
                                .executes(ctx -> remove(ctx.getSource())))
        );
    }

    // -------------------------------------------------------------------------

    private static int info(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        String dimId = world.getRegistryKey().getValue().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

        if (claim == null) {
            source.sendFeedback(() -> Text.literal("§aThis chunk is §eunclaimed§a."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6=== Claim Info ==="), false);
        source.sendFeedback(() -> Text.literal("§eChunk: §f" + chunkPos.x + ", " + chunkPos.z), false);
        source.sendFeedback(() -> Text.literal("§eDimension: §f" + dimId), false);
        source.sendFeedback(() -> Text.literal("§eOwner: §f" + claim.getOwnerName()), false);
        source.sendFeedback(() -> Text.literal("§eTeam: §f"
                + (claim.getTeamName() != null ? claim.getTeamName() : "none")), false);
        source.sendFeedback(() -> Text.literal("§eBanner: §f"
                + claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ()), false);
        return 1;
    }

    private static int linkTeam(ServerCommandSource source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        String dimId = world.getRegistryKey().getValue().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

        if (claim == null) {
            source.sendFeedback(() -> Text.literal("§cThis chunk is not claimed."), false);
            return 0;
        }
        if (!claim.getOwnerUuid().equals(player.getUuid()) && !source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§cYou do not own this claim."), false);
            return 0;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendFeedback(() -> Text.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isMember(player.getUuid()) && !source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§cYou are not a member of team §e" + teamName + "§c."), false);
            return 0;
        }

        claims.setTeam(dimId, chunkPos.x, chunkPos.z, team.getName());
        source.sendFeedback(() -> Text.literal("§aClaim linked to team §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int remove(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
        String dimId = world.getRegistryKey().getValue().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x, chunkPos.z);

        if (claim == null) {
            source.sendFeedback(() -> Text.literal("§cThis chunk is not claimed."), false);
            return 0;
        }
        if (!claim.getOwnerUuid().equals(player.getUuid()) && !source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§cYou do not own this claim."), false);
            return 0;
        }

        claims.removeClaim(dimId, chunkPos.x, chunkPos.z);
        source.sendFeedback(() -> Text.literal("§6Claim removed."), false);
        return 1;
    }
}
