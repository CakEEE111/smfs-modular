package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.util.GhostUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 「驾驭」皮嫁骨·礼成体 —— 走本体自己的鬼槽位。
 *
 * <h2>为什么用本体的接口</h2>
 *
 * <p>本体的驾驭是一整套体系（鬼槽位、等级、复苏度、主鬼切换、技能按键）。
 * 自己另起一套会和它的界面、按键、数据完全脱节，所以这里直接复用它：</p>
 *
 * <ul>
 *   <li>{@code GhostUtils.registerAddonGhostMapping} —— 让本体认识这个鬼种
 *       （显示名 / 能力描述 / 驾驭物品）；</li>
 *   <li>{@code PlayerEvents.getUnlockedGhostSlot} —— 找第一个已解锁的空槽；</li>
 *   <li>{@code PlayerEvents.setGhostSlotData} —— 把驾驭物品写进槽位；</li>
 *   <li>{@code validateGhostSlots} —— 让本体重建槽位缓存。</li>
 * </ul>
 *
 * <p>写进槽位之后，鬼就完全归本体的系统管了 —— 技能按键、面板、等级都走本体。</p>
 */
public final class UnionCapture {

    /**
     * 鬼种 ID —— 必须是<b>不带命名空间</b>的裸名。
     *
     * <p>本体内部所有查表（GHOST_TYPE_TO_ITEM_MAP、ADDON_GHOST_MAPPINGS、
     * 槽位的 ghostType 字段）用的都是 "kakarot_ghost" 这种裸名。
     * 写成 "smfsaddon:pijiagu_union" 会一路查空，最后落到兜底的纸（踩过）。</p>
     */
    public static final String GHOST_TYPE = "pijiagu_union";

    private UnionCapture() {
    }

    /** 婚礼走完之后调用：把礼成体收进玩家的鬼槽位 */
    public static boolean capture(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        int slot = PlayerEvents.getUnlockedGhostSlot(player);
        if (slot < 0) {
            serverPlayer.sendMessage(Text.literal(
                    "§7礼成体站在你面前，但你身上没有可以容纳它的槽位。"), false);
            return false;
        }

        NbtCompound ghostNbt = new NbtCompound();
        ghostNbt.putString("Type", GHOST_TYPE);   // 裸名
        ghostNbt.putString("Name", "皮嫁骨·礼成体");

        ItemStack token = GhostUtils.createTamedItem(GHOST_TYPE, ghostNbt);
        if (token.isEmpty() || token.getItem() == Items.AIR) {
            // 兜底：正常情况下本体已经能按 ghostType 构造出我们的驾驭物品
            token = new ItemStack(Items.PAPER);
            token.setCustomName(Text.literal("§d皮嫁骨·礼成体"));
        }

        PlayerEvents.setGhostSlotData(player, slot, token);
        PlayerEvents.validateGhostSlots(player);

        // 成为异类：本体把"异类"记在成就上，这里直接解锁它
        try {
            com.xie.smfs.manager.AdvancementManager.unlockAdvancement(serverPlayer,
                    com.xie.smfs.manager.AdvancementManager.BECOME_ABERRATION_ADVANCEMENT);
        } catch (Throwable throwable) {
            SmfsAddon.LOGGER.warn("[smfsaddon] 解锁异类成就失败: {}", throwable.toString());
        }

        // 礼成之后，她把皮还给你了 —— 骨架上重新盖回一层皮
        serverPlayer.removeStatusEffect(SmfsAddon.FLESH_REMOVED);

        serverPlayer.sendMessage(Text.literal("§d你成了这场婚礼的一部分。§7槽位 " + (slot + 1) + " 已归你。"), false);
        serverPlayer.sendMessage(Text.literal("§7你不再是人。§8（异类）"), false);
        serverPlayer.sendMessage(Text.literal("§d她走过来，把皮还给了你。"), false);
        SmfsAddon.LOGGER.info("[smfsaddon] {} 驾驭了礼成体（槽位 {}）",
                player.getName().getString(), slot + 1);
        return true;
    }
}
