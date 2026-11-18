package com.example.smfsaddon.skill;

import com.example.smfsaddon.item.ItemGhostHost;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * 判断玩家手上有没有"融合了某只鬼"的物品。
 *
 * <p>这是技能体系的通用钥匙：本体所有技能内部都调
 * {@code PlayerEvents.hasGhostType(player, ghostType)} 来确认"你有没有这只鬼"。
 * 我们在那里加一条：<b>手上拿着融合了这只鬼的物品，也算有</b>。
 *
 * <p>于是 17 个技能不需要任何逐个适配 —— 它们自己的判定就通过了。
 */
public final class FusedGhostIndex {

    private FusedGhostIndex() {
    }

    /**
     * 玩家的主手或副手是否融合了指定鬼种（短名，如 {@code qiaomen_ghost}）。
     */
    public static boolean holds(PlayerEntity player, String ghostType) {
        if (player == null || ghostType == null || ghostType.isEmpty()) {
            return false;
        }
        for (Hand hand : Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }
            if (ItemGhostHost.boundGhostTypes(stack).contains(ghostType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家的<b>背包里</b>是否有融合了这只鬼的物品（含护甲与副手）。
     *
     * <h2>为什么"拥有"和"手持"要分开</h2>
     *
     * <p>本体问 {@code hasGhostType} 的时候，问的是「你有没有驾驭这只鬼」，
     * 不是「你现在有没有拿着它」。鬼槽里的鬼不会因为你切了物品就消失，
     * 融合物品也应该一样 —— 否则一换手，夜间被动、被动抗性就全没了。
     *
     * <p>而"用这把剑开门"这种事仍然只认手持（{@link #holds}），
     * 那是使用行为，不是拥有关系。
     *
     * <p>扫描由 {@code FusedGhostSlots} 缓存，每 tick 最多重建一次。
     */
    public static boolean carried(PlayerEntity player, String ghostType) {
        if (player == null || ghostType == null || ghostType.isEmpty()) {
            return false;
        }
        for (var bound : com.example.smfsaddon.item.FusedGhostSlots.carried(player)) {
            if (bound.ghostType().equals(ghostType)) {
                return true;
            }
        }
        return false;
    }
}
