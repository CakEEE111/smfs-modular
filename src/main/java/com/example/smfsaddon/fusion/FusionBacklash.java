package com.example.smfsaddon.fusion;

import com.example.smfsaddon.item.GhostSpiritPower;
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

    /** 融合失败额外挨的灵异伤害 = 素材鬼最大灵异 × 这个比例 */
    public static final float SPIRIT_BACKLASH_RATIO = 0.05F;

    private FusionBacklash() {
    }

    /**
     * 扣理智，下限 0。     *
     * <h2>为什么不能直接用本体的两个属性 API</h2>
     *
     * <p>本体对"理智"有两套存法，而且不同步：
     *
     * <ul>
     *   <li>NBT 缓存里的 {@code sanity} —— <b>本体自己的 tick 与死亡判定读的是这个</b></li>
     *   <li>属性实例 {@code SpiritAttributes.SANITY} —— 面板显示用</li>
     * </ul>
     *
     * <p>{@code addSpiritAttribute(p, SANITY, -x)} 走 NBT 累加，能扣但<b>不夹 0</b>，
     * 反复失败会把理智扣成 -40（面板上是负数，死亡判定也不会触发）。
     *
     * <p>{@code subtractSpiritAttribute} 看着对，实则对理智无效：它先
     * {@code getSpiritAttribute} 读的是<b>属性实例</b>（理智不走属性通道，读到 0），
     * 算出 {@code max(0, 0-24)=0} 再累加 0 —— 等于什么都没做。
     *
     * <p>所以这里直接读 NBT、夹 0、再用本体的 {@code setCurrentSanity} 写回
     * （它会同时更新属性实例并同步客户端）。
     */
    private static void drainSanity(PlayerEntity player, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        net.minecraft.nbt.NbtCompound data = PlayerEvents.getSpiritAttributes(player);
        float current = data.contains("sanity") ? (float) data.getDouble("sanity") : 100.0F;
        float next = Math.max(0.0F, current - amount);
        PlayerEvents.setCurrentSanity(player, next);
    }

    /**
     * 融合失败的额外代价：扣**灵异强度**，为素材厉鬼最大灵异的 5%。
     *
     * <h2>为什么不是 {@code player.damage(ghostSource, x)}</h2>
     *
     * <p>本体里"灵异伤害"打在人身上就是<b>直接扣血</b>——鬼杀玩家用的正是这条通道
     * （幽灵规律里 {@code target.damage(ghost, 1000)} 就是这个）。所以拿它当惩罚，
     * 20 血的玩家挨 140 会当场毙命，玩家那一千多灵异强度一点忙都帮不上。
     *
     * <p>真正对应"灵异伤害值"的资源是<b>灵异强度</b>（{@code currentSpirit}）：
     * 它会随复苏因子自然回复，清空也不致死。所以这里扣它。
     *
     * <p>扣法有个坑：{@code setSpiritAttribute} 对 CURRENT_SPIRIT 是<b>累加</b>语义
     * （NBT 里做 {@code current + value}），所以传负数就是扣；扣的量要先夹到
     * "不超过当前值"，否则会变成负数。
     *
     * @param ghostType 素材厉鬼的类型 id（字符串形式，允许带命名空间）
     */
    public static void spiritBacklash(PlayerEntity player, String ghostType) {
        if (player == null || player.getWorld() == null || player.getWorld().isClient()) {
            return;
        }
        float maxSpirit = GhostSpiritPower.rawBonus(ghostType);
        float loss = maxSpirit * SPIRIT_BACKLASH_RATIO;
        if (loss <= 0.0F) {
            return;
        }
        // 读的是 NBT 里的当前灵异强度，和面板显示同源
        var data = PlayerEvents.getSpiritAttributes(player);
        float current = data.contains("currentSpirit") ? (float) data.getDouble("currentSpirit") : 0.0F;
        float actual = Math.min(loss, current);
        if (actual <= 0.0F) {
            player.sendMessage(Text.literal("§4灵异反噬 §8灵异强度已空"), true);
            return;
        }
        PlayerEvents.addSpiritAttribute(player, SpiritAttributes.CURRENT_SPIRIT, -actual);
        player.sendMessage(Text.literal("§4灵异反噬 §8灵异强度 −" + Math.round(actual)), true);
    }

    /**
     * 规律反噬：融合时规律凑不够数，缺口越大代价越重。
     *
     * @param shortfall  缺口（还差多少压制成本）
     * @param required   该物品需要的总压制成本
     * @param multiplier 难度倍率（0.6 / 1.2）
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
        drainSanity(player, sanityLoss);

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
