package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import io.github.kuscheltiermafia.teams.TeamRole;

import java.util.UUID;

/**
 * /echoteam create <teamname>
 * /echoteam invite <player>
 * /echoteam accept <teamname>
 * /echoteam leave <teamname>
 * /echoteam members <teamname>
 */
public class TeamCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess) {
        dispatcher.register(
                Commands.literal("echoteam")
                        .then(Commands.literal("create")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> invite(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("teamname", StringArgumentType.word())
                                        .executes(ctx -> accept(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "teamname")))))
                        .then(Commands.literal("leave")
                                .executes(ctx -> leave(ctx.getSource())))
                        .then(Commands.literal("members")
                                .executes(ctx -> members(ctx.getSource())))
                        .then(Commands.literal("setrole")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(ctx -> setRole(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "role")
                                                )))))
        );
    }

    // -------------------------------------------------------------------------

    private static int create(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        if (teams.getTeamForPlayer(player.getUUID()) != null) {
            source.sendSuccess(() -> Component.literal("§cYou are already in a team. Leave it before creating a new one."), false);
            return 0;
        }

        if (teams.teamExists(teamName)) {
            source.sendSuccess(() -> Component.literal("§cA team named §e" + teamName + " §calready exists."), false);
            return 0;
        }
        if (!teams.createTeam(teamName, player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cCould not create team §e" + teamName + "§c."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aTeam §e" + teamName + " §acreated!"), false);
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly the team leader can invite players."), false);
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
        if (teams.getTeamForPlayer(target.getUUID()) != null) {
            source.sendSuccess(() -> Component.literal("§e" + targetName + " §cis already in another team."), false);
            return 0;
        }

        team.invite(target.getUUID());
        teams.saveTeam(team);

        Component clickAccept = Component.literal("[Click to accept]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/echoteam accept " + team.getName()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Accept invite to " + team.getName()))));

        target.sendSystemMessage(Component.literal("§6You have been invited to team §e" + team.getName() + "§6. ").append(clickAccept));
        source.sendSuccess(() -> Component.literal("§aInvited §e" + targetName + " §ato §e" + team.getName() + "§a."), false);
        return 1;
    }

    private static int accept(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        if (teams.getTeamForPlayer(player.getUUID()) != null) {
            source.sendSuccess(() -> Component.literal("§cYou are already in a team."), false);
            return 0;
        }

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

    private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());

        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isMember(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cYou are not in this team."), false);
            return 0;
        }

        team.removeMember(player.getUUID());
        if (team.getMembers().isEmpty()) {
            teams.removeTeam(team.getName());
            source.sendSuccess(() -> Component.literal("§6Team §e" + team.getName() + " §6has been disbanded (no members left)."), false);
        } else {
            teams.saveTeam(team);
            source.sendSuccess(() -> Component.literal("§aYou left team §e" + team.getName() + "§a."), false);
        }
        return 1;
    }

    private static int members(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6=== Team Members: §e" + team.getName() + "§6 ==="), false);
        for (UUID memberUuid : team.getMembers()) {
            String name = resolvePlayerName(source, memberUuid);
            TeamRole role = team.getRole(memberUuid);

            Component base = Component.literal("§f- " + name + " §7[" + role.name() + "]");
            source.sendSuccess(() -> base, false);

            if (team.isLeader(player.getUUID()) && !memberUuid.equals(player.getUUID())) {
                String commandPrefix = "/echoteam setrole " + name + " ";
                Component actions = Component.literal("  ")
                        .append(clickAction("[Member]", commandPrefix + "member", ChatFormatting.GRAY))
                        .append(Component.literal(" "))
                        .append(clickAction("[Mod]", commandPrefix + "moderator", ChatFormatting.AQUA))
                        .append(Component.literal(" "))
                        .append(clickAction("[Leader]", commandPrefix + "leader", ChatFormatting.GOLD));
                source.sendSuccess(() -> actions, false);
            }
        }
        return 1;
    }

    private static int setRole(CommandSourceStack source, String playerName, String rawRole) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(actor.getUUID());

        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly the team leader can change roles."), false);
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null || !team.isMember(target.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cThat player is not an online member of your team."), false);
            return 0;
        }

        TeamRole role = TeamRole.fromString(rawRole);
        team.setRole(actor.getUUID(), target.getUUID(), role);
        teams.saveTeam(team);

        source.sendSuccess(() -> Component.literal("§aSet role of §e" + target.getName().getString() + " §ato §e" + role.name() + "§a."), false);
        target.sendSystemMessage(Component.literal("§6Your role in team §e" + team.getName() + " §6is now §e" + role.name() + "§6."));
        return 1;
    }

    private static Component clickAction(String text, String runCommand, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(runCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Run: " + runCommand))));
    }

    private static String resolvePlayerName(CommandSourceStack source, UUID uuid) {
        var player = source.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        return uuid.toString().substring(0, 8) + "...";
    }
}
