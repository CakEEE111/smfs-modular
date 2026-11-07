package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 埋头必杀：玩家低头，它就下手。
 *
 * <p>本体「埋头鬼」的规律，与抬头鬼互为镜像。
 */
public final class LookDownModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "look_down");

    private static final float PITCH_THRESHOLD = 30.0F;

    public LookDownModule() {
        super(ID, "埋头必杀", "look_down/bowed_head", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                              GhostEntity self, World world) {
        return player.getPitch() > PITCH_THRESHOLD;
    }

    @Override
    protected String condition() {
        return "低头的瞬间，它就站在你面前";
    }
}
