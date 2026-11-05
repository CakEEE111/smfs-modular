package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 超级赛亚人金发 —— 几何与贴图都取自龙珠模组，不是手画的。
 *
 * <p>几何：`kakarot_ghost.geo.json` 里 head → gokucabello → pelo1..34 那套骨骼，
 * 原封不动抠出来存成 `assets/smfsaddon/geo/entity/ssj_hair.geo.json`，
 * 由 GeckoLib 自己解析、自己渲染（= 龙珠模组同款做法）。
 * <p>贴图：龙珠模组 `saga_goku_end_ssj.png`（超赛金发版）。
 *
 * <p>为什么挂在 FeatureRenderer 上：本特效属于**玩家**（变身 buff 在玩家身上），
 * 不是鬼实体。FeatureRenderer 的回调发生在「模型空间、头部已就位」的时刻，
 * 这是唯一能正确跟着头转的位置。曾经在 LivingEntityRenderer#render 的 TAIL 注入，
 * 那时模型矩阵已 pop，画出来是一团贴在地上的黑块。
 *
 * <p>光照必须给满亮度：鬼域会把环境光压到极暗，用传入的 light 会渲染成一块黑。
 */
public class SsjHairFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    public SsjHairFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity entity, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!entity.hasStatusEffect(SmfsAddon.SUPER_SAIYAN)) {
            return;
        }

        SsjHairGeoRenderer.get().renderHair(matrices, vertexConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, headYaw, headPitch);
    }
}
