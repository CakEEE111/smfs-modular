package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 静止必杀：玩家一动不动，它就下手。
 *
 * <p>本体「傀儡鬼」的规律。不读鬼的记忆，只比较玩家这一 tick 与上一 tick 的位移，
 * 所以任何宿主装上都能生效。
 */
public final class StationaryGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "stationary_gate");

    /** 位移低于这个平方值就当没动（大约 0.02 格） */
    private static final double STILL_SQ = 4.0E-4D;

    public StationaryGateModule() {
        super(ID, "静止必杀", "stationary_gate/motionless", 35.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return player.getVelocity().lengthSquared() < STILL_SQ;
    }

    @Override
    protected String condition() {
        return "站着不动的人，它才动手";
    }
}
