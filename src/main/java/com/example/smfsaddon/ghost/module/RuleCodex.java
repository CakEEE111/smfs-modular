package com.example.smfsaddon.ghost.module;

import com.xie.smfs.common.events.PlayerEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家侧的规律图鉴。
 *
 * <p>记录「哪个玩家，在哪只鬼身上，见过哪条规律多少次」。
 * 数据存在本体自己的玩家灵异数据里（{@code PlayerEvents}），
 * 所以跟着原版存档走，不需要额外文件。
 *
 * <h2>两个必须注意的点</h2>
 *
 * <p><b>节流</b>：杀人规律每 tick 都会被询问，若每次命中都记一笔，
 * 一场战斗就能把图鉴刷满，而且每次写回都会触发一次网络同步。
 * 因此同一个（鬼种类 + 规则）组合每 {@value #OBSERVE_COOLDOWN_TICKS} tick
 * 最多记一次。
 *
 * <p><b>读改写</b>：{@code PlayerEvents.getSpiritAttributes} 返回的是<b>副本</b>，
 * 改完必须整体写回，否则不会生效。
 */
public final class RuleCodex {

    /** 在本体玩家数据里的键 */
    private static final String KEY = "smfsaddon_codex";

    /**
     * 图鉴存档格式版本，与图鉴本体分开存，避免污染条目键空间。
     *
     * <p>现在没人读它。留着是为了将来改键结构（例如改成规则级共享）时，
     * 能判断旧数据是哪一版，而不是靠条目里有没有分隔符去猜。
     */
    private static final String KEY_VERSION = "smfsaddon_codex_v";
    private static final int SCHEMA_VERSION = 1;

    /** 同一组合两次记录之间的最小间隔（tick） */
    private static final long OBSERVE_COOLDOWN_TICKS = 40L;

    private static final char SEP = '|';

    /** 节流戳，只在内存里，不落盘 */
    private static final Map<String, Long> LAST_OBSERVED = new HashMap<>();

    /** 每个玩家的图鉴缓存，避免每次读都复制整份玩家数据 */
    private static final Map<UUID, NbtCompound> CACHE = new HashMap<>();

    private RuleCodex() {
    }

    /**
     * 记录一次观察。
     *
     * @return 是否真正记录（被节流挡掉时返回 {@code false}）
     */
    public static boolean observe(PlayerEntity player, Identifier ghostType,
                                  Identifier ruleId, long tick) {
        if (player.getWorld().isClient()) {
            return false;
        }

        String throttleKey = player.getUuidAsString() + SEP + ghostType + SEP + ruleId;
        Long last = LAST_OBSERVED.get(throttleKey);
        if (last != null && tick - last < OBSERVE_COOLDOWN_TICKS) {
            return false;
        }
        LAST_OBSERVED.put(throttleKey, tick);

        NbtCompound codex = load(player);
        String entryKey = ghostType + String.valueOf(SEP) + ruleId;
        codex.putInt(entryKey, codex.getInt(entryKey) + 1);
        flush(player, codex);
        return true;
    }

    /**
     * 读取某条已记录的观察次数，未记录返回 0。
     */
    public static int count(PlayerEntity player, Identifier ghostType, Identifier ruleId) {
        return load(player).getInt(ghostType + String.valueOf(SEP) + ruleId);
    }

    /**
     * 列出该玩家全部已观察条目。未发现的规律不会出现在这里 ——
     * 图鉴的边界就是玩家的经验边界。
     */
    public static List<Observation> entries(PlayerEntity player) {
        NbtCompound codex = load(player);
        List<Observation> result = new ArrayList<>();
        for (String key : codex.getKeys()) {
            int idx = key.indexOf(SEP);
            if (idx <= 0) {
                continue;
            }
            Identifier ghostType = Identifier.tryParse(key.substring(0, idx));
            Identifier ruleId = Identifier.tryParse(key.substring(idx + 1));
            if (ghostType == null || ruleId == null) {
                continue;
            }
            result.add(new Observation(ghostType, ruleId, codex.getInt(key)));
        }
        return result;
    }

    /**
     * 清空该玩家的图鉴。
     */
    public static void reset(PlayerEntity player) {
        CACHE.put(player.getUuid(), new NbtCompound());
        flush(player, new NbtCompound());
    }

    /**
     * 玩家退出时释放缓存，避免长时间运行后内存里堆着离线玩家数据。
     */
    public static void forget(UUID playerId) {
        CACHE.remove(playerId);
        LAST_OBSERVED.keySet().removeIf(key -> key.startsWith(playerId.toString() + SEP));
    }

    private static NbtCompound load(PlayerEntity player) {
        NbtCompound cached = CACHE.get(player.getUuid());
        if (cached != null) {
            return cached;
        }
        NbtCompound data = PlayerEvents.getSpiritAttributes(player);
        NbtCompound codex = data.contains(KEY, NbtElement.COMPOUND_TYPE)
                ? data.getCompound(KEY).copy()
                : new NbtCompound();
        CACHE.put(player.getUuid(), codex);
        return codex;
    }

    private static void flush(PlayerEntity player, NbtCompound codex) {
        NbtCompound data = PlayerEvents.getSpiritAttributes(player);
        data.put(KEY, codex.copy());
        data.putInt(KEY_VERSION, SCHEMA_VERSION);
        PlayerEvents.setSpiritAttributes(player, data);
    }

    /**
     * 一条观察记录。
     */
    public record Observation(Identifier ghostType, Identifier ruleId, int count) {
    }
}
