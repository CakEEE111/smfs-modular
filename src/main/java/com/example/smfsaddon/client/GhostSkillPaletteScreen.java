package com.example.smfsaddon.client;

import com.example.smfsaddon.item.GhostNames;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 灵异技能面板：列出手上融合的鬼所带的技能，点一下就放。
 *
 * <h2>技能从哪来</h2>
 *
 * <p>见 {@link GhostSkillChannels}：扫服务端声明的 {@code _skill} 通道，不扫方法名。
 * 本体自己命名不统一（鬼血/鬼湖的逻辑写在包里、静悄悄的 handler 名字完全不符合约定、
 * 完整鬼影共用鬼影头的包），只有通道才是全集。
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
    private record Row(String ghostType, GhostSkillChannels.Entry entry) {
    }

    private final List<Row> rows;
    private int page = 0;
    private int rowsPerPage = MIN_ROWS;

    /** 打开这块面板的那件物品：只列它自己绑定的鬼 */
    private final ItemStack source;

    /** 打开时刻：挡掉「按住 X 不放 → 键重复事件立刻把面板关掉」 */
    private final long openedAt = System.currentTimeMillis();

    public GhostSkillPaletteScreen(ItemStack source) {
        super(Text.literal("灵异技能"));
        this.source = source;
        this.rows = buildRows(source);
    }

    /**
     * 只读这一件物品上的鬼。
     *
     * <h2>为什么不扫整个背包</h2>
     *
     * <p>背包里可能同时放好几把融合武器，扫背包会把它们的技能混在一起，
     * 玩家分不清"点这个会不会用到另一把剑上的鬼"。所以面板跟物品走：
     * 拿哪件按 X，就只看那一件。
     */
    private static List<Row> buildRows(ItemStack source) {
        List<Row> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }

        LinkedHashSet<String> types = new LinkedHashSet<>(
                com.example.smfsaddon.item.ItemGhostHost.boundGhostTypes(source));

        for (String ghostType : types) {
            var entries = GhostSkillChannels.entries(ghostType);
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
                                Text.literal("§8" + GhostNames.of(row.ghostType()) + "（无主动技能）"),
                                button -> { })
                        .dimensions(this.width / 2 - 100, y, 200, 20)
                        .build());
                none.active = false;
            } else {
                GhostSkillChannels.Entry entry = row.entry();
                this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("§f" + entry.label()),
                                button -> {
                                    // 直接发本体/模组自己的技能包：冷却、资格、复苏平衡都在包里
                                    GhostSkillChannels.dispatch(entry);
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 16, 0xFFFFFF);
        // 标题下方标出这块面板属于哪件物品，背包里有多把融合武器时不会看混
        if (this.source != null && !this.source.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§8" + this.source.getName().getString()),
                    this.width / 2, 29, 0x888888);
        }

        if (this.rows.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§7这件物品上没有融合厉鬼"),
                    this.width / 2, 54, 0xAAAAAA);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 同一个键再按一次就关掉。
        // 界面打开时按键事件被 Screen 接管，KeyBinding.wasPressed() 收不到，
        // 所以要和当前绑定键的原始键码比对（跟着玩家的改键走，不写死 X）。
        var bound = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
                .getBoundKeyOf(SmfsAddonClient.SKILL_PALETTE_KEY);
        if (bound.getCategory() == net.minecraft.client.util.InputUtil.Type.KEYSYM
                && bound.getCode() == keyCode) {
            // 刚打开 250ms 内的按键算「开面板的那一下」，不关
            if (System.currentTimeMillis() - this.openedAt > 250L) {
                this.close();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
