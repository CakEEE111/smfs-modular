package com.example.smfsaddon.command;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.RuleCodex;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规律图鉴。
 *
 * <pre>
 * /ghostcodex          查看自己的图鉴
 * /ghostcodex reset    清空（调试用）
 * </pre>
 *
 * <p>为什么先做命令而不是 GUI：图鉴的价值全在数据层 ——
 * 记录什么、什么时候揭示、按什么分组。这些定下来之后，
 * 换成书本界面只是渲染工作，不影响任何逻辑。
 */
public final class GhostCodexCommand {

    private GhostCodexCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ghostcodex")
                        .executes(context -> show(context.getSource()))
                        .then(CommandManager.literal("reset")
                                .executes(context -> reset(context.getSource())))));
    }

    private static int show(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只有玩家能查看图鉴"));
            return 0;
        }

        List<RuleCodex.Observation> all = RuleCodex.entries(player);
        if (all.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§7图鉴是空的。去活下来一次，再来看看。"), false);
            return 1;
        }

        // 按鬼种类分组，保持稳定顺序
        Map<Identifier, List<RuleCodex.Observation>> byGhost = new LinkedHashMap<>();
        for (RuleCodex.Observation observation : all) {
            byGhost.computeIfAbsent(observation.ghostType(), key -> new ArrayList<>())
                    .add(observation);
        }

        source.sendFeedback(() -> Text.literal("§8===== §6厉鬼规律图鉴 §8====="), false);
        for (Map.Entry<Identifier, List<RuleCodex.Observation>> group : byGhost.entrySet()) {
            String ghostName = displayName(group.getKey());
            source.sendFeedback(
                    () -> Text.literal("§f" + ghostName + " §8(" + group.getKey() + ")"),
                    false
            );
            for (RuleCodex.Observation observation : group.getValue()) {
                appendEntry(source, observation);
            }
        }
        return 1;
    }

    private static void appendEntry(ServerCommandSource source, RuleCodex.Observation observation) {
        GhostRule rule = GhostModuleRegistry.findRule(observation.ruleId());
        GhostCodexEntry codex = rule == null ? null : rule.codex();

        if (codex == null) {
            // 规则被移除或本来就不进图鉴，只留一条痕迹
            source.sendFeedback(
                    () -> Text.literal("  §8· 未知规律 §7×" + observation.count()),
                    false
            );
            return;
        }

        String title = "  §e· " + codex.title() + " §7×" + observation.count();
        source.sendFeedback(() -> Text.literal(title), false);

        if (observation.count() >= codex.hintRevealAt()) {
            source.sendFeedback(() -> Text.literal("    §7" + codex.hint()), false);
        } else {
            int remaining = codex.hintRevealAt() - observation.count();
            source.sendFeedback(
                    () -> Text.literal("    §8？？？（再观察 " + remaining + " 次）"),
                    false
            );
        }
    }

    private static String displayName(Identifier ghostType) {
        EntityType<?> type = Registries.ENTITY_TYPE.get(ghostType);
        return type == null ? ghostType.toString() : type.getName().getString();
    }

    private static int reset(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只有玩家能清空图鉴"));
            return 0;
        }
        RuleCodex.reset(player);
        source.sendFeedback(() -> Text.literal("§7图鉴已清空"), true);
        return 1;
    }
}
