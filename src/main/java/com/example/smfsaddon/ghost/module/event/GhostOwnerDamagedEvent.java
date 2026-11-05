package com.example.smfsaddon.ghost.module.event;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 持有者受伤。
 *
 * <p>前面几个事件都是"东西被用"，这个不一样：它是<b>物品护主</b>。
 * 替死鬼这类能力不属于杀人规律，也不属于使用时触发 ——
 * 它是在主人要死的那一刻顶上去。
 *
 * <p>规则可以调 {@link #cancel()} 把这次伤害整个压掉。
 */
public final class GhostOwnerDamagedEvent implements GhostEvent {

    public static final Identifier ID = new Identifier("smfsaddon", "owner_damaged");

    private final PlayerEntity player;
    private final DamageSource source;
    private final float amount;

    private boolean cancelled = false;

    public GhostOwnerDamagedEvent(PlayerEntity player, DamageSource source, float amount) {
        this.player = player;
        this.source = source;
        this.amount = amount;
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public PlayerEntity player() {
        return player;
    }

    public DamageSource source() {
        return source;
    }

    public float amount() {
        return amount;
    }

    /**
     * 取消这次伤害。任一条规则调用后，伤害不再结算。
     */
    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
