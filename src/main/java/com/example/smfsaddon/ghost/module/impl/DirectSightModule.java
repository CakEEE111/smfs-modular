package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * 直视必杀：玩家看着它，它就下手。
 *
 * <p>本体「死亡视线」的规律。与 {@link FacingAwayModule 背对必杀} 互补 ——
 * 两个都装上时，唯一安全的角度是<b>侧身</b>。
 *
 * <p>加了遮挡判定：隔着墙看它不算，否则躲在墙后转头就没命，玩家会觉得毫无道理。
 */
public final class DirectSightModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "direct_sight");

    /** 视线点积阈值，0.5 约等于 60 度锥角（与原实现一致） */
    private static final double SIGHT_DOT = 0.5D;

    public DirectSightModule() {
        super(ID, "直视必杀", "direct_sight/eye_contact", 50.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        if (self == null) {
            return false;
        }
        Vec3d toGhost = self.getEyePos().subtract(player.getEyePos());
        if (toGhost.lengthSquared() < 1.0E-4D) {
            return false;
        }
        if (player.getRotationVec(1.0F).dotProduct(toGhost.normalize()) < SIGHT_DOT) {
            return false;
        }
        // 隔墙不算
        return world.raycast(new RaycastContext(
                player.getEyePos(), self.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player)).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    @Override
    protected String condition() {
        return "与它对视、且中间没有遮挡，它就会动手";
    }
}
