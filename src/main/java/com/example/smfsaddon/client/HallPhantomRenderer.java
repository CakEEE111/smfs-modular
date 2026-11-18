package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * 把礼堂模型画在世界里（纯幻影，没有实体）。
 *
 * <p>矩阵在世界渲染事件里没有 1/16 换算，所以这里要自己补 ——
 * 这和之前给厉鬼画头骨时踩过的坑一样。</p>
 */
public final class HallPhantomRenderer implements GeoRenderer<com.xie.smfs.entity.GhostEntity> {

    private static final Identifier MODEL =
            new Identifier(SmfsAddon.MOD_ID, "geo/entity/pijiagu_hall_v2.geo.json");
    private static final Identifier TEXTURE =
            new Identifier(SmfsAddon.MOD_ID, "textures/entity/pijiagu_hall_atlas.png");

    private static final HallPhantomRenderer INSTANCE = new HallPhantomRenderer();
    private static boolean warned;
    private static boolean diagnosed;

    private final GeoModel<com.xie.smfs.entity.GhostEntity> model = new GeoModel<>() {
        @Override public Identifier getModelResource(com.xie.smfs.entity.GhostEntity a) { return MODEL; }
        @Override public Identifier getTextureResource(com.xie.smfs.entity.GhostEntity a) { return TEXTURE; }
        @Override public Identifier getAnimationResource(com.xie.smfs.entity.GhostEntity a) { return null; }
    };

    private HallPhantomRenderer() {
    }

    public static HallPhantomRenderer get() {
        return INSTANCE;
    }

    @Override public GeoModel<com.xie.smfs.entity.GhostEntity> getGeoModel() { return model; }
    @Override public com.xie.smfs.entity.GhostEntity getAnimatable() { return null; }
    @Override public Identifier getTextureLocation(com.xie.smfs.entity.GhostEntity a) { return TEXTURE; }
    @Override public void updateAnimatedTextureFrame(com.xie.smfs.entity.GhostEntity a) { }
    @Override public void fireCompileRenderLayersEvent() { }
    @Override public boolean firePreRenderEvent(MatrixStack p, BakedGeoModel m, VertexConsumerProvider b,
                                                float t, int l) { return false; }
    @Override public void firePostRenderEvent(MatrixStack p, BakedGeoModel m, VertexConsumerProvider b,
                                              float t, int l) { }

    @Override
    public RenderLayer getRenderType(com.xie.smfs.entity.GhostEntity a, Identifier texture,
                                     VertexConsumerProvider b, float partialTick) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    /**
     * 在"玩家模型空间"里画（父矩阵已经含 1/16）。
     *
     * <p>这里只做 y 轴翻转，不再除 16 —— 和骨架身体那次踩的坑完全一样。</p>
     */
    public void drawInModelSpace(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                 float scale, float offsetYBlocks, float offsetZBlocks) {
        BakedGeoModel baked;
        try {
            baked = model.getBakedModel(MODEL);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                SmfsAddon.LOGGER.error("[smfsaddon] 礼堂模型加载失败", e);
            }
            return;
        }
        if (!warned) {
            warned = true;
            SmfsAddon.LOGGER.info("[smfsaddon] 礼堂模型已加载：骨骼={}",
                    baked.topLevelBones().size());
        }
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
        VertexConsumer buffer = buffers.getBuffer(layer);
        matrices.push();
        // 单位是"格"：y 轴稍后会被翻转，所以正数实际是往下（和骨架身体同一个规律）
        matrices.translate(0.0F, offsetYBlocks, offsetZBlocks);
        matrices.scale(-scale, -scale, scale);
        for (var bone : baked.topLevelBones()) {
            renderRecursively(matrices, null, bone, layer, buffers, buffer, false,
                    0.0F, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        matrices.pop();
    }

    public void draw(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                     double x, double y, double z, float yawDegrees, float scale) {
        BakedGeoModel baked;
        try {
            baked = model.getBakedModel(MODEL);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                SmfsAddon.LOGGER.error("[smfsaddon] 礼堂幻影模型加载失败", e);
            }
            return;
        }

        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
        VertexConsumer buffer = buffers.getBuffer(layer);

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y
                .rotationDegrees(-yawDegrees));
        // 世界渲染没有 1/16 换算，必须自己补
        float unit = scale / 16.0F;
        matrices.scale(-unit, -unit, unit);
        for (var bone : baked.topLevelBones()) {
            renderRecursively(matrices, null, bone, layer, buffers, buffer, false,
                    0.0F, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        matrices.pop();
    }
}
