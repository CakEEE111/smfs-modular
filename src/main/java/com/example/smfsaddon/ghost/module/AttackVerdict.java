package com.example.smfsaddon.ghost.module;

/**
 * 厉鬼模块对「是否可以攻击该玩家」的裁决。
 *
 * <p>三态设计的意义：模块可以支持，也可以否决，但必须能「不说话」。
 * 默认 {@link #ABSTAIN} 保证一个没有挂任何模块的旧鬼，
 * 行为与加入模块系统之前完全一致 —— 依然走它自己覆写的
 * {@code GhostEntity#shouldAttackPlayer}。
 */
public enum AttackVerdict {

    /**
     * 允许攻击。
     */
    ALLOW,

    /**
     * 明确拒绝攻击，优先级高于 {@link #ALLOW}。
     */
    DENY,

    /**
     * 未表态，交由宿主自身的规则逻辑处理。
     */
    ABSTAIN
}
