package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.SpatialModule;
import com.example.smfsaddon.ghost.module.event.GhostAttackHitEvent;
import com.example.smfsaddon.ghost.module.event.GhostAttackRuleEvent;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.xie.smfs.damage.ModDamageSources;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 低处踩踏。
 *
 * <p>原实现只读鬼的坐标和攻击冷却，所以它只能装在鬼身上。
 * 这里改成<b>只依赖位置</b>（{@code SpatialHost}），于是同一份模块：
 *
 * <ul>
 *   <li>挂在鬼身上 —— 鬼去踩站在低处的玩家（判定型规则）；</li>
 *   <li>挂在剑身上 —— 你砍比自己低的目标时必杀（动作型规则）。</li>
 * </ul>
 *
 * <p>两条规则监听不同事件，但描述的是同一条规律的两个方向。
 * 这就是「模块化要兼容」的实际含义：规律不绑宿主，绑的是<b>语义</b>。
 */
public final class StepGhostModule implements SpatialModule {

    public static final Identifier ID = new Identifier("smfsaddon", "step_ghost");

    /** 28 格，与原实现一致 */
    private static final double RANGE_SQ = 28.0D * 28.0D;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "低处踩踏";
    }

    /**
     * 踩人鬼不靠自然复苏，规则纯条件判定。
     */
    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new LowGroundRule(), new LowGroundStrikeRule());
    }

    /**
     * 判定型：鬼主动袭击时，只对站在低处的目标表态允许。
     *
     * <p>原实现里还有「攻击冷却 ≤ 0」这一条，这里去掉了 ——
     * 冷却属于鬼自己的身体状态，而规律本身描述的是「谁会挨打」。
     * 把它写进规律会让这条规律没法移植到别处。
     */
    private static final class LowGroundRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "step_ghost/low_ground");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public double revivalGain() {
            return 0.5D;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "低处踩踏",
                    "只袭击比自己低至少一格、且在 28 格内的目标"
            );
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
            Vec3d self = context.position();
            if (self == null) {
                return false;
            }
            PlayerEntity player = attackEvent.player();
            return player.getY() < self.y - 1.0D
                    && player.getPos().squaredDistanceTo(self) <= RANGE_SQ;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            ((GhostAttackRuleEvent) event).allow();
        }
    }

    /**
     * 动作型：用装着这条规律的物品攻击时，目标比自己低就必杀。
     *
     * <p>伤害必须用本体的伤害源，否则打在鬼身上是「无效伤害」——
     * 数字会飘，但不掉灵异强度。
     */
    private static final class LowGroundStrikeRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "step_ghost/low_ground_strike");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public double revivalGain() {
            return 1.0D;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "自上而下",
                    "用承载此规律的物品攻击低于自己的目标，一击必杀"
            );
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostAttackHitEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackHitEvent hit)) {
                return false;
            }
            Entity attacker = hit.player();
            LivingEntity target = hit.target();
            if (attacker == null || target == null) {
                return false;
            }
            return target.getY() < attacker.getY() - 1.0D;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!(event instanceof GhostAttackHitEvent hit) || context.world() == null) {
                return;
            }
            hit.target().damage(ModDamageSources.ghost(context.world()), 1000.0F);
        }
    }
}
