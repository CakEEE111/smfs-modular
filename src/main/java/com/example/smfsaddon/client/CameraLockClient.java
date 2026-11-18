package com.example.smfsaddon.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 婚礼视角锁定（客户端这一半）。
 *
 * <p>只做两件事：记住"要盯谁、还剩多久"，以及提供当前应该用的朝向。
 * 真正的改朝向在 {@code CameraMixin} 里 —— 那里能拿到相机实例。</p>
 */
public final class CameraLockClient {

    private static int targetEntityId = -1;
    private static int remainingTicks;

    /** 是否连相机一起接管（默认否：只锁操作，不锁视角） */
    private static boolean cameraTaken;

    /** 仪式期间把玩家钉在原地 —— 位置由服务端下发时的客户端坐标决定 */
    private static net.minecraft.util.math.Vec3d anchor;

    private CameraLockClient() {
    }

    public static void accept(int entityId, int ticks, boolean takeCamera) {
        if (entityId < 0) {
            targetEntityId = -1;
            remainingTicks = 0;
            anchor = null;
            cameraTaken = false;
            return;
        }
        targetEntityId = entityId;
        remainingTicks = ticks;
        cameraTaken = takeCamera;

        var client = MinecraftClient.getInstance();
        anchor = client.player == null ? null : client.player.getPos();
    }

    /** 每客户端 tick 递减；实体消失也立刻解除 */
    public static void tick() {
        if (targetEntityId < 0) {
            return;
        }
        if (--remainingTicks <= 0 || resolve() == null) {
            targetEntityId = -1;
            remainingTicks = 0;
        }
    }

    public static boolean active() {
        return targetEntityId >= 0 && resolve() != null;
    }

    /** 只有这个为真时才接管相机 */
    public static boolean cameraActive() {
        return active() && cameraTaken;
    }

    /** 仪式锁定中：玩家不能移动、不能交互 */
    public static boolean blockingInput() {
        return active();
    }

    public static net.minecraft.util.math.Vec3d anchor() {
        return anchor;
    }



    private static Entity resolve() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return null;
        }
        Entity entity = client.world.getEntityById(targetEntityId);
        return entity != null && entity.isAlive() ? entity : null;
    }

    /** 机位距礼堂的水平距离（礼堂放大 2 倍后约 13 格深，退远才拍得全） */
    private static final double CAMERA_DISTANCE = 18.0D;

    /** 机位高度：比礼堂中心高一点，带俯视 */
    private static final double CAMERA_HEIGHT = 7.0D;

    /**
     * 电影机位：站在礼堂外侧（玩家那一侧的后方），俯视礼堂正中。
     *
     * <p>不再是"玩家眼睛 + 转头看"——那样玩家一旦站在礼堂模型里，镜头就卡在
     * 梁柱之间。现在相机的<b>位置</b>也由我们决定，玩家在哪都不影响画面。</p>
     */
    public static Vec3d cameraPos() {
        Entity target = resolve();
        if (target == null) {
            return null;
        }
        Vec3d hall = target.getPos();
        Vec3d from = anchor != null ? anchor : hall.add(0, 0, 1);
        Vec3d dir = new Vec3d(hall.x - from.x, 0, hall.z - from.z);
        if (dir.lengthSquared() < 1.0E-4D) {
            dir = new Vec3d(0, 0, 1);
        }
        dir = dir.normalize();
        return hall.add(dir.multiply(CAMERA_DISTANCE)).add(0.0D, CAMERA_HEIGHT, 0.0D);
    }

    /** 朝向：从机位看向礼堂的中心偏上 */
    public static float[] rotationFrom(Vec3d cameraPos) {
        Entity target = resolve();
        if (target == null) {
            return null;
        }
        Vec3d focus = target.getPos().add(0.0D, 2.5D, 0.0D);
        Vec3d delta = focus.subtract(cameraPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(delta.y, horizontal) * 180.0D / Math.PI));
        return new float[]{yaw, pitch};
    }
}
