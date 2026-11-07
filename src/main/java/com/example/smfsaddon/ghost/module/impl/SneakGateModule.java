package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 潜行必杀：玩家蹲下（潜行），它就下手。
 *
 * <p>本体「潜行鬼」的规律。注意它反直觉 —— <b>蹲下才危险</b>，
 * 站着走过去反而安全。
 */
public final class SneakGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "sneak_gate");

    public SneakGateModule() {
        super(ID, "潜行必杀", "sneak_gate/crouching", 40.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return player.isSneaking();
    }

    @Override
    protected String condition() {
        return "蹲下的人才是目标 —— 站着走过去反而安全";
    }
}
