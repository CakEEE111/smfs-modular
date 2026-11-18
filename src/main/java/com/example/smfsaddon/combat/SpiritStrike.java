package com.example.smfsaddon.combat;

import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.effect.SpiritAttributes;
import com.xie.smfs.entity.GhostEntity;
import com.xie.smfs.event.ModEvents;
import com.xie.smfs.manager.MainGhostManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 按本体的规则结算灵异伤害。
 *
 * <h2>为什么不能直接 damage()</h2>
 *
 * <p>本体那套灵异伤害有两个硬约束：</p>
 *
 * <ol>
 *   <li>必须 <b>玩家当攻击者</b> 且打开 {@code ModEvents.processingSpiritDamage}，
 *       否则对鬼造成的伤害会被本体直接吞掉；</li>
 *   <li>打鬼时本体<b>忽略传入的 amount</b>，改用玩家 NBT 里的 {@code spiritDamage} 重算 ——
 *       所以等级加成只能临时写进 NBT，打完立刻还原。</li>
 * </ol>
 */
public final class SpiritStrike {

    private static final float SPIRIT_SCALE = 0.5f;
    private static final float LEVEL_STEP = 0.25f;

    private SpiritStrike() {
    }

    public static float deal(ServerPlayerEntity player, LivingEntity target, float base) {
        int level = 1;
        try {
            level = Math.max(1, MainGhostManager.getMainGhostLevel(player));
        } catch (Throwable ignored) {
            // 拿不到等级就按 1 级算
        }
        float levelMultiplier = 1.0f + (level - 1) * LEVEL_STEP;

        float spirit;
        try {
            spirit = PlayerEvents.getSpiritAttribute(player, SpiritAttributes.SPIRIT_DAMAGE);
        } catch (Throwable throwable) {
            spirit = 0.0f;
        }

        float amount = (base + Math.max(0.0f, spirit) * SPIRIT_SCALE) * levelMultiplier;

        boolean isGhost = target instanceof GhostEntity;
        boolean scaled = isGhost && levelMultiplier > 1.001f;
        double original = 0.0;

        if (scaled) {
            NbtCompound data = PlayerEvents.getSpiritAttributes(player);
            original = data.getDouble("spiritDamage");
            data.putDouble("spiritDamage", original * levelMultiplier);
            PlayerEvents.setSpiritAttributes(player, data);
        }

        ModEvents.processingSpiritDamage.set(true);
        try {
            target.damage(player.getDamageSources().playerAttack(player), amount);
        } finally {
            ModEvents.processingSpiritDamage.set(false);
            if (scaled) {
                NbtCompound data = PlayerEvents.getSpiritAttributes(player);
                data.putDouble("spiritDamage", original);
                PlayerEvents.setSpiritAttributes(player, data);
            }
        }
        return amount;
    }
}
