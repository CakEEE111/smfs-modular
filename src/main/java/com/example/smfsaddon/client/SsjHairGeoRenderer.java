package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * 用 GeckoLib 渲染龙珠模组那套头发几何 —— 也就是龙珠模组自己的做法。
 *
 * 和之前"拍平成 ModelPart"的区别：
 *  - 保留 36 根骨骼的**父子层级**（head → gokucabello → pelo1..34），
 *    每根骨骼的 pivot / rotation 都按原样生效；
 *  - 立方体级的 rotation 也由 GeckoLib 的原生解析处理，不需要我们自己把旋转塞进枢轴；
 *  - 法线、明暗、UV 展开全部走 GeckoLib 的渲染管线。
 *
 * 坐标系：geo 是 Bedrock 空间（y 向上、单位 1/16、骨骼与立方体都用**绝对坐标**），
 * 而 feature renderer 拿到的 matrixStack 已经是 MC 模型空间（y 向下、头颈在 y=0）。
 * 所以这里做三件事：
 * <pre>
 *   y' = -y + 1.5   Bedrock y 向上 → MC y 向下；+1.5 把颈部 24/16 抬到 0
 *   x' = -x         GeckoLib 烘焙时已对 x 取负（origin = -(x+size)/16），渲染时再取负还原
 *   z' = -z         龙珠模组几何正面朝 +z，MC 正面朝 -z，见 Z_FLIP
 * </pre>
 * 注意 GeckoLib 的骨骼 pivot 是绝对的，它靠着「translateToPivotPoint / translateAwayFromPivotPoint
 * 互相抵消」把整棵树保持在同一空间里，所以这里**不需要**再补 head 骨骼的 (0,24,0) 偏移，
 * 补了反而会多抬 1.5 格（踩过一次）。
 */
public final class SsjHairGeoRenderer implements GeoRenderer<GhostEntity> {

    private static final Identifier MODEL =
            new Identifier(SmfsAddon.MOD_ID, "geo/entity/ssj_hair.geo.json");
    private static final Identifier TEXTURE =
            new Identifier(SmfsAddon.MOD_ID, "textures/entity/ssj_hair.png");

    private static final SsjHairGeoRenderer INSTANCE = new SsjHairGeoRenderer();

    /** 只报一次模型缺失，避免每帧刷屏 */
    private static boolean warned;

    /**
     * 垂直对齐偏移（单位：格）。
     * 推导：头部在 Bedrock 里颈部 y=1.5、头顶 y=2.0；在 MC 模型空间里颈部 y=0、头顶 y=-0.5。
     * 映射 y' = -y + c，代入颈部得 c = 1.5。验算头顶：-2.0 + 1.5 = -0.5 ✓
     * 若要微调，只改这一个数：正值下移，负值上移。
     */
    private static final float Y_OFFSET = 1.5f;

    /**
     * 前后偏移（单位：格）。正值往后、负值往前。
     * 注意这是"位移"不是"翻转"：如果刘海整个盖在脸上（前后朝向反了），调这个没用，
     * 需要翻转 z 轴 —— 那是另一回事，见 Z_FLIP。
     */
    private static final float Z_OFFSET = 0.0f;

    /**
     * 是否翻转前后轴。true 时等价于 matrices.scale(-1,-1,-1)，
     * 会让模型前后朝向反一次（刘海从脸前挪到脑后）。
     *
     * <p>实测命中的情况：龙珠模组这套几何的「正面」在 geo 空间是 +z，
     * 而 MC 模型空间正面是 -z（GeckoLib 烘焙时只对 x 取负，不动 z），
     * 所以必须再翻一次 z，刘海才会在脸前。
     */
    private static final boolean Z_FLIP = true;

    /**
     * 头部俯仰 / 左右转的符号。
     *
     * <p>为什么写死符号而不是直接用 headYaw/headPitch：z 翻转（diag(1,1,-1)）是反射，
     * 反射会把**绕 x、绕 y 的旋转方向一起反掉**（绕 z 不受影响）。
     * 也就是说打开 Z_FLIP 之后，同样的角度数会让头发的转头方向反过来。
     * 这两个常量就是对消这一点的：Z_FLIP 生效时取 (−1, +1)，关闭时取 (+1, −1)。
     *
     * <p>若发现「玩家向左看、头发向右转」，只改这两个数即可。
     */
    private static final float PITCH_SIGN = -1.0f;
    private static final float YAW_SIGN = 1.0f;

    private final GeoModel<GhostEntity> model = new GeoModel<>() {
        @Override
        public Identifier getModelResource(GhostEntity animatable) {
            return MODEL;
        }

        @Override
        public Identifier getTextureResource(GhostEntity animatable) {
            return TEXTURE;
        }

        @Override
        public Identifier getAnimationResource(GhostEntity animatable) {
            return null;
        }
    };

    private SsjHairGeoRenderer() {
    }

    public static SsjHairGeoRenderer get() {
        return INSTANCE;
    }

    @Override
    public GeoModel<GhostEntity> getGeoModel() {
        return model;
    }

    @Override
    public GhostEntity getAnimatable() {
        return null;
    }

    @Override
    public Identifier getTextureLocation(GhostEntity animatable) {
        return TEXTURE;
    }

    @Override
    public RenderLayer getRenderType(GhostEntity animatable, Identifier texture,
                                     VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    @Override
    public void updateAnimatedTextureFrame(GhostEntity animatable) {
        // 没有动画贴图，留空
    }

    @Override
    public void fireCompileRenderLayersEvent() {
        // 没有额外渲染层
    }

    @Override
    public boolean firePreRenderEvent(MatrixStack poseStack, BakedGeoModel model,
                                      VertexConsumerProvider bufferSource, float partialTick, int packedLight) {
        return true;   // 不派发事件，直接放行渲染
    }

    @Override
    public void firePostRenderEvent(MatrixStack poseStack, BakedGeoModel model,
                                    VertexConsumerProvider bufferSource, float partialTick, int packedLight) {
        // 不派发任何事件
    }

    /** 头部朝向（角度制）跟随玩家头部 */
    public void renderHair(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                           float headYaw, float headPitch) {
        BakedGeoModel baked;
        try {
            baked = model.getBakedModel(MODEL);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                SmfsAddon.LOGGER.error("[smfsaddon] 头发模型未进 GeckoLib 缓存，请检查 geo 路径", e);
            }
            return;
        }
        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
        VertexConsumer buffer = buffers.getBuffer(layer);

        baked.getBone("head").ifPresent(head -> {
            head.setRotX(headPitch * PITCH_SIGN * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(headYaw * YAW_SIGN * MathHelper.RADIANS_PER_DEGREE);
        });

        matrices.push();
        // 先按 Y_OFFSET 平移，再翻转 y 轴（Bedrock 向上 / MC 模型空间向下）。
        // 顺序必须是"先平移后翻转"：写成 scale 在前的话平移量会被一起取反。
        matrices.translate(0.0f, Y_OFFSET, Z_OFFSET);
        // y：Bedrock y 向上 → MC 模型空间 y 向下
        // x：GeckoLib 烘焙时对 x 取负（origin = -(x+size)/16）
        matrices.scale(-1.0f, -1.0f, Z_FLIP ? -1.0f : 1.0f);

        for (GeoBone bone : baked.topLevelBones()) {
            renderRecursively(matrices, null, bone, layer, buffers, buffer, false,
                    0.0f, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        matrices.pop();
    }
}
