package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 皮被剪掉之后，把整个身体换成骨架。
 *
 * <p>原版各部件由 {@code PlayerHeadHideMixin} 在 {@code setAngles} 末尾隐藏，
 * 这里只负责画骨架；骨架的四肢角度由 {@link SkeletonBodyRenderer} 从原版部位抄过来，
 * 所以摆臂摆腿是自动的。</p>
 */
public class SkeletonBodyFeature extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {

    private boolean broken;

    public SkeletonBodyFeature(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (broken || !entity.hasStatusEffect(SmfsAddon.FLESH_REMOVED)) {
            return;
        }
        try {
            SkeletonBodyRenderer.get().renderBody(matrices, vertexConsumers, light,
                    this.getContextModel());
        } catch (Throwable throwable) {
            broken = true;
            SmfsAddon.LOGGER.error("[smfsaddon] 骨架渲染失败，已停用该图层", throwable);
        }
    }
}
