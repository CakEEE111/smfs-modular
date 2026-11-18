package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.CameraLockClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 婚礼过场镜头。
 *
 * <p>照本体的写法：<b>取消</b>原版对相机的计算，位置与朝向全部自己给 ——
 * 之前的版本只在原版算完之后改朝向，结果相机还挂在玩家眼睛上，
 * 玩家站在礼堂模型里时镜头就卡在梁柱之间。</p>
 *
 * <p>现在机位固定在礼堂外侧、比礼堂中心高 7 格，俯视礼堂正中，
 * 无论玩家站在哪，画面都是"正对着礼堂看完整场婚礼"。</p>
 */
@Mixin(Camera.class)
public abstract class CameraLockMixin {

    @Shadow private Vec3d pos;
    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow private boolean thirdPerson;
    @Shadow private boolean ready;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$ceremonyCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                           boolean inverseView, float tickDelta, CallbackInfo callbackInfo) {
        if (!CameraLockClient.cameraActive()) {
            return;
        }
        Vec3d cameraPos = CameraLockClient.cameraPos();
        float[] rotation = CameraLockClient.rotationFrom(cameraPos);
        if (cameraPos == null || rotation == null) {
            return;
        }

        callbackInfo.cancel();
        this.pos = cameraPos;
        this.yaw = rotation[0];
        this.pitch = rotation[1];
        this.thirdPerson = true;   // 第一人称手臂会挡画面，当作第三人称处理
        this.ready = true;
    }
}
