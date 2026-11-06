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
     * 融合失败的额外灵异伤害：素材厉鬼**最大灵异的 5%**。
     *
     * <p>为什么单独一条而不是并进 {@link #apply}：{@code apply} 只拿到缺口比例，
     * 不知道融合的是哪只鬼；而这一刀要按"你融的那只鬼有多强"来算 ——
     * 融敲门鬼（最大灵异 2800）失败挨 140，融鬼血（2500）挨 125。
     *
     * <p>走本体的灵异伤害源（{@code ModDamageSources.ghost}）：那是鬼打人的通道，
     * 会按灵异抗性结算，也不会被普通护甲吃掉。玩家抗性越高，这一刀越轻。
     *
     * @param ghostType 素材厉鬼的类型 id（字符串形式，允许带命名空间）
     */
    public static void spiritBacklash(PlayerEntity player, String ghostType) {
        if (player == null || player.getWorld() == null || player.getWorld().isClient()) {
            return;
        }
        float maxSpirit = GhostSpiritPower.rawBonus(ghostType);
        float damage = maxSpirit * SPIRIT_BACKLASH_RATIO;
        if (damage <= 0.0F) {
            return;
        }
        // 清无敌帧：融合是交互动作，不该因为上一刀还在冷却就白吃
        player.timeUntilRegen = 0;
        player.damage(com.xie.smfs.damage.ModDamageSources.ghost(player.getWorld()), damage);
        player.sendMessage(Text.literal("§4灵异反噬 §8−" + Math.round(damage)), true);
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
