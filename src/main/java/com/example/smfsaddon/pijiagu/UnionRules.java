package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.entity.PijiaguUnionEntity;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.damage.ModDamageSources;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 礼成体的杀人规律。
 *
 * <p>它不追人、不判断谁在攻击它 —— 它只管<b>礼</b>。来客失礼，就该入席。
 * 四条规律全部围绕"参加一场已经结束的婚礼"这件事：</p>
 *
 * <ol>
 *   <li><b>不可背对</b> —— 背对新人的来客，是在告辞</li>
 *   <li><b>不可空手</b> —— 空手赴宴，等于没把这场婚礼当回事</li>
 *   <li><b>不可越过供桌</b> —— 供桌之后是新人的位置，不是来客的</li>
 *   <li><b>不可打断礼成</b> —— 席间动手，礼就废了</li>
 * </ol>
 *
 * <p>每条规律都有明确的"守规矩就没事"的解法，这是本体厉鬼的核心：
 * 死的人一定是先犯了错。</p>
 */
public final class UnionRules {

    private static final double BACK_TURN_RANGE = 24.0D;
    /** 背对多久算失礼（tick） */
    private static final int BACK_TURN_LIMIT = 60;
    /** 空手多久开始扣（tick） */
    private static final int EMPTY_HAND_INTERVAL = 40;
    /** 供桌之后的禁地半径 */
    private static final double ALTAR_ZONE = 7.0D;

    /** 玩家 UUID → 已经背对多少 tick */
    private static final Map<UUID, Integer> BACK_TURN = new HashMap<>();
    /** 玩家 UUID → 上次提示时间 */
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private UnionRules() {
    }

    /** 每 10 tick 由礼成体调用一次 */
    public static void tick(PijiaguUnionEntity union, ServerWorld world) {
        long now = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            double distance = player.distanceTo(union);
            if (distance > BACK_TURN_RANGE) {
                BACK_TURN.remove(player.getUuid());
                continue;
            }

            // ── 规律一：不可背对
            Vec3d toUnion = union.getPos().subtract(player.getPos()).normalize();
            Vec3d look = player.getRotationVec(1.0F);
            if (look.dotProduct(toUnion) < -0.35D) {
                int ticks = BACK_TURN.merge(player.getUuid(), 10, Integer::sum);
                if (ticks >= BACK_TURN_LIMIT) {
                    BACK_TURN.put(player.getUuid(), 0);
                    punish(union, world, player, "§7你在它面前转过身去。",
                            "§c『背对新人的来客，是在告辞。』");
                }
            } else {
                BACK_TURN.remove(player.getUuid());
            }

            // ── 规律二：不可空手
            boolean emptyHanded = player.getMainHandStack().isEmpty()
                    && player.getOffHandStack().isEmpty();
            if (emptyHanded && now % EMPTY_HAND_INTERVAL == 0) {
                PlayerEvents.handleSpiritDamage(player, 40.0F, 40.0F,
                        ModDamageSources.ghost(world));
                hint(player, now, "§7你空着手来。", "§c『赴宴不带礼，是把席面当什么了。』");
            }

            // ── 规律三：不可越过供桌（席面中心往后 7 格就是新人位）
            double behind = player.getZ() - union.getZ();
            if (Math.abs(player.getX() - union.getX()) < 12.0D
                    && behind < -ALTAR_ZONE && behind > -ALTAR_ZONE * 2) {
                punish(union, world, player, "§7你越过了供桌。",
                        "§c『供桌之后是新人的位置，不是来客的。』");
            }
        }
    }

    /** 席间动手：礼废了 */
    public static void onAttacked(PijiaguUnionEntity union, ServerPlayerEntity player) {
        punish(union, (ServerWorld) union.getWorld(), player,
                "§7你在席间动了手。",
                "§4『礼成了，你还想打断它。』");
    }

    private static void punish(PijiaguUnionEntity union, ServerWorld world,
                               ServerPlayerEntity player, String first, String second) {
        // 先动手：规律的惩罚是它亲自过来扇两下，而不是站在远处放技能
        union.punishMelee(player);

        // 再请入席：开一场以它为中心的席面
        FeastCeremony.start(world, union.getBlockPos(), player);
        // 立刻入席，不等 9.5 秒
        player.addStatusEffect(new StatusEffectInstance(SmfsAddon.SEATED, 200, 0, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 255, false, false, false));
        player.sendMessage(Text.literal(first), false);
        player.sendMessage(Text.literal(second), false);
    }

    private static void hint(ServerPlayerEntity player, long now, String first, String second) {
        long last = LAST_HINT.getOrDefault(player.getUuid(), 0L);
        if (now - last < 200) {
            return;
        }
        LAST_HINT.put(player.getUuid(), now);
        player.sendMessage(Text.literal(first), false);
        player.sendMessage(Text.literal(second), false);
    }
}
