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
 * <p>驾驭物上的灵异数值是"驾驭这只鬼能给你的加成"，属于整个人。
 * 融进物品只窃取一层，取 10%——一只鬼约 +0.5~5 点灵异伤害，
 * 融五只也就 +3~25，与本体武器加成同量级，不至于一把剑秒天秒地。
 */
public final class GhostSpiritPower {

    /** 每只鬼窃取的比例 */
    public static final float STEAL_RATIO = 0.10F;

    /** 鬼种 → 该鬼贡献的灵异伤害（已乘比例） */
    private static final Map<String, Float> CACHE = new HashMap<>();

    private GhostSpiritPower() {
    }

    /**
     * 一件物品的总灵异伤害 = 各只鬼贡献之和。
     */
    public static float totalOf(ItemStack stack) {
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
     * 该鬼驾驭物上的原始灵异数值；找不到返回 0。
     */
    public static float rawBonus(String ghostType) {
        Item item = itemOf(ghostType);
        return item instanceof BaseGhostEyeItem eye ? eye.getSpiritDamageBonus() : 0.0F;
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
