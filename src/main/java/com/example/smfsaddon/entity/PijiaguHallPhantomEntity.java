package com.example.smfsaddon.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 「守护灵礼堂」——G 技能召出的那具。
 *
 * <h2>为什么是普通实体，不是本体的鬼</h2>
 *
 * <p>之前继承 {@code GhostEntity}，玩家会被它推着走：isCollidable / isPushable /
 * collidesWith / noClip / 缩小包围盒五条路全堵上都没用 —— 推力来自本体鬼自己的逻辑。</p>
 *
 * <p>改成继承 {@code Entity}：没有任何 AI、没有寻路、不参与推挤，
 * 同时仍能走正常的实体渲染管线（比世界渲染事件那套坐标好算得多）。</p>
 */
public class PijiaguHallPhantomEntity extends Entity {

    private UUID ownerUuid;
    private int endTick;

    public PijiaguHallPhantomEntity(EntityType<? extends PijiaguHallPhantomEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setOwner(PlayerEntity owner, int durationTicks) {
        this.ownerUuid = owner.getUuid();
        this.endTick = this.age + durationTicks;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (ownerUuid == null) {
            this.discard();
            return;
        }
        PlayerEntity owner = world.getPlayerByUuid(ownerUuid);
        if (owner == null || this.age > endTick) {
            this.discard();
            return;
        }
        // 正后方 2 格
        double yaw = Math.toRadians(owner.getYaw());
        this.setPosition(owner.getX() + Math.sin(yaw) * 2.0D, owner.getY(),
                owner.getZ() - Math.cos(yaw) * 2.0D);
        this.setYaw(owner.getYaw());
    }

    // ---- 完全无物理 ----

    @Override public boolean isCollidable() { return false; }

    @Override public boolean isPushable() { return false; }

    @Override protected void initDataTracker() {
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
