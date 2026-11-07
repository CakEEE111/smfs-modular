package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * 暗处必杀：玩家站在光照不足的地方，它就下手。
 *
 * <p>本体「影子鬼」的规律，取的是<b>方块光</b>而不是天空光 ——
 * 白天站在野外照样算亮，只有真正有光源的地方才算安全。
 */
public final class LowLightModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "low_light");

    /** 7 及以下算暗（与此前实现的低光判定一致） */
    private static final int DARK_THRESHOLD = 7;

    public LowLightModule() {
        super(ID, "暗处必杀", "low_light/shadowed", 45.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        return world.getLightLevel(LightType.BLOCK, player.getBlockPos()) <= DARK_THRESHOLD;
    }

    @Override
    protected String condition() {
        return "站在没有光源的地方，它就会出现";
    }
}
