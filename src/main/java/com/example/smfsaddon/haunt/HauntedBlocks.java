package com.example.smfsaddon.haunt;

import com.example.smfsaddon.item.ItemGhostHost;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 鬼方块的运行逻辑。
 *
 * <h2>它是什么</h2>
 *
 * <p>某个位置被"污染"了 —— 方块还是原来那个方块（草、原石、深板岩），
 * 但里面关着一只鬼。玩家靠近时，那只鬼会<b>隔着方块</b>按自己的规律判断你该死不该死。
 *
 * <h2>三个约束</h2>
 *
 * <ol>
 *   <li><b>不实体化</b>：判定通过只是"打你一下"，鬼还在方块里。
 *       只有复苏值攒满、或者方块被打破，它才真的爬出来。</li>
 *   <li><b>判定频率低</b>：3 秒一次。靠近不会立刻死，玩家有反应窗口。</li>
 *   <li><b>看不见但听得见</b>：普通玩家看不到任何异象，但靠近时会听到一声遇鬼音 ——
 *       声音就是唯一的预警。</li>
 * </ol>
 *
 * <h2>为什么不直接把玩家杀死</h2>
 *
 * <p>本体杀人的入口 {@code executeAttack} 是 protected，从外部调不了；
 * 自己造一条伤害路径又会绕开本体的抗性与保护结算。所以这里用
 * {@link GhostEntity#tryAttack} —— 它和本体鬼打人是同一个方法，
 * 伤害、冷却、附魔、无敌帧全部沿用原版。
 */
public final class HauntedBlocks {

    /** 判定范围（格） */
    private static final double TRIGGER_RADIUS = 5.0D;

    /**
     * 判定间隔：100 tick = 5 秒。
     *
     * <p>和本体一致 —— 本体每次袭击之后把 {@code attackCooldown} 设成 100。
     * 早先用 60（3 秒），比本体还勤，压着人打。
     */
    private static final long JUDGE_INTERVAL = 100L;

    /**
     * 方块状态下的伤害倍率。
     *
     * <p>只有 10%：鬼还被关在方块里，只能隔着砖缝伸一只手出来，
     * 不该和它本体破封而出时的威力一样。真正的全额伤害留给"复苏之后"。
     */
    private static final float BLOCK_DAMAGE_RATIO = 0.10F;

    /**
     * 预警音的重复间隔：200 tick = 10 秒。
     *
     * <p>早先是"进范围只响一次"，结果很坑：玩家在方块旁边敲命令创建它时，
     * 第一个 tick 就已经在范围内，音效当场播掉，之后再也不响 —— 看起来像"只有打碎才有声音"。
     * 改成周期重复，既解决这个错觉，也让"附近有东西"这件事持续被感知。
     */
    private static final long WARN_INTERVAL = 200L;

    /** 每次判定通过涨多少复苏 */
    private static final int REVIVAL_PER_HIT = 3;

    /** 复苏满了就破封 */
    private static final int MAX_REVIVAL = 30;

    /** 异象可见范围（只给"身上有灵异物品"的人） */
    private static final double SIGHT_RADIUS = 8.0D;

    private HauntedBlocks() {
    }

    // ---------------------------------------------------------------- 制造

    /**
     * 把某个位置变成鬼方块。
     *
     * @return 是否成功
     */
    public static boolean haunt(ServerWorld world, BlockPos pos, Identifier ghostType) {
        if (world.getBlockState(pos).isAir()) {
            return false;
        }
        HauntStore store = HauntStore.of(world);
        store.put(pos, new HauntStore.Entry(ghostType));
        return true;
    }

    public static void clear(ServerWorld world, BlockPos pos) {
        HauntStore.of(world).remove(pos);
    }

    // ---------------------------------------------------------------- 每 tick

    /**
     * 世界 tick。每次只做一次很便宜的遍历：没有玩家靠近的条目直接跳过。
     */
    public static void tick(ServerWorld world) {
        HauntStore store = HauntStore.of(world);
        if (store.haunts().isEmpty()) {
            return;
        }

        long now = world.getTime();
        List<BlockPos> drop = new ArrayList<>();

        for (Map.Entry<BlockPos, HauntStore.Entry> item : new ArrayList<>(store.haunts().entrySet())) {
            BlockPos pos = item.getKey();
            HauntStore.Entry entry = item.getValue();

            // 方块被别的途径挖掉/替换了：数据跟着清掉，别留幽灵条目
            if (world.getBlockState(pos).isAir()) {
                drop.add(pos);
                continue;
            }

            PlayerEntity near = nearestPlayer(world, pos);
            if (near == null) {
                // 没人时复位，下次进来立刻重新预警
                if (entry.warnedAt != 0L) {
                    entry.warnedAt = 0L;
                    store.markDirty();
                }
                continue;
            }

            // 预警音：进范围就响，之后每 10 秒补一次
            if (now - entry.warnedAt >= WARN_INTERVAL) {
                entry.warnedAt = now;
                store.markDirty();
                world.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                        SoundCategory.HOSTILE, 1.0F, 0.8F + world.getRandom().nextFloat() * 0.4F);
            }

            if (near.isCreative() || near.isSpectator()) {
                // 创造/旁观只给音效和粒子，不参与判定 ——
                // 他们本来就打不掉血，硬判也只会得到"伤害被忽略"的假象。
                // 但要在动作栏说清楚，否则测试时看起来就像功能没生效。
                if (near instanceof ServerPlayerEntity player && now % 200L == 0L) {
                    player.sendMessage(Text.literal("§7（创造模式不参与鬼方块判定）"), true);
                }
                continue;
            }

            if (now - entry.judgedAt < JUDGE_INTERVAL) {
                continue;
            }
            entry.judgedAt = now;
            store.markDirty();

            if (judge(world, pos, entry, near) && entry.revival >= MAX_REVIVAL) {
                // 苏醒：鬼真的爬出来了
                release(world, pos, entry.ghostType, near);
                drop.add(pos);
            }
        }

        for (BlockPos pos : drop) {
            store.remove(pos);
        }
    }

    /**
     * 让方块里的鬼判断一次。
     *
     * @return 这次是否真的打中了
     */
    private static boolean judge(ServerWorld world, BlockPos pos, HauntStore.Entry entry,
                                 PlayerEntity player) {
        // 每次新建一只，不复用：本体不少鬼的 shouldAttackPlayer 里带着
        // attackCooldown / blockAttackCooldown 之类的身体状态，缓存下来的影子
        // 打完一次就永远"冷却中"，之后再也判不过 —— 表现就是"怎么打都不触发"。
        GhostEntity ghost = freshShadow(world, entry.ghostType, pos);
        if (ghost == null) {
            return false;
        }
        try {
            if (ghost.isPlayerProtected(player)) {
                return false;
            }
            if (!ghost.shouldAttackPlayer(player)) {
                return false;
            }

            // 伤害按 10% 结算：不能直接 tryAttack，那是全额伤害。
            // 走 player.damage 仍然经过原版护甲/附魔/吸收，只是数值被压过。
            float full = (float) ghost.getAttributeValue(
                    net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
            float amount = Math.max(1.0F, full * BLOCK_DAMAGE_RATIO);
            boolean hit = player.damage(ghost.getDamageSources().mobAttack(ghost), amount);

            if (hit) {
                entry.revival += REVIVAL_PER_HIT;
                world.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_HURT,
                        SoundCategory.HOSTILE, 0.6F, 1.2F);
            }
            return hit;
        } finally {
            ghost.discard();
        }
    }

    // ---------------------------------------------------------------- 破封

    /**
     * 方块被玩家打破：鬼直接爬出来，不打人。
     */
    public static void onBroken(ServerWorld world, BlockPos pos) {
        HauntStore store = HauntStore.of(world);
        HauntStore.Entry entry = store.get(pos);
        if (entry == null) {
            return;
        }
        release(world, pos, entry.ghostType, null);
        store.remove(pos);
    }

    /** 把鬼实体放出来，落在方块位置上 */
    private static void release(ServerWorld world, BlockPos pos, Identifier ghostType,
                                PlayerEntity target) {
        EntityType<?> type = Registries.ENTITY_TYPE.getOrEmpty(ghostType).orElse(null);
        if (type == null) {
            return;
        }
        Entity entity = type.create(world);
        if (!(entity instanceof GhostEntity ghost)) {
            if (entity != null) {
                entity.discard();
            }
            return;
        }
        ghost.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (target != null) {
            ghost.setTarget(target);
        }
        world.spawnEntity(ghost);

        world.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                SoundCategory.HOSTILE, 1.0F, 0.6F);
        if (target instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.literal("§4厉鬼复苏 §8方块里的东西爬了出来"), true);
        }
    }

    // ---------------------------------------------------------------- 视觉

    /**
     * 给"身上带灵异物品"的玩家一点提示。
     *
     * <p>只向这类玩家发粒子包：普通玩家根本收不到，不是"生成了但看不见"。
     * 这也是为什么不用方块自带光照 —— 那会让所有人都重算光照。
     */
    public static void spawnHints(ServerWorld world) {
        HauntStore store = HauntStore.of(world);
        if (store.haunts().isEmpty()) {
            return;
        }
        if (world.getTime() % 20L != 0L) {
            return;
        }

        for (Map.Entry<BlockPos, HauntStore.Entry> item : store.haunts().entrySet()) {
            BlockPos pos = item.getKey();
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.squaredDistanceTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                        > SIGHT_RADIUS * SIGHT_RADIUS) {
                    continue;
                }
                if (!carriesSpiritItem(player)) {
                    continue;
                }
                world.spawnParticles(ParticleTypes.SOUL,
                        pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D,
                        2, 0.2D, 0.05D, 0.2D, 0.01D);
            }
        }
    }

    /** 身上（含副手、护甲）有任何一件带规律的物品，就算"能感知到" */
    private static boolean carriesSpiritItem(PlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (ItemGhostHost.moduleCount(stack) > 0) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (ItemGhostHost.moduleCount(stack) > 0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 内部

    private static PlayerEntity nearestPlayer(ServerWorld world, BlockPos pos) {
        PlayerEntity best = null;
        double bestDistance = TRIGGER_RADIUS * TRIGGER_RADIUS;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            double distance = player.squaredDistanceTo(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static GhostEntity freshShadow(ServerWorld world, Identifier type, BlockPos pos) {
        EntityType<?> entityType = Registries.ENTITY_TYPE.getOrEmpty(type).orElse(null);
        if (entityType == null) {
            return null;
        }
        Entity created = entityType.create(world);
        if (!(created instanceof GhostEntity ghost)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }
        ghost.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return ghost;
    }

    /** 目前没有需要清空的静态缓存，保留方法给上层统一调用 */
    public static void clearCache() {
        // 影子改为每次新建后这里不再持有任何引用
    }
}
