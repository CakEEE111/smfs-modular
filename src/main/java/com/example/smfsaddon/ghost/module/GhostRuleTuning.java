package com.example.smfsaddon.ghost.module;

import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则级全局调参。
 *
 * <p>解决的问题：一条规则往往被多只鬼复用。它太强或代价太离谱时，
 * 不该去改 43 个类里的魔数，也不该逐个模块改 —— 在这里改一次，
 * 所有用到该规则的鬼同时生效。
 *
 * <p>目前提供两个旋钮：
 * <ul>
 *   <li>{@code revivalScale} 复苏代价倍率 —— 调「这条规则有多贵」；</li>
 *   <li>{@code enabled} 启停 —— 调「这条规则还存不存在」。</li>
 * </ul>
 *
 * <p>约定：所有覆盖都是运行期内存态，重启回到默认值。
 * 需要持久化时由调用方自己存盘（命令层面已经够用）。
 *
 * <p>线程安全：调度在服务端主线程，但配置可能从别处写入，故用并发容器。
 */
public final class GhostRuleTuning {

    private static final Map<Identifier, Double> REVIVAL_SCALE = new ConcurrentHashMap<>();
    private static final Set<Identifier> DISABLED = ConcurrentHashMap.newKeySet();

    private GhostRuleTuning() {
    }

    /**
     * 设置某条规则的复苏代价倍率。1.0 为默认，0.0 表示不产生代价。
     */
    public static void setRevivalScale(Identifier ruleId, double scale) {
        if (scale < 0.0D) {
            throw new IllegalArgumentException("revival scale must be >= 0: " + scale);
        }
        if (scale == 1.0D) {
            REVIVAL_SCALE.remove(ruleId);
        } else {
            REVIVAL_SCALE.put(ruleId, scale);
        }
    }

    public static double revivalScale(Identifier ruleId) {
        return REVIVAL_SCALE.getOrDefault(ruleId, 1.0D);
    }

    /**
     * 启停某条规则。停用后该规则不再参与任何事件。
     */
    public static void setEnabled(Identifier ruleId, boolean enabled) {
        if (enabled) {
            DISABLED.remove(ruleId);
        } else {
            DISABLED.add(ruleId);
        }
    }

    public static boolean isEnabled(Identifier ruleId) {
        return !DISABLED.contains(ruleId);
    }

    /**
     * 清空全部覆盖，回到默认状态。
     */
    public static void reset() {
        REVIVAL_SCALE.clear();
        DISABLED.clear();
    }
}
