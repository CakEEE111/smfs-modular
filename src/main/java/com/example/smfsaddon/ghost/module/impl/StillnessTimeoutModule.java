package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 久立必杀：连续站住超过 5 秒，它就下手。
 *
 * <p>与 {@link StationaryGateModule 静止必杀}的区别：那个是"一站住就死"，
 * 这个是"站久了才死" —— 留了五秒反应时间，玩家还有机会逃。
 *
 * <p>状态同样记在宿主的容器里（{@code context.state()}），换宿主各自独立。
 */
public final class StillnessTimeoutModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "stillness_timeout");

    private static final double STILL_SQ = 2.5E-3D;

    /** 100 tick = 5 秒 */
    private static final long STILL_TICKS = 100L;

    public StillnessTimeoutModule() {
        super(ID, "久立必杀", "stillness_timeout/lingering", 35.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        NbtCompound state = context.state();
        String key = "still_" + player.getUuidAsString();
        long now = context.ticks();

        boolean still = player.getVelocity().horizontalLengthSquared() < STILL_SQ
                && player.isOnGround();
        if (!still) {
            state.putLong(key, 0L);
            return false;
        }
        long start = state.getLong(key);
        if (start == 0L) {
            state.putLong(key, now);
            return false;
        }
        return now - start >= STILL_TICKS;
    }

    @Override
    protected String condition() {
        return "原地站住超过五秒，它就会动手";
    }
}
