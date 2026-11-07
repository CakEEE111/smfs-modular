package com.example.smfsaddon.ghost.module;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 厉鬼模块注册表。
 *
 * <p>模块实现是单例，注册一次后全局共享。
 * 重复注册同一个 ID 会直接抛异常 —— 这是启动期错误，
 * 应当尽早暴露，而不是被静默覆盖。
 */
public final class GhostModuleRegistry {

    private static final Map<Identifier, GhostModule> MODULES = new LinkedHashMap<>();

    private GhostModuleRegistry() {
    }

    public static void register(GhostModule module) {
        Identifier id = module.id();
        if (MODULES.containsKey(id)) {
            throw new IllegalStateException("Duplicate ghost module id: " + id);
        }
        MODULES.put(id, module);
    }

    public static GhostModule get(Identifier id) {
        return MODULES.get(id);
    }

    public static boolean contains(Identifier id) {
        return MODULES.containsKey(id);
    }

    public static Collection<GhostModule> all() {
        return Collections.unmodifiableCollection(MODULES.values());
    }

    /**
     * 按规则 ID 反查规则实现。
     *
     * <p>图鉴只存 {@code (鬼种类, 规则 ID, 次数)}，渲染时用它找回
     * 规则的展示信息。找不到返回 {@code null} —— 注册表里没有的规则
     * 多半是被移除的内容，图鉴应跳过而不是报错。
     */
    public static GhostRule findRule(Identifier ruleId) {
        for (GhostModule module : MODULES.values()) {
            for (GhostRule rule : module.rules()) {
                if (rule.id().equals(ruleId)) {
                    return rule;
                }
            }
        }
        return null;
    }

    /**
     * 注册本模组内置的厉鬼模块。
     *
     * <p>在模组初始化阶段调用一次。
     */
    public static void registerBuiltins() {
        register(new com.example.smfsaddon.ghost.module.impl.StepGhostModule());
        register(new com.example.smfsaddon.ghost.module.impl.DomainPresenceModule());
        register(new com.example.smfsaddon.ghost.module.impl.SpatialPressureModule());
        // 环境类机制：判定只读玩家侧状态，换成任何宿主都能生效，供交错借用
        register(new com.example.smfsaddon.ghost.module.impl.FacingAwayModule());
        register(new com.example.smfsaddon.ghost.module.impl.LookUpModule());
        register(new com.example.smfsaddon.ghost.module.impl.LookDownModule());
        register(new com.example.smfsaddon.ghost.module.impl.WaterGateModule());
        register(new com.example.smfsaddon.ghost.module.impl.LowLightModule());
        register(new com.example.smfsaddon.ghost.module.impl.CropGateModule());
        register(new com.example.smfsaddon.ghost.module.impl.MineralGateModule());
        register(new com.example.smfsaddon.ghost.module.impl.SneakGateModule());
        register(new com.example.smfsaddon.ghost.module.impl.StationaryGateModule());
        register(new com.example.smfsaddon.ghost.module.impl.DoorBaneModule());
        register(new com.example.smfsaddon.ghost.module.impl.NativeGhostRuleModule());
        register(new com.example.smfsaddon.ghost.module.impl.ScapegoatModule());
        // 灵异交错：让鬼借用别人的杀人规律（按难度决定条数）
        register(new com.example.smfsaddon.ghost.module.impl.CrossingModule());
        // 协同也是模块，注册进同一张表
        GhostComboRegistry.registerBuiltins();
    }
}
