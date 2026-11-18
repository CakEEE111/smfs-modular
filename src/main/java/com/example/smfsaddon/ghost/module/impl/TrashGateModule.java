package com.example.smfsaddon.ghost.module.impl;

import com.example.smfsaddon.ghost.module.GhostModuleContext;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 拾荒必杀：附近有掉落物的人，它就下手。
 *
 * <p>本体「垃圾鬼」的规律。原实现是扫玩家周围 6 格内的掉落物实体，
 * 这里照做 —— 掉落物是<b>世界状态</b>，不是谁的记忆，所以能跨宿主。
 */
public final class TrashGateModule extends EnvGateModule {

    public static final Identifier ID = new Identifier("smfsaddon", "trash_gate");

    /** 与原实现一致：6 格 */
    private static final double SCAN_RADIUS = 6.0D;

    public TrashGateModule() {
        super(ID, "拾荒必杀", "trash_gate/littered", 40.0D);
    }

    @Override
    protected boolean test(GhostModuleContext context, PlayerEntity player,
                           GhostEntity self, World world) {
        return !world.getEntitiesByClass(
                ItemEntity.class,
                player.getBoundingBox().expand(SCAN_RADIUS),
                item -> !item.isRemoved() && item.isAlive()
        ).isEmpty();
    }

    @Override
    protected String condition() {
        return "身边散落着掉落物时，它就会动手";
    }
}
