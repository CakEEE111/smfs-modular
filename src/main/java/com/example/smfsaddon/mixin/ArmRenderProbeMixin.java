package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.FirstPersonState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给"第一人称手臂渲染"打上标记。
 *
 * <p>两条路径都要覆盖：空手 {@code renderArm}、拿东西 {@code renderArmHoldingItem}。</p>
 */
@Mixin(net.minecraft.client.render.item.HeldItemRenderer.class)
public abstract class ArmRenderProbeMixin {

    @Inject(method = "renderArm", at = @At("HEAD"))
    private void smfsaddon$armBegin(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    int light, Arm arm, CallbackInfo callbackInfo) {
        FirstPersonState.begin();
    }

    @Inject(method = "renderArm", at = @At("RETURN"))
    private void smfsaddon$armEnd(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  int light, Arm arm, CallbackInfo callbackInfo) {
        FirstPersonState.end();
    }

    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"))
    private void smfsaddon$holdingBegin(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        int light, float equipProgress, float swingProgress,
                                        Arm arm, CallbackInfo callbackInfo) {
        FirstPersonState.begin();
    }

    @Inject(method = "renderArmHoldingItem", at = @At("RETURN"))
    private void smfsaddon$holdingEnd(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      int light, float equipProgress, float swingProgress,
                                      Arm arm, CallbackInfo callbackInfo) {
        FirstPersonState.end();
    }
}
