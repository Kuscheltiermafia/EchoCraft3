package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.TextInput;
import io.github.kuscheltiermafia.teams.TeamData;
import io.github.kuscheltiermafia.teams.TeamManager;
import io.github.kuscheltiermafia.teams.TeamRole;
import io.github.kuscheltiermafia.users.LocatorBarColor;
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
                        .then(buildHiddenTeamCommand())
        );
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> buildHiddenTeamCommand() {
        return Commands.argument("echoteam_hidden", StringArgumentType.greedyString())
                .suggests((context, builder) -> builder.buildFuture())
                .executes(ctx -> executeHiddenTeamCommand(ctx.getSource(), StringArgumentType.getString(ctx, "echoteam_hidden")));
    }

    private static int executeHiddenTeamCommand(CommandSourceStack source, String rawInput) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) return 0;

        String[] parts = input.split("\\s+");
        if (parts.length == 1 && "settings_color".equals(parts[0])) {
            return openTeamColorSettings(source);
        }
        if (parts.length == 3 && "setrole".equals(parts[0])) {
            return setRole(source, parts[1], parts[2]);
        }
        if (parts.length == 2 && "teamnamecolor".equals(parts[0])) {
            return setTeamNameColor(source, parts[1]);
        }
        if (parts.length == 2 && "teamnamecolor_refresh".equals(parts[0])) {
            return setTeamNameColorAndRefresh(source, parts[1]);
        }
        if (parts.length == 2 && "teamnamecolor_hex_refresh".equals(parts[0])) {
            return setTeamNameHexColorAndRefresh(source, parts[1]);
        }
        if (input.startsWith("teamname ") && input.length() > "teamname ".length()) {
            return setTeamName(source, input.substring("teamname ".length()));
        }
        return 0;
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
        TeamData created = teams.getTeam(teamName);
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Team "),
                created != null ? teams.getDisplayComponent(created) : TextPalette.yellow(teamName),
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
                        .withHoverEvent(new HoverEvent.ShowText(TextPalette.join(
                                TextPalette.white("Accept invite to "),
                                teams.getDisplayComponent(team)
                        ))));

        target.sendSystemMessage(TextPalette.join(
                TextPalette.white("You have been invited to team "),
                teams.getDisplayComponent(team),
                TextPalette.white(". "),
                clickAccept
        ));
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Invited "),
                TextPalette.yellow(targetName),
                TextPalette.white(" to "),
                teams.getDisplayComponent(team),
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
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.red("Team "),
                    TextPalette.yellow(teamName),
                    TextPalette.red(" does not exist.")
            ), false);
            return 0;
        }
        if (!team.isInvited(player.getUUID())) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.red("You have not been invited to "),
                    teams.getDisplayComponent(team),
                    TextPalette.red(".")
            ), false);
            return 0;
        }

        team.acceptInvite(player.getUUID());
        teams.saveTeam(team);
        source.sendSuccess(() -> TextPalette.join(
                Component.literal("You joined team ").withStyle(ChatFormatting.GREEN),
                teams.getDisplayComponent(team),
                Component.literal("!").withStyle(ChatFormatting.GREEN)
        ), false);
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
            ClaimManager claims = ClaimManager.get(source.getServer());
            List<ClaimData> teamClaims = claims.getAllClaims().stream()
                    .filter(c -> c.getTeamName() != null && c.getTeamName().equalsIgnoreCase(team.getName()))
                    .toList();
            breakTeamBannersWithEffect(source, teamClaims);
            int removedClaims = claims.removeClaimsByTeam(team.getName());
            giveClaimBanners(player, removedClaims);
            teams.removeTeam(team.getName());
            source.sendSuccess(() -> TextPalette.join(
                    Component.literal("Team ").withStyle(ChatFormatting.GOLD),
                    teams.getDisplayComponent(team),
                    Component.literal(" has been disbanded (no members left). ").withStyle(ChatFormatting.GOLD)
            ), false);
            if (removedClaims > 0) {
                source.sendSuccess(() -> Component.literal("§e" + removedClaims + " claim banner(s) were returned to you."), false);
            }
        } else {
            teams.saveTeam(team);
            source.sendSuccess(() -> TextPalette.join(
                    Component.literal("You left team ").withStyle(ChatFormatting.GREEN),
                    teams.getDisplayComponent(team),
                    Component.literal(".").withStyle(ChatFormatting.GREEN)
            ), false);
        }
        return 1;
    }

    private static int setTeamName(CommandSourceStack source, String rawTeamName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can change the teamname."), false);
            return 0;
        }

        String value = rawTeamName == null ? "" : rawTeamName.trim();
        if (value.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cTeamname cannot be empty."), false);
            return 0;
        }
        if (value.length() > 32) {
            source.sendSuccess(() -> Component.literal("§cTeamname is too long (max 32 characters)."), false);
            return 0;
        }

        team.setDisplayName(value);
        teams.saveTeam(team);
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Teamname set to "),
                teams.getDisplayComponent(team),
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static int setTeamNameColor(CommandSourceStack source, String rawColor) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can change the teamname color."), false);
            return 0;
        }

        String normalizedHex = LocatorBarColor.normalizeHex(rawColor);
        String color = normalizedHex != null ? normalizedHex : (rawColor == null ? "" : rawColor.trim().toLowerCase());
        if (normalizedHex == null) {
            boolean knownPreset = false;
            for (LocatorBarColor preset : LocatorBarColor.values()) {
                if (preset.token().equals(color)) {
                    knownPreset = true;
                    break;
                }
            }
            if (!knownPreset) {
                source.sendSuccess(() -> Component.literal("§cInvalid color. Use any Minecraft chat color token or #RRGGBB."), false);
                return 0;
            }
        }

        team.setTeamNameColor(color);
        teams.saveTeam(team);
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Teamname color set to "),
                teams.getDisplayComponent(team),
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static int setTeamNameColorAndRefresh(CommandSourceStack source, String rawColor) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int result = setTeamNameColor(source, rawColor);
        if (result == 1) {
            ServerPlayer player = source.getPlayerOrException();
            TeamData team = TeamManager.get(source.getServer()).getTeamForPlayer(player.getUUID());
            if (team != null) {
                openTeamColorDialog(player, team);
            }
        }
        return result;
    }

    private static int setTeamNameHexColorAndRefresh(CommandSourceStack source, String rawHex) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int result = setTeamNameColor(source, rawHex);
        ServerPlayer player = source.getPlayerOrException();
        TeamData team = TeamManager.get(source.getServer()).getTeamForPlayer(player.getUUID());
        if (team != null) {
            openTeamColorDialog(player, team);
        }
        return result;
    }

    private static int openTeamColorSettings(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TeamManager teams = TeamManager.get(source.getServer());
        TeamData team = teams.getTeamForPlayer(player.getUUID());
        if (team == null) {
            source.sendSuccess(() -> Component.literal("§cYou are not in a team."), false);
            return 0;
        }
        if (!team.isLeader(player.getUUID())) {
            source.sendSuccess(() -> Component.literal("§cOnly team leaders can change the teamname color."), false);
            return 0;
        }

        openTeamColorDialog(player, team);
        return 1;
    }

    private static void openTeamColorDialog(ServerPlayer player, TeamData team) {
        List<Input> inputs = new ArrayList<>();
        String current = team.getTeamNameColor();
        inputs.add(new Input("hex", new TextInput(
                200,
                TextPalette.white("Custom Hex (#RRGGBB)"),
                false,
                current != null && current.startsWith("#") ? current : "",
                7,
                Optional.empty()
        )));

        List<ActionButton> actions = new ArrayList<>();
        for (LocatorBarColor color : LocatorBarColor.values()) {
            boolean selected = color.token().equalsIgnoreCase(current);
            String marker = selected ? "[Selected] " : "[Select] ";
            Component label = Component.literal(marker)
                    .withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                    .append(Component.literal(color.displayName()).withStyle(color.formatting()));
            actions.add(new ActionButton(
                    new CommonButtonData(label, 170),
                    Optional.of(new StaticAction(new ClickEvent.RunCommand("/echoteam teamnamecolor_refresh " + color.token())))
            ));
        }
        actions.add(new ActionButton(
                new CommonButtonData(TextPalette.white("Apply Custom Hex"), 170),
                Optional.of(buildHexApplyAction("/echoteam teamnamecolor_hex_refresh $(hex)"))
        ));

        CommonDialogData common = new CommonDialogData(
                TextPalette.white("Teamname Color"),
                Optional.of(TextPalette.yellow("Choose a color for your teamname")),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                inputs
        );
        player.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.empty(), 2)));
    }

    private static Action buildHexApplyAction(String commandTemplate) {
        try {
            Class<?> parsedTemplateClass = Class.forName("net.minecraft.server.dialog.action.ParsedTemplate");
            Object parsed = null;
            for (java.lang.reflect.Method method : parsedTemplateClass.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) continue;
                method.setAccessible(true);

                Object value = method.invoke(null, commandTemplate);
                if (parsedTemplateClass.isInstance(value)) {
                    parsed = value;
                    break;
                }
                if (value != null && value.getClass().getName().equals("com.mojang.serialization.DataResult")) {
                    java.lang.reflect.Method resultMethod = value.getClass().getMethod("result");
                    Object result = resultMethod.invoke(value);
                    if (result instanceof Optional<?> optional && optional.isPresent() && parsedTemplateClass.isInstance(optional.get())) {
                        parsed = optional.get();
                        break;
                    }
                }
            }

            if (parsed != null) {
                Class<?> commandTemplateClass = Class.forName("net.minecraft.server.dialog.action.CommandTemplate");
                for (java.lang.reflect.Constructor<?> ctor : commandTemplateClass.getConstructors()) {
                    if (ctor.getParameterCount() == 1 && parsedTemplateClass.isAssignableFrom(ctor.getParameterTypes()[0])) {
                        return (Action) ctor.newInstance(parsed);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback keeps the UI usable if template internals differ by version.
        }
        return new StaticAction(new ClickEvent.SuggestCommand("/echoteam teamnamecolor_hex_refresh "));
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

        if (team.isLeader(viewer.getUUID())) {
            actions.add(dialogButton(
                    "Set Teamname",
                    new ClickEvent.SuggestCommand("/echoteam teamname " + team.getDisplayName())
            ));
            actions.add(dialogButton(
                    "Set Teamname Color",
                    new ClickEvent.RunCommand("/echoteam settings_color")
            ));
        }

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
                TextPalette.join(TextPalette.white("Team Settings: "), TeamManager.get(source.getServer()).getDisplayComponent(team)),
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
                teams.getDisplayComponent(team),
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
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.red("Team "),
                    TextPalette.yellow(targetTeamName),
                    TextPalette.red(" does not exist.")
            ), false);
            return 0;
        }
        if (targetTeam.getName().equalsIgnoreCase(ownTeam.getName())) {
            source.sendSuccess(() -> Component.literal("§cYou cannot ally your own team."), false);
            return 0;
        }
        if (teams.areAllies(ownTeam.getName(), targetTeam.getName())) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.yellow("Your team is already allied with "),
                    teams.getDisplayComponent(targetTeam),
                    TextPalette.yellow(".")
            ), false);
            return 0;
        }

        teams.inviteAlly(ownTeam.getName(), targetTeam.getName());
        source.sendSuccess(() -> TextPalette.join(
                Component.literal("Sent ally request to ").withStyle(ChatFormatting.GREEN),
                teams.getDisplayComponent(targetTeam),
                Component.literal(".").withStyle(ChatFormatting.GREEN)
        ), false);

        ServerPlayer targetLeader = source.getServer().getPlayerList().getPlayer(targetTeam.getLeader());
        if (targetLeader != null) {
            Component accept = Component.literal("[Accept]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand("/echoteam allies accept " + ownTeam.getName()))
                            .withHoverEvent(new HoverEvent.ShowText(TextPalette.join(
                                    TextPalette.white("Accept ally invite from "),
                                    teams.getDisplayComponent(ownTeam)
                            ))));
            targetLeader.sendSystemMessage(TextPalette.join(
                    Component.literal("Your team received an ally invite from ").withStyle(ChatFormatting.GOLD),
                    teams.getDisplayComponent(ownTeam),
                    Component.literal(". ").withStyle(ChatFormatting.GOLD),
                    accept
            ));
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
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.red("No pending ally request from "),
                    teams.getDisplayComponent(sourceTeamName),
                    TextPalette.red(".")
            ), false);
            return 0;
        }
        source.sendSuccess(() -> TextPalette.join(
                Component.literal("Your team is now allied with ").withStyle(ChatFormatting.GREEN),
                teams.getDisplayComponent(sourceTeamName),
                Component.literal(".").withStyle(ChatFormatting.GREEN)
        ), false);
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
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.red("Could not remove ally "),
                    teams.getDisplayComponent(targetTeamName),
                    TextPalette.red(".")
            ), false);
            return 0;
        }
        source.sendSuccess(() -> TextPalette.join(
                Component.literal("Removed ally relation with ").withStyle(ChatFormatting.GREEN),
                teams.getDisplayComponent(targetTeamName),
                Component.literal(".").withStyle(ChatFormatting.GREEN)
        ), false);
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
            source.sendSuccess(() -> TextPalette.join(
                    Component.literal("Allies of ").withStyle(ChatFormatting.GOLD),
                    teams.getDisplayComponent(ownTeam),
                    Component.literal(":").withStyle(ChatFormatting.GOLD)
            ), false);
            for (String ally : ownTeam.getAllies()) {
                source.sendSuccess(() -> TextPalette.join(
                        TextPalette.white("- "),
                        teams.getDisplayComponent(ally)
                ), false);
            }
        }
        if (!ownTeam.getPendingAllyInvites().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6Pending ally requests:"), false);
            for (String invite : ownTeam.getPendingAllyInvites()) {
                source.sendSuccess(() -> TextPalette.join(
                        TextPalette.white("- "),
                        teams.getDisplayComponent(invite),
                        Component.literal(" (use /echoteam allies accept " + invite + ")").withStyle(ChatFormatting.GRAY)
                ), false);
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

    private static void giveClaimBanners(ServerPlayer player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            ItemStack stack = ModItems.createClaimBannerStack();
            stack.setCount(stackSize);
            boolean added = player.addItem(stack);
            if (!added) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            }
            remaining -= stackSize;
        }
    }

    private static void breakTeamBannersWithEffect(CommandSourceStack source, List<ClaimData> claims) {
        for (ClaimData claim : claims) {
            for (var level : source.getServer().getAllLevels()) {
                if (!level.dimension().toString().equals(claim.getDimensionId())) continue;

                BlockPos bannerPos = new BlockPos(claim.getBannerX(), claim.getBannerY(), claim.getBannerZ());
                var state = level.getBlockState(bannerPos);
                if (!state.is(Blocks.WHITE_BANNER) && !state.is(Blocks.WHITE_WALL_BANNER)) {
                    continue;
                }

                level.sendParticles(ParticleTypes.EXPLOSION, bannerPos.getX() + 0.5, bannerPos.getY() + 0.6, bannerPos.getZ() + 0.5,
                        8, 0.2, 0.2, 0.2, 0.01);
                level.sendParticles(ParticleTypes.POOF, bannerPos.getX() + 0.5, bannerPos.getY() + 0.6, bannerPos.getZ() + 0.5,
                        16, 0.3, 0.3, 0.3, 0.02);
                level.playSound(null, bannerPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.45f, 1.3f);
                // Remove only the claim banner block without drops or collateral damage.
                level.setBlock(bannerPos, Blocks.AIR.defaultBlockState(), 3);
                break;
            }
        }
    }

    private static boolean isOperator(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        Boolean bySource = invokeBoolean(source, "hasPermission", 2);
        if (bySource != null) return bySource;
        bySource = invokeBoolean(source, "hasPermissionLevel", 2);
        if (bySource != null) return bySource;

        Integer level = invokeInt(source, "permissionLevel");
        if (level != null) return level >= 2;
        level = invokeInt(source, "getPermissionLevel");
        if (level != null) return level >= 2;

        Boolean byPlayer = invokeBoolean(player, "hasPermissions", 2);
        if (byPlayer != null) return byPlayer;

        try {
            Object playerList = source.getServer().getPlayerList();
            Object gameProfile = player.getGameProfile();
            for (java.lang.reflect.Method method : playerList.getClass().getMethods()) {
                if (!"isOp".equals(method.getName()) || method.getParameterCount() != 1) continue;
                Class<?> paramType = method.getParameterTypes()[0];

                Object arg = null;
                if (paramType.isInstance(gameProfile)) {
                    arg = gameProfile;
                } else {
                    for (java.lang.reflect.Constructor<?> ctor : paramType.getConstructors()) {
                        Class<?>[] params = ctor.getParameterTypes();
                        if (params.length == 2 && params[0] == String.class && params[1] == java.util.UUID.class) {
                            arg = ctor.newInstance(player.getName().getString(), player.getUUID());
                            break;
                        }
                        if (params.length == 2 && params[0] == java.util.UUID.class && params[1] == String.class) {
                            arg = ctor.newInstance(player.getUUID(), player.getName().getString());
                            break;
                        }
                    }
                }

                if (arg != null) {
                    Object value = method.invoke(playerList, arg);
                    if (value instanceof Boolean allowed) {
                        return allowed;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        // Safe fallback: if we cannot prove OP status, treat player as non-op for hidden command visibility.
        return false;
    }

    private static Boolean invokeBoolean(Object target, String methodName, int arg) {
        try {
            Object value = target.getClass().getMethod(methodName, int.class).invoke(target, arg);
            return value instanceof Boolean b ? b : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer invokeInt(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Integer i) return i;
            if (value instanceof Number n) return n.intValue();
            return null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
