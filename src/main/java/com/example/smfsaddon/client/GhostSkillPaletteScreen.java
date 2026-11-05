package com.example.smfsaddon.client;

import com.example.smfsaddon.item.ItemGhostHost;
import com.example.smfsaddon.skill.CastSkillPacket;
import com.example.smfsaddon.skill.GhostSkillTable;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 灵异技能面板：列出手上融合的鬼所带的技能，点一下就放。
 *
 * <h2>为什么必须分页</h2>
 *
 * <p>一只鬼最多有 N / G / V / J 四个技能，手上再合几只鬼，
 * 条目很容易超出屏幕 —— 早先直接往下堆，超出的按钮被挤出可视区，
 * 看起来就是「显示不全」。
 */
public class GhostSkillPaletteScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int MIN_ROWS = 4;

    /**
     * 一行：某只鬼的某个技能。
     *
     * <p>技能为 {@code null} 表示这只鬼没有主动技能，仍然占一行。
     */
    private record Row(String ghostType, GhostSkillTable.Entry entry) {
    }

    private final List<Row> rows;
    private int page = 0;
    private int rowsPerPage = MIN_ROWS;

    public GhostSkillPaletteScreen() {
        super(Text.literal("灵异技能"));
        this.rows = buildRows();
    }

    private static List<Row> buildRows() {
        List<Row> result = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return result;
        }

        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (Hand hand : Hand.values()) {
            ItemStack stack = client.player.getStackInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }
            types.addAll(ItemGhostHost.boundGhostTypes(stack));
        }

        for (String ghostType : types) {
            var entries = GhostSkillTable.entries(ghostType);
            if (entries.isEmpty()) {
                // 没有主动技能的鬼也要占一行，否则玩家会以为没合上
                result.add(new Row(ghostType, null));
                continue;
            }
            for (var entry : entries) {
                result.add(new Row(ghostType, entry));
            }
        }
        return result;
    }

    @Override
    protected void init() {
        super.init();

        // 上下各留出标题和翻页的位置
        this.rowsPerPage = Math.max(MIN_ROWS, (this.height - 110) / ROW_HEIGHT);
        int totalPages = Math.max(1, (this.rows.size() + rowsPerPage - 1) / rowsPerPage);
        this.page = Math.max(0, Math.min(this.page, totalPages - 1));

        int start = this.page * rowsPerPage;
        int end = Math.min(this.rows.size(), start + rowsPerPage);

        int y = 44;
        for (int i = start; i < end; i++) {
            Row row = this.rows.get(i);
            if (row.entry() == null) {
                ButtonWidget none = this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("§8" + row.ghostType() + "（无主动技能）"),
                                button -> { })
                        .dimensions(this.width / 2 - 100, y, 200, 20)
                        .build());
                none.active = false;
            } else {
                String ghostType = row.ghostType();
                String key = row.entry().key();
                this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("§f" + row.entry().label()),
                                button -> {
                                    cast(ghostType, key);
                                    this.close();
                                })
                        .dimensions(this.width / 2 - 100, y, 200, 20)
                        .build());
            }
            y += ROW_HEIGHT;
        }

        if (totalPages > 1) {
            int navY = this.height - 40;
            ButtonWidget prev = ButtonWidget.builder(Text.literal("◀ 上一页"), b -> {
                this.page--;
                this.clearAndInit();
            }).dimensions(this.width / 2 - 100, navY, 96, 20).build();
            prev.active = this.page > 0;
            this.addDrawableChild(prev);

            ButtonWidget next = ButtonWidget.builder(Text.literal("下一页 ▶"), b -> {
                this.page++;
                this.clearAndInit();
            }).dimensions(this.width / 2 + 4, navY, 96, 20).build();
            next.active = this.page < totalPages - 1;
            this.addDrawableChild(next);
        }
    }

    /**
     * 释放一个技能。
     *
     * <p>优先借本体自己的技能包：冷却、资格、复苏平衡都在那个包里，
     * 走这条路等于「玩家按了原键」，行为天然一致，也不怕本体加技能。
     * 只有本体没为这个技能开通道时，才回退到我们自己的反射通道
     * （那条路服务端会补一次通用冷却）。
     */
    private void cast(String ghostType, String key) {
        if (SkillChannels.dispatch(ghostType, key)) {
            return;
        }
        ClientPlayNetworking.send(CastSkillPacket.ID,
                PacketByteBufs.create().writeString(ghostType).writeString(key));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 20, 0xFFFFFF);

        if (this.rows.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§7手上没有融合厉鬼的物品"),
                    this.width / 2, 50, 0xAAAAAA);
        } else {
            int totalPages = Math.max(1, (this.rows.size() + rowsPerPage - 1) / rowsPerPage);
            if (totalPages > 1) {
                String info = "§8第 " + (this.page + 1) + " / " + totalPages + " 页";
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(info), this.width / 2, this.height - 54, 0x888888);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
