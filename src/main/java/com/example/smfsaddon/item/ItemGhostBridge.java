package com.example.smfsaddon.item;

import com.example.smfsaddon.ghost.module.GhostDebug;
import com.example.smfsaddon.ghost.module.GhostModuleData;
import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.event.GhostItemUseEvent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 物品与模块系统之间的桥。
 *
 * <p>任何物品只要身上带着模块（物品 NBT 里的模块容器），就能在这里被驱动。
 * 这一步刻意不做成某个物品类的特权 —— 剑、镐、符纸都应该是同一个入口，
 * 否则「万物插件化」就又退回到「专用物品」。
 */
public final class ItemGhostBridge {

    private ItemGhostBridge() {
    }

    /**
     * 物品被使用时触发一次规律派发。
     *
     * <p>{@code pos} / {@code state} 可以为 {@code null}（对空气使用）。
     */
    public static void fired(ItemStack stack, PlayerEntity player, World world,
                             BlockPos pos, BlockState state) {
        if (world.isClient() || stack.isEmpty()) {
            return;
        }
        // 先做一次廉价的检查，避免给没有模块的物品付建宿主的开销
        if (ItemGhostHost.moduleCount(stack) <= 0) {
            return;
        }

        ItemGhostHost host = ItemGhostHost.open(stack, world, player);

        // 调试口：把「哪个模块、对着什么方块」一起打出来。
        // 只报次数没法区分「装错了模块」和「触发了但判定不过」。
        if (GhostDebug.enabled()) {
            StringBuilder ids = new StringBuilder();
            for (GhostModuleData data : host.modules().all()) {
                if (ids.length() > 0) {
                    ids.append('+');
                }
                ids.append(data.moduleId().getPath());
            }
            GhostDebug.notice(player, "触发 " + ids + " @"
                    + (state == null ? "空气" : state.getBlock().toString()));
        }

        GhostModuleDispatcher.itemUsed(host, new GhostItemUseEvent(player, stack, pos, state));
        host.flush();
    }

    /**
     * 物品攻击命中时触发一次规律派发。
     *
     * <p>和 {@link #fired} 分开是因为它们是两条交互路径：
     * 右键是"用"，左键是"打"。规律要装在剑上，就必须能拿到"打中了谁"。
     */
    public static void attackFired(ItemStack stack, PlayerEntity attacker, LivingEntity target) {
        if (attacker == null || target == null || stack.isEmpty()) {
            return;
        }
        if (attacker.getWorld().isClient()) {
            return;
        }

        // 灵异伤害先行：哪怕这件物品没挂模块（只绑了鬼），也该有伤害
        applySpiritDamage(stack, attacker, target);

        if (ItemGhostHost.moduleCount(stack) <= 0) {
            return;
        }

        ItemGhostHost host = ItemGhostHost.open(stack, attacker.getWorld(), attacker);
        GhostModuleDispatcher.attackHit(host,
                new com.example.smfsaddon.ghost.module.event.GhostAttackHitEvent(attacker, target));
        host.flush();
    }

    /**
     * 融合物品的灵异伤害。
     *
     * <h2>为什么必须走本体的伤害源</h2>
     *
     * <p>原版 {@code playerAttack} 打在鬼身上会被当成无效伤害 ——
     * 有数字飘出来，实际不掉灵异强度。本体的 {@code ModDamageSources.ghost()}
     * 才是"灵异伤害"这条通道，门后必杀那条规律用的也是它。
     *
     * <p>伤害值来自 {@link GhostSpiritPower}：每只融进去的鬼贡献其驾驭物灵异数值的 10%。
     */
    private static void applySpiritDamage(ItemStack stack, PlayerEntity attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive()) {
            return;
        }
        float spirit = GhostSpiritPower.totalOf(stack);
        if (spirit <= 0.0F) {
            return;
        }
        // 这一下发生在普通攻击之后，目标还处于 10 tick 无敌帧里，
        // 直接打会被吃掉。清掉无敌计时再打 —— 灵异伤害是叠加的，不是替换。
        target.timeUntilRegen = 0;
        target.damage(com.xie.smfs.damage.ModDamageSources.ghost(attacker.getWorld()), spirit);
    }

    /**
     * 防止递归：替死逻辑内部可能再次造成伤害。
     */
    private static boolean inOwnerDamage = false;

    /**
     * 持有者受伤：让双手上的物品有机会介入（替死这类能力）。
     *
     * @return 是否已把这次伤害压掉
     */
    public static boolean ownerDamaged(PlayerEntity player,
                                       net.minecraft.entity.damage.DamageSource source, float amount) {
        if (player == null || player.getWorld().isClient() || inOwnerDamage) {
            return false;
        }
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty() || ItemGhostHost.moduleCount(stack) <= 0) {
                continue;
            }
            inOwnerDamage = true;
            try {
                ItemGhostHost host = ItemGhostHost.open(stack, player.getWorld(), player);
                com.example.smfsaddon.ghost.module.event.GhostOwnerDamagedEvent event =
                        new com.example.smfsaddon.ghost.module.event.GhostOwnerDamagedEvent(
                                player, source, amount);
                GhostModuleDispatcher.ownerDamaged(host, event);
                host.flush();
                if (event.isCancelled()) {
                    return true;
                }
            } finally {
                inOwnerDamage = false;
            }
        }
        return false;
    }
}
