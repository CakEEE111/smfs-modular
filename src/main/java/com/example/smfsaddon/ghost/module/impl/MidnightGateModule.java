package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 夜半必杀：只在夜里动手。
 *
 * <p>和雷雨必杀同类，读的是世界时间。用 13000~23000 这一段，
 * 大致对应太阳落山到黎明。
 */
public final class MidnightGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "midnight_gate");

    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    public MidnightGateModule() {
        super(ID, "夜半必杀", "midnight_gate/nightfall", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    @Override
    protected String condition() {
        return "只在夜里动手";
    }
}
