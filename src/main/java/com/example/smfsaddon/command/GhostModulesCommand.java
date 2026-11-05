package com.example.smfsaddon.command;

import com.example.smfsaddon.ghost.module.GhostCombo;
import com.example.smfsaddon.ghost.module.GhostComboRegistry;
import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.ghost.module.GhostModules;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.GhostRuleTuning;
import com.example.smfsaddon.item.GhostLanternItem;
import com.example.smfsaddon.item.ItemGhostHost;
import com.xie.smfs.entity.GhostEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 开发用命令：在游戏里直接查看 / 增删最近一只鬼的模块。
 *
 * <pre>
 * /ghostmodules registry              列出已注册模块
 * /ghostmodules list                  列出 8 格内最近一只鬼的模块与复苏值
 * /ghostmodules attach smfsaddon:xxx  挂上
 * /ghostmodules detach smfsaddon:xxx  摘下
 * </pre>
 */
public final class GhostModulesCommand {

    private static final double SEARCH_RADIUS = 8.0D;

    private GhostModulesCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ghostmodules")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("registry")
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    source.sendFeedback(
                                            () -> Text.literal("已注册厉鬼模块："), false);
                                    for (GhostModule module : GhostModuleRegistry.all()) {
                                        source.sendFeedback(
                                                () -> Text.literal("  - " + module.id()
                                                        + "（规则 " + module.rules().size()
                                                        + " / 能力 " + module.abilities().size() + "）"),
                                                false);
                                    }
                                    if (!GhostComboRegistry.all().isEmpty()) {
                                        source.sendFeedback(() -> Text.literal("已注册协同："), false);
                                        for (GhostCombo combo : GhostComboRegistry.all()) {
                                            source.sendFeedback(
                                                    () -> Text.literal("  - " + combo.id()
                                                            + " ← " + combo.requiredModules()),
                                                    false);
                                        }
                                    }
                                    return 1;
                                }))
                        .then(CommandManager.literal("rules")
                                .executes(context -> listRules(context.getSource())))
                        .then(CommandManager.literal("assembly")
                                .executes(context -> listAssembly(context.getSource())))
                        .then(CommandManager.literal("held")
                                .executes(context -> inspectHeld(context.getSource())))
                        .then(CommandManager.literal("slots")
                                .executes(context -> listSlots(context.getSource())))
                        .then(CommandManager.literal("check")
                                .executes(context -> qualityCheck(context.getSource())))
                        .then(CommandManager.literal("debug")
                                .executes(context -> toggleDebug(context.getSource())))
                        .then(CommandManager.literal("graft")
                                .then(CommandManager.argument("ghost", IdentifierArgumentType.identifier())
                                        .executes(context -> graftToHeld(
                                                context.getSource(),
                                                IdentifierArgumentType.getIdentifier(context, "ghost")))))
                        .then(CommandManager.literal("tune")
                                .then(CommandManager.argument("rule", IdentifierArgumentType.identifier())
                                        .then(costPreset("off", 0.0D))
                                        .then(costPreset("cheap", 0.5D))
                                        .then(costPreset("normal", 1.0D))
                                        .then(costPreset("costly", 2.0D))
                                        .then(costPreset("brutal", 4.0D))))
                        .then(CommandManager.literal("disable")
                                .then(CommandManager.argument("rule", IdentifierArgumentType.identifier())
                                        .executes(context -> setRuleEnabled(
                                                context.getSource(),
                                                IdentifierArgumentType.getIdentifier(context, "rule"),
                                                false))))
                        .then(CommandManager.literal("enable")
                                .then(CommandManager.argument("rule", IdentifierArgumentType.identifier())
                                        .executes(context -> setRuleEnabled(
                                                context.getSource(),
                                                IdentifierArgumentType.getIdentifier(context, "rule"),
                                                true))))
                        .then(CommandManager.literal("list")
                                .executes(context -> listNearest(context.getSource())))
                        .then(CommandManager.literal("attach")
                                .then(CommandManager.argument("module", IdentifierArgumentType.identifier())
                                        .executes(context -> {
                                            Identifier id = IdentifierArgumentType.getIdentifier(context, "module");
                                            return attachNearest(context.getSource(), id);
                                        })))
                        .then(CommandManager.literal("detach")
                                .then(CommandManager.argument("module", IdentifierArgumentType.identifier())
                                        .executes(context -> {
                                            Identifier id = IdentifierArgumentType.getIdentifier(context, "module");
                                            return detachNearest(context.getSource(), id);
                                        })))
                ));
    }

    private static int listNearest(ServerCommandSource source) {
        GhostEntity ghost = findNearest(source);
        if (ghost == null) {
            source.sendError(Text.literal("8 格内没有找到鬼"));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("最近的鬼：" + ghost.getName().getString()
                + "  模块数 " + GhostModules.count(ghost)), false);

        List<GhostModuleData> all = List.copyOf(GhostModules.of(ghost).modules().all());
        if (all.isEmpty()) {
            source.sendFeedback(() -> Text.literal("  （没有挂任何模块）"), false);
            return 1;
        }
        for (GhostModuleData data : all) {
            source.sendFeedback(() -> Text.literal("  - " + data.moduleId()
                    + "  复苏 " + String.format("%.1f", data.revival())
                    + "  强度 " + String.format("%.2f", data.intensity())), false);
        }

        // 交错是隐藏机制（玩家只能从行为上感知），这里必须能看到，否则没法排查
        GhostModuleData crossing = GhostModules.of(ghost).modules()
                .get(com.example.smfsaddon.ghost.module.impl.CrossingModule.ID);
        if (crossing != null) {
            var types = com.example.smfsaddon.ghost.module.impl.CrossingModule.types(crossing);
            source.sendFeedback(() -> Text.literal("  §d交错规律 §8(" + types.size() + " 条)："
                    + types), false);
        }
        return 1;
    }

    private static int attachNearest(ServerCommandSource source, Identifier moduleId) {
        GhostEntity ghost = findNearest(source);
        if (ghost == null) {
            source.sendError(Text.literal("8 格内没有找到鬼"));
            return 0;
        }
        if (!GhostModuleRegistry.contains(moduleId)) {
            source.sendError(Text.literal("模块未注册：" + moduleId));
            return 0;
        }
        if (GhostModules.has(ghost, moduleId)) {
            source.sendError(Text.literal("这只鬼已经带有该模块"));
            return 0;
        }
        GhostModules.attach(ghost, moduleId);
        source.sendFeedback(() -> Text.literal("已挂载 " + moduleId), true);
        return 1;
    }

    private static int detachNearest(ServerCommandSource source, Identifier moduleId) {
        GhostEntity ghost = findNearest(source);
        if (ghost == null) {
            source.sendError(Text.literal("8 格内没有找到鬼"));
            return 0;
        }
        if (!GhostModules.detach(ghost, moduleId)) {
            source.sendError(Text.literal("这只鬼没有该模块：" + moduleId));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("已卸载 " + moduleId), true);
        return 1;
    }

    /**
     * 把一只鬼的规律移植进主手物品。
     *
     * <p>这是装置界面的命令行等价物：验证机制时不必先做完 UI。
     */
    private static int graftToHeld(ServerCommandSource source, Identifier ghostType) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只有玩家能移植"));
            return 0;
        }

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            source.sendError(Text.literal("主手没有物品"));
            return 0;
        }

        var result = com.example.smfsaddon.fusion.GhostGrafting
                .tryGraftForGhost(held, ghostType, player);

        if (!result.success()) {
            if (result.chance() <= 0.0D) {
                source.sendError(Text.literal("移植失败：物品或规律清单无效"));
                return 0;
            }
            // 反噬已由 tryGraft 内部结算，这里只报告结果
            source.sendFeedback(() -> Text.literal("§c压制不足，赌输了 §8(成功率 "
                    + percent(result.chance())
                    + "，压制 " + (int) result.suppression()
                    + " < 需求 " + (int) result.required() + ")"), false);
            return 0;
        }

        player.playerScreenHandler.syncState();
        if (result.grafted() == 0) {
            // 成功但一条都没进：这些规律本来就在物品上（重复合入）
            source.sendFeedback(() -> Text.literal("§7这些规律已经在物品上了"), false);
            return 1;
        }
        String prefix = result.lucky() ? "§e压制不足，但赌赢了" : "§a已移植";
        source.sendFeedback(() -> Text.literal(prefix + " " + result.grafted() + " 条规律 §8(压制 "
                + (int) result.suppression() + " / 需求 " + (int) result.required()
                + (result.lucky() ? "，成功率 " + percent(result.chance()) : "") + ")"), false);
        return 1;
    }

    private static String percent(double chance) {
        return Math.round(chance * 100.0D) + "%";
    }

    /**
     * 切换模块调试观察口。
     */
    private static int toggleDebug(ServerCommandSource source) {
        boolean enabled = com.example.smfsaddon.ghost.module.GhostDebug.toggle();
        source.sendFeedback(
                () -> Text.literal(enabled ? "§a模块调试提示：开" : "§7模块调试提示：关"),
                true);
        return 1;
    }

    /**
     * 检查主手上的物品宿主。
     *
     * <p>物品宿主的毛病都是「静默不工作」—— 模块挂上了、数据没写、效果没触发，
     * 从外面看不出区别。这个命令把内部状态摊开：模块、复苏值、是否到阈值。
     */
    private static int inspectHeld(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只有玩家能检查手上的物品"));
            return 0;
        }

        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            source.sendError(Text.literal("主手没有物品"));
            return 0;
        }

        int count = ItemGhostHost.moduleCount(stack);
        source.sendFeedback(() -> Text.literal("§8=== 主手物品 ==="), false);
        source.sendFeedback(() -> Text.literal("§f" + stack.getName().getString()
                + " §8模块数：" + count), false);

        if (count <= 0) {
            source.sendFeedback(
                    () -> Text.literal("§c没有模块 —— 说明合入没写进物品 NBT"), false);
            return 1;
        }

        // 绑定的鬼种原始值：排查「显示成别的名字」时，这一行才是真相
        var bound = ItemGhostHost.boundGhostTypes(stack);
        source.sendFeedback(() -> Text.literal("§8绑定鬼种：" + bound), false);

        ItemGhostHost host = ItemGhostHost.open(stack, player.getWorld(), player);
        for (GhostModuleData data : List.copyOf(host.modules().all())) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            String name = module == null ? data.moduleId().toString() : module.displayName();
            // 复苏值一起打出来：变身/龟派气功这类消耗记在这里，不给看就是黑箱
            source.sendFeedback(
                    () -> Text.literal("  §e" + name
                            + " §8复苏 " + String.format("%.1f", data.revival())
                            + " §8(" + data.moduleId() + ")"), false);
        }
        return 1;
    }

    /**
     * 质量检测：这只鬼身上到底有几条规则、哪几条是废的。
     *
     * <p>为什么需要它：一只鬼的"强度"由几处叠加而成 —— 它自己的本体逻辑、
     * 装配表给的机制模块、以及交错借来的规律。任何一处没生效，
     * 从外面看都只是"这只鬼有点弱"，分不出是哪层出了问题。
     */
    private static int qualityCheck(ServerCommandSource source) {
        GhostEntity ghost = findNearest(source);
        if (ghost == null) {
            source.sendError(Text.literal("8 格内没有找到鬼"));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        Identifier typeId = net.minecraft.registry.Registries.ENTITY_TYPE.getId(ghost.getType());

        List<GhostModuleData> all = List.copyOf(GhostModules.of(ghost).modules().all());

        int ruleTotal = 0;
        int disabled = 0;
        java.util.Map<Identifier, Integer> groups = new java.util.LinkedHashMap<>();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (GhostModuleData data : all) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                lines.add("  §c未知模块 " + data.moduleId() + "（注册表里没有，能力已失效）");
                continue;
            }
            int enabled = 0;
            for (GhostRule rule : module.rules()) {
                ruleTotal++;
                if (!GhostRuleTuning.isEnabled(rule.id())) {
                    disabled++;
                    continue;
                }
                enabled++;
                Identifier group = rule.exclusionGroup();
                if (group != null) {
                    groups.merge(group, 1, Integer::sum);
                }
            }
            lines.add("  §7· §e" + module.displayName() + " §8→ " + enabled + " 条启用"
                    + (enabled < module.rules().size()
                    ? "（停用 " + (module.rules().size() - enabled) + "）" : ""));
        }

        source.sendFeedback(() -> Text.literal("§8=== 厉鬼质量检测 ==="), false);
        double distance = player == null ? 0.0D : Math.sqrt(player.squaredDistanceTo(ghost));
        // 实体名对本体鬼来说都是一句通用话（"未知规则的厉鬼"），
        // 真正能区分的是注册表 id → 中文名
        String display = typeId == null
                ? ghost.getName().getString()
                : com.example.smfsaddon.item.GhostNames.of(typeId.getPath());
        String head = "§f" + display + " §8(" + typeId + ")"
                + (player == null ? "" : String.format("  距离 %.1f", distance));
        source.sendFeedback(() -> Text.literal(head), false);
        String summary = "§f模块 " + all.size()
                + " §8/ §f规则 " + ruleTotal
                + " §8（启用 " + (ruleTotal - disabled) + "，停用 " + disabled + "）";
        source.sendFeedback(() -> Text.literal(summary), false);
        for (String line : lines) {
            source.sendFeedback(() -> Text.literal(line), false);
        }

        // ===== 诊断 =====
        source.sendFeedback(() -> Text.literal("§8— 诊断 —"), false);

        GhostModuleData nativeData = GhostModules.of(ghost).modules()
                .get(com.example.smfsaddon.ghost.module.impl.NativeGhostRuleModule.ID);
        if (nativeData == null) {
            // 只有 3 只鬼有装配表，所以这是常态而不是异常 —— 别标成警告
            source.sendFeedback(() -> Text.literal(
                    "  §7· 未装配源规律 §8（按本体自己的逻辑判定，交错照常生效）"), false);
        } else {
            var bound = com.example.smfsaddon.ghost.module.impl.NativeGhostRuleModule
                    .boundTypes(nativeData);
            source.sendFeedback(() -> Text.literal(
                    "  §a✓ 本源规律绑定 " + bound.size() + " 只 §8" + bound), false);
        }

        GhostModuleData crossingData = GhostModules.of(ghost).modules()
                .get(com.example.smfsaddon.ghost.module.impl.CrossingModule.ID);
        if (crossingData == null) {
            source.sendFeedback(() -> Text.literal(
                    "  §7· 无交错（和平难度，或这只鬼在交错功能前就已生成）"), false);
        } else {
            var types = com.example.smfsaddon.ghost.module.impl.CrossingModule.types(crossingData);
            var dead = com.example.smfsaddon.crossing.GhostRuleCrossing.unavailableTypes();
            source.sendFeedback(() -> Text.literal("  §a✓ 交错 " + types.size() + " 条"), false);
            for (Identifier borrowed : types) {
                String name = com.example.smfsaddon.item.GhostNames.of(borrowed.getPath());
                long[] stat = com.example.smfsaddon.crossing.GhostRuleCrossing.statsOf(borrowed);
                boolean cannotBuild = dead.contains(borrowed);
                String line;
                if (cannotBuild) {
                    line = "    §8· §c" + name + " §8造不出影子，恒为假";
                } else if (stat == null) {
                    line = "    §8· §7" + name + " §8还没判定过";
                } else {
                    // 判定过足够多次却一次没命中 → 基本可以认定是白借
                    boolean suspect = stat[0] >= 5 && stat[1] == 0;
                    line = "    §8· §7" + name + " §8判定 " + stat[0] + " 次，命中 " + stat[1]
                            + (suspect ? "  §e疑似白借（它的判定依赖自身场景）" : "");
                }
                source.sendFeedback(() -> Text.literal(line), false);
            }
        }

        long conflict = groups.values().stream().filter(count -> count > 1).count();
        if (conflict > 0) {
            source.sendFeedback(() -> Text.literal(
                    "  §e⚠ 有 " + conflict + " 组互斥规则同时启用 §8（高优先级会压过低优先级）"), false);
        }
        if (ruleTotal == 0) {
            source.sendFeedback(() -> Text.literal(
                    "  §e⚠ 一条规则都没有 §8：这只鬼完全按本体硬编码逻辑行动"), false);
        }
        if (ruleTotal > 0 && disabled == 0 && conflict == 0 && nativeData != null) {
            source.sendFeedback(() -> Text.literal("  §a✓ 未发现异常"), false);
        }
        return 1;
    }

    /**
     * 按本体鬼槽 API 的视角列出槽位。
     *
     * <p>和 {@code /ghostmodules held} 的分工：那条看的是物品内部的模块，
     * 这条看的是<b>附属扫槽时看到的画面</b>。附属的被动没触发，
     * 十有八九是这里看不见它。
     */
    private static int listSlots(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("只有玩家能查"));
            return 0;
        }
        var api = com.xie.smfs.api.TameableItemAPI.getInstance();
        var virtual = com.example.smfsaddon.item.FusedGhostSlots.bySlot(player);

        source.sendFeedback(() -> Text.literal("§8=== 鬼槽（本体 API 视角）==="), false);
        int shown = 0;
        for (int slot = 0; slot < com.xie.smfs.common.events.PlayerEvents.GHOST_SLOT_COUNT; slot++) {
            if (!api.isGhostSlotOccupied(player, slot)) {
                continue;
            }
            shown++;
            int index = slot;
            String type = api.getGhostTypeInSlot(player, slot);
            boolean projection = virtual.containsKey(slot);
            source.sendFeedback(() -> Text.literal("  §f槽 " + index + " §7"
                    + (type == null ? "?" : type) + "  "
                    + (projection ? "§d融合物品投影" : "§a真实鬼槽")), false);
        }
        if (shown == 0) {
            source.sendFeedback(() -> Text.literal("  §7（全空）"), false);
        }
        source.sendFeedback(() -> Text.literal("§8本机融合鬼：" + virtual.values()), false);
        return 1;
    }

    /**
     * 列出数据包载入的装配表。
     */
    private static int listAssembly(ServerCommandSource source) {
        var all = com.example.smfsaddon.assembly.GhostAssembly.all();
        if (all.isEmpty()) {
            source.sendFeedback(
                    () -> Text.literal("§7装配表为空：没有找到任何 ghost_assembly 数据包配置"),
                    false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("§8=== 厉鬼装配表 ==="), false);
        for (var entry : all.entrySet()) {
            source.sendFeedback(() -> Text.literal("§f" + entry.getKey()), false);
            for (var variant : entry.getValue()) {
                source.sendFeedback(
                        () -> Text.literal("  §7· " + variant.id()
                                + " §8×" + variant.weight() + "  " + variant.modules()),
                        false);
            }
        }
        return 1;
    }

    /**
     * 列出最近一只鬼身上所有规则的优先级、代价与互斥组。
     */
    private static int listRules(ServerCommandSource source) {
        GhostEntity ghost = findNearest(source);
        if (ghost == null) {
            source.sendError(Text.literal("8 格内没有找到鬼"));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("最近鬼身上的规则："), false);

        boolean any = false;
        for (GhostModuleData data : List.copyOf(GhostModules.of(ghost).modules().all())) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            if (module == null) {
                continue;
            }
            for (GhostRule rule : module.rules()) {
                any = true;
                Identifier group = rule.exclusionGroup();
                String line = "  - " + rule.id()
                        + "  优先级 " + rule.priority()
                        + "  代价 " + rule.revivalGain()
                        + (group != null ? "  互斥组 " + group : "")
                        + "  ×" + GhostRuleTuning.revivalScale(rule.id())
                        + (GhostRuleTuning.isEnabled(rule.id()) ? "" : "  [已停用]");
                source.sendFeedback(() -> Text.literal(line), false);
            }
        }
        if (!any) {
            source.sendFeedback(() -> Text.literal("  （没有规则）"), false);
        }
        return 1;
    }

    /**
     * 生成一个"代价档位"字面量子命令。
     *
     * <p>用固定档位而不是自由数值，是刻意的：调平衡时人不会去想
     * "到底该填 1.37 还是 1.41"，只会在「有点贵」和「贵到没人用」之间选。
     */
    private static LiteralArgumentBuilder<ServerCommandSource> costPreset(String name, double scale) {
        return CommandManager.literal(name).executes(context -> {
            Identifier ruleId = IdentifierArgumentType.getIdentifier(context, "rule");
            GhostRuleTuning.setRevivalScale(ruleId, scale);
            context.getSource().sendFeedback(
                    () -> Text.literal("规则 " + ruleId + " 的复苏代价倍率 → ×" + scale),
                    true);
            return 1;
        });
    }

    /**
     * 启停某条规则，对所有用到它的鬼同时生效。
     */
    private static int setRuleEnabled(ServerCommandSource source, Identifier ruleId, boolean enabled) {
        GhostRuleTuning.setEnabled(ruleId, enabled);
        source.sendFeedback(
                () -> Text.literal("规则 " + ruleId + (enabled ? " 已启用" : " 已停用")),
                true);
        return 1;
    }

    private static GhostEntity findNearest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return null;
        }
        return player.getWorld()
                .getEntitiesByClass(GhostEntity.class, player.getBoundingBox().expand(SEARCH_RADIUS),
                        ghost -> ghost.isAlive())
                .stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .orElse(null);
    }
}
