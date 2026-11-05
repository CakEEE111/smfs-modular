package com.example.smfsaddon.mixin;

import com.example.smfsaddon.fusion.FusionOpen;
import com.xie.smfs.client.screen.GhostControlScreen;
import com.xie.smfs.event.screen.GhostControlScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在本体的灵异主界面里加一个子入口。
 *
 * <p>本体的子入口不是标准按钮控件，而是「render 里手绘方块 + mouseClicked 里判区域」，
 * 每个入口一个 {@code drawXxxButton}。这里照抄同样的做法：
 * 挂在 render 末尾画、挂在 mouseClicked 开头判。
 *
 * <p>继承 {@code HandledScreen} 是为了直接拿到 {@code x} / {@code y} / {@code textRenderer}
 * 这三个 protected 字段 —— 用 {@code @Shadow} 去影子继承来的字段会解析不到目标。
 *
 * <p>位置放在 {@code (+200, +183)}：本体已占用 +8 / +43 / +78 / +113 / +148，
 * 每个 25×25、间隔 35，+183 是下一个空位。
 */
@Mixin(GhostControlScreen.class)
public abstract class GhostControlScreenMixin
        extends HandledScreen<GhostControlScreenHandler> {

    private static final int SMFSADDON_X = 200;
    private static final int SMFSADDON_Y = 183;
    private static final int SMFSADDON_SIZE = 25;

    public GhostControlScreenMixin(GhostControlScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void smfsaddon$drawFusionEntry(DrawContext context, int mouseX, int mouseY,
                                           float delta, CallbackInfo callbackInfo) {
        int left = this.x + SMFSADDON_X;
        int top = this.y + SMFSADDON_Y;
        boolean hovered = isInside(mouseX, mouseY, left, top);

        context.fill(left, top, left + SMFSADDON_SIZE, top + SMFSADDON_SIZE,
                hovered ? 0xFF2A2A3A : 0xFF1A1A22);
        context.drawBorder(left, top, SMFSADDON_SIZE, SMFSADDON_SIZE,
                hovered ? 0xFFFFFFFF : 0xFF555555);

        String label = "装置";
        int textWidth = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label,
                left + (SMFSADDON_SIZE - textWidth) / 2,
                top + (SMFSADDON_SIZE - 9) / 2,
                0xFFFFFF, false);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void smfsaddon$clickFusionEntry(double mouseX, double mouseY, int button,
                                            CallbackInfoReturnable<Boolean> callbackInfo) {
        int left = this.x + SMFSADDON_X;
        int top = this.y + SMFSADDON_Y;
        if (!isInside(mouseX, mouseY, left, top)) {
            return;
        }

        // 带服务端槽位的界面必须由服务端打开，客户端只负责请求
        ClientPlayNetworking.send(FusionOpen.ID, PacketByteBufs.empty());
        callbackInfo.setReturnValue(true);
    }

    private static boolean isInside(double mouseX, double mouseY, int left, int top) {
        return mouseX >= left && mouseX <= left + SMFSADDON_SIZE
                && mouseY >= top && mouseY <= top + SMFSADDON_SIZE;
    }
}
