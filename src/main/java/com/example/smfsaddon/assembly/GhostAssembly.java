package com.example.smfsaddon.assembly;

import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.GhostModuleRegistry;
import com.example.smfsaddon.ghost.module.LingyiHost;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * 厉鬼装配表：鬼类型 → 一组装配变体。
 *
 * <p>表的键是<b>实体类型 ID</b>，不是 Java 类。所以它对任何附属的鬼都成立 ——
 * 只要那只鬼继承本体 {@code GhostEntity}（也就带有模块容器），
 * 就会被自动装配，对方<b>一行代码都不用改</b>。
 *
 * <h2>变体与随机</h2>
 *
 * <p>同一只鬼可以有多个变体（不同的规律组合），生成时按权重抽一个。
 * <b>抽中即固定</b>：变体索引会写进实体 NBT，读档后沿用，
 * 不会每 tick 重抽 —— 否则鬼的行为会闪烁，玩家也没法推理规律。
 *
 * <h2>与手动挂载的关系</h2>
 *
 * <p>装配只在实体首次 tick 时执行一次，且只补缺失的模块。
 * 因此手动挂载的模块不会被覆盖，手动卸载的也不会每 tick 被加回来。
 */
public final class GhostAssembly {

    private static final Logger LOGGER = LoggerFactory.getLogger("smfsaddon/assembly");

    private static final Map<Identifier, List<GhostVariant>> ASSEMBLY = new LinkedHashMap<>();

    private GhostAssembly() {
    }

    public static void put(Identifier entityType, List<GhostVariant> variants) {
        ASSEMBLY.put(entityType, List.copyOf(variants));
    }

    public static List<GhostVariant> variantsFor(Identifier entityType) {
        return ASSEMBLY.getOrDefault(entityType, List.of());
    }

    public static Map<Identifier, List<GhostVariant>> all() {
        return Collections.unmodifiableMap(ASSEMBLY);
    }

    public static void clear() {
        ASSEMBLY.clear();
    }

    /**
     * 按权重抽一个变体。没有配置时返回 {@code null}。
     */
    public static GhostVariant pick(Identifier entityType, Random random) {
        List<GhostVariant> variants = ASSEMBLY.get(entityType);
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        if (variants.size() == 1) {
            return variants.get(0);
        }

        int total = 0;
        for (GhostVariant variant : variants) {
            total += variant.weight();
        }
        int roll = random.nextInt(total);
        for (GhostVariant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant;
            }
        }
        return variants.get(variants.size() - 1);
    }

    /**
     * 按索引取变体，越界返回 {@code null}。
     *
     * <p>数据包改动后变体数量可能变少，旧存档里的索引要能安全退化。
     */
    public static GhostVariant byIndex(Identifier entityType, int index) {
        List<GhostVariant> variants = ASSEMBLY.get(entityType);
        if (variants == null || index < 0 || index >= variants.size()) {
            return null;
        }
        return variants.get(index);
    }

    public static int indexOf(Identifier entityType, GhostVariant variant) {
        return variantsFor(entityType).indexOf(variant);
    }

    /**
     * 把变体的模块装配到宿主上，只补缺失的。
     *
     * @return 实际新挂上的模块数量
     */
    public static int applyTo(LingyiHost host, GhostVariant variant) {
        if (variant == null || variant.modules().isEmpty()) {
            return 0;
        }

        int applied = 0;
        for (Identifier moduleId : variant.modules()) {
            if (host.modules().has(moduleId)) {
                continue;
            }
            if (!GhostModuleRegistry.contains(moduleId)) {
                LOGGER.warn("装配表引用了未注册的模块 {}，已跳过", moduleId);
                continue;
            }
            try {
                GhostModuleDispatcher.attach(host, moduleId);
                applied++;
            } catch (RuntimeException exception) {
                // 典型原因是能力不匹配：比如给鬼配了只服务方块宿主的模块
                LOGGER.warn("装配模块 {} 失败：{}", moduleId, exception.getMessage());
            }
        }
        return applied;
    }

    /**
     * 供命令与调试：把某类型的变体压成可读文本。
     */
    public static List<String> describe(Identifier entityType) {
        List<String> lines = new ArrayList<>();
        for (GhostVariant variant : variantsFor(entityType)) {
            lines.add(variant.id() + "（权重 " + variant.weight() + "）: " + variant.modules());
        }
        return lines;
    }
}
