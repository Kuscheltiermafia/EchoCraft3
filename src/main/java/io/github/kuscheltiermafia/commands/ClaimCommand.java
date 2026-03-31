package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.kuscheltiermafia.claims.ClaimData;
import io.github.kuscheltiermafia.claims.ClaimManager;
import io.github.kuscheltiermafia.teams.TeamManager;
import io.github.kuscheltiermafia.util.TextPalette;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                        .then(Commands.literal("settings")
                                .executes(ctx -> showMenu(ctx.getSource())))
                        .then(buildToggleCommand())
                        .then(buildToggleDialogCommand())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleCommand() {
        return Commands.literal("toggle")
                .then(Commands.literal("explosions")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "explosions", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "explosions", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "explosions", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "explosions", false))))
                .then(Commands.literal("pvp")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "pvp", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "pvp", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "pvp", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "pvp", false))))
                .then(Commands.literal("foreign_break")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "foreign_break", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "foreign_break", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_break", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_break", false))))
                .then(Commands.literal("foreign_place")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "foreign_place", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "foreign_place", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_place", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_place", false))))
                .then(Commands.literal("foreign_interact")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_interact", false))))
                .then(Commands.literal("foreign_entity")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "foreign_entity", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "foreign_entity", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "foreign_entity", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "foreign_entity", false))))
                .then(Commands.literal("ally_break")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "ally_break", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "ally_break", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "ally_break", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "ally_break", false))))
                .then(Commands.literal("ally_place")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "ally_place", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "ally_place", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "ally_place", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "ally_place", false))))
                .then(Commands.literal("ally_interact")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "ally_interact", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "ally_interact", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "ally_interact", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "ally_interact", false))))
                .then(Commands.literal("ally_entity")
                        .then(Commands.literal("allowed").executes(ctx -> toggle(ctx.getSource(), "ally_entity", true)))
                        .then(Commands.literal("disallowed").executes(ctx -> toggle(ctx.getSource(), "ally_entity", false)))
                        .then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), "ally_entity", true)))
                        .then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), "ally_entity", false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleDialogCommand() {
        return Commands.literal("toggle_dialog")
                .then(Commands.literal("explosions").executes(ctx -> toggleFromDialog(ctx.getSource(), "explosions")))
                .then(Commands.literal("pvp").executes(ctx -> toggleFromDialog(ctx.getSource(), "pvp")))
                .then(Commands.literal("foreign_break").executes(ctx -> toggleFromDialog(ctx.getSource(), "foreign_break")))
                .then(Commands.literal("foreign_place").executes(ctx -> toggleFromDialog(ctx.getSource(), "foreign_place")))
                .then(Commands.literal("foreign_interact").executes(ctx -> toggleFromDialog(ctx.getSource(), "foreign_interact")))
                .then(Commands.literal("foreign_entity").executes(ctx -> toggleFromDialog(ctx.getSource(), "foreign_entity")))
                .then(Commands.literal("ally_break").executes(ctx -> toggleFromDialog(ctx.getSource(), "ally_break")))
                .then(Commands.literal("ally_place").executes(ctx -> toggleFromDialog(ctx.getSource(), "ally_place")))
                .then(Commands.literal("ally_interact").executes(ctx -> toggleFromDialog(ctx.getSource(), "ally_interact")))
                .then(Commands.literal("ally_entity").executes(ctx -> toggleFromDialog(ctx.getSource(), "ally_entity")));
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
            source.sendSuccess(() -> TextPalette.white("This chunk is not claimed."), false);
            return 1;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        Component teamComponent = claim.getTeamName() != null && !claim.getTeamName().isBlank()
                ? teams.getDisplayComponent(claim.getTeamName())
                : TextPalette.yellow("Unknown Team");
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("This chunk is claimed by "),
                teamComponent,
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static int linkTeam(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Claims are now team-bound automatically when a "),
                TextPalette.yellow("Claim Banner"),
                TextPalette.white(" is placed.")
        ), false);
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
            source.sendSuccess(() -> TextPalette.white("This chunk is not claimed."), false);
            return 0;
        }
        TeamManager teams = TeamManager.get(source.getServer());
        if (claim.getTeamName() == null || !teams.canManageClaims(claim.getTeamName(), player.getUUID())) {
            source.sendSuccess(() -> TextPalette.white("Only team Moderators and Leaders can remove this claim."), false);
            return 0;
        }

        claims.removeClaim(dimId, chunkPos.x(), chunkPos.z());
        source.sendSuccess(() -> TextPalette.white("Claim removed."), false);
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
            source.sendSuccess(() -> TextPalette.white("This chunk is not claimed."), false);
            return 0;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        boolean canManage = claim.getTeamName() != null && teams.canManageClaims(claim.getTeamName(), player.getUUID());

        if (!canManage) {
            source.sendSuccess(() -> TextPalette.white("Only team Moderators and Leaders can change claim settings."), false);
            return 0;
        }

        int changedClaims = claims.setTeamSetting(claim.getTeamName(), key, enabled);
        if (changedClaims < 0) {
            source.sendSuccess(() -> TextPalette.white("Unknown setting."), false);
            return 0;
        }

        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Set "),
                TextPalette.yellow(toDisplayLabel(key)),
                TextPalette.white(" to "),
                TextPalette.status(enabled),
                TextPalette.white(" for "),
                TextPalette.yellow(String.valueOf(changedClaims)),
                TextPalette.white(" team claim" + (changedClaims == 1 ? "" : "s") + ".")
        ), false);
        return 1;
    }

    private static int toggleFromDialog(CommandSourceStack source, String key) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClaimData claim = getCurrentClaim(source, player);
        if (claim == null) {
            source.sendSuccess(() -> TextPalette.white("This chunk is not claimed."), false);
            return 0;
        }

        int result = toggle(source, key, !getSettingValue(claim, key));
        if (result != 1) {
            return result;
        }

        ClaimData updatedClaim = getCurrentClaim(source, player);
        if (updatedClaim != null) {
            openClaimSettingsDialog(player, updatedClaim);
        }
        return 1;
    }

    private static int showMenu(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClaimData claim = getCurrentClaim(source, player);
        if (claim == null) {
            source.sendSuccess(() -> TextPalette.white("This chunk is not claimed."), false);
            return 0;
        }

        TeamManager teams = TeamManager.get(source.getServer());
        boolean canManage = claim.getTeamName() != null && teams.canManageClaims(claim.getTeamName(), player.getUUID());
        if (!canManage) {
            source.sendSuccess(() -> TextPalette.white("Only team Moderators and Leaders can change claim settings."), false);
            return 0;
        }

        openClaimSettingsDialog(player, claim);
        return 1;
    }

    private static void openClaimSettingsDialog(ServerPlayer viewer, ClaimData claim) {
        List<Input> inputs = new ArrayList<>();
        /*
        inputs.add(new Input("explosions", new BooleanInput(TextPalette.white("Explosions"), claim.isExplosionsAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("pvp", new BooleanInput(TextPalette.white("PvP"), claim.isPvpAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("ally_break", new BooleanInput(TextPalette.white("Ally Break"), claim.isAllyBreakAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("ally_place", new BooleanInput(TextPalette.white("Ally Place"), claim.isAllyPlaceAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("ally_interact", new BooleanInput(TextPalette.white("Ally Interact"), claim.isAllyInteractAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("ally_entity", new BooleanInput(TextPalette.white("Ally Entity"), claim.isAllyEntityAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("foreign_break", new BooleanInput(TextPalette.white("Foreign Break"), claim.isForeignBreakAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("foreign_place", new BooleanInput(TextPalette.white("Foreign Place"), claim.isForeignPlaceAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("foreign_interact", new BooleanInput(TextPalette.white("Foreign Interact"), claim.isForeignInteractAllowed(), "Allow", "Disallow")));
        inputs.add(new Input("foreign_entity", new BooleanInput(TextPalette.white("Foreign Entity"), claim.isForeignEntityAllowed(), "Allow", "Disallow")));
         */

        List<ActionButton> actions = new ArrayList<>();
        actions.add(toggleDialogButton("Explosions", claim.isExplosionsAllowed(), "explosions"));
        actions.add(toggleDialogButton("PvP", claim.isPvpAllowed(), "pvp"));

        actions.add(toggleDialogButton("Ally Break", claim.isAllyBreakAllowed(), "ally_break"));
        actions.add(toggleDialogButton("Ally Place", claim.isAllyPlaceAllowed(), "ally_place"));
        actions.add(toggleDialogButton("Ally Interact", claim.isAllyInteractAllowed(), "ally_interact"));
        actions.add(toggleDialogButton("Ally Entity", claim.isAllyEntityAllowed(), "ally_entity"));

        actions.add(toggleDialogButton("Foreign Break", claim.isForeignBreakAllowed(), "foreign_break"));
        actions.add(toggleDialogButton("Foreign Place", claim.isForeignPlaceAllowed(), "foreign_place"));
        actions.add(toggleDialogButton("Foreign Interact", claim.isForeignInteractAllowed(), "foreign_interact"));
        actions.add(toggleDialogButton("Foreign Entity", claim.isForeignEntityAllowed(), "foreign_entity"));

        CommonDialogData common = new CommonDialogData(
                TextPalette.white("Claim Settings"),
                Optional.of(TextPalette.yellow("Use the buttons below to apply changes.")),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                inputs
        );
        MultiActionDialog dialog = new MultiActionDialog(common, actions, Optional.empty(), 2);
        viewer.openDialog(Holder.direct(dialog));
    }

    private static ActionButton dialogButton(String label, String command) {
        return new ActionButton(
                new CommonButtonData(Component.literal(label).withStyle(net.minecraft.ChatFormatting.WHITE), 180),
                Optional.of(new StaticAction(new ClickEvent.RunCommand(command)))
        );
    }

    private static ActionButton toggleDialogButton(String label, boolean currentAllowed, String key) {
        Component text = TextPalette.join(
                TextPalette.white(label + ": "),
                TextPalette.status(currentAllowed)
        );
        return new ActionButton(
                new CommonButtonData(text, 180),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/claim toggle_dialog " + key)))
        );
    }


    private static String toDisplayLabel(String key) {
        return switch (key) {
            case "explosions" -> "Explosions";
            case "pvp" -> "PvP";
            case "foreign_break" -> "Foreign Break";
            case "foreign_place" -> "Foreign Place";
            case "foreign_interact" -> "Foreign Interact";
            case "foreign_entity" -> "Foreign Entity";
            case "ally_break" -> "Ally Break";
            case "ally_place" -> "Ally Place";
            case "ally_interact" -> "Ally Interact";
            case "ally_entity" -> "Ally Entity";
            default -> key;
        };
    }

    private static ClaimData getCurrentClaim(CommandSourceStack source, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos.getX() >> 4, playerPos.getZ() >> 4);
        String dimId = player.level().dimension().toString();
        ClaimManager claims = ClaimManager.get(source.getServer());
        return claims.getClaim(dimId, chunkPos.x(), chunkPos.z());
    }

    private static boolean getSettingValue(ClaimData claim, String key) {
        return switch (key) {
            case "explosions" -> claim.isExplosionsAllowed();
            case "pvp" -> claim.isPvpAllowed();
            case "foreign_break" -> claim.isForeignBreakAllowed();
            case "foreign_place" -> claim.isForeignPlaceAllowed();
            case "foreign_interact" -> claim.isForeignInteractAllowed();
            case "foreign_entity" -> claim.isForeignEntityAllowed();
            case "ally_break" -> claim.isAllyBreakAllowed();
            case "ally_place" -> claim.isAllyPlaceAllowed();
            case "ally_interact" -> claim.isAllyInteractAllowed();
            case "ally_entity" -> claim.isAllyEntityAllowed();
            default -> false;
        };
    }
}
