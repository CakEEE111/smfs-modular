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

    /**
     * 水平速度低于这个平方值就当没动（约 0.07 格/tick）。
     *
     * <p>只看水平分量：{@code getVelocity()} 里带着重力，站在地上的玩家
     * y 分量常常是 -0.078 左右，算进去会让"站着不动"永远不成立。
     */
    private static final double STILL_HORIZONTAL_SQ = 5.0E-3D;

    public StationaryGateModule() {
        super(ID, "静止必杀", "stationary_gate/motionless", 35.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return player.getVelocity().horizontalLengthSquared() < STILL_HORIZONTAL_SQ
                && player.isOnGround();
    }

    @Override
    protected String condition() {
        return "站着不动的人，它才动手";
    }
}
