package com.example.smfsaddon.command;

import com.example.smfsaddon.pijiagu.FeastSite;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** 调试：在脚下让席面长出来 */
public final class FeastCommand {

    private FeastCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("feast")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> run(context.getSource()))
                        .then(CommandManager.literal("end").executes(context -> end(context.getSource())))));
    }

    private static int end(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        com.example.smfsaddon.pijiagu.FeastSite.dissolve(
                (ServerWorld) player.getWorld(), player.getBlockPos());
        source.sendFeedback(() -> Text.literal("§7席散"), false);
        return 1;
    }

    private static int run(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        // 传"玩家脚下那一格"，生成器会把它的下一格直接换成石砖地基
        BlockPos center = player.getBlockPos();
        com.example.smfsaddon.pijiagu.FeastCeremony.start(
                (ServerWorld) player.getWorld(), center, player);
        source.sendFeedback(() -> Text.literal("§d席面开始升起"), false);
        return 1;
    }
}
