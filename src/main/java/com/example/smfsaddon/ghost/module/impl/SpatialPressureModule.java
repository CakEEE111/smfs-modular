package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostCodexEntry;
import com.example.smfsaddon.ghost.module.GhostDebug;
import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.example.smfsaddon.ghost.module.GhostRule;
import com.example.smfsaddon.ghost.module.SpatialModule;
import com.example.smfsaddon.ghost.module.event.GhostEvent;
import com.example.smfsaddon.ghost.module.event.GhostNearbyPlayerEvent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 空间压迫模块。
 *
 * <p><b>这个模块存在的意义是验证抽象，不是提供玩法。</b>
 * 它只声明 {@code SpatialHost} 一种能力，所以同一份代码可以同时挂在
 * 鬼实体和方块上 —— 挂上去之后行为完全一致。
 *
 * <p>如果哪天它被改成依赖 {@code GhostEntityHost}，就说明有人又在规则里
 * 顺手取了鬼的特性，「万物插件化」就退化成「万物都是鬼」。
 */
public final class SpatialPressureModule implements SpatialModule {

    public static final Identifier ID = new Identifier("smfsaddon", "spatial_pressure");

    private static final double SCAN_RADIUS = 6.0D;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "无形压迫";
    }

    /**
     * 不自然复苏：压迫是常驻的，靠玩家靠近才积累。
     */
    @Override
    public double naturalRevivalRate() {
        return 0.0D;
    }

    @Override
    public double maxRevival() {
        return 100.0D;
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
        return List.of(new PressureRule());
    }

    private static final class PressureRule implements GhostRule {

        private static final Identifier RULE_ID =
                new Identifier("smfsaddon", "spatial_pressure/pressure");

        @Override
        public Identifier id() {
            return RULE_ID;
        }

        @Override
        public GhostCodexEntry codex() {
            return GhostCodexEntry.of(
                    "无形压迫",
                    "靠近 6 格内会持续被压住；待久了会开始走不动"
            );
        }

        @Override
        public double revivalGain() {
            // 每 10 tick 扫一次，每次 +5 → 约 10 秒到阈值。
            // 早先给 2.0，要 25 秒才触发，实测时很容易被当成「没反应」。
            return 5.0D;
        }

        @Override
        public boolean supports(Identifier eventId) {
            return GhostNearbyPlayerEvent.ID.equals(eventId);
        }

        @Override
        public boolean canTrigger(GhostModuleContext context, GhostEvent event) {
            return event instanceof GhostNearbyPlayerEvent;
        }

        @Override
        public void onTrigger(GhostModuleContext context, GhostEvent event) {
            if (!context.revived()) {
                return;
            }
            if (!(event instanceof GhostNearbyPlayerEvent nearby)) {
                return;
            }

            PlayerEntity player = nearby.player();

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));

            // 调试观察口：默认关闭，/ghostmodules debug 打开后才可见。
            // 效果生效与否在外部看不出来，这个提示让模块自己说话。
            if (GhostDebug.enabled()) {
                long now = context.ticks();
                NbtCompound state = context.state();
                if (now - state.getLong("lastNotice") >= 40) {
                    state.putLong("lastNotice", now);
                    GhostDebug.notice(player, "被压住了 " + (int) context.revival());
                }
            }

            if (context.world() != null) {
                context.world().playSound(
                        null,
                        player.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        SoundCategory.HOSTILE,
                        0.6F,
                        0.8F
                );
            }
        }
    }
}
