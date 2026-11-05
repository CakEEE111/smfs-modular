package com.example.smfsaddon.fusion;

import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleContainer;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.effect.SpiritAttributes;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 灵异装置的压制公式。
 *
 * <p>一侧是玩家：灵力上限、灵异抗性、灵异伤害三项加权，合成一个「压制值」。
 * 另一侧是物品上的规律：每条模块的 {@code suppressionCost()} × 强度，求和得到「需求值」。
 *
 * <pre>
 * 压制值 ≥ 需求值 → 压住了，无视反噬
 * 压制值 &lt; 需求值 → 压不住，按差值承受反噬
 * </pre>
 *
 * <h2>为什么权重单独放在这里</h2>
 *
 * <p>这三个数就是整套玩法的平衡旋钮。写在类里、集中在一处，
 * 调数值时不需要碰逻辑；界面上也会把两个值都显示出来，
 * 玩家能自己算出「我还差多少」，而不是靠猜。
 */
public final class FusionFormula {

    /** 灵力上限权重：量大但边际弱 */
    public static final double WEIGHT_MAX_SPIRIT = 0.10D;

    /** 灵异抗性权重：最直接，抗性就是拿来压鬼的 */
    public static final double WEIGHT_RESISTANCE = 1.00D;

    /** 灵异伤害权重：攻击性折半计入，鼓励纯抗性堆叠 */
    public static final double WEIGHT_DAMAGE = 0.50D;

    /** 模块未注册时的兜底需求 */
    private static final double DEFAULT_COST = 30.0D;

    /**
     * 成本倍率。
     *
     * <p>模块自身的 {@code suppressionCost()} 是「相对危险度」，
     * 这个系数把它换算成实际要压的量级。早先没乘，导致
     * 单只鬼只要 90，而玩家压制值动辄上万 —— 等于白送。
     *
     * <p><b>调平衡就改这一个数。</b>
     */
    public static final double COST_SCALE = 30.0D;

    /**
     * 边际成本：同一件物品上每多融一只鬼，成本上浮多少。
     *
     * <p>0.75 表示第二只贵 75%，第三只贵 150%……
     * 这是为了堵住「一只剑塞十只鬼」——不是为了难为玩家，
     * 而是融合太多本来就会让技能面板失控。
     */
    public static final double MARGINAL_GROWTH = 0.75D;

    private FusionFormula() {
    }

    /**
     * 玩家的压制值。
     */
    public static double suppression(PlayerEntity player) {
        float maxSpirit = PlayerEvents.getMaxSpirit(player);
        float resistance = PlayerEvents.getSpiritAttribute(player, SpiritAttributes.SPIRIT_RESISTANCE);
        float damage = PlayerEvents.getSpiritAttribute(player, SpiritAttributes.SPIRIT_DAMAGE);
        return maxSpirit * WEIGHT_MAX_SPIRIT
                + resistance * WEIGHT_RESISTANCE
                + damage * WEIGHT_DAMAGE;
    }

    /**
     * 一次融合的总需求。
     *
     * @param moduleIds      要融进去的模块
     * @param existingGhosts 物品上已经绑定了几只鬼（决定边际成本）
     */
    public static double requiredFor(java.util.List<net.minecraft.util.Identifier> moduleIds,
                                     int existingGhosts) {
        double base = 0.0D;
        for (net.minecraft.util.Identifier moduleId : moduleIds) {
            var module = com.example.smfsaddon.ghost.module.GhostModuleRegistry.get(moduleId);
            base += module == null ? DEFAULT_COST : module.suppressionCost();
        }
        double growth = 1.0D + MARGINAL_GROWTH * Math.max(0, existingGhosts);
        return base * COST_SCALE * growth;
    }

    /**
     * 容器里所有规律的总需求。
     */
    public static double required(GhostModuleContainer modules) {
        if (modules == null || modules.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (GhostModuleData data : modules.all()) {
            GhostModule module = GhostModuleRegistry.get(data.moduleId());
            double cost = module == null ? DEFAULT_COST : module.suppressionCost();
            // 强度至少按 0.1 计，避免强度被调成 0 时规律变成白嫖
            sum += cost * Math.max(0.1D, data.intensity());
        }
        return sum;
    }
}
