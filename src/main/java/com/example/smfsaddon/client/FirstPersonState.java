package com.example.smfsaddon.client;

/**
 * 标记"此刻正在画第一人称手持物/手臂"。
 *
 * <p>第一人称手臂和世界里的玩家模型<b>是同一个 PlayerEntityModel 实例</b>，
 * 所以我们为了换骨架而把 arm 部件 visible=false 之后，第一人称也看不到手了。
 * 这里在 {@code HeldItemRenderer} 的两个入口前后打标记，隐藏逻辑据此让步。</p>
 */
public final class FirstPersonState {

    private static boolean renderingHand;

    private FirstPersonState() {
    }

    public static void begin() {
        renderingHand = true;
    }

    public static void end() {
        renderingHand = false;
    }

    public static boolean isRenderingHand() {
        return renderingHand;
    }
}
