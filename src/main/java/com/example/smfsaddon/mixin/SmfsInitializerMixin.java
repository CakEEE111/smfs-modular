package com.example.smfsaddon.mixin;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.Smfs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在本体初始化结束时补做内容注册。
 *
 * <p>解决的问题：Fabric <b>不保证</b> {@code main} 入口按 {@code depends} 顺序执行。
 * 实测同一个工程两次启动，一次 smfs 先、一次 smfsaddon 先 —— 后者会让
 * {@code GhostAPI} 尚未注册，卡卡罗特内容注册被跳过，
 * 连带掉落表、成就、状态效果全部解析失败。
 *
 * <p>与其赌执行顺序，不如把「本体确实初始化完了」变成确定性事件：
 * 在本体 {@code onInitialize} 的末尾回调一次。
 *
 * <p>为什么不用 {@code ServerLifecycleEvents}：那时注册表已冻结，
 * 无法再注册实体与物品。
 */
@Mixin(Smfs.class)
public abstract class SmfsInitializerMixin {

    /**
     * {@code onInitialize} 是本体自己的方法，不是 Minecraft 的方法，
     * 没有混淆映射可查，因此必须 {@code remap = false}，
     * 否则注解处理器会报 "Unable to locate obfuscation mapping"。
     */
    @Inject(method = "onInitialize", at = @At("TAIL"), remap = false)
    private void smfsaddon$afterSmfsInit(CallbackInfo callbackInfo) {
        SmfsAddon.onSmfsReady();
    }
}
