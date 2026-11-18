package com.example.smfsaddon.client;

import com.example.smfsaddon.entity.PijiaguHallPhantomEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * 守护灵礼堂的渲染器。
 *
 * <p>实体渲染器传进来的矩阵原点就在实体脚下、单位是格，
 * 所以只需要按 1/16 × 缩放系数换算 —— 不用猜世界渲染事件的那套坐标。</p>
 */
public class HallPhantomEntityRenderer extends EntityRenderer<PijiaguHallPhantomEntity> {

    private static final float SCALE = 0.35F;
    private static boolean logged;

    public HallPhantomEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(PijiaguHallPhantomEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        if (!logged) {
            logged = true;
            com.example.smfsaddon.SmfsAddon.LOGGER.info(
                    "[smfsaddon] 守护灵渲染：render() 被调用，实体位置=({},{},{})",
                    String.format("%.1f", entity.getX()), String.format("%.1f", entity.getY()),
                    String.format("%.1f", entity.getZ()));
        }
        // 朝向由实体渲染器自己处理（它已经把矩阵转到实体 yaw），这里不再额外旋转
        HallPhantomRenderer.get().draw(matrices, vertexConsumers, light,
                0.0D, 0.0D, 0.0D, 0.0F, SCALE);
    }

    @Override
    public Identifier getTexture(PijiaguHallPhantomEntity entity) {
        return new Identifier("smfsaddon", "textures/entity/pijiagu_hall_atlas.png");
    }
}
