package com.example.smfsaddon.mixin;

import com.example.smfsaddon.client.CameraLockClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 婚礼期间剥夺玩家的一切操作。
 *
 * <p>照本体自己的写法（{@code ClientPlayerEntityMixin} 里那套）：在 tick 开头把
 * 移动输入清零、冲刺关掉、水平速度归零、位置钉回原位。这样按 WASD 不动、
 * 按空格不跳、按潜行也不下蹲。</p>
 *
 * <p>位置用客户端自己在收到锁定包时的坐标 —— 不需要服务端每 tick 同步。</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class CeremonyInputLockMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void smfsaddon$lockCeremonyInput(CallbackInfo callbackInfo) {
        if (!CameraLockClient.blockingInput()) {
            return;
        }
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;

        self.input.movementForward = 0.0F;
        self.input.movementSideways = 0.0F;
        self.input.jumping = false;
        self.input.sneaking = false;
        self.setSprinting(false);
        self.setVelocity(0.0D, self.getVelocity().y, 0.0D);

        var anchor = CameraLockClient.anchor();
        if (anchor != null) {
            self.setPosition(anchor.x, anchor.y, anchor.z);
        }
    }
}
