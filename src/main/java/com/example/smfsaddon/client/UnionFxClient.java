package com.example.smfsaddon.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.random.Random;

/**
 * 席面的镜头演出。
 *
 * <h2>为什么直接改 pitch/yaw</h2>
 *
 * <p>没有 shader 的情况下，最直接的"被按着头"就是每 tick 给视角一个小偏移：
 * 震屏是随机抖动，入席是持续的向下漂移。玩家仍然能反抗（往回抬），
 * 但镜头一直在被往下拽 —— 这正是要的效果。</p>
 */
public final class UnionFxClient {

    private static long shakeUntil;
    private static long seatedUntil;
    private static final Random RANDOM = Random.create();

    private UnionFxClient() {
    }

    public static void shake(int ticks) {
        shakeUntil = System.currentTimeMillis() + ticks * 50L;
    }

    public static void seated(int ticks) {
        seatedUntil = System.currentTimeMillis() + ticks * 50L;
    }

    public static void release() {
        shakeUntil = 0;
        seatedUntil = 0;
    }

    public static void tick(ClientPlayerEntity player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();

        if (now < shakeUntil) {
            // 震屏：±1.5°
            player.setYaw(player.getYaw() + (RANDOM.nextFloat() - 0.5F) * 3.0F);
            player.setPitch(player.getPitch() + (RANDOM.nextFloat() - 0.5F) * 3.0F);
        }

        if (now < seatedUntil) {
            // 视线下垂：每 tick 往下压 0.45°，最多压到 30°
            float pitch = player.getPitch();
            if (pitch < 30.0F) {
                player.setPitch(Math.min(30.0F, pitch + 0.45F));
            }
        }
    }
}
