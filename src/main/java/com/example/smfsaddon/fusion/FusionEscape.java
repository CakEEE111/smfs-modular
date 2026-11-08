package com.example.smfsaddon.fusion;

import com.example.smfsaddon.item.GhostNames;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 融合失败时，素材厉鬼可能当场复苏。
 *
 * <h2>为什么要有这个</h2>
 *
 * <p>融合失败原本<b>什么都不损失</b>——厉鬼还在槽里，点一下就能重来。
 * 于是"压制不足"只是拖时间：最坏情况点十次、掉点血，总能赌赢。
 *
 * <p>现在加了这条：第一次失败 5%，之后每次失败再 +10%（5% → 15% → 25% …）。
 * 失败不再是免费的，但也不是必死 —— 它在逼玩家"别硬来"，
 * 而不是"别玩这个功能"。
 *
 * <h2>复苏的形式照本体来</h2>
 *
 * <p>本体黄金容器释放厉鬼是这样做的（{@code GoldenContainerItem#releaseGhost}）：
 * 在玩家脚下把实体造出来、重置状态、扔进世界。这里用同样的路子 ——
 * 鬼当场从容器里爬出来，站在你面前。
 *
 * <p>复苏后素材<b>消失</b>（它已经是实体了，不在槽里了），
 * 而且它带着交错模块重新生成，等于当场变成一只完整的鬼。
 */
public final class FusionEscape {

    /** 第一次失败的基础概率 */
    public static float BASE_CHANCE = 0.05F;

    /** 每多失败一次，叠加的概率 */
    public static float CHANCE_STEP = 0.10F;

    /** 玩家 → 鬼种 → 连续失败次数 */
    private static final Map<UUID, Map<String, Integer>> FAILURES = new HashMap<>();

    private static final int TRACK_LIMIT = 512;

    /** 总开关，配置里可关掉 */
    public static boolean ENABLED = true;

    public static void applyConfig(boolean enabled, float baseChance, float stepChance) {
        ENABLED = enabled;
        BASE_CHANCE = baseChance;
        CHANCE_STEP = stepChance;
    }

    private FusionEscape() {
    }

    /**
     * 结算一次失败，可能让厉鬼复苏。
     *
     * @return {@code true} 表示鬼跑了（调用方应当清空素材槽）
     */
    public static boolean maybeEscape(PlayerEntity player, String ghostType) {
        if (!ENABLED) {
            return false;
        }
        if (player == null || ghostType == null || player.getWorld() == null
                || player.getWorld().isClient()) {
            return false;
        }

        int failures = failures(player, ghostType);
        float chance = Math.min(1.0F, BASE_CHANCE + CHANCE_STEP * failures);

        if (player.getRandom().nextFloat() >= chance) {
            // 没跑，难度继续累加
            setFailures(player, ghostType, failures + 1);
            return false;
        }

        // 跑了：先真的把鬼造出来，成功了才清零 ——
        // 若这只鬼没有对应的实体类型（比如只有物品的鬼），释放失败，
        // 那就当作没发生，难度继续累加，别让玩家白吃一次概率
        if (!release(player, ghostType)) {
            return false;
        }
        setFailures(player, ghostType, 0);
        return true;
    }

    /**
     * 成功一次就把这笔账清掉 —— 赌赢了不该继续背着上把的运气债。
     */
    public static void reset(PlayerEntity player, String ghostType) {
        if (player == null || ghostType == null) {
            return;
        }
        setFailures(player, ghostType, 0);
    }

    /** 当前这只鬼的复苏概率，给界面提示用 */
    public static float chanceFor(PlayerEntity player, String ghostType) {
        return Math.min(1.0F, BASE_CHANCE + CHANCE_STEP * failures(player, ghostType));
    }

    // ---------------------------------------------------------------- 内部

    private static boolean release(PlayerEntity player, String ghostType) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Identifier typeId = GhostGrafting.entityTypeOf(ghostType);
        if (typeId == null) {
            return false;
        }
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(typeId).orElse(null);
        if (type == null) {
            return false;
        }
        Entity entity = type.create(world);
        if (!(entity instanceof GhostEntity ghost)) {
            if (entity != null) {
                entity.discard();
            }
            return false;
        }

        // 就落在玩家脚下 —— 和本体释放黄金容器一样，不给你反应距离
        ghost.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(),
                player.getYaw(), 0.0F);
        ghost.setNoGravity(false);
        ghost.setInvulnerable(false);
        world.spawnEntity(ghost);

        player.sendMessage(Text.literal("§4厉鬼复苏 §8"
                + GhostNames.of(ghostType) + " 从容器里爬了出来"), false);
        return true;
    }

    private static int failures(PlayerEntity player, String ghostType) {
        Map<String, Integer> perGhost = FAILURES.get(player.getUuid());
        return perGhost == null ? 0 : perGhost.getOrDefault(ghostType, 0);
    }

    private static void setFailures(PlayerEntity player, String ghostType, int value) {
        if (FAILURES.size() > TRACK_LIMIT) {
            FAILURES.clear();
        }
        Map<String, Integer> perGhost =
                FAILURES.computeIfAbsent(player.getUuid(), ignored -> new HashMap<>());
        if (value <= 0) {
            perGhost.remove(ghostType);
            return;
        }
        perGhost.put(ghostType, value);
    }

    /** 世界卸载时清空，别让玩家对象被静态表攥住 */
    public static void clearCache() {
        FAILURES.clear();
    }
}
