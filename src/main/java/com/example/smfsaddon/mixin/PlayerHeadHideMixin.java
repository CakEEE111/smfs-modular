package com.example.smfsaddon.mixin;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 剥皮时隐藏原版头部。
 *
 * <h2>为什么放在 setAngles 末尾</h2>
 *
 * <p>之前在 {@code PlayerEntityRenderer.render} 的开头改 {@code head.visible}，
 * 结果实测原版头还在 —— 因为 {@code render} 内部还会调 {@code animateModel}
 * 与 {@code setAngles}，那时机的可见性会被重新算一遍。</p>
 *
 * <p>{@code setAngles} 是"模型姿态算完、马上就要画"的那一刻，在这里改才有效。</p>
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerHeadHideMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void smfsaddon$hideHeadWhenShed(LivingEntity entity, float limbAngle, float limbDistance,
                                             float animationProgress, float headYaw, float headPitch,
                                             CallbackInfo callbackInfo) {
        PlayerEntityModel<?> self = (PlayerEntityModel<?>) (Object) this;
        boolean fleshRemoved = entity.hasStatusEffect(SmfsAddon.FLESH_REMOVED);
        boolean shed = fleshRemoved || entity.hasStatusEffect(SmfsAddon.SKIN_SHED);

        // 只处理"世界里那个玩家模型"。第一人称手臂用的是 ItemRenderer 里的另一份
        // PlayerEntityModel 实例，如果把手臂也藏了，第一人称就看不到手了（踩过）。
        // 第一人称手臂用的就是这个世界模型，正在画手时不要隐藏，否则看不到手
        if (com.example.smfsaddon.client.FirstPersonState.isRenderingHand()) {
            return;
        }

        // 剪皮：整个人换成骨架，所有部件（含外套/袖/裤）都藏起来
        self.head.visible = !shed;
        self.hat.visible = !shed;
        if (fleshRemoved) {
            self.body.visible = false;
            self.rightArm.visible = false;
            self.leftArm.visible = false;
            self.rightLeg.visible = false;
            self.leftLeg.visible = false;
            self.jacket.visible = false;
            self.rightSleeve.visible = false;
            self.leftSleeve.visible = false;
            self.rightPants.visible = false;
            self.leftPants.visible = false;
        }
    }
}
