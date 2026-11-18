package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 被剥皮时把玩家的头换成老鼠头骨。
 *
 * <p>头骨几何直接取自"皮嫁骨·新郎"模型里的 {@code head_g}（连带下颌、耳、须），
 * 缩放到贴合玩家 8×8×8 的头部，贴图也沿用新郎那张 —— 所以它和本体怪物是同一副骨。</p>
 *
 * <p>原版头部由 {@code PlayerSsjHairMixin} 在渲染前隐藏，这里只负责画头骨。</p>
 */
public class SkinShedSkullFeature extends FeatureRenderer<AbstractClientPlayerEntity,
        PlayerEntityModel<AbstractClientPlayerEntity>> {

    /** 一旦渲染出错就永久关闭，避免每帧刷屏并污染渲染状态 */
    private boolean broken;
    private boolean entered;

    public SkinShedSkullFeature(FeatureRendererContext<AbstractClientPlayerEntity,
            PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!entity.hasStatusEffect(SmfsAddon.SKIN_SHED)
                || entity.hasStatusEffect(SmfsAddon.FLESH_REMOVED)) {
            return;   // 已经整具换骨了，头由骨架自己带
        }
        // 渲染期抛异常会污染顶点缓冲，导致后续所有文字变成方框 ——
        // 所以这里必须兜住，出错就永久跳过这一层。
        if (broken) {
            return;
        }
        if (!entered) {
            entered = true;
            SmfsAddon.LOGGER.info("[smfsaddon] 头骨图层开始渲染（首次）");
        }
        try {
            SkinShedSkullRenderer.get().renderSkull(matrices, vertexConsumers, light,
                    this.getContextModel().head);
        } catch (Throwable throwable) {
            broken = true;
            SmfsAddon.LOGGER.error("[smfsaddon] 头骨渲染失败，已停用该图层（异常类型：{}）",
                    throwable.getClass().getName(), throwable);
        }
    }
}
