package com.example.smfsaddon.ghost.module.impl;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 背对必杀：玩家背朝着它，它就下手。
 *
 * <p>本体「巨人鬼影」的规律。判定只看玩家的朝向，不读任何自身状态，
 * 所以换成任何一只鬼都能生效。
 */
public final class FacingAwayModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "facing_away");

    /** 点积小于这个值才算背对（越负越严格） */
    private static final double BACK_TURNED_DOT = 0.0D;

    public FacingAwayModule() {
        super(ID, "背对必杀", "facing_away/back_turned", 45.0D);
    }

    @Override
    protected boolean test(PlayerEntity player, GhostEntity self, World world) {
        if (self == null) {
            return false;
        }
        Vec3d toGhost = self.getPos().subtract(player.getEyePos());
        if (toGhost.lengthSquared() < 1.0E-4D) {
            return false;
        }
        return player.getRotationVec(1.0F).dotProduct(toGhost.normalize()) < BACK_TURNED_DOT;
    }

    @Override
    protected String condition() {
        return "只要背朝着它，它就会动手";
    }
}
