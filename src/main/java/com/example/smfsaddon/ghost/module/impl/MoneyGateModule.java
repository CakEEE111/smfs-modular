package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 无钱必杀：身上不带鬼钱的人，它就下手。
 *
 * <p>本体「商人鬼」的规律。原实现还多一条"是不是熟客"，那属于鬼的记忆，
 * 搬不过来；剩下的"带没带钱"完全读玩家背包，可以移植。
 *
 * <p>这也是少见的一条<b>有破解方式</b>的规律：兜里揣着鬼钱，它就不动你。
 */
public final class MoneyGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "money_gate");

    public MoneyGateModule() {
        super(ID, "无钱必杀", "money_gate/broke", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) {
                continue;
            }
            if (Registries.ITEM.getId(stack.getItem()).getPath().startsWith("ghost_money")) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected String condition() {
        return "身上不带鬼钱的人，它才动手";
    }
}
