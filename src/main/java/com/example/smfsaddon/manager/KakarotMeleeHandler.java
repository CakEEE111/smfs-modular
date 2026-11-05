package com.example.smfsaddon.manager;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.event.ModEvents;
import com.xie.smfs.manager.MainGhostManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 让「卡卡罗特」为主鬼时，左键平A也能对鬼造成灵异伤害。
 *
 * 为什么需要这个：
 * 本体 `GhostEntity#damage()` 对玩家来源有一条硬门槛 ——
 *   没有灵异武器(SpiritWeapon) / 没抹尸油 / 没有灵异奔涌效果 → `return true` 直接吞掉伤害。
 * 所以空手或普通剑砍鬼是**完全没有反应**的，这是本体刻意的设计。
 *
 * 做法是复用本体自己的"技能伤害"通道：
 *   `GhostSkillManager.isSkillDamage(source)` = 玩家来源 && `ModEvents.processingSpiritDamage`
 * 在攻击回调里把这个标记打开，攻击结算完（本 tick 末）再关掉。
 * 这样走的是本体原有的伤害公式与音效粒子，不复制任何逻辑。
 */
public final class KakarotMeleeHandler {

    private static final AtomicBoolean PENDING_RESET = new AtomicBoolean(false);

    private KakarotMeleeHandler() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (!(entity instanceof LivingEntity)) {
                return ActionResult.PASS;
            }
            if (!isKakarotMainGhost(player)) {
                return ActionResult.PASS;
            }

            ModEvents.processingSpiritDamage.set(true);
            PENDING_RESET.set(true);
            return ActionResult.PASS;
        });

        // 伤害在本次网络包处理中结算，Tick 末统一复位，避免影响其它伤害判定
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING_RESET.getAndSet(false)) {
                ModEvents.processingSpiritDamage.set(false);
            }
        });
    }

    private static boolean isKakarotMainGhost(PlayerEntity player) {
        if (SmfsAddon.KAKAROT_GHOST_ITEM == null) {
            return false;
        }
        int slot = MainGhostManager.getMainGhostSlot(player);
        ItemStack stack = PlayerEvents.getGhostSlotItem(player, slot);
        return !stack.isEmpty() && stack.getItem() == SmfsAddon.KAKAROT_GHOST_ITEM;
    }
}
