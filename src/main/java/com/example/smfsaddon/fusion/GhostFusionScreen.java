package com.example.smfsaddon.fusion;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * 灵异装置的界面：左边放物品，右边放厉鬼，点「合入」把规律打进去。
 *
 * <p>不引入贴图，背景用纯色块 + 边框画 —— 少一份资源就少一处「贴图 404 变紫黑方块」。
 * 界面重点是三个数字：压制、需求、成功率。
 */
public class GhostFusionScreen extends HandledScreen<GhostFusionScreenHandler> {

    private static final int PANEL_COLOR = 0xF0101014;
    private static final int BORDER_COLOR = 0xFF6A6A80;

    public GhostFusionScreen(GhostFusionScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - this.backgroundWidth) / 2;
        int top = (this.height - this.backgroundHeight) / 2;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("合入"),
                        button -> ClientPlayNetworking.send(FusionGraft.ID, PacketByteBufs.empty()))
                .dimensions(left + 100, top + 50, 68, 18)
                .build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = (this.width - this.backgroundWidth) / 2;
        int top = (this.height - this.backgroundHeight) / 2;

        context.fill(left, top, left + this.backgroundWidth, top + this.backgroundHeight, PANEL_COLOR);
        context.drawBorder(left, top, this.backgroundWidth, this.backgroundHeight, BORDER_COLOR);

        // 两个槽的框，位置与 ScreenHandler 里的 (62,24) / (98,24) 对应
        context.drawBorder(left + 61, top + 23, 18, 18, BORDER_COLOR);
        context.drawBorder(left + 97, top + 23, 18, 18, BORDER_COLOR);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int left = (this.width - this.backgroundWidth) / 2;
        int top = (this.height - this.backgroundHeight) / 2;

        context.drawText(this.textRenderer, "灵异装置", left + 8, top + 6, 0xFFD0D0E0, false);
        context.drawText(this.textRenderer, "物品", left + 56, top + 12, 0xFF9A9AB0, false);
        context.drawText(this.textRenderer, "厉鬼", left + 92, top + 12, 0xFF9A9AB0, false);

        context.drawText(this.textRenderer,
                "压制  " + format(handler.clientSuppression()),
                left + 8, top + 48, 0xFF9AD0FF, false);
        context.drawText(this.textRenderer,
                "需求  " + format(handler.clientRequired())
                        + "（" + handler.clientModuleCount() + " 条）",
                left + 8, top + 60, 0xFFFFC080, false);

        int state = handler.clientState();
        String status = switch (state) {
            case GhostFusionScreenHandler.STATE_SAFE -> "§a可压制";
            case GhostFusionScreenHandler.STATE_RISKY -> "§c成功率 " + handler.clientChancePercent() + "%";
            default -> "§7放入物品与厉鬼";
        };
        context.drawText(this.textRenderer, status, left + 8, top + 72, 0xFFFFFFFF, false);

        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * 不用原版左上角标题，自己在 render 里画。
     */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    private static String format(double value) {
        return String.format("%.0f", value);
    }
}
