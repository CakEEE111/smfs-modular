package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 裸身必杀：玩家一件护甲都没穿，它就下手。
 *
 * <p>本体「衣服鬼」的规律。注意它和"装备越好越安全"是同一个逻辑的两面 ——
 * 这条规律逼玩家在探索时也要穿点东西。
 */
public final class BareArmorModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "bare_armor");

    public BareArmorModule() {
        super(ID, "裸身必杀", "bare_armor/unarmored", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        for (ItemStack armor : player.getInventory().armor) {
            if (!armor.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected String condition() {
        return "一件护甲都没穿的人，它才动手";
    }
}
