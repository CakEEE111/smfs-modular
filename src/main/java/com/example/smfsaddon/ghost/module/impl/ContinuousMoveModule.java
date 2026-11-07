package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 移动必杀：连续走动超过 3 秒，它就下手。
 *
 * <p>本体「雾鬼／静悄悄」的规律。原实现把"什么时候开始动"记在鬼自己身上
 * （实例 map），所以换个实例就失忆 —— <b>但模块不是</b>：
 * {@code context.state()} 存在宿主自己的容器里，跟着这只鬼走。
 *
 * <p>这是"记忆型规律也能模块化"的验证点：状态属于<b>宿主</b>，不属于模块类。
 */
public final class ContinuousMoveModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "continuous_move");

    /** 水平速度超过这个值算在走（约 0.05 格/tick） */
    private static final double MOVING_SQ = 2.5E-3D;

    /** 连续走这么久就动手：60 tick = 3 秒 */
    private static final long MOVE_TICKS = 60L;

    public ContinuousMoveModule() {
        super(ID, "移动必杀", "continuous_move/walking", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        NbtCompound state = context.state();
        String key = "move_" + player.getUuidAsString();
        long now = context.ticks();

        if (player.getVelocity().horizontalLengthSquared() < MOVING_SQ) {
            state.putLong(key, 0L);
            return false;
        }
        long start = state.getLong(key);
        if (start == 0L) {
            state.putLong(key, now);
            return false;
        }
        return now - start >= MOVE_TICKS;
    }

    @Override
    protected String condition() {
        return "连续走动超过三秒，它就会动手";
    }
}
