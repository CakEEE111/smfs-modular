package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 跳跃必杀：双脚离地正在上升的人，它就下手。
 *
 * <p>本体「跳跃鬼」的规律。原实现盯着 {@code isOnGround} 与竖直速度，
 * 这里只保留可移植的那部分 —— 判定的是<b>玩家此刻的状态</b>，
 * 不是鬼自己记了什么。
 */
public final class JumpGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "jump_gate");

    public JumpGateModule() {
        super(ID, "跳跃必杀", "jump_gate/airborne", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return !player.isOnGround() && player.getVelocity().y > 0.0D;
    }

    @Override
    protected String condition() {
        return "跳起来的那一下，它就会动手";
    }
}
