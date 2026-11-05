package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostAbility;
import com.example.smfsaddon.ghost.module.GhostAbilityContext;
import com.example.smfsaddon.ghost.module.LivingModule;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.event.GhostNearbyPlayerEvent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 鬼域压迫模块 —— 事件驱动型规则的示例。
 *
 * <p>与 {@link StepGhostModule} 的对比：
 * 那个是「被询问时表态」的判定型规则，这个是「事情发生时反应」的反应型规则。
 * 两种形态都由同一套 {@code GhostRule} 表达，不需要两套机制。
 *
 * <p>行为：玩家靠近时按距离累积复苏值；复苏值足够时释放压制能力。
 *
 * <p>这是一个可以直接删掉的示例模块，用来演示
 * 「复苏值累积 + 能力代价 + 邻近扫描」这条链路。
 */
public final class DomainPresenceModule implements LivingModule {

    public static final Identifier ID = new Identifier("smfsaddon", "domain_presence");

    private static final double MAX_REVIVAL = 100.0D;
    private static final double SCAN_RADIUS = 12.0D;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "鬼域压迫";
    }

    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public double maxRevival() {
        return MAX_REVIVAL;
    }

    @Override
    public int nearbyScanInterval() {
        return 10;
    }

    @Override
    public double nearbyScanRadius() {
        return SCAN_RADIUS;
    }

    @Override
    public List<GhostRule> rules() {
        return List.of(new MadnessRule(), new AwarenessRule());
    }

    @Override
    public List<GhostAbility> abilities() {
        return List.of(new SuppressionBurst());
    }

    /**
     * 「发狂」与「察觉」是一对互斥规则，共用同一个互斥组。
     *
     * <p>玩家在 8 格内时两条都满足条件，但「发狂」优先级更高，
     * 同组只允许它生效 —— 这才是原著里「规则冲突」的写法：
     * 不是两条叠加，而是一条压倒另一条。
     */
    private static final class MadnessRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "domain_presence/madness");

        private static final Identifier GROUP =
                new Identifier("smfsaddon", "domain_presence/proximity");

        private static final double RANGE_SQ = 8.0D * 8.0D;

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public com.example.smfsaddon.ghost.module.GhostCodexEntry codex() {
            return com.example.smfsaddon.ghost.module.GhostCodexEntry.of(
                    "近身发狂",
                    "玩家进入 8 格内时复苏骤增，并压过「远处察觉」"
            );
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public Identifier exclusionGroup() {
            return GROUP;
        }

        @Override
        public double revivalGain() {
            return 4.0D;
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostNearbyPlayerEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            return event instanceof GhostNearbyPlayerEvent nearby
                    && nearby.distanceSq() <= RANGE_SQ;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            // 代价统一由 revivalGain 结算，这里不需要额外动作
        }
    }

    /**
     * 远处察觉：与「发狂」互斥，优先级更低，代价也更低。
     */
    private static final class AwarenessRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "domain_presence/awareness");

        private static final Identifier GROUP =
                new Identifier("smfsaddon", "domain_presence/proximity");

        private static final double RANGE_SQ = SCAN_RADIUS * SCAN_RADIUS;

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public com.example.smfsaddon.ghost.module.GhostCodexEntry codex() {
            return com.example.smfsaddon.ghost.module.GhostCodexEntry.of(
                    "远处察觉",
                    "12 格内开始积累复苏，越近越快；8 格内会让位给「近身发狂」"
            );
        }

        @Override
        public Identifier exclusionGroup() {
            return GROUP;
        }

        @Override
        public double revivalGain() {
            return 1.0D;
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostNearbyPlayerEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            return event instanceof GhostNearbyPlayerEvent nearby
                    && nearby.distanceSq() <= RANGE_SQ;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
        }
    }

    /**
     * 消耗复苏值，对最近的玩家施加一次压制。
     */
    private static final class SuppressionBurst implements GhostAbility {

        private static final Identifier ABILITY_ID =
                new Identifier("smfsaddon", "domain_presence/suppression_burst");

        private static final double COST = 60.0D;

        @Override
        public Identifier id() {
            return ABILITY_ID;
        }

        @Override
        public double revivalCost() {
            return COST;
        }

        @Override
        public void use(GhostAbilityContext context) {
            // 这里刻意用 entity() 而不是 ghost()：本模块只声明了「活物」能力，
            // 换成 ghost() 就把自己锁死在鬼身上了，明明逻辑对任何活物都成立。
            LivingEntity entity = context.module().entity();
            if (entity == null) {
                return;
            }

            PlayerEntity nearest = null;
            double nearestSq = Double.MAX_VALUE;
            for (PlayerEntity player : entity.getWorld().getPlayers()) {
                double distanceSq = entity.squaredDistanceTo(player);
                if (distanceSq < nearestSq) {
                    nearestSq = distanceSq;
                    nearest = player;
                }
            }
            if (nearest == null || nearestSq > SCAN_RADIUS * SCAN_RADIUS) {
                return;
            }

            nearest.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
            entity.getWorld().playSound(
                    null,
                    entity.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE,
                    0.8F,
                    0.9F
            );
        }
    }
}
