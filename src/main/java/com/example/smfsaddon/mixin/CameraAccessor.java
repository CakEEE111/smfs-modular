package com.example.smfsaddon.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 只为调用 {@code Camera#setRotation} —— 它是 protected，mixin 里不能直接调。
 */
@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setRotation")
    void smfsaddon$setRotation(float yaw, float pitch);
}
