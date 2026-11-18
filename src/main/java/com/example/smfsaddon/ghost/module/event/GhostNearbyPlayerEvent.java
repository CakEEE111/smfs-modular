package com.example.smfsaddon.ghost.module.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 玩家进入模块声明的扫描半径时派发。
 *
 * <p>扫描间隔与半径由 {@code GhostModule#nearbyScanInterval} /
 * {@code GhostModule#nearbyScanRadius} 决定，默认关闭。
 */
public final class GhostNearbyPlayerEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "nearby_player");

    private final PlayerEntity player;
    private final double distanceSq;

    public GhostNearbyPlayerEvent(PlayerEntity player, double distanceSq) {
        this.player = player;
        this.distanceSq = distanceSq;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    public PlayerEntity player() {
        return player;
    }

    /**
     * 与宿主的平方距离，用来避免开方。
     */
    public double distanceSq() {
        return distanceSq;
    }
}
