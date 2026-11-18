package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 进食必杀：玩家正在吃东西，它就下手。
 *
 * <p>本体「食物鬼」的规律。吃东西时手不能拿武器、也不能跑，
 * 这条规律专门惩罚"在野外安心回血"的行为。
 */
public final class EatingGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "eating_gate");

    public EatingGateModule() {
        super(ID, "进食必杀", "eating_gate/feeding", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return player.isUsingItem() && player.getActiveItem().isFood();
    }

    @Override
    protected String condition() {
        return "正在进食的人，它才动手";
    }
}
