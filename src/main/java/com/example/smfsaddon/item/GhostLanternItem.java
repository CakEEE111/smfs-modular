package com.example.smfsaddon.item;

import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.impl.SpatialPressureModule;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 鬼灯笼 —— 承载规律的灵异道具。
 *
 * <p>它证明的是「同一份模块代码可以跑在物品上」。
 * 挂上去的仍然是 {@link SpatialPressureModule} —— 一个只要求空间能力的模块，
 * 和挂在鬼、挂在方块上的是同一份实现。
 *
 * <h2>为什么是自己驱动 tick</h2>
 *
 * <p>物品没有心跳。{@code inventoryTick} 由持有者的实体 tick 带动，
 * 这是物品唯一能拿到的时钟。所以 {@code TickHost} 不是框架送的，
 * 是宿主必须自己接上的东西 —— 这条在框架文档里被单独标了出来。
 */
public class GhostLanternItem extends Item {

    /**
     * 降频间隔。
     *
     * <p>物品宿主每次开启都要新建一个宿主对象；背包里几十个物品如果每 tick
     * 都分配，开销很难看。10 tick 一次足够驱动范围判定，也把分配摊薄了。
     */
    private static final int TICK_INTERVAL = 10;

    public GhostLanternItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient() || world.getTime() % TICK_INTERVAL != 0) {
            return;
        }

        ItemGhostHost host = ItemGhostHost.open(stack, world, entity);

        boolean attachedNow = false;
        if (host.modules().isEmpty()) {
            // 首次拿到时挂上示例模块；已存档的物品读档后模块已存在，不会重复挂
            GhostModuleDispatcher.attach(host, SpatialPressureModule.ID);
            attachedNow = true;
        }

        GhostModuleDispatcher.tick(host);
        boolean written = host.flush();

        // 首次挂上模块后必须把物品 NBT 同步到客户端，
        // 否则客户端那份 ItemStack 一直是旧的，tooltip 永远显示「承载规律：无」。
        if (attachedNow && written && entity instanceof ServerPlayerEntity player) {
            player.playerScreenHandler.syncState();
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        // 词条由 ItemTooltipMixin 统一追加；
        // 但本类覆写了 appendTooltip，不调 super 的话混入不会执行。
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.literal("§7它被谁摸过，自己记得。"));
    }
}
