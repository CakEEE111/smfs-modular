package com.example.smfsaddon.mixin;

import com.example.smfsaddon.SmfsAddon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 骨架状态下，把"玩家皮肤"这个渲染图层换成骨头贴图。
 *
 * <h2>为什么拦在 RenderLayer 这一层</h2>
 *
 * <p>前两次都失败了：改 {@code player.getSkinTexture()} 的返回值没用，
 * 包 {@code renderFirstPersonItem} 的 {@code VertexConsumerProvider} 也没用 ——
 * 说明手臂那条路是<b>直接调用</b> {@code RenderLayer.getEntitySolid(皮肤)} 拿到图层再画。</p>
 *
 * <p>所以干脆拦最底层：只要请求的是"本地玩家的皮肤图层"，就换成骨头图层。
 * 物品、附魔光效、其他实体都不受影响。</p>
 */
@Mixin(RenderLayer.class)
public abstract class SkinLayerSwapMixin {

    /**
     * 手臂专用贴图：整张 64×64 铺满骨色。
     *
     * <p>不能直接用骨架贴图 —— 它只覆盖模型需要的那几个 UV 区域，
     * 换到玩家皮肤的手臂 UV 上会采到透明像素，结果就是"手臂看不见"（踩过）。</p>
     */
    private static final Identifier SKELETON_SKIN =
            new Identifier(SmfsAddon.MOD_ID, "textures/entity/skeleton_arm.png");

    @Inject(method = "getEntitySolid", at = @At("HEAD"), cancellable = true)
    private static void smfsaddon$swapSolid(Identifier texture,
                                            CallbackInfoReturnable<RenderLayer> callbackInfo) {
        if (smfsaddon$isLocalPlayerSkin(texture)) {
            callbackInfo.setReturnValue(RenderLayer.getEntitySolid(SKELETON_SKIN));
        }
    }

    @Inject(method = "getEntityTranslucent", at = @At("HEAD"), cancellable = true)
    private static void smfsaddon$swapTranslucent(Identifier texture, boolean affectsOutline,
                                                   CallbackInfoReturnable<RenderLayer> callbackInfo) {
        if (smfsaddon$isLocalPlayerSkin(texture)) {
            callbackInfo.setReturnValue(RenderLayer.getEntityTranslucent(SKELETON_SKIN));
        }
    }

    @Inject(method = "getEntityCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void smfsaddon$swapCutout(Identifier texture, boolean affectsOutline,
                                              CallbackInfoReturnable<RenderLayer> callbackInfo) {
        if (smfsaddon$isLocalPlayerSkin(texture)) {
            callbackInfo.setReturnValue(RenderLayer.getEntityCutoutNoCull(SKELETON_SKIN));
        }
    }

    @Inject(method = "getEntityCutout", at = @At("HEAD"), cancellable = true)
    private static void smfsaddon$swapCutoutPlain(Identifier texture,
                                                   CallbackInfoReturnable<RenderLayer> callbackInfo) {
        if (smfsaddon$isLocalPlayerSkin(texture)) {
            callbackInfo.setReturnValue(RenderLayer.getEntityCutoutNoCull(SKELETON_SKIN));
        }
    }

    @Inject(method = "getEntityCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"), cancellable = true)
    private static void smfsaddon$swapCutoutNoFlag(Identifier texture,
                                                    CallbackInfoReturnable<RenderLayer> callbackInfo) {
        if (smfsaddon$isLocalPlayerSkin(texture)) {
            callbackInfo.setReturnValue(RenderLayer.getEntityCutoutNoCull(SKELETON_SKIN));
        }
    }

    private static boolean debugged;
    private static int swapped;

    /** 每 120 次替换打一行，确认确实发生了 */
    private static void maybeReport() {
        if (swapped > 0 && swapped % 120 == 0) {
            SmfsAddon.LOGGER.info("[smfsaddon] 图层替换已生效：累计 {} 次", swapped);
        }
    }

    private static boolean smfsaddon$isLocalPlayerSkin(Identifier texture) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || texture == null) {
            return false;
        }
        boolean hasEffect = client.player.hasStatusEffect(SmfsAddon.FLESH_REMOVED);
        if (hasEffect) {
            swapped++;
            maybeReport();
        }
        if (hasEffect && !debugged && texture.toString().contains("skin")) {
            debugged = true;
            SmfsAddon.LOGGER.info("[smfsaddon] 图层诊断：请求={} 玩家皮肤={}",
                    texture, client.player.getSkinTexture());
        }
        return hasEffect && texture.equals(client.player.getSkinTexture());
    }
}
