package com.example.smfsaddon.ghost.module;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * 模块调试观察口。
 *
 * <p>写模块最常见的困境不是「跑不通」，而是 <b>「看不出跑没跑通」</b>：
 * 效果加上了、状态改对了，但外部一点动静都没有；
 * 又或者效果确实生效了，只是测试条件让人感觉不到
 * （创造模式飞行时缓慢无效，就是这么被漏掉的）。
 *
 * <p>所以框架提供一个开关式的观察口：模块里随手写
 * {@code GhostDebug.notice(player, "被压住了")}，
 * 默认什么都不发生，需要时用 {@code /ghostmodules debug} 打开。
 *
 * <p>这样调试用的可见性可以留在代码里，而不会污染正式玩法。
 */
public final class GhostDebug {

    private static boolean enabled = false;

    private GhostDebug() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 切换开关，返回切换后的状态。
     */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /**
     * 向玩家动作栏发一条调试提示。开关关闭时什么都不做，
     * 所以调用方不需要自己判断。
     */
    public static void notice(PlayerEntity player, String message) {
        if (!enabled || player == null) {
            return;
        }
        player.sendMessage(Text.literal("§8[灵异] " + message), true);
    }
}
