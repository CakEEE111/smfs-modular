package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 染疫必杀：身上带着瘟疫的人，它就下手。
 *
 * <p>本体「疫鬼」的规律。瘟疫是本体自己的状态效果，
 * 这条规律让"被别的鬼感染过"变成长期危险。
 */
public final class PlagueGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "plague_gate");

    public PlagueGateModule() {
        super(ID, "染疫必杀", "plague_gate/infected", 50.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return player.hasStatusEffect(com.xie.smfs.registry.ModEffects.PLAGUE);
    }

    @Override
    protected String condition() {
        return "染上瘟疫的人，它才动手";
    }
}
