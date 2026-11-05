package com.example.smfsaddon.skill;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.manager.GhostDomainManager;
import com.xie.smfs.manager.GhostSkillManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 鬼种 → 本体技能方法。反射拉取，不手写。
 *
 * <h2>命名比想象中复杂</h2>
 *
 * <p>本体把技能写成公开静态方法，但后缀不止一种：
 *
 * <pre>
 * handleQiaomenGhostSkill      → 默认键
 * handleWishGhostNSkill        → N 键
 * handleDoorGhostJSkill        → J 键
 * handleGhostMerchantSkill     → 名字里没有 "Ghost" 结尾
 * handleCandyGhostJSkill       → 同上，且有按键
 * </pre>
 *
 * <p>所以解析分三步：剥掉 {@code handle} 与 {@code (按键)?Skill} 后缀 →
 * 去掉残留的 {@code Ghost} → 用真实实体类型校准
 * （{@code wish} 要变成 {@code wish_ghost}，而 {@code ghost_merchant} 本身就对）。
 *
 * <p>校准这一步很关键：鬼种字符串是本体的内部键，猜错一个名字，
 * 技能内部的 {@code hasGhostType} 就通不过，表现是「点了没反应」。
 */
public final class GhostSkillTable {

    /** {@code handle(核心)(按键)?Skill} */
    private static final Pattern PATTERN =
            Pattern.compile("^handle(.*?)(?:([NGVJ]))?Skill$");

    /**
     * 一个可选技能。
     *
     * @param key   界面上的按键标记（N/G/V/J，可为空）
     * @param label 显示名
     */
    public record Entry(String key, String label, Consumer<PlayerEntity> cast) {
    }

    private static final Map<String, List<Entry>> SKILLS = new LinkedHashMap<>();

    /**
     * 本体真实用到的鬼种字符串。
     *
     * <p>不能只靠实体注册表校准 —— 替死鬼、许愿鬼这些<b>只有物品、没有实体</b>，
     * 拿实体表去对必然对不上。这份词表直接取自本体的
     * {@code hasGhostType(player, "...")} 调用点。
     */
    private static final java.util.Set<String> KNOWN_GHOST_TYPES = java.util.Set.of(
            "block_ghost", "box_ghost", "candy_ghost", "clothes_ghost", "complete_shadow_ghost",
            "crop_ghost", "death_sight_ghost", "fog_ghost", "food_ghost", "funeral_music_ghost",
            "ganshi_bride_ghost", "ghost_dream", "ghost_fire", "ghost_fist", "ghost_lake",
            "ghost_merchant", "ghost_officer", "ghost_pressure", "ghost_shadow_head",
            "ghost_smoke", "giant_shadow_ghost", "grave_earth_ghost", "jump_ghost",
            "lost_ghost", "mineral_ghost", "plague_ghost", "puppet_ghost",
            "scapegoat_ghost", "shadow_ghost", "silent_ghost_eye", "sneak_ghost",
            "step_ghost", "trash_ghost", "untouchable_ghost", "villager_ghost",
            "water_ghost", "wish_ghost", "qiaomen_ghost", "door_ghost"
    );
    private static boolean scanned = false;

    /** 仅用于美化，缺条目不影响功能 */
    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("qiaomen_ghost", "敲门"),
            Map.entry("step_ghost", "踩人"),
            Map.entry("jump_ghost", "跳跃"),
            Map.entry("box_ghost", "鬼箱"),
            Map.entry("death_sight_ghost", "死亡视线"),
            Map.entry("fog_ghost", "迷雾"),
            Map.entry("block_ghost", "堵路"),
            Map.entry("food_ghost", "食物"),
            Map.entry("crop_ghost", "庄稼"),
            Map.entry("trash_ghost", "垃圾"),
            Map.entry("water_ghost", "水"),
            Map.entry("grave_earth_ghost", "坟土"),
            Map.entry("taitou_ghost", "抬头"),
            Map.entry("ditou_ghost", "低头"),
            Map.entry("giant_shadow_ghost", "巨影"),
            Map.entry("ganshi_bride_ghost", "干尸新娘"),
            Map.entry("scapegoat_ghost", "替死"),
            Map.entry("wish_ghost", "许愿"),
            Map.entry("lost_ghost", "遗忘"),
            Map.entry("door_ghost", "门"),
            Map.entry("ghost_merchant", "鬼商"),
            Map.entry("candy_ghost", "糖果"),
            Map.entry("plague_ghost", "瘟疫"),
            Map.entry("mineral_ghost", "矿物"),
            Map.entry("shadow_ghost", "影子"),
            Map.entry("villager_ghost", "村民"),
            Map.entry("ghost_eye", "鬼眼")
    );

    private GhostSkillTable() {
    }

    private static void ensureScanned() {
        if (scanned) {
            return;
        }
        scanned = true;

        int fromDomain = scanClass(GhostDomainManager.class);
        int fromSkill = scanClass(GhostSkillManager.class);

        SmfsAddon.LOGGER.info("[smfsaddon] 已从本体拉取技能：{} 个鬼种，域 {} / 技能 {}",
                SKILLS.size(), fromDomain, fromSkill);
    }

    /**
     * 扫描一个类上的技能方法。
     */
    private static int scanClass(Class<?> holder) {
        int accepted = 0;
        for (Method method : holder.getMethods()) {
            if (method.getDeclaringClass() != holder) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            // 只要单参数版本，避开 handleXxx(player, boolean) 这类重载
            if (method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != PlayerEntity.class) {
                continue;
            }
            // 被动技不放进面板：它不需要玩家主动释放
            if (method.getName().contains("Passive")) {
                continue;
            }

            Matcher matcher = PATTERN.matcher(method.getName());
            if (!matcher.matches()) {
                continue;
            }

            String core = matcher.group(1);
            String key = matcher.group(2) == null ? "" : matcher.group(2);
            if (core.isEmpty()) {
                continue;
            }
            // 去掉残留的 "Ghost"（handleQiaomenGhostSkill → Qiaomen）
            if (core.endsWith("Ghost")) {
                core = core.substring(0, core.length() - "Ghost".length());
            }
            if (core.isEmpty()) {
                continue;
            }

            String ghostType = resolveGhostType(core);
            if (ghostType == null) {
                SmfsAddon.LOGGER.warn("[smfsaddon] 无法解析技能 {} 对应的鬼种，已跳过",
                        method.getName());
                continue;
            }

            String label = displayName(ghostType) + (key.isEmpty() ? "" : " " + key + " 键");
            SKILLS.computeIfAbsent(ghostType, ignored -> new ArrayList<>())
                    .add(new Entry(key, label, player -> invoke(method, player)));
            accepted++;
        }
        return accepted;
    }

    /**
     * 把方法名里的核心词校准成真实的鬼种字符串。
     *
     * <p>{@code wish} → {@code wish_ghost}；{@code ghost_merchant} 保持不变。
     * 做法是拿去和已注册的实体类型对一遍 —— 猜错名字的话，
     * 技能内部的 {@code hasGhostType} 会失败，点了没反应。
     */
    private static String resolveGhostType(String core) {
        String snake = camelToSnake(core);
        String[] candidates = {snake, snake + "_ghost"};

        for (String candidate : candidates) {
            for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                if (id.getPath().equals(candidate)) {
                    return candidate;
                }
            }
        }

        // 实体对不上很正常：替死鬼、许愿鬼这些只有物品、没有实体。
        // 改用词表做最长匹配 —— "PuppetGhostResistance" 应归到 puppet_ghost，
        // "GhostEye" 应归到 silent_ghost_eye。
        String best = null;
        for (String known : KNOWN_GHOST_TYPES) {
            if (snake.equals(known)
                    || snake.startsWith(known)
                    || known.startsWith(snake)
                    || snake.contains(known)
                    || known.contains(snake)) {
                if (best == null || known.length() > best.length()) {
                    best = known;
                }
            }
        }
        if (best != null) {
            return best;
        }

        String fallback = snake.endsWith("_ghost") ? snake : snake + "_ghost";
        SmfsAddon.LOGGER.warn("[smfsaddon] 技能核心词 {} 无法归属，猜测为 {}", core, fallback);
        return fallback;
    }

    private static String camelToSnake(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String displayName(String ghostType) {
        String known = DISPLAY_NAMES.get(ghostType);
        if (known != null) {
            return known;
        }
        return ghostType.endsWith("_ghost")
                ? ghostType.substring(0, ghostType.length() - "_ghost".length())
                : ghostType;
    }

    private static void invoke(Method method, PlayerEntity player) {
        try {
            method.invoke(null, player);
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            SmfsAddon.LOGGER.warn("[smfsaddon] 释放技能 {} 失败：{}",
                    method.getName(), cause.toString());
        }
    }

    public static boolean has(String ghostType) {
        ensureScanned();
        return ghostType != null && SKILLS.containsKey(ghostType);
    }

    public static List<Entry> entries(String ghostType) {
        ensureScanned();
        return SKILLS.getOrDefault(ghostType, List.of());
    }

    public static List<String> knownTypes() {
        ensureScanned();
        return List.copyOf(SKILLS.keySet());
    }

    /**
     * 技能的可读名，用于提示。
     */
    public static String labelOf(String ghostType, String key) {
        for (Entry entry : entries(ghostType)) {
            if (entry.key().equals(key == null ? "" : key)) {
                return entry.label();
            }
        }
        return ghostType;
    }

    /**
     * 按鬼种 + 键位释放。{@code key} 为空时表示该鬼的默认技能。
     */
    public static boolean cast(PlayerEntity player, String ghostType, String key) {
        ensureScanned();
        for (Entry entry : SKILLS.getOrDefault(ghostType, List.of())) {
            if (entry.key().equals(key == null ? "" : key)) {
                try {
                    entry.cast().accept(player);
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            }
        }
        return false;
    }
}
