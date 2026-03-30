package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * /team create <teamname>
 * /team invite <player> <teamname>
 * /team accept <teamname>
 * /team leave <teamname>
 * /team members <teamname>
 */
public class TeamCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {
        dispatcher.register(
                CommandManager.literal("team")
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(CommandManager.literal("invite")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .then(CommandManager.argument("teamname", StringArgumentType.word())
                                                .executes(ctx -> invite(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "teamname"))))))
                        .then(CommandManager.literal("accept")
                                .then(CommandManager.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> accept(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(CommandManager.literal("leave")
                                .then(CommandManager.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> leave(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(CommandManager.literal("members")
                                .then(CommandManager.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> members(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
        );
    }

    // -------------------------------------------------------------------------

    private static int create(ServerCommandSource source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeamManager teams = TeamManager.get(source.getServer());

        if (teams.teamExists(teamName)) {
            source.sendFeedback(() -> Text.literal("§cA team named §e" + teamName + " §calready exists."), false);
            return 0;
        }
        teams.createTeam(teamName, player.getUuid());
        source.sendFeedback(() -> Text.literal("§aTeam §e" + teamName + " §acreated!"), false);
        return 1;
    }

    private static int invite(ServerCommandSource source, String targetName, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendFeedback(() -> Text.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isLeader(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("§cYou are not the leader of §e" + teamName + "§c."), false);
            return 0;
        }

        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            source.sendFeedback(() -> Text.literal("§cPlayer §e" + targetName + " §cis not online."), false);
            return 0;
        }
        if (team.isMember(target.getUuid())) {
            source.sendFeedback(() -> Text.literal("§e" + targetName + " §cis already in the team."), false);
            return 0;
        }

        team.invite(target.getUuid());
        teams.saveTeam(team);

        target.sendMessage(Text.literal("§6You have been invited to team §e" + team.getName()
                + "§6. Use §a/team accept " + team.getName() + " §6to join."), false);
        source.sendFeedback(() -> Text.literal("§aInvited §e" + targetName + " §ato §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int accept(ServerCommandSource source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendFeedback(() -> Text.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isInvited(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("§cYou have not been invited to §e" + teamName + "§c."), false);
            return 0;
        }

        team.acceptInvite(player.getUuid());
        teams.saveTeam(team);
        source.sendFeedback(() -> Text.literal("§aYou joined team §e" + team.getName() + "§a!"), false);
        return 1;
    }

    private static int leave(ServerCommandSource source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendFeedback(() -> Text.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isMember(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("§cYou are not in team §e" + teamName + "§c."), false);
            return 0;
        }

        team.removeMember(player.getUuid());
        if (team.getMembers().isEmpty()) {
            teams.removeTeam(teamName);
            source.sendFeedback(() -> Text.literal("§6Team §e" + teamName + " §6has been disbanded (no members left)."), false);
        } else {
            teams.saveTeam(team);
            source.sendFeedback(() -> Text.literal("§aYou left team §e" + team.getName() + "§a."), false);
        }
        return 1;
    }

    private static int members(ServerCommandSource source, String teamName) {
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendFeedback(() -> Text.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder("§6Team §e").append(team.getName()).append("§6 members (")
                .append(team.getMembers().size()).append("):");
        for (UUID memberUuid : team.getMembers()) {
            String name = resolvePlayerName(source, memberUuid);
            boolean isLeader = team.isLeader(memberUuid);
            sb.append("\n  §f").append(name).append(isLeader ? " §6[Leader]" : "");
        }
        String msg = sb.toString();
        source.sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static String resolvePlayerName(ServerCommandSource source, UUID uuid) {
        var player = source.getServer().getPlayerManager().getPlayer(uuid);
        if (player != null) return player.getGameProfile().getName();
        return uuid.toString().substring(0, 8) + "...";
    }
}
