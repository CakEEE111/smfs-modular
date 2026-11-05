package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.item.GhostNames;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能表：<b>从服务端声明的技能通道推出来</b>，不扫方法名。
 *
 * <h2>为什么放弃反射扫方法</h2>
 *
 * <p>早先是扫本体的 {@code handleXxxSkill(PlayerEntity)} 方法名。实测五个鬼全落空，
 * 翻字节码才看清本体自己就不统一：
 *
 * <pre>
 * 鬼血   handleGhostBloodSkill 不存在，逻辑直接写在包里
 * 鬼湖   同上，包里内联操作鬼仆
 * 静悄悄 调的是 SilentGhostTeleportHandler.handleJKeyTeleport，名字完全不符合约定
 * 鬼烟   根本是另一只鬼（ghost_wind）的包，物品鬼种 ghost_smoke 没有主动技能
 * 完整鬼影 共用鬼影头的包 ghost_shadow_head_*，而它自己的名字是 complete_shadow_ghost
 * </pre>
 *
 * <p>但每个技能<b>一定有通道</b>——客户端能发的东西就是技能全集。
 * 所以改成扫 {@code ClientPlayNetworking.getSendable()}：
 * 名字里带 {@code _skill} 的就是技能，通道名本身就编码了「哪只鬼、哪个键」。
 *
 * <p>顺带解决两件事：附属自己的技能也能列出来（不需要认识它的类），
 * 冷却与资格全由那个包自己处理（见 {@link SkillChannels} 的说明）。
 */
public final class GhostSkillChannels {

    /**
     * 一个可释放的技能。
     *
     * @param key     按键标记（N/G/V/J），默认技能为空串
     * @param label   显示名
     * @param channel 要发的通道
     */
    public record Entry(String key, String label, Identifier channel) {
    }

    /**
     * 通道名和鬼种名对不上的少数例外。
     *
     * <p>都是本体自己的命名问题，不是猜的：逐个反编译技能包，
     * 看它 {@code canUseGhostSkill} 里认的是哪个鬼种。
     *
     * <ul>
     *   <li>{@code complete_shadow_ghost}（完整鬼影）用的是 {@code ghost_shadow_head_*} 那四个包，
     *       包内判定写的是「完整鬼影 <b>或</b> 鬼影头」</li>
     *   <li>{@code silent_ghost_eye}（鬼眼）的包叫 {@code ghost_eye_j_skill}，
     *       但包内 canUseGhostSkill 认的是 {@code silent_ghost_eye}</li>
     * </ul>
     */
    private static final Map<String, String> CHANNEL_ALIAS = Map.of(
            "complete_shadow_ghost", "ghost_shadow_head",
            "silent_ghost_eye", "ghost_eye"
    );

    private static final String SUFFIX = "_skill";

    /**
     * 会从包缓冲区读参数的通道，面板不能列。
     *
     * <p>面板发的都是空包（绝大多数技能包不读参数）。但鬼影头的 G/N/V 三个包要读
     * 目标实体 id、坐标、布尔量 —— 空包会让服务端 handler 读越界，
     * 轻则日志报错，重则踢人。所以这几个只能靠本体自己的键位用。
     *
     * <p>判断依据是反编译：包里出现 {@code readInt} / {@code readFloat} / {@code readBoolean}。
     */
    private static final Set<String> NEEDS_ARGUMENTS = Set.of(
            "ghost_shadow_head_g_skill",
            "ghost_shadow_head_n_skill",
            "ghost_shadow_head_v_skill"
    );

    private static final Map<String, List<Entry>> BY_GHOST = new HashMap<>();
    private static Set<Identifier> scannedFrom = Set.of();

    private GhostSkillChannels() {
    }

    /**
     * 进服后预热一次，把通道数打进日志 —— 面板还没打开时也能确认扫描结果。
     */
    public static void warmUp() {
        ensureScanned();
    }

    /**
     * 某只鬼的技能列表。还没进游戏（通道表为空）时返回空。
     */
    public static List<Entry> entries(String ghostType) {
        ensureScanned();
        if (ghostType == null) {
            return List.of();
        }
        List<Entry> direct = BY_GHOST.getOrDefault(ghostType, List.of());
        String alias = CHANNEL_ALIAS.get(ghostType);
        if (alias == null) {
            return direct;
        }
        // 有别名时合并去重（同一按键只留一个）
        Map<String, Entry> merged = new java.util.LinkedHashMap<>();
        direct.forEach(entry -> merged.put(entry.key(), entry));
        BY_GHOST.getOrDefault(alias, List.of()).forEach(entry -> merged.putIfAbsent(entry.key(), entry));
        List<Entry> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(Entry::key));
        return result;
    }

    /**
     * 发一个空包。这些技能包都不读参数（读参数的是多参技能，本来也不该一键放）。
     */
    public static void dispatch(Identifier channel) {
        if (channel == null) {
            return;
        }
        ClientPlayNetworking.send(channel, PacketByteBufs.empty());
    }

    private static void ensureScanned() {
        Set<Identifier> sendable;
        try {
            sendable = ClientPlayNetworking.getSendable();
        } catch (RuntimeException exception) {
            // 还没进世界，下一次再扫
            return;
        }
        if (sendable.equals(scannedFrom)) {
            return;
        }
        scannedFrom = sendable;
        BY_GHOST.clear();

        for (Identifier channel : sendable) {
            String path = channel.getPath();
            if (!path.endsWith(SUFFIX)) {
                continue;
            }
            if (NEEDS_ARGUMENTS.contains(path)) {
                continue;
            }
            String body = path.substring(0, path.length() - SUFFIX.length());
            String key = "";
            // 末尾的 _n / _g / _v / _j 是按键
            if (body.length() > 2 && body.charAt(body.length() - 2) == '_') {
                char letter = Character.toLowerCase(body.charAt(body.length() - 1));
                if (letter == 'n' || letter == 'g' || letter == 'v' || letter == 'j') {
                    key = String.valueOf(Character.toUpperCase(letter));
                    body = body.substring(0, body.length() - 2);
                }
            }
            // 个别通道带前缀，例如 client_ghost_fire_j_skill
            if (body.startsWith("client_")) {
                body = body.substring("client_".length());
            }
            if (body.isEmpty()) {
                continue;
            }
            String label = GhostNames.of(body) + (key.isEmpty() ? "" : " " + key + " 键");
            BY_GHOST.computeIfAbsent(body, ignored -> new ArrayList<>())
                    .add(new Entry(key, label, channel));
        }

        for (List<Entry> list : BY_GHOST.values()) {
            list.sort(Comparator
                    .comparingInt((Entry entry) -> order(entry.key()))
                    .thenComparing(Entry::label));
        }
        SmfsAddon.LOGGER.info("[smfsaddon] 已从技能通道拉取技能：{} 个鬼种，{} 条通道",
                BY_GHOST.size(),
                BY_GHOST.values().stream().mapToInt(List::size).sum());
    }

    private static int order(String key) {
        return switch (key) {
            case "" -> 0;
            case "N" -> 1;
            case "G" -> 2;
            case "V" -> 3;
            case "J" -> 4;
            default -> 5;
        };
    }

    /** 调试用：当前识别到的所有鬼种 */
    public static Set<String> knownTypes() {
        ensureScanned();
        return new LinkedHashSet<>(BY_GHOST.keySet());
    }

    /** 调试用：某只鬼认出来的通道名，便于排查 */
    public static List<String> channelPaths(String ghostType) {
        List<String> paths = new ArrayList<>();
        for (Entry entry : entries(ghostType)) {
            paths.add(entry.channel().toString() + (entry.key().isEmpty() ? "" : " [" + entry.key() + "]"));
        }
        return paths;
    }
}
