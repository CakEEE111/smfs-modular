package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * 守护灵礼堂 —— 挂在玩家渲染器上的图层。
 *
 * <h2>为什么用 FeatureRenderer</h2>
 *
 * <p>世界渲染事件和独立实体渲染器都试过：日志证明 render() 被调用了、
 * 坐标也对，但画面就是空的 —— 说明问题在于那两处矩阵空间的单位换算。
 * 而 FeatureRenderer 这条路是<b>已验证能画</b>的（骨架的头骨就用它），
 * 它的矩阵就是玩家模型的模型空间，单位和小人一致，不需要再猜。</p>
 */
public class HallGuardianFeature extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {

    public HallGuardianFeature(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!HallPhantomClient.isOwner(entity)) {
            return;
        }
        try {
            matrices.push();
            // 0.35 倍 ≈ 1.4 格高；y 下移 1.5 格对齐脚底；z 正方向 = 身后 2 格
            HallPhantomRenderer.get().drawInModelSpace(matrices, vertexConsumers, light,
                    0.85F, 1.2F, 2.0F);
            matrices.pop();
        } catch (Throwable throwable) {
            SmfsAddon.LOGGER.error("[smfsaddon] 守护灵礼堂渲染失败", throwable);
        }
    }
}
