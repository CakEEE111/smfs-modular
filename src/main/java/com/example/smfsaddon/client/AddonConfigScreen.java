package com.example.smfsaddon.client;

import com.example.smfsaddon.config.AddonConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 配置界面：在灵异主界面的「装置」旁边多一个「配置」入口。
 *
 * <h2>为什么做成 ± 按钮而不是滑条</h2>
 *
 * <p>这些值的手感是"跳档"的：交错借 2 条还是 3 条、复苏 5% 还是 15%，
 * 中间那些小数没有意义。滑条会给出"可以精细调"的错觉，还要处理
 * "拖动中不生效、松手才生效"的时序。加减按钮相反：点一下结果立刻可见。
 *
 * <p>点「保存」写回 {@code config/smfsaddon.json} 并立刻应用，不需要重启游戏。
 */
public final class AddonConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LABEL_WIDTH = 108;
    private static final int BUTTON_WIDTH = 16;
    private static final int VALUE_WIDTH = 46;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    public AddonConfigScreen(Screen parent) {
        super(Text.literal("厉鬼模块化 · 配置"));
        this.parent = parent;
        buildRows();
    }

    private void buildRows() {
        AddonConfig config = AddonConfig.get();

        // 左列：灵异交错
        rows.add(Row.integer("简单 · 借规律", () -> config.crossing.easyRules,
                value -> config.crossing.easyRules = (int) value, 1, 0, 8));
        rows.add(Row.integer("普通 · 借规律", () -> config.crossing.normalRules,
                value -> config.crossing.normalRules = (int) value, 1, 0, 8));
        rows.add(Row.integer("困难 · 借规律", () -> config.crossing.hardRules,
                value -> config.crossing.hardRules = (int) value, 1, 0, 8));
        rows.add(Row.integer("普通 · 借机制", () -> config.crossing.normalMechanisms,
                value -> config.crossing.normalMechanisms = (int) value, 1, 0, 4));
        rows.add(Row.integer("困难 · 借机制", () -> config.crossing.hardMechanisms,
                value -> config.crossing.hardMechanisms = (int) value, 1, 0, 4));
        rows.add(Row.integer("出手间隔 (tick)", () -> config.crossing.allowCooldownTicks,
                value -> config.crossing.allowCooldownTicks = (int) value, 10, 0, 200));

        // 右列：融合与复苏
        rows.add(Row.decimal("灵异伤害窃取", () -> config.fusion.stealRatio,
                value -> config.fusion.stealRatio = (float) value, 0.05D, 0.01D, 1.0D));
        rows.add(Row.decimal("继承倍率 / 只", () -> config.fusion.inheritPerGhost,
                value -> config.fusion.inheritPerGhost = (float) value, 0.25D, 0.0D, 5.0D));
        rows.add(Row.integer("融合入门费", () -> (int) config.fusion.entryCost,
                value -> config.fusion.entryCost = value, 10, 0, 1000));
        rows.add(Row.decimal("复苏基础概率", () -> config.escape.baseChance,
                value -> config.escape.baseChance = (float) value, 0.05D, 0.0D, 1.0D));
        rows.add(Row.decimal("复苏每次叠加", () -> config.escape.stepChance,
                value -> config.escape.stepChance = (float) value, 0.05D, 0.0D, 1.0D));
        rows.add(Row.toggle("失败复苏", () -> config.escape.enabled,
                value -> config.escape.enabled = value));
    }

    @Override
    protected void init() {
        int columnWidth = LABEL_WIDTH + BUTTON_WIDTH * 2 + VALUE_WIDTH + 12;
        int leftColumnX = this.width / 2 - columnWidth - 8;
        int rightColumnX = this.width / 2 + 8;
        int startY = 52;
        int half = (rows.size() + 1) / 2;

        for (int index = 0; index < rows.size(); index++) {
            boolean right = index >= half;
            int localIndex = right ? index - half : index;
            int x = right ? rightColumnX : leftColumnX;
            int y = startY + localIndex * ROW_HEIGHT;
            Row row = rows.get(index);

            addDrawableChild(row.minusButton(x + LABEL_WIDTH, y, this));
            addDrawableChild(row.plusButton(x + LABEL_WIDTH + BUTTON_WIDTH + VALUE_WIDTH + 4, y, this));
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> {
            AddonConfig.save();
            AddonConfig.apply();
            close();
        }).dimensions(this.width / 2 - 84, this.height - 30, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
                .dimensions(this.width / 2 + 4, this.height - 30, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 24, 0xFFFFFF);

        int columnWidth = LABEL_WIDTH + BUTTON_WIDTH * 2 + VALUE_WIDTH + 12;
        int leftColumnX = this.width / 2 - columnWidth - 8;
        int rightColumnX = this.width / 2 + 8;
        int startY = 52;
        int half = (rows.size() + 1) / 2;

        for (int index = 0; index < rows.size(); index++) {
            boolean right = index >= half;
            int localIndex = right ? index - half : index;
            int x = right ? rightColumnX : leftColumnX;
            int y = startY + localIndex * ROW_HEIGHT;
            Row row = rows.get(index);

            context.drawTextWithShadow(this.textRenderer, row.label, x, y + 5, 0xFFCCCCCC);
            String value = row.text();
            int valueX = x + LABEL_WIDTH + BUTTON_WIDTH + 2;
            context.drawTextWithShadow(this.textRenderer, value,
                    valueX + (VALUE_WIDTH - this.textRenderer.getWidth(value)) / 2,
                    y + 5, 0xFFFFFFFF);
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("改完点保存立即生效，写入 config/smfsaddon.json"),
                this.width / 2, this.height - 46, 0x888888);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 值变了就整体重建：比逐个控件改文字、改回调可靠得多 */
    private void rebuild() {
        if (this.client != null) {
            this.client.setScreen(new AddonConfigScreen(this.parent));
        }
    }

    // ---------------------------------------------------------------- 行

    private static final class Row {

        private final String label;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double step;
        private final double min;
        private final double max;
        private final boolean integer;
        private final boolean toggle;

        private Row(String label, DoubleSupplier getter, DoubleConsumer setter,
                    double step, double min, double max, boolean integer, boolean toggle) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.step = step;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.toggle = toggle;
        }

        static Row integer(String label, DoubleSupplier getter, DoubleConsumer setter,
                           double step, double min, double max) {
            return new Row(label, getter, setter, step, min, max, true, false);
        }

        static Row decimal(String label, DoubleSupplier getter, DoubleConsumer setter,
                           double step, double min, double max) {
            return new Row(label, getter, setter, step, min, max, false, false);
        }

        static Row toggle(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
            return new Row(label, () -> getter.getAsBoolean() ? 1.0D : 0.0D,
                    value -> setter.accept(value > 0.5D), 1.0D, 0, 1, true, true);
        }

        String text() {
            if (toggle) {
                return getter.getAsDouble() > 0.5D ? "开" : "关";
            }
            double value = getter.getAsDouble();
            return integer ? String.valueOf(Math.round(value)) : String.format("%.2f", value);
        }

        ButtonWidget minusButton(int x, int y, AddonConfigScreen screen) {
            return ButtonWidget.builder(Text.literal("−"), button -> screen.shift(this, -step))
                    .dimensions(x, y, BUTTON_WIDTH, 18).build();
        }

        ButtonWidget plusButton(int x, int y, AddonConfigScreen screen) {
            return ButtonWidget.builder(Text.literal("+"), button -> screen.shift(this, step))
                    .dimensions(x, y, BUTTON_WIDTH, 18).build();
        }
    }

    private void shift(Row row, double delta) {
        double next = Math.max(row.min, Math.min(row.max, row.getter.getAsDouble() + delta));
        row.setter.accept(next);
        rebuild();
    }
}
