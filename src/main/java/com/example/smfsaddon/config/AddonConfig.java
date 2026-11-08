package com.example.smfsaddon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置：{@code config/smfsaddon.json}。
 *
 * <h2>为什么要它</h2>
 *
 * <p>在这之前，交错条数、融合比例、复苏概率全是 {@code static final} 常量 ——
 * 玩家说"太难了"，只能改代码重新编译发版。有了配置，一句
 * "改 config 里的 crossing.hardRules" 就够了。
 *
 * <h2>读不到就用默认值</h2>
 *
 * <p>配置文件可能被写坏、可能是旧版本缺字段、可能整个文件消失。
 * 这三种情况都<b>不允许</b>影响启动：字段缺失走默认值，JSON 解析失败就丢弃并用默认值覆盖，
 * 顶多日志里警告一声。
 *
 * <h2>为什么用可变静态字段</h2>
 *
 * <p>数值被十几个地方引用（{@code FusionFormula.ENTRY_COST} 之类）。
 * 把它们从 {@code static final} 改成可写静态字段，是改动面最小、
 * 又不需要在每个调用点塞一个 {@code get()} 的办法。写入只发生在加载那一刻。
 */
public final class AddonConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("smfsaddon/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "smfsaddon.json";

    private static AddonConfig instance;

    public Crossing crossing = new Crossing();
    public Fusion fusion = new Fusion();
    public Escape escape = new Escape();

    /** 灵异交错 */
    public static final class Crossing {
        /** 和平难度借几条规律 */
        public int peacefulRules = 0;
        public int easyRules = 2;
        public int normalRules = 3;
        public int hardRules = 4;

        /** 和平难度借几个机制 */
        public int peacefulMechanisms = 0;
        public int easyMechanisms = 0;
        public int normalMechanisms = 1;
        public int hardMechanisms = 2;

        /**
         * 交错两次出手的最小间隔（tick）。
         *
         * <p>本体的出手节奏写在每只鬼自己的 {@code shouldAttackPlayer} 里，
         * 交错走了短路分支会绕开它，所以这里必须自己补一道闸。
         */
        public int allowCooldownTicks = 40;
    }

    /** 融合 */
    public static final class Fusion {
        /** 每只鬼贡献其驾驭物最大灵异的比例 */
        public float stealRatio = 0.10F;

        /** 每融一只鬼，继承倍率增加多少 */
        public float inheritPerGhost = 0.50F;

        /** 每次合入的入门费（还要乘 costScale） */
        public double entryCost = 50.0D;

        /** 模块危险度换算成实际需求的系数 */
        public double costScale = 30.0D;

        /** 同一件物品每多融一只鬼，成本上浮多少 */
        public double marginalGrowth = 0.75D;

        /** 压制公式里最大灵异的权重 */
        public double weightMaxSpirit = 1.00D;
        public double weightResistance = 1.00D;
        public double weightDamage = 0.50D;
    }

    /** 融合失败时厉鬼复苏 */
    public static final class Escape {
        /** 第一次失败的基础概率 */
        public float baseChance = 0.05F;

        /** 每多失败一次叠加的概率 */
        public float stepChance = 0.10F;

        /** 总开关：关掉之后失败不再有复苏风险 */
        public boolean enabled = true;
    }

    private AddonConfig() {
    }

    // ---------------------------------------------------------------- 读写

    public static AddonConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path file = path();
        AddonConfig loaded = null;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, AddonConfig.class);
            } catch (Exception exception) {
                LOGGER.warn("[smfsaddon] 配置文件解析失败，改用默认值：{}", exception.toString());
            }
        }
        if (loaded == null) {
            loaded = new AddonConfig();
        }
        // 缺字段（旧版配置）时补默认值：Gson 对缺失字段留 null
        loaded.fillDefaults();
        instance = loaded;
        apply();
        save();
    }

    private void fillDefaults() {
        if (crossing == null) {
            crossing = new Crossing();
        }
        if (fusion == null) {
            fusion = new Fusion();
        }
        if (escape == null) {
            escape = new Escape();
        }
    }

    public static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(get(), writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("[smfsaddon] 配置文件写入失败：{}", exception.toString());
        }
    }

    /**
     * 把配置写进各处引用的静态字段。
     *
     * <p>只在 {@link #load()} 和配置界面保存后调用。
     */
    public static void apply() {
        AddonConfig config = get();

        com.example.smfsaddon.crossing.GhostRuleCrossing.applyConfig(
                config.crossing.peacefulRules, config.crossing.easyRules,
                config.crossing.normalRules, config.crossing.hardRules,
                config.crossing.peacefulMechanisms, config.crossing.easyMechanisms,
                config.crossing.normalMechanisms, config.crossing.hardMechanisms,
                config.crossing.allowCooldownTicks);

        com.example.smfsaddon.fusion.FusionFormula.applyConfig(
                config.fusion.entryCost, config.fusion.costScale,
                config.fusion.marginalGrowth, config.fusion.weightMaxSpirit,
                config.fusion.weightResistance, config.fusion.weightDamage);

        com.example.smfsaddon.fusion.FusionEscape.applyConfig(
                config.escape.enabled, config.escape.baseChance, config.escape.stepChance);

        com.example.smfsaddon.item.GhostSpiritPower.applyConfig(
                config.fusion.stealRatio, config.fusion.inheritPerGhost);
    }
}
