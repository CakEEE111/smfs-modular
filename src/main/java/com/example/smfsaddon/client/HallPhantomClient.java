package com.example.smfsaddon.client;

import net.minecraft.entity.player.PlayerEntity;

/**
 * 「守护灵礼堂」的客户端幻影。
 *
 * <h2>为什么不用实体</h2>
 *
 * <p>试过把实体做成守护灵：isCollidable / isPushable / collidesWith / noClip /
 * 缩小包围盒五条路全堵上，玩家依然会被推开 —— 推挤来自本体鬼自己的逻辑，
 * 不是原版碰撞。既然推不掉，就不要实体。</p>
 *
 * <p>现在服务端只负责伤害脉冲，客户端按这个时间窗自己画一具礼堂幻影：
 * 没有实体就没有任何物理交互，绝对不可能推动玩家。</p>
 */
public final class HallPhantomClient {

    private static long endTick = -1;
    private static int ownerId = -1;

    private HallPhantomClient() {
    }

    public static void start(int ownerEntityId, int durationTicks) {
        ownerId = ownerEntityId;
        endTick = -1;   // 由第一次渲染时用世界时间补上
        pendingTicks = durationTicks;
    }

    private static int pendingTicks;

    public static void tick(net.minecraft.client.world.ClientWorld world) {
        if (world == null) {
            return;
        }
        if (endTick < 0 && pendingTicks > 0) {
            endTick = world.getTime() + pendingTicks;
        }
        if (endTick >= 0 && world.getTime() > endTick) {
            endTick = -1;
            ownerId = -1;
            pendingTicks = 0;
        }
    }

    public static boolean isActive() {
        return endTick >= 0 && ownerId >= 0;
    }

    public static boolean isOwner(net.minecraft.entity.Entity entity) {
        return isActive() && entity != null && entity.getId() == ownerId;
    }

    public static PlayerEntity owner(net.minecraft.client.world.ClientWorld world) {
        if (world == null || ownerId < 0) {
            return null;
        }
        var entity = world.getEntityById(ownerId);
        return entity instanceof PlayerEntity player ? player : null;
    }
}
