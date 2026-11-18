package com.example.smfsaddon.ghost.module;

import net.minecraft.util.Identifier;

/**
 * 厉鬼能力。
 *
 * <p>能力是「主动」的：有明确的复苏值代价，有可用条件，
 * 调用后产生一次效果。与规则（被动、事件驱动）互补。
 */
public interface GhostAbility {

    /**
     * 能力在所属模块内的唯一标识。
     */
    Identifier id();

    /**
     * 使用一次需要消耗的复苏值。
     */
    default double revivalCost() {
        return 0.0D;
    }

    /**
     * 当前是否可以释放。
     *
     * <p>默认要求复苏值足够。
     */
    default boolean canUse(GhostAbilityContext context) {
        return context.module().data().revival() >= revivalCost();
    }

    /**
     * 释放能力。引擎保证调用前已通过 {@link #canUse} 检查并扣除代价。
     */
    void use(GhostAbilityContext context);
}
