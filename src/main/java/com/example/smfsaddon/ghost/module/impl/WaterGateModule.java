package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 涉水必杀：玩家碰到水，它就下手。
 *
 * <p>本体「水鬼」的规律。只看玩家是否在水里，与鬼自己无关。
 */
public final class WaterGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "water_gate");

    public WaterGateModule() {
        super(ID, "涉水必杀", "water_gate/submerged", 50.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return player.isTouchingWater() || player.isSubmergedInWater();
    }

    @Override
    protected String condition() {
        return "碰到水的人，它才动手";
    }
}
