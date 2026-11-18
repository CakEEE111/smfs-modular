package com.example.smfsaddon.manager;

import com.example.smfsaddon.ghost.module.GhostModule;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.ghost.module.impl.NativeGhostRuleModule;
import com.example.smfsaddon.item.FusedGhostSlots;
import com.example.smfsaddon.item.ItemGhostHost;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.item.BaseGhostEyeItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 卡卡罗特「在哪」：鬼槽里，还是被融进了物品。
 *
 * <h2>为什么需要这个</h2>
 *
 * <p>早先变身每秒扣的是 {@code MainGhostManager.getMainGhostSlot(player)}——
 * <b>当前主鬼</b>那一格。玩家没驾驭卡卡罗特、只是把卡卡罗特融进剑时，
 * 主鬼槽里可能是任何别的鬼，于是变身消耗的是别人的复苏度。
 * 龟派气功的等级倍率也一样取错对象。
 *
 * <p>所以凡是"以卡卡罗特为代价"的地方，都要先问清楚它在哪：
 *
 * <ul>
 *   <li>在鬼槽里 → 用那一格的复苏度/等级（本体那套）</li>
 *   <li>只在物品上 → 用物品上那条规律的复苏值，到顶就打断变身</li>
 *   <li>哪都没有 → 视为没有（调用方自行决定拒绝还是按 1 级算）</li>
 * </ul>
 */
public final class KakarotSlots {

    public static final String GHOST_TYPE = "kakarot_ghost";

    /**
     * 一次释放的复苏代价（净 +12，与词条上写的一致）。
     */
    public static final int RELEASE_COST = 12;

    private KakarotSlots() {
    }

    /**
     * 结算一次技能释放的代价 —— <b>只记在卡卡罗特自己身上</b>。
     *
     * <h2>为什么不用 {@code PlayerEvents.balanceRevivalDegree}</h2>
     *
     * <p>那个方法的语义是「<b>主鬼</b> +20、其他槽 −8」。玩家只把卡卡罗特融在武器上时，
     * 主鬼槽里是别的鬼 —— 结果是放一次变身，别人的复苏度涨了 20。
     * 本体的技能以主鬼为代价是合理的（技能本来就挂在主鬼身上），
     * 但我们的技能挂在物品上，代价就必须跟着物品走。
     *
     * <p>所以这里绕开 balance，按「卡卡罗特在哪」分别记：
     * 在槽里就加那一格，只有物品就加物品上那条规律。
     */
    public static void chargeRelease(ServerPlayerEntity player, int amount) {
        int slot = findSlot(player);
        if (slot >= 0) {
            int degree = PlayerEvents.getGhostSlotRevivalDegree(player, slot);
            int required = PlayerEvents.getGhostSlotRequiredRevivalDegree(player, slot);
            if (required <= 0) {
                required = 1000;
            }
            int next = Math.min(degree + amount, required - 1);
            if (next > degree) {
                PlayerEvents.updateGhostSlotValue(player, slot, "revivalDegree", next);
            }
            return;
        }
        // 只有物品：涨满会清零并打断变身由每 tick 的 burn 负责，
        // 这里的释放代价不追求把形态掐断，忽略返回值
        burnItemRevival(player, amount);
    }

    /**
     * 卡卡罗特所在的鬼槽下标；没有则 {@code -1}。
     */
    public static int findSlot(PlayerEntity player) {
        if (player == null) {
            return -1;
        }
        for (int slot = 0; slot < PlayerEvents.GHOST_SLOT_COUNT; slot++) {
            ItemStack stack = PlayerEvents.getGhostSlotItem(player, slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BaseGhostEyeItem eye)) {
                continue;
            }
            if (GHOST_TYPE.equals(eye.getGhostType())) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 等级：槽里有就取槽里的等级，只有物品时按 1 级算（融合物品没有等级体系）。
     */
    public static int level(PlayerEntity player) {
        int slot = findSlot(player);
        if (slot < 0) {
            return 1;
        }
        return Math.max(1, PlayerEvents.getGhostSlotLevel(player, slot));
    }

    /**
     * 承载卡卡罗特的物品；没有则 {@code null}。
     */
    public static ItemStack carriedItem(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        for (FusedGhostSlots.Bound bound : FusedGhostSlots.carried(player)) {
            if (GHOST_TYPE.equals(bound.ghostType())) {
                return bound.stack();
            }
        }
        return null;
    }

    /**
     * 往物品上那条规律加复苏值。
     *
     * <p>涨到上限时<b>清零</b>再返回 true：一次变身把这只鬼"复苏的力气"用完了，
     * 得重新攒。不清零的话，之后每次变身都会一上来就爆，等于永久禁用。
     *
     * @return 是否已经涨到上限（到了就该打断变身）
     */
    public static boolean burnItemRevival(ServerPlayerEntity player, double amount) {
        ItemStack stack = carriedItem(player);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemGhostHost host = ItemGhostHost.open(stack, player.getWorld(), player);
        GhostModuleData data = host.modules().get(NativeGhostRuleModule.ID);
        if (data == null) {
            return false;
        }
        GhostModule module = GhostModuleRegistry.get(NativeGhostRuleModule.ID);
        double max = module == null ? 100.0D : module.maxRevival();

        double next = data.revival() + amount;
        boolean maxed = next >= max;
        data.setRevival(maxed ? 0.0D : next);
        host.flush();
        return maxed;
    }
}
