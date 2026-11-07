package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 抬头必杀：玩家抬头看它，它就下手。
 *
 * <p>本体「抬头鬼」的规律。只看 pitch，不依赖鬼自身状态。
 */
public final class LookUpModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "look_up");

    /** 与原实现一致：pitch < -30 视为抬头 */
    private static final float PITCH_THRESHOLD = -30.0F;

    public LookUpModule() {
        super(ID, "抬头必杀", "look_up/eye_contact", 40.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return player.getPitch() < PITCH_THRESHOLD;
    }

    @Override
    protected String condition() {
        return "抬头的瞬间就是它动手的时机";
    }
}
