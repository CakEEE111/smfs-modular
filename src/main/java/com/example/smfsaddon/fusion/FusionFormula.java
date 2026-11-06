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

    /**
     * 灵力上限权重：<b>全额计入</b>。
     *
     * <p>早先取 0.10（"量大但边际弱"），实测直接把玩法锁死：
     * 最大灵异 5150 的玩家只算 515 压制，而首次合入就要 5400 ——
     * 一只都融不了，界面上的数字看着像算错了。
     *
     * <p>现在全额：5150 就是 5150，首次合入差的那点由抗性与灵异伤害补。
     */
    public static final double WEIGHT_MAX_SPIRIT = 1.00D;

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

    /**
     * 每次合入的「入门费」，与要融几个模块无关。
     *
     * <p>没有这一项时，第一只鬼只要 {@code 30 × 30 = 900}，
     * 而玩家压制值动辄上万 —— 表现就是「前几只随便塞，等于没有要求」。
     * 模块成本只能区分「这只鬼危不危险」，区分不了「有没有做这件事的代价」。
     *
     * <p>150 这个量级是配着 {@link #COST_SCALE} 选的：
     * 第一只约 {@code (150+30)×30 = 5400}，之后按 75% 复利上去。
     * 要整体加减负，调这一个数就够。
     */
    public static final double ENTRY_COST = 150.0D;

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
        // 没有模块就没有可融的东西（界面上右槽为空时会走到这里）
        if (moduleIds == null || moduleIds.isEmpty()) {
            return 0.0D;
        }
        // 入门费：跟模块无关，但每次合入都要付
        double base = ENTRY_COST;
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
