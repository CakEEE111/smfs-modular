package com.example.smfsaddon.ghost.module.event;

import net.minecraft.util.Identifier;

/**
 * 每 tick 派发一次，仅在服务端。
 */
public final class GhostTickEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "tick");

    private final long tick;

    public GhostTickEvent(long tick) {
        this.tick = tick;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    /**
     * 宿主当前的存活 tick 数，可直接用于取模控制节奏。
     */
    public long tick() {
        return tick;
    }
}
