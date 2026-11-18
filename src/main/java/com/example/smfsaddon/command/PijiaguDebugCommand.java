package com.example.smfsaddon.command;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.entity.PijiaguSkinEntity;
import com.example.smfsaddon.entity.PijiaguUnionEntity;
import com.example.smfsaddon.pijiagu.PijiaguWedding;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * 皮嫁骨测试指令。
 *
 * <pre>
 * /pijiagu flesh              给自己挂「皮已离体」（等价于剪过皮）
 * /pijiagu spirit &lt;max&gt; &lt;cur&gt;  直接写灵力上限与当前灵力
 * /pijiagu skin               在面前召一具"你自己的皮"
 * /pijiagu union              在面前召一只礼成体
 * /pijiagu start              无视条件直接起礼（没有皮就先召一具）
 * /pijiagu off                移除「皮已离体」
 * </pre>
 */
public final class PijiaguDebugCommand {

    private PijiaguDebugCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> {
            dispatcher.register(CommandManager.literal("pijiagu")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("flesh").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        player.addStatusEffect(new StatusEffectInstance(
                                SmfsAddon.FLESH_REMOVED, -1, 0, false, false, false));
                        send(player, "§d皮已离体（测试）");
                        return 1;
                    }))
                    .then(CommandManager.literal("off").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        player.removeStatusEffect(SmfsAddon.FLESH_REMOVED);
                        send(player, "§7已移除「皮已离体」");
                        return 1;
                    }))
                    .then(CommandManager.literal("spirit")
                            .then(CommandManager.argument("max", FloatArgumentType.floatArg(0))
                                    .then(CommandManager.argument("cur", FloatArgumentType.floatArg(0))
                                            .executes(ctx -> {
                                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                                float max = FloatArgumentType.getFloat(ctx, "max");
                                                float cur = FloatArgumentType.getFloat(ctx, "cur");
                                                var nbt = com.xie.smfs.common.events.PlayerEvents
                                                        .getSpiritAttributes(player);
                                                nbt.putDouble("maxSpirit", max);
                                                nbt.putDouble("currentSpirit", cur);
                                                com.xie.smfs.common.events.PlayerEvents
                                                        .setSpiritAttributes(player, nbt);
                                                send(player, "§7灵力：上限 " + (int) max
                                                        + " / 当前 " + (int) cur);
                                                return 1;
                                            }))))
                    .then(CommandManager.literal("skin").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        spawn(player, true);
                        return 1;
                    }))
                    .then(CommandManager.literal("union").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        spawn(player, false);
                        return 1;
                    }))
                    .then(CommandManager.literal("yaw")
                            .then(CommandManager.argument("deg", FloatArgumentType.floatArg(-360, 360))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        float deg = FloatArgumentType.getFloat(ctx, "deg");
                                        PijiaguWedding.YAW_OFFSET = deg;
                                        send(player, "§7礼堂摆位偏移 = " + (int) deg
                                                + "°（再执行一次 /pijiagu start 看效果）");
                                        return 1;
                                    })))
                    .then(CommandManager.literal("spawnrate")
                            .then(CommandManager.argument("seconds", FloatArgumentType.floatArg(0.1f, 600f))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        float sec = FloatArgumentType.getFloat(ctx, "seconds");
                                        com.example.smfsaddon.pijiagu.PijiaguSpawns.PERIOD =
                                                Math.max(20, (int) (sec * 20));
                                        send(player, "§7生成判定间隔 = " + sec + " 秒");
                                        return 1;
                                    })))
                    .then(CommandManager.literal("chance")
                            .then(CommandManager.argument("p", FloatArgumentType.floatArg(0.05f, 1f))
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        float p = FloatArgumentType.getFloat(ctx, "p");
                                        com.example.smfsaddon.pijiagu.PijiaguSpawns.CHANCE = p;
                                        send(player, "§7单次成功率 = " + (int) (p * 100) + "%");
                                        return 1;
                                    })))
                    .then(CommandManager.literal("spawnnow").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        boolean ok = com.example.smfsaddon.pijiagu.PijiaguSpawns.forceSpawn(
                                (ServerWorld) player.getWorld(), player);
                        send(player, ok ? "§7已生成一只" : "§c附近太亮 / 已满 3 只 / 没找到合适地面");
                        return 1;
                    }))
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        com.example.smfsaddon.pijiagu.FeastCeremony.clear();
                        send(player, "§7席面状态已清空（可以重新施放）");
                        return 1;
                    }))
                    .then(CommandManager.literal("info").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerWorld world = (ServerWorld) player.getWorld();
                        var halls = world.getEntitiesByClass(
                                com.example.smfsaddon.entity.PijiaguHallEntity.class,
                                player.getBoundingBox().expand(64.0D), Entity::isAlive);
                        player.sendMessage(Text.literal("§7玩家=("
                                + String.format("%.2f", player.getX()) + ","
                                + String.format("%.2f", player.getZ()) + ") yaw="
                                + String.format("%.1f", player.getYaw())), false);
                        if (halls.isEmpty()) {
                            player.sendMessage(Text.literal("§c附近没有礼堂"), false);
                        } else {
                            for (var hall : halls) {
                                for (String line : hall.debugInfo().split("\n")) {
                                    player.sendMessage(Text.literal("§7" + line), false);
                                }
                            }
                        }
                        var skins = world.getEntitiesByClass(
                                com.example.smfsaddon.entity.PijiaguSkinEntity.class,
                                player.getBoundingBox().expand(64.0D), Entity::isAlive);
                        for (var skin : skins) {
                            player.sendMessage(Text.literal("§d新娘=("
                                    + String.format("%.2f", skin.getX()) + ","
                                    + String.format("%.2f", skin.getZ()) + ")"), false);
                        }
                        return 1;
                    }))
                    .then(CommandManager.literal("start").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        PijiaguWedding.forceStart((ServerWorld) player.getWorld(), player);
                        return 1;
                    })));
        });
    }

    private static void spawn(ServerPlayerEntity player, boolean skin) {
        ServerWorld world = (ServerWorld) player.getWorld();
        if (skin) {
            // 测试时频繁召会堆一地，先清掉旧的
            for (PijiaguSkinEntity old : world.getEntitiesByClass(PijiaguSkinEntity.class,
                    player.getBoundingBox().expand(48.0D), Entity::isAlive)) {
                old.discard();
            }
        }
        Entity entity = skin
                ? SmfsAddon.PIJIAGU_SKIN.create(world)
                : SmfsAddon.PIJIAGU_UNION.create(world);
        if (entity == null) {
            send(player, "§c生成失败：实体类型为空");
            return;
        }
        double yaw = Math.toRadians(player.getYaw());
        double x = player.getX() + Math.sin(yaw) * 3.0D;
        double z = player.getZ() - Math.cos(yaw) * 3.0D;
        entity.refreshPositionAndAngles(x, player.getY(), z, player.getYaw() + 180.0F, 0.0F);
        world.spawnEntity(entity);
        send(player, skin ? "§7召出：你自己的皮" : "§7召出：礼成体");
    }

    private static void send(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }
}
