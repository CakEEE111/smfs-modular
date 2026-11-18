package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 着火必杀：身上带火的人，它就下手。
 *
 * <p>本体「烧伤鬼」的规律。掉进岩浆之后不是"爬出来就安全"——
 * 身上还烧着的那几秒正是最危险的时候。
 */
public final class BurningGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "burning_gate");

    public BurningGateModule() {
        super(ID, "着火必杀", "burning_gate/on_fire", 45.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return player.isOnFire();
    }

    @Override
    protected String condition() {
        return "身上带火的人，它才动手";
    }
}
