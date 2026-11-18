package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.CameraLockClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 婚礼期间连"攻击 / 使用 / 开背包"也不允许。
 *
 * <p>只锁移动是不够的：玩家还能挥剑、吃东西、按 E 打开背包看物品，
 * 那样"被强行按在席间"的感觉就散了。</p>
 */
@Mixin(MinecraftClient.class)
public abstract class CeremonyInteractLockMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$blockAttack(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (CameraLockClient.blockingInput()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$blockUse(CallbackInfo callbackInfo) {
        if (CameraLockClient.blockingInput()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$blockKeys(CallbackInfo callbackInfo) {
        if (CameraLockClient.blockingInput()) {
            callbackInfo.cancel();
        }
    }
}
