package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.SsjHairFeatureRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给玩家渲染器挂上金发图层。
 *
 * 只是"注册一个 FeatureRenderer"，不改变任何原有渲染逻辑。
 * 继承 LivingEntityRenderer 是为了能访问 protected 的 addFeature。
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerSsjHairMixin
        extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    protected PlayerSsjHairMixin(EntityRendererFactory.Context ctx,
                                 PlayerEntityModel<AbstractClientPlayerEntity> model,
                                 float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("TAIL"))
    private void smfsaddon$addHairLayer(EntityRendererFactory.Context ctx, boolean thinArms, CallbackInfo ci) {
        this.addFeature(new SsjHairFeatureRenderer(this));
        // 剪皮之后整具身体换成骨架
        this.addFeature(new com.example.smfsaddon.client.SkeletonBodyFeature(this));
        // G 技能的守护灵礼堂
        this.addFeature(new com.example.smfsaddon.client.HallGuardianFeature(this));
        // 剥皮时用老鼠头骨顶掉原版头部
        this.addFeature(new com.example.smfsaddon.client.SkinShedSkullFeature(this));
    }

}
