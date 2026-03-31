package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

/**
 * /claim info         – shows claim info for the chunk the player is standing in
 * /claim team <name>  – links the current chunk's claim to a team
 * /claim remove       – removes the current chunk's claim (owner or op only)
 */
public class ClaimCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess) {
        dispatcher.register(
                Commands.literal("claim")
                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("team")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> linkTeam(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("remove")
                                .executes(ctx -> remove(ctx.getSource())))
        );
    }

    // -------------------------------------------------------------------------

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = source.getServer().overworld();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = world.dimension().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x(), chunkPos.z());

        if (claim == null) {
            source.sendSuccess(() -> Component.literal("§aThis chunk is §eunclaimed§a."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§6=== Claim Info ==="), false);
        source.sendSuccess(() -> Component.literal("§eChunk: §f" + chunkPos.x() + ", " + chunkPos.z()), false);
        source.sendSuccess(() -> Component.literal("§eDimension: §f" + dimId), false);
        source.sendSuccess(() -> Component.literal("§eOwner: §f" + claim.getOwnerName()), false);
        source.sendSuccess(() -> Component.literal("§eTeam: §f"
                + (claim.getTeamName() != null ? claim.getTeamName() : "none")), false);
        source.sendSuccess(() -> Component.literal("§eBanner: §f"
                + claim.getBannerX() + ", " + claim.getBannerY() + ", " + claim.getBannerZ()), false);
        return 1;
    }

    private static int linkTeam(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = source.getServer().overworld();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = world.dimension().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x(), chunkPos.z());

        if (claim == null) {
            source.sendSuccess(() -> Component.literal("§cThis chunk is not claimed."), false);
            return 0;
        }
        if (!claim.getOwnerUuid().equals(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou do not own this claim."), false);
            return 0;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isMember(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou are not a member of team §e" + teamName + "§c."), false);
            return 0;
        }

        claims.setTeam(dimId, chunkPos.x(), chunkPos.z(), team.getName());
        source.sendSuccess(() -> Component.literal("§aClaim linked to team §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int remove(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel world = source.getServer().overworld();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = world.dimension().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x(), chunkPos.z());

        if (claim == null) {
            source.sendSuccess(() -> Component.literal("§cThis chunk is not claimed."), false);
            return 0;
        }
        if (!claim.getOwnerUuid().equals(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou do not own this claim."), false);
            return 0;
        }

        claims.removeClaim(dimId, chunkPos.x(), chunkPos.z());
        source.sendSuccess(() -> Component.literal("§6Claim removed."), false);
        return 1;
    }
}
