package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 携矿必杀：玩家背包里带着矿石/金属，它就下手。
 *
 * <p>本体「矿鬼」的规律。按物品 id 的命名习惯判定（{@code *_ore} / {@code raw_*} /
 * {@code *ingot*}），不写死具体物品 —— 附属新增的矿石只要照这个名字命名，自动算数。
 */
public final class MineralGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "mineral_gate");

    public MineralGateModule() {
        super(ID, "携矿必杀", "mineral_gate/carrying_ore", 55.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                              GhostEntity self, World world) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) {
                continue;
            }
            if (isOreLike(Registries.ITEM.getId(stack.getItem()).getPath())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOreLike(String path) {
        return path.endsWith("_ore")
                || path.startsWith("raw_")
                || path.contains("ingot")
                || path.endsWith("_gem");
    }

    @Override
    protected String condition() {
        return "背包里带着矿石的人，它才动手";
    }
}
