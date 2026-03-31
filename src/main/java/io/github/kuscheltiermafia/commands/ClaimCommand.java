package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
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
                                .executes(ctx -> linkTeam(ctx.getSource())))
                        .then(Commands.literal("menu")
                                .executes(ctx -> showMenu(ctx.getSource())))
                        .then(buildToggleCommand())
                        .then(Commands.literal("remove")
                                .executes(ctx -> remove(ctx.getSource())))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleCommand() {
        return Commands.literal("toggle")
                .then(Commands.literal("explosions")
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "explosions", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "explosions", false))))
                .then(Commands.literal("pvp")
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "pvp", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "pvp", false))))
                .then(Commands.literal("foreign_break")
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_break", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_break", false))))
                .then(Commands.literal("foreign_place")
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_place", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_place", false))))
                .then(Commands.literal("foreign_interact")
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", false))));
    }

    // -------------------------------------------------------------------------

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = player.level().dimension().toString();

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

    private static int linkTeam(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = player.level().dimension().toString();

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
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }

        claims.setTeam(dimId, chunkPos.x(), chunkPos.z(), team.getName());
        source.sendSuccess(() -> Component.literal("§aClaim linked to team §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int remove(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = player.level().dimension().toString();

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

    private static int toggle(CommandSourceStack source, String key, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = player.level().dimension().toString();

        ClaimManager claims = ClaimManager.get(source.getServer());
        ClaimData claim = claims.getClaim(dimId, chunkPos.x(), chunkPos.z());

        if (claim == null) {
            source.sendSuccess(() -> Component.literal("§cThis chunk is not claimed."), false);
            return 0;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        boolean canManage = claim.getOwnerUuid().equals(player.getUUID());
        if (!canManage && claim.getTeamName() != null) {
            canManage = teams.canManageClaims(claim.getTeamName(), player.getUUID());
        }

        if (!canManage) {
            source.sendSuccess(() -> Component.literal("§cOnly the owner, team moderators or leader can change claim settings."), false);
            return 0;
        }

        switch (key) {
            case "explosions" -> claims.setExplosionsAllowed(dimId, chunkPos.x(), chunkPos.z(), enabled);
            case "pvp" -> claims.setPvpAllowed(dimId, chunkPos.x(), chunkPos.z(), enabled);
            case "foreign_break" -> claims.setForeignBreakAllowed(dimId, chunkPos.x(), chunkPos.z(), enabled);
            case "foreign_place" -> claims.setForeignPlaceAllowed(dimId, chunkPos.x(), chunkPos.z(), enabled);
            case "foreign_interact" -> claims.setForeignInteractAllowed(dimId, chunkPos.x(), chunkPos.z(), enabled);
            default -> {
                source.sendSuccess(() -> Component.literal("§cUnknown toggle key."), false);
                return 0;
            }
        }

        source.sendSuccess(() -> Component.literal("§aSet §e" + key + " §ato §e" + (enabled ? "ON" : "OFF") + "§a."), false);
        return 1;
    }

    private static int showMenu(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.sendSuccess(() -> Component.literal("§6=== Claim Settings Menu ==="), false);
        source.sendSuccess(() -> toggleRow("explosions"), false);
        source.sendSuccess(() -> toggleRow("pvp"), false);
        source.sendSuccess(() -> toggleRow("foreign_break"), false);
        source.sendSuccess(() -> toggleRow("foreign_place"), false);
        source.sendSuccess(() -> toggleRow("foreign_interact"), false);
        return 1;
    }

    private static Component toggleRow(String key) {
        return Component.literal("§e" + key + " §7")
                .append(clickButton("[ON]", "/claim toggle " + key + " on", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(clickButton("[OFF]", "/claim toggle " + key + " off", ChatFormatting.RED));
    }

    private static Component clickButton(String text, String command, ChatFormatting color) {
        return Component.literal(text)
                .withStyle(style -> style
                        .withColor(color)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run: " + command))));
    }
}
