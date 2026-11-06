package com.example.smfsaddon.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import com.xie.smfs.item.BaseGhostEyeItem;

import java.util.HashMap;
import java.util.Map;

/**
 * 融合物品的灵异伤害：每只融进去的鬼，窃取它驾驭物灵异数值的 10%。
 *
 * <h2>从哪取"这只鬼的灵异数值"</h2>
 *
 * <p>不另开配置表，直接读本体给每只鬼注册的驾驭物
 * （{@code smfs:<鬼种>}，{@link BaseGhostEyeItem}）上的
 * {@code getSpiritDamageBonus()}。理由：
 *
 * <ul>
 *   <li>那是本体自己给这只鬼定的值，改平衡时只改一处</li>
 *   <li>附属的鬼只要按本体的方式注册驾驭物，这里自动就有数</li>
 *   <li>不用维护"鬼种 → 数值"的表，也就不会出现表和图鉴对不上</li>
 * </ul>
 *
 * <p>找不到驾驭物（只有实体、没有物品的鬼）就按 0 算，不猜。
 *
 * <h2>为什么是 10%</h2>
 *
 * <p>本体驾驭物的「最大灵异」是 2000~2800 一档，取一成即 200~280 一只。
 * 融三只 ≈ 730 基础灵异伤害，与本体灵异武器（许愿长枪 310 一档）同量级，
 * 略高——因为这是拿五只鬼换一把剑，且鬼的价值不该被浪费在物品上一点不剩。
 */
public final class GhostSpiritPower {

    /** 每只鬼窃取的比例 */
    public static final float STEAL_RATIO = 0.10F;

    /**
     * 继承倍率：玩家自身灵异伤害转成武器伤害的比例。
     *
     * <p>取本体的默认值 50%（{@code SpiritWeapon.getSpiritDamageMultiplier}）。
     * 公式与本体一致：
     * <pre>实际灵异伤害 = 基础灵异伤害 + 玩家灵异伤害 × 继承倍率</pre>
     */
    public static final float INHERIT_MULTIPLIER = 0.5F;

    /** 鬼种 → 该鬼贡献的灵异伤害（已乘比例） */
    private static final Map<String, Float> CACHE = new HashMap<>();

    private GhostSpiritPower() {
    }

    /**
     * 基础灵异伤害 = 各只鬼贡献之和（不含玩家继承部分）。
     */
    public static float baseOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0F;
        }
        float total = 0.0F;
        for (String ghostType : ItemGhostHost.boundGhostTypes(stack)) {
            total += of(ghostType);
        }
        return total;
    }

    /**
     * 实际灵异伤害 = 基础 + 玩家灵异伤害 × 继承倍率。
     */
    public static float totalOf(ItemStack stack, net.minecraft.entity.player.PlayerEntity player) {
        float base = baseOf(stack);
        if (base <= 0.0F) {
            return 0.0F;
        }
        float playerSpirit = 0.0F;
        if (player != null) {
            playerSpirit = com.xie.smfs.common.events.PlayerEvents.getSpiritAttribute(
                    player, com.xie.smfs.effect.SpiritAttributes.SPIRIT_DAMAGE);
        }
        return base + playerSpirit * INHERIT_MULTIPLIER;
    }

    /**
     * 单只鬼贡献的灵异伤害。
     */
    public static float of(String ghostType) {
        if (ghostType == null || ghostType.isEmpty()) {
            return 0.0F;
        }
        Float cached = CACHE.get(ghostType);
        if (cached != null) {
            return cached;
        }
        float value = rawBonus(ghostType) * STEAL_RATIO;
        CACHE.put(ghostType, value);
        return value;
    }

    /**
     * 该鬼驾驭物上的**最大灵异**；找不到返回 0。
     *
     * <h2>为什么取「最大灵异」而不是「灵异伤害」</h2>
     *
     * <p>驾驭物上那三项是同一只鬼的三种面板：
     * 最大灵异（上限，2000~2800 一档）、灵异抗性、灵异伤害（这项很多鬼是 0）。
     *
     * <p>取「灵异伤害」会得到荒谬的小数：鬼血是 0、敲门 90、遗忘 180，
     * 融三只才 27 —— 一把融了三只鬼的剑比空手强不了多少。
     * 取「最大灵异」才符合"窃取这只鬼的灵异量"的直觉：2500 的一成是 250。
     */
    public static float rawBonus(String ghostType) {
        Item item = itemOf(ghostType);
        return item instanceof BaseGhostEyeItem eye ? eye.getMaxSpiritBonus() : 0.0F;
    }

    /**
     * 按鬼种找驾驭物。
     *
     * <p>先试 {@code smfs:<鬼种>}（本体与照抄本体命名的附属都命中），
     * 再全表按路径同名找 —— 别的附属可能注册在自己的命名空间下。
     */
    private static Item itemOf(String ghostType) {
        String path = ghostType;
        Identifier direct = Identifier.tryParse(ghostType);
        if (direct != null) {
            path = direct.getPath();
            Item found = Registries.ITEM.getOrEmpty(direct).orElse(null);
            if (found != null) {
                return found;
            }
        }
        Item bySmfs = Registries.ITEM.getOrEmpty(new Identifier("smfs", path)).orElse(null);
        if (bySmfs != null) {
            return bySmfs;
        }
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!id.getPath().equals(path)) {
                continue;
            }
            Item found = Registries.ITEM.getOrEmpty(id).orElse(null);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
