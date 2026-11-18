package com.example.smfsaddon.ghost.module.event;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.Identifier;

/**
 * 鬼受到伤害时派发，仅服务端。
 */
public final class GhostDamagedEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "damaged");

    private final DamageSource source;
    private final float amount;

    public GhostDamagedEvent(DamageSource source, float amount) {
        this.source = source;
        this.amount = amount;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    public DamageSource source() {
        return source;
    }

    public float amount() {
        return amount;
    }
}
