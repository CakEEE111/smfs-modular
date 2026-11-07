package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 饥饿必杀：玩家饱食度见底，它就下手。
 *
 * <p>本体没有这条规律，是给交错补的一个"长期压力"型机制 ——
 * 它不会立刻要命，但会让"饿着肚子赶路"变成一种真实的危险。
 */
public final class HungerGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "hunger_gate");

    /** 6 点饱食度 = 3 个鸡腿 */
    private static final int HUNGRY_LEVEL = 6;

    public HungerGateModule() {
        super(ID, "饥饿必杀", "hunger_gate/starving", 35.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return player.getHungerManager().getFoodLevel() <= HUNGRY_LEVEL;
    }

    @Override
    protected String condition() {
        return "饿着肚子的人，它才动手";
    }
}
