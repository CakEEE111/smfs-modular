package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 开箱必杀：玩家正开着容器，它就下手。
 *
 * <p>本体「箱子鬼」的规律，但实现方式不同 —— 原版用一张静态 map 记
 * "谁最近开过箱子"再超时清理；这里直接读玩家当前打开的界面。
 *
 * <p><b>正在开着，就是正在开</b>，不需要记任何东西，也不会出现
 * "关掉箱子几秒后还被追杀"这种记忆残留。
 */
public final class ContainerGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "container_gate");

    public ContainerGateModule() {
        super(ID, "开箱必杀", "container_gate/looting", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return player instanceof ServerPlayerEntity server
                && server.currentScreenHandler instanceof GenericContainerScreenHandler;
    }

    @Override
    protected String condition() {
        return "正在翻箱倒柜的人，它才动手";
    }
}
