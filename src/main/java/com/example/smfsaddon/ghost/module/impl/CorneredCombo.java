package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostCombo;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.event.GhostAttackRuleEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.host.GhostEntityHost;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 协同示例：墙角必杀。
 *
 * <p>踩人鬼本来只打「比自己低一格」的目标 —— 站高处就安全。
 * 但一旦它同时具备「踩踏」与「鬼域压迫」两套规律，
 * 贴脸的距离会压过高度差：**站哪儿都没用**。
 *
 * <p>这就是拼图拼上之后应该发生的事：不是数值变大，
 * 而是一条原本不存在的规律出现了。
 */
public final class CorneredCombo {

    public static final Identifier ID = new Identifier("smfsaddon", "cornered");

    private CorneredCombo() {
    }

    public static GhostCombo create() {
        return GhostCombo.of(ID)
                .needs(StepGhostModule.ID, DomainPresenceModule.ID)
                .needsCapability(GhostEntityHost.class)
                .rules(new CorneredRule())
                .build();
    }

    /**
     * 贴脸时无视高度差。
     *
     * <p>优先级给到 300，压过踩踏规律的 100 —— 同一事件里先执行，
     * 它一旦表态允许，后面的规则即使不满足也不影响结果。
     */
    private static final class CorneredRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "cornered/point_blank");

        /** 3 格内算贴脸 */
        private static final double POINT_BLANK_SQ = 9.0D;

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 300;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "墙角必杀",
                    "当它同时具备踩踏与压迫两套规律时，贴脸会无视高度差"
            );
        }

        @Override
        public double revivalGain() {
            return 3.0D;
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostAttackRuleEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackRuleEvent attackEvent)) {
                return false;
            }
            GhostEntity ghost = context.ghost();
            if (ghost == null) {
                return false;
            }
            PlayerEntity player = attackEvent.player();
            return ghost.squaredDistanceTo(player) <= POINT_BLANK_SQ;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            ((GhostAttackRuleEvent) event).allow();
        }
    }
}
