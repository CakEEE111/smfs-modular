package com.example.smfsaddon.fusion;

import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.effect.SpiritAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * 反噬结算。三项同时作用：理智、生命、复苏度。
 *
 * <h2>为什么按比例而不是按差值</h2>
 *
 * <p>早先用「差值 × 系数」再截断。融合成本放大之后，差值动辄上千，
 * 乘出来全部撞到上限，于是<b>每次失败都是同样的满额惩罚</b> ——
 * 「差一点」和「差一倍」没有区别，缩放彻底失效。
 *
 * <p>现在改成看<b>缺口占比</b>：{@code shortfall / required}。
 * 差 10% 和差 80% 的手感天差地别，而且不会随数值膨胀失效。
 */
public final class FusionBacklash {

    /** 赌赢了也要付的代价（硬吃下来） */
    public static final double FACTOR_LUCKY = 0.6D;

    /** 赌输了的代价 */
    public static final double FACTOR_FAILED = 1.2D;

    private FusionBacklash() {
    }

    /**
     * @param shortfall  压制值与需求值的差
     * @param required   需求值（用来算缺口占比）
     * @param multiplier 倍率：赌赢 / 赌输的代价不同
     */
    public static void apply(PlayerEntity player, double shortfall, double required,
                             double multiplier) {
        if (shortfall <= 0.0D || multiplier <= 0.0D) {
            return;
        }

        // 缺口占比封顶 1.0，再乘倍率（0.6 / 1.2）
        double ratio = required <= 0.0D ? 1.0D : Math.min(1.0D, shortfall / required);
        double scaled = ratio * multiplier;

        // ① 理智：4 ~ 24
        float sanityLoss = (float) (4.0D + 20.0D * scaled);
        PlayerEvents.addSpiritAttribute(player, SpiritAttributes.SANITY, -sanityLoss);

        // ② 生命：2 ~ 12
        float damage = (float) (2.0D + 8.0D * scaled);
        player.damage(player.getDamageSources().magic(), damage);

        // ③ 复苏度：1 ~ 5（所有已占用鬼槽）
        int revivalUp = (int) Math.round(1.0D + 4.0D * scaled);
        PlayerEvents.decreaseAllSlotRevivalDegree(player, -revivalUp);

        player.sendMessage(
                Text.literal("§4规律反噬 §8缺口 " + Math.round(ratio * 100.0D) + "%"),
                true
        );
    }
}
