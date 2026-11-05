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
}
