package com.terrawrath.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terrawrath.disaster.Disaster;
import com.terrawrath.disaster.DisasterManager;
import com.terrawrath.disaster.DisasterType;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /disaster start &lt;type&gt; [intensity] [front|nearby], /disaster stop, /disaster random [intensity]
 */
public final class DisasterCommand {
    private DisasterCommand() {}

    public static boolean allowed(CommandSourceStack src) {
        if (Commands.LEVEL_GAMEMASTERS.check(src.permissions())) {
            return true;
        }
        ServerPlayer p = src.getPlayer();
        return p != null && src.getServer().isSingleplayerOwner(p.nameAndId());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        String[] ids = Arrays.stream(DisasterType.values()).map(t -> t.id).toArray(String[]::new);
        dispatcher.register(Commands.literal("disaster")
            .requires(DisasterCommand::allowed)
            .then(Commands.literal("start")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b))
                    .executes(ctx -> start(ctx, 1.0F, "front"))
                    .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.25F, 2.0F))
                        .executes(ctx -> start(ctx, FloatArgumentType.getFloat(ctx, "intensity"), "front"))
                        .then(Commands.argument("placement", StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"front", "nearby"}, b))
                            .executes(ctx -> start(ctx, FloatArgumentType.getFloat(ctx, "intensity"), StringArgumentType.getString(ctx, "placement")))))))
            .then(Commands.literal("stop").executes(DisasterCommand::stop))
            .then(Commands.literal("random")
                .executes(ctx -> random(ctx, 1.0F, "front"))
                .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.25F, 2.0F))
                    .executes(ctx -> random(ctx, FloatArgumentType.getFloat(ctx, "intensity"), "front"))
                    .then(Commands.argument("placement", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"front", "nearby"}, b))
                        .executes(ctx -> random(ctx, FloatArgumentType.getFloat(ctx, "intensity"), StringArgumentType.getString(ctx, "placement"))))))
            .then(Commands.literal("list").executes(DisasterCommand::list)));
    }

    private static DisasterManager.Placement placement(String s) {
        return "nearby".equalsIgnoreCase(s) ? DisasterManager.Placement.NEARBY : DisasterManager.Placement.FRONT;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, float intensity, String placement) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "type");
        DisasterType type = DisasterType.byId(id);
        if (type == null) {
            ctx.getSource().sendFailure(Component.translatable("terrawrath.command.unknown", id));
            return 0;
        }
        Disaster d = DisasterManager.start(player, type, intensity, placement(placement));
        ctx.getSource().sendSuccess(() -> Component.translatable("terrawrath.command.started",
            Component.translatable(type.nameKey()).withStyle(ChatFormatting.GOLD), String.format("%.2f", intensity)), true);
        return d != null ? 1 : 0;
    }

    private static int random(CommandContext<CommandSourceStack> ctx, float intensity, String placement) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Disaster d = DisasterManager.startRandom(player, intensity, placement(placement));
        ctx.getSource().sendSuccess(() -> Component.translatable("terrawrath.command.started",
            Component.translatable(d.type.nameKey()).withStyle(ChatFormatting.GOLD), String.format("%.2f", intensity)), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        int n = DisasterManager.stopAll();
        ctx.getSource().sendSuccess(() -> Component.translatable("terrawrath.command.stopped", n), true);
        return n;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (DisasterManager.active().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("terrawrath.command.none"), false);
            return 0;
        }
        for (Disaster d : DisasterManager.active()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(d.type.nameKey()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(String.format(" @ %.0f %.0f %.0f", d.center().x, d.center().y, d.center().z)).withStyle(ChatFormatting.GRAY)), false);
        }
        return DisasterManager.active().size();
    }
}
