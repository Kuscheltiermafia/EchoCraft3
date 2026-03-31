package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;

import java.util.UUID;

/**
 * /team create <teamname>
 * /team invite <player> <teamname>
 * /team accept <teamname>
 * /team leave <teamname>
 * /team members <teamname>
 */
public class TeamCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess) {
        dispatcher.register(
                Commands.literal("team")
                        .then(Commands.literal("create")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("teamname", StringArgumentType.word())
                                                .executes(ctx -> invite(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "teamname"))))))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> accept(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("leave")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> leave(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("members")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> members(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
        );
    }

    // -------------------------------------------------------------------------

    private static int create(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        if (teams.teamExists(teamName)) {
            source.sendSuccess(() -> Component.literal("§cA team named §e" + teamName + " §calready exists."), false);
            return 0;
        }
        teams.createTeam(teamName, player.getUUID());
        source.sendSuccess(() -> Component.literal("§aTeam §e" + teamName + " §acreated!"), false);
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou are not the leader of §e" + teamName + "§c."), false);
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendSuccess(() -> Component.literal("§cPlayer §e" + targetName + " §cis not online."), false);
            return 0;
        }
        if (team.isMember(target.getUUID())) {
            source.sendSuccess(() -> Component.literal("§e" + targetName + " §cis already in the team."), false);
            return 0;
        }

        team.invite(target.getUUID());
        teams.saveTeam(team);

        target.sendSystemMessage(Component.literal("§6You have been invited to team §e" + team.getName()
                + "§6. Use §a/team accept " + team.getName() + " §6to join."));
        source.sendSuccess(() -> Component.literal("§aInvited §e" + targetName + " §ato §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int accept(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isInvited(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou have not been invited to §e" + teamName + "§c."), false);
            return 0;
        }

        team.acceptInvite(player.getUUID());
        teams.saveTeam(team);
        source.sendSuccess(() -> Component.literal("§aYou joined team §e" + team.getName() + "§a!"), false);
        return 1;
    }

    private static int leave(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
            return 0;
        }
        if (!team.isMember(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou are not in team §e" + teamName + "§c."), false);
            return 0;
        }

        team.removeMember(player.getUUID());
        if (team.getMembers().isEmpty()) {
            teams.removeTeam(teamName);
            source.sendSuccess(() -> Component.literal("§6Team §e" + teamName + " §6has been disbanded (no members left)."), false);
        } else {
            teams.saveTeam(team);
            source.sendSuccess(() -> Component.literal("§aYou left team §e" + team.getName() + "§a."), false);
        }
        return 1;
    }

    private static int members(CommandSourceStack source, String teamName) {
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeam(teamName);
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + teamName + " §cdoes not exist."), false);
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
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static String resolvePlayerName(CommandSourceStack source, UUID uuid) {
        var player = source.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        return uuid.toString().substring(0, 8) + "...";
    }
}
