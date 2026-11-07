package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 雷雨必杀：只在雷雨天动手。
 *
 * <p>不读玩家状态，读的是天气 —— 属于"环境型"，和血迹鬼那类
 * "特定条件下才出现"的设定是一路的。
 */
public final class ThunderGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "thunder_gate");

    public ThunderGateModule() {
        super(ID, "雷雨必杀", "thunder_gate/storm", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return world.isThundering();
    }

    @Override
    protected String condition() {
        return "只在雷雨天动手";
    }
}
