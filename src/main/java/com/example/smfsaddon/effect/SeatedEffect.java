package com.example.smfsaddon.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * 「入席」—— 你已经在席间了。
 *
 * <p>表现：走不快、看不清、心跳样地持续掉血。它不阻止你逃，
 * 但会让你每走一步都后悔。</p>
 */
public class SeatedEffect extends StatusEffect {

    public SeatedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x6B0F1A);
    }

    @Override
    public String getTranslationKey() {
        return "effect.smfsaddon.seated";
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 40 == 0 || duration <= 1;   // 每 2 秒一次，挂上瞬间也吃一次
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) {
            return;
        }
        // 礼的重量：走本体的灵异伤害通道（吃灵异抗性，不吃护甲）
        // 伤害统一由 FeastCeremony 结算，这里只管视觉与控制
        // 入席之后走不动了 —— 缓慢 255 级就是物理意义上的"站着"
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 255, false, false, false));
        // 画面扭曲，看不清方向
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0, false, false, false));
    }
}
