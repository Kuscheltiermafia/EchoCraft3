package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import io.github.kuscheltiermafia.teams.TeamRole;
import io.github.kuscheltiermafia.util.TextPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                        .then(Commands.literal("settings")
                                .executes(ctx -> settings(ctx.getSource()))
                                .then(Commands.argument("member", StringArgumentType.word())
                                        .executes(ctx -> settingsMember(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "member")
                                        ))))
                        .then(Commands.literal("allies")
                                .then(Commands.literal("invite")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .executes(ctx -> allyInvite(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                                .then(Commands.literal("accept")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .executes(ctx -> allyAccept(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .executes(ctx -> allyRemove(ctx.getSource(), StringArgumentType.getString(ctx, "team")))))
                                .then(Commands.literal("list")
                                        .executes(ctx -> allyList(ctx.getSource()))))
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
        teams.rememberPlayerName(player.getUUID(), player.getName().getString());

        if (teams.getTeamForPlayer(player.getUUID()) != null) {
            source.sendSuccess(() -> TextPalette.white("You are already in a team. Leave it before creating a new one."), false);
            return 0;
        }

        if (teams.teamExists(teamName)) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.white("A team named "),
                    TextPalette.yellow(teamName),
                    TextPalette.white(" already exists.")
            ), false);
            return 0;
        }
        if (!teams.createTeam(teamName, player.getUUID())) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.white("Could not create team "),
                    TextPalette.yellow(teamName),
                    TextPalette.white(".")
            ), false);
            return 0;
        }
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Team "),
                TextPalette.yellow(teamName),
                TextPalette.white(" created.")
        ), false);
        return 1;
    }

    private static int invite(CommandSourceStack source, String targetName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(player.getUUID(), player.getName().getString());

        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> TextPalette.white("You are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> TextPalette.white("Only team leaders can invite players."), false);
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.white("Player "),
                    TextPalette.yellow(targetName),
                    TextPalette.white(" is not online.")
            ), false);
            return 0;
        }
        teams.rememberPlayerName(target.getUUID(), target.getName().getString());
        if (team.isMember(target.getUUID())) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.yellow(targetName),
                    TextPalette.white(" is already in the team.")
            ), false);
            return 0;
        }
        if (teams.getTeamForPlayer(target.getUUID()) != null) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.yellow(targetName),
                    TextPalette.white(" is already in another team.")
            ), false);
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

        target.sendSystemMessage(TextPalette.join(
                TextPalette.white("You have been invited to team "),
                TextPalette.yellow(team.getName()),
                TextPalette.white(". "),
                clickAccept
        ));
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Invited "),
                TextPalette.yellow(targetName),
                TextPalette.white(" to "),
                TextPalette.yellow(team.getName()),
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static int accept(CommandSourceStack source, String teamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(player.getUUID(), player.getName().getString());

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
        teams.rememberPlayerName(player.getUUID(), player.getName().getString());

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

    private static int settings(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }

        openTeamSettingsDialog(source, player, team);
        return 1;
    }

    private static void openTeamSettingsDialog(CommandSourceStack source, ServerPlayer viewer, TeamData team) {
        List<ActionButton> actions = new ArrayList<>();

        boolean leader = team.isLeader(viewer.getUUID());
        int rendered = 0;
        for (UUID memberUuid : team.getMembers()) {
            if (rendered >= 12) break;
            String name = resolvePlayerName(source, memberUuid);
            TeamRole role = team.getRole(memberUuid);
            String label = name + " [" + role.displayName() + "]";
            ClickEvent click = leader && !memberUuid.equals(viewer.getUUID())
                    ? new ClickEvent.RunCommand("/echoteam settings " + name)
                    : new ClickEvent.SuggestCommand("/msg " + name + " ");
            actions.add(dialogButton(label, click));
            rendered++;
        }

        CommonDialogData common = new CommonDialogData(
                TextPalette.join(TextPalette.white("Team Settings: "), TextPalette.yellow(team.getName())),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                List.of()
        );
        MultiActionDialog dialog = new MultiActionDialog(common, actions, Optional.empty(), 2);
        viewer.openDialog(Holder.direct(dialog));
    }

    private static int settingsMember(CommandSourceStack source, String memberName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(actor.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can change roles."), false);
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(memberName);
        if (target == null || !team.isMember(target.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cMember §e" + memberName + " §cis not online or not in your team."), false);
            return 0;
        }
        if (target.getUUID().equals(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cUse the command directly if you want to change your own role."), false);
            return 0;
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(dialogButton("Set Member", new ClickEvent.RunCommand("/echoteam setrole " + memberName + " member")));
        actions.add(dialogButton("Set Moderator", new ClickEvent.RunCommand("/echoteam setrole " + memberName + " moderator")));
        actions.add(dialogButton("Set Leader", new ClickEvent.RunCommand("/echoteam setrole " + memberName + " leader")));

        CommonDialogData common = new CommonDialogData(
                TextPalette.join(TextPalette.white("Change Role: "), TextPalette.yellow(memberName)),
                Optional.empty(),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                List.of()
        );
        actor.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.empty(), 1)));
        return 1;
    }

    private static ActionButton dialogButton(String label, ClickEvent clickEvent) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 150), Optional.of(new StaticAction(clickEvent)));
    }



    private static int setRole(CommandSourceStack source, String playerName, String rawRole) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(actor.getUUID(), actor.getName().getString());
        TeamData team = teams.getTeamForPlayer(actor.getUUID());

        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can change roles."), false);
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null || !team.isMember(target.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cThat player is not an online member of your team."), false);
            return 0;
        }
        teams.rememberPlayerName(target.getUUID(), target.getName().getString());

        TeamRole role = TeamRole.fromString(rawRole);
        team.setRole(actor.getUUID(), target.getUUID(), role);
        teams.saveTeam(team);

        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Set role of "),
                TextPalette.yellow(target.getName().getString()),
                TextPalette.white(" to "),
                TextPalette.yellow(role.displayName()),
                TextPalette.white(".")
        ), false);
        target.sendSystemMessage(TextPalette.join(
                TextPalette.white("Your role in team "),
                TextPalette.yellow(team.getName()),
                TextPalette.white(" is now "),
                TextPalette.yellow(role.displayName()),
                TextPalette.white(".")
        ));
        return 1;
    }

    private static int allyInvite(CommandSourceStack source, String targetTeamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(actor.getUUID(), actor.getName().getString());
        TeamData ownTeam = teams.getTeamForPlayer(actor.getUUID());
        if (ownTeam == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!ownTeam.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can manage allies."), false);
            return 0;
        }
        TeamData targetTeam = teams.getTeam(targetTeamName);
        if (targetTeam == null) {
            source.sendSuccess(() -> Component.literal("§cTeam §e" + targetTeamName + " §cdoes not exist."), false);
            return 0;
        }
        if (targetTeam.getName().equalsIgnoreCase(ownTeam.getName())) {
            source.sendSuccess(() -> Component.literal("§cYou cannot ally your own team."), false);
            return 0;
        }
        if (teams.areAllies(ownTeam.getName(), targetTeam.getName())) {
            source.sendSuccess(() -> Component.literal("§eYour team is already allied with §f" + targetTeam.getName() + "§e."), false);
            return 0;
        }

        teams.inviteAlly(ownTeam.getName(), targetTeam.getName());
        source.sendSuccess(() -> Component.literal("§aSent ally request to §e" + targetTeam.getName() + "§a."), false);

        ServerPlayer targetLeader = source.getServer().getPlayerList().getPlayer(targetTeam.getLeader());
        if (targetLeader != null) {
            Component accept = Component.literal("[Accept]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand("/echoteam allies accept " + ownTeam.getName()))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Accept ally invite from " + ownTeam.getName()))));
            targetLeader.sendSystemMessage(Component.literal("§6Your team received an ally invite from §e" + ownTeam.getName() + "§6. ").append(accept));
        }
        return 1;
    }

    private static int allyAccept(CommandSourceStack source, String sourceTeamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(actor.getUUID(), actor.getName().getString());
        TeamData ownTeam = teams.getTeamForPlayer(actor.getUUID());
        if (ownTeam == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!ownTeam.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can manage allies."), false);
            return 0;
        }
        if (!teams.acceptAlly(ownTeam.getName(), sourceTeamName)) {
            source.sendSuccess(() -> Component.literal("§cNo pending ally request from §e" + sourceTeamName + "§c."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aYour team is now allied with §e" + sourceTeamName + "§a."), false);
        return 1;
    }

    private static int allyRemove(CommandSourceStack source, String targetTeamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(actor.getUUID(), actor.getName().getString());
        TeamData ownTeam = teams.getTeamForPlayer(actor.getUUID());
        if (ownTeam == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!ownTeam.isLeader(actor.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can manage allies."), false);
            return 0;
        }
        if (!teams.removeAlly(ownTeam.getName(), targetTeamName)) {
            source.sendSuccess(() -> Component.literal("§cCould not remove ally §e" + targetTeamName + "§c."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aRemoved ally relation with §e" + targetTeamName + "§a."), false);
        return 1;
    }

    private static int allyList(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        teams.rememberPlayerName(actor.getUUID(), actor.getName().getString());
        TeamData ownTeam = teams.getTeamForPlayer(actor.getUUID());
        if (ownTeam == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }

        if (ownTeam.getAllies().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eYour team has no allies."), false);
        } else {
            source.sendSuccess(() -> Component.literal("§6Allies of §e" + ownTeam.getName() + "§6:"), false);
            for (String ally : ownTeam.getAllies()) {
                source.sendSuccess(() -> Component.literal("§f- " + ally), false);
            }
        }
        if (!ownTeam.getPendingAllyInvites().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6Pending ally requests:"), false);
            for (String invite : ownTeam.getPendingAllyInvites()) {
                source.sendSuccess(() -> Component.literal("§f- " + invite + " §7(use /echoteam allies accept " + invite + ")"), false);
            }
        }
        return 1;
    }


    private static String resolvePlayerName(CommandSourceStack source, UUID uuid) {
        TeamManager teams = TeamManager.get(source.getServer());
        String known = teams.getKnownPlayerName(uuid);
        if (known != null && !known.isBlank()) return known;
        var player = source.getServer().getPlayerList().getPlayer(uuid);
        if (player != null) {
            String name = player.getName().getString();
            teams.rememberPlayerName(uuid, name);
            return name;
        }
        try {
            Object server = source.getServer();
            Object cache = null;
            for (String methodName : new String[]{"getProfileCache", "getUserCache"}) {
                try {
                    cache = server.getClass().getMethod(methodName).invoke(server);
                    if (cache != null) break;
                } catch (Exception ignoredInner) {
                    // Try the next known cache accessor name.
                }
            }
            if (cache != null) {
                var method = cache.getClass().getMethod("get", UUID.class);
                Object result = method.invoke(cache, uuid);
                if (result instanceof java.util.Optional<?> optional && optional.isPresent()) {
                    Object profile = optional.get();
                    var getName = profile.getClass().getMethod("getName");
                    Object value = getName.invoke(profile);
                    if (value instanceof String name && !name.isBlank()) {
                        teams.rememberPlayerName(uuid, name);
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to compact UUID suffix if no profile cache entry exists.
        }
        return uuid.toString().substring(0, 8) + "...";
    }
}
