package io.github.kuscheltiermafia.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kuscheltiermafia.users.LocatorBarColor;
import io.github.kuscheltiermafia.users.UserSettingsManager;
import io.github.kuscheltiermafia.util.TextPalette;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext ignoredRegistryAccess) {
        var root = Commands.literal("user")
                .then(Commands.literal("settings").executes(ctx -> openSettings(ctx.getSource())));

        var colorRoot = Commands.literal("color");
        colorRoot.then(Commands.literal("hex")
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> setHexColor(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "value")
                        ))));
        for (LocatorBarColor color : LocatorBarColor.values()) {
            colorRoot.then(Commands.literal(color.token())
                    .executes(ctx -> setColor(ctx.getSource(), color)));
        }

        dispatcher.register(root.then(colorRoot));
    }

    private static int openSettings(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        openDialog(player);
        return 1;
    }

    private static int setColor(CommandSourceStack source, LocatorBarColor color) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UserSettingsManager.get(source.getServer()).setLocatorColor(player.getUUID(), color);
        syncWaypointColor(player, color.token(), null);
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Locator bar color set to "),
                Component.literal(color.displayName()).withStyle(color.formatting()),
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static int setHexColor(CommandSourceStack source, String rawHex) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UserSettingsManager settings = UserSettingsManager.get(source.getServer());
        if (!settings.setLocatorHexColor(player.getUUID(), rawHex)) {
            source.sendSuccess(() -> TextPalette.join(
                    TextPalette.white("Invalid hex color. Use "),
                    TextPalette.yellow("#RRGGBB"),
                    TextPalette.white(" or "),
                    TextPalette.yellow("RRGGBB"),
                    TextPalette.white(".")
            ), false);
            return 0;
        }
        String normalized = settings.getLocatorColorToken(player.getUUID());
        syncWaypointColor(player, null, normalized);
        source.sendSuccess(() -> TextPalette.join(
                TextPalette.white("Locator bar color set to "),
                Component.literal(normalized).withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(Integer.parseInt(normalized.substring(1), 16)))),
                TextPalette.white(".")
        ), false);
        return 1;
    }

    private static void openDialog(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        var settings = UserSettingsManager.get(server);
        var currentToken = settings.getLocatorColorToken(player.getUUID());
        var currentPreset = settings.getLocatorColorPreset(player.getUUID());

        List<Input> inputs = new ArrayList<>();
        inputs.add(new Input("hex", new TextInput(
                200,
                TextPalette.white("Custom Hex (#RRGGBB)"),
                false,
                currentToken.startsWith("#") ? currentToken : "",
                7,
                Optional.empty()
        )));

        List<ActionButton> actions = new ArrayList<>();
        for (LocatorBarColor color : LocatorBarColor.values()) {
            String marker = color == currentPreset && !currentToken.startsWith("#") ? "[Selected] " : "[Select] ";
            Component label = Component.literal(marker)
                    .withStyle(color == currentPreset && !currentToken.startsWith("#") ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.WHITE)
                    .append(Component.literal(color.displayName()).withStyle(color.formatting()));
            actions.add(new ActionButton(
                    new CommonButtonData(label, 170),
                    Optional.of(new StaticAction(new ClickEvent.RunCommand("/user color " + color.token())))
            ));
        }
        actions.add(new ActionButton(
                new CommonButtonData(TextPalette.white("Apply Custom Hex"), 170),
                Optional.of(buildHexApplyAction())
        ));

        CommonDialogData common = new CommonDialogData(
                TextPalette.white("User Settings"),
                Optional.of(TextPalette.yellow("Locator Color")),
                true,
                false,
                DialogAction.CLOSE,
                List.of(),
                inputs
        );
        player.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.empty(), 2)));
    }

    private static Action buildHexApplyAction() {
        try {
            Class<?> parsedTemplateClass = Class.forName("net.minecraft.server.dialog.action.ParsedTemplate");
            Object parsed = null;
            for (java.lang.reflect.Method method : parsedTemplateClass.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) continue;
                method.setAccessible(true);

                Object value = method.invoke(null, "/user color hex $(hex)");
                if (parsedTemplateClass.isInstance(value)) {
                    parsed = value;
                    break;
                }

                // Mojang uses DataResult for template parsing; unwrap result() if needed.
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
        return new StaticAction(new ClickEvent.SuggestCommand("/user color hex "));
    }

    private static void syncWaypointColor(ServerPlayer player, String presetToken, String normalizedHex) {
        MinecraftServer server = player.level().getServer();
        var source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withSuppressedOutput();
        if (normalizedHex != null && !normalizedHex.isBlank()) {
            String hex = normalizedHex.startsWith("#") ? normalizedHex.substring(1) : normalizedHex;
            tryWaypointCommands(server, source,
                    "waypoint modify @s color hex " + hex
            );
            return;
        }
        if (presetToken != null && !presetToken.isBlank()) {
            tryWaypointCommands(server, source,
                    "waypoint modify @s color " + presetToken
            );
        }
    }

    private static void tryWaypointCommands(MinecraftServer server, CommandSourceStack source, String... candidates) {
        for (String command : candidates) {
            try {
                server.getCommands().performPrefixedCommand(source, command);
            } catch (Exception ignored) {
                // Try next syntax variant for this game version/mapping.
            }
        }
    }
}

