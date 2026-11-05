package com.example.smfsaddon.ghost.module.event;

import com.example.smfsaddon.ghost.module.AttackVerdict;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 杀人规律裁决事件。
 *
 * <p>对应原有的 {@code GhostEntity#shouldAttackPlayer(PlayerEntity)}。
 * 规则通过 {@link #allow()} 或 {@link #deny()} 参与裁决，
 * 都不调用则保持 {@link AttackVerdict#ABSTAIN}，由宿主自身的旧逻辑决定。
 *
 * <p>这样做的关键好处：一个挂满模块的新鬼，和一个
 * 什么都没挂的旧鬼，在同一个方法里共存，互不干扰。
 */
public final class GhostAttackRuleEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "attack_rule");

    private final PlayerEntity player;

    private AttackVerdict verdict = AttackVerdict.ABSTAIN;

    public GhostAttackRuleEvent(PlayerEntity player) {
        this.player = player;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    public PlayerEntity player() {
        return player;
    }

    public AttackVerdict verdict() {
        return verdict;
    }

    /**
     * 表态允许。已有明确表态时不覆盖，避免规则之间互相抢话。
     */
    public void allow() {
        if (verdict == AttackVerdict.ABSTAIN) {
            verdict = AttackVerdict.ALLOW;
        }
    }

    /**
     * 表态否决，优先级高于允许。
     */
    public void deny() {
        verdict = AttackVerdict.DENY;
    }
}
