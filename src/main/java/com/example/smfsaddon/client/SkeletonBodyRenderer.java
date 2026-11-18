package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * 剪掉皮之后的骨架身体。
 *
 * <h2>为什么不用 GeckoLib 的动画</h2>
 *
 * <p>骨架的六个骨骼名和原版玩家模型的六个部位一一对应
 * （head / body / right_arm / left_arm / right_leg / left_leg）。
 * 所以这里不用动画文件，而是每帧把原版部位的角度<b>抄</b>给骨骼：</p>
 *
 * <pre>
 *   bone.rotX = -head.pitch      // y 轴翻转会让 X / Y 旋转反向
 *   bone.rotY = -head.yaw
 *   bone.rotZ =  head.roll
 * </pre>
 *
 * <p>这样走路摆臂、抬头低头、潜行弯腰全部天然正确，不需要自己维护一套动画状态机。</p>
 */
public final class SkeletonBodyRenderer implements GeoRenderer<com.xie.smfs.entity.GhostEntity> {

    private static final Identifier MODEL =
            new Identifier(SmfsAddon.MOD_ID, "geo/entity/skeleton_horror.geo.json");
    private static final Identifier TEXTURE =
            new Identifier(SmfsAddon.MOD_ID, "textures/entity/skeleton_horror.png");

    private static final SkeletonBodyRenderer INSTANCE = new SkeletonBodyRenderer();
    /**
     * 位置微调，单位是 <b>1/16 格</b>（16 = 1 格），在 Bedrock 空间里：
     * +Y 向上、+X 向右（屏幕）、+Z 向后。
     *
     * <p>实测骨架整体偏高，所以 Y 给负数往下压。</p>
     */
    private static final float OFFSET_X = 0.0F;
    // 单位是格；因为后面还有一次 y 轴翻转，+Y 在这里实际是"往下"
    private static final float OFFSET_Y = 1.5F;
    private static final float OFFSET_Z = 0.0F;

    private static boolean warned;
    private static boolean logged;
    private static boolean loggedDetail;
    private static boolean loggedScale;
    private static int dbgTick;

    private final GeoModel<com.xie.smfs.entity.GhostEntity> model = new GeoModel<>() {
        @Override public Identifier getModelResource(com.xie.smfs.entity.GhostEntity animatable) { return MODEL; }
        @Override public Identifier getTextureResource(com.xie.smfs.entity.GhostEntity animatable) { return TEXTURE; }
        @Override public Identifier getAnimationResource(com.xie.smfs.entity.GhostEntity animatable) { return null; }
    };

    private SkeletonBodyRenderer() {
    }

    public static SkeletonBodyRenderer get() {
        return INSTANCE;
    }

    @Override public GeoModel<com.xie.smfs.entity.GhostEntity> getGeoModel() { return model; }
    @Override public com.xie.smfs.entity.GhostEntity getAnimatable() { return null; }
    @Override public Identifier getTextureLocation(com.xie.smfs.entity.GhostEntity animatable) { return TEXTURE; }

    @Override
    public RenderLayer getRenderType(com.xie.smfs.entity.GhostEntity animatable, Identifier texture,
                                     VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    /** 以下四个是 GeoRenderer 的抽象方法，我们不接 GeckoLib 的事件系统，全部留空 */

    @Override
    public void updateAnimatedTextureFrame(com.xie.smfs.entity.GhostEntity animatable) {
    }

    @Override
    public void fireCompileRenderLayersEvent() {
    }

    @Override
    public boolean firePreRenderEvent(MatrixStack poseStack,
                                      software.bernie.geckolib.cache.object.BakedGeoModel model,
                                      VertexConsumerProvider bufferSource, float partialTick,
                                      int packedLight) {
        return false;
    }

    @Override
    public void firePostRenderEvent(MatrixStack poseStack,
                                    software.bernie.geckolib.cache.object.BakedGeoModel model,
                                    VertexConsumerProvider bufferSource, float partialTick,
                                    int packedLight) {
    }

    /** 在玩家渲染器里调用：矩阵栈位于实体原点，MC 模型空间 */
    public void renderBody(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                           PlayerEntityModel<?> playerModel) {
        BakedGeoModel baked;
        try {
            baked = model.getBakedModel(MODEL);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                SmfsAddon.LOGGER.error("[smfsaddon] 骨架模型加载失败", e);
            }
            return;
        }
        if (!logged) {
            logged = true;
            int boneCount = baked.topLevelBones().size();
            SmfsAddon.LOGGER.info("[smfsaddon] 骨架开始渲染：骨骼={} 个（同名骨骼={}）",
                    boneCount, baked.getBone("head").isPresent() ? "有" : "无");
        }

        if (!loggedDetail) {
            loggedDetail = true;
            var m = matrices.peek().getPositionMatrix();
            SmfsAddon.LOGGER.info("[smfsaddon] 骨架诊断：矩阵位移=({},{},{}) 骨骼 head={} body={} rArm={} rLeg={}",
                    m.m30(), m.m31(), m.m32(),
                    baked.getBone("head").isPresent(), baked.getBone("body").isPresent(),
                    baked.getBone("right_arm").isPresent(), baked.getBone("right_leg").isPresent());
        }

        copyRotations(baked, "head", playerModel.head);
        copyRotations(baked, "body", playerModel.body);
        copyRotations(baked, "right_arm", playerModel.rightArm);
        copyRotations(baked, "left_arm", playerModel.leftArm);
        copyRotations(baked, "right_leg", playerModel.rightLeg);

        if (++dbgTick % 40 == 0) {
            SmfsAddon.LOGGER.info("[smfsaddon] 骨架诊断：右腿 pitch={} 左臂 pitch={} 头 pitch={}",
                    playerModel.rightLeg.pitch, playerModel.leftArm.pitch, playerModel.head.pitch);
        }
        copyRotations(baked, "left_leg", playerModel.leftLeg);

        RenderLayer layer = RenderLayer.getEntityCutoutNoCull(TEXTURE);
        VertexConsumer buffer = buffers.getBuffer(layer);

        matrices.push();
        // Bedrock（y 向上）→ MC 模型空间（y 向下）。
        // 注意：玩家渲染器传进来的矩阵**已经带了 1/16 的单位换算**，
        // 这里再除一次 16 就会缩成针尖大小（踩过）。
        // 位置微调放在翻转之前：这里是 Bedrock 空间，+Y 向上、单位 1/16 格
        matrices.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        if (!loggedScale) {
            loggedScale = true;
            var mm = matrices.peek().getPositionMatrix();
            SmfsAddon.LOGGER.info("[smfsaddon] 骨架矩阵：缩放 x={} y={} z={}",
                    mm.m00(), mm.m11(), mm.m22());
        }
        for (GeoBone bone : baked.topLevelBones()) {
            renderRecursively(matrices, null, bone, layer, buffers, buffer, false,
                    0.0F, light, OverlayTexture.DEFAULT_UV, 1.0F, 1.0F, 1.0F, 1.0F);
        }
        matrices.pop();
    }

    /**
     * 把原版部位的角度抄给同名骨骼。
     *
     * <p>之前用反射取 {@code pitch/yaw/roll}，运行期一直没生效（骨骼纹丝不动），
     * 改成直接访问字段 —— {@code ModelPart} 的这三个字段本来就是 public。</p>
     */
    private static void copyRotations(BakedGeoModel model, String boneName,
                                      net.minecraft.client.model.ModelPart part) {
        model.getBone(boneName).ifPresent(bone -> {
            // 父级曾经把 y 轴翻转，X / Y 方向的旋转方向会跟着反，所以取负
            bone.setRotX(-part.pitch);
            bone.setRotY(-part.yaw);
            bone.setRotZ(part.roll);
        });
    }
}
