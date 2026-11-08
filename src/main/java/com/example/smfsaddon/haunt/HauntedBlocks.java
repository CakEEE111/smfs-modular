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
     *
     * <p>这个系数乘的是<b>灵异伤害</b>（{@code getSpiritualDamage}），不是物理攻击力 ——
     * 乘完之后仍走本体的 {@code PlayerEvents.handleSpiritDamage} 结算。
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

    // ---------------------------------------------------------------- 新区块

    /**
     * 待处理的新区块。
     *
     * <p>区块加载可能发生在别的线程，直接写 PersistentState 不安全，先入队，
     * 统一在世界 tick 里处理。
     */
    private static final Map<ServerWorld, java.util.Queue<Long>> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 每轮最多处理几个，避免进图时卡顿 */
    private static final int ROLL_BUDGET = 16;

    /** 区块加载时登记。真正会不会生成，在 tick 里按概率与总量上限决定 */
    public static void onChunkLoad(ServerWorld world, int chunkX, int chunkZ) {
        com.example.smfsaddon.config.AddonConfig.Haunt config =
                com.example.smfsaddon.config.AddonConfig.get().haunt;
        if (!config.enabled || config.newChunkChance <= 0.0D) {
            return;
        }
        PENDING.computeIfAbsent(world, ignored -> new java.util.concurrent.ConcurrentLinkedQueue<>())
                .add(chunkKey(chunkX, chunkZ));
    }

    private static void processPending(ServerWorld world) {
        java.util.Queue<Long> queue = PENDING.get(world);
        if (queue == null) {
            return;
        }
        com.example.smfsaddon.config.AddonConfig.Haunt config =
                com.example.smfsaddon.config.AddonConfig.get().haunt;
        HauntStore store = HauntStore.of(world);

        for (int i = 0; i < ROLL_BUDGET; i++) {
            Long key = queue.poll();
            if (key == null) {
                return;
            }
            // 到顶就不再生成了 —— 这是防数据无限增长的硬闸
            if (store.size() >= config.maxTotal) {
                queue.clear();
                return;
            }
            try {
                rollChunk(world, store, config, (int) (key >> 32), (int) (long) key);
            } catch (RuntimeException exception) {
                com.example.smfsaddon.SmfsAddon.LOGGER.warn(
                        "[smfsaddon] 新区块鬼方块生成失败：{}", exception.toString());
            }
        }
    }

    /** 单个区块掷骰 */
    private static void rollChunk(ServerWorld world, HauntStore store,
                                  com.example.smfsaddon.config.AddonConfig.Haunt config,
                                  int chunkX, int chunkZ) {
        // 每区块只处理一次：否则重复加载会反复掷骰
        if (!store.markRolled(chunkKey(chunkX, chunkZ))) {
            return;
        }
        if (world.getRandom().nextDouble() >= config.newChunkChance) {
            return;
        }

        int x = chunkX * 16 + world.getRandom().nextInt(16);
        int z = chunkZ * 16 + world.getRandom().nextInt(16);
        BlockPos target = pickSpot(world, x, z, config);
        if (target == null) {
            return;
        }
        List<Identifier> pool = com.example.smfsaddon.crossing.GhostRuleCrossing.ghostTypeIds(world);
        if (pool.isEmpty()) {
            return;
        }
        Identifier ghostType = pool.get(world.getRandom().nextInt(pool.size()));
        store.put(target, new HauntStore.Entry(ghostType));
        com.example.smfsaddon.SmfsAddon.LOGGER.info(
                "[smfsaddon] 野鬼方块（新区块）：{} 埋在 {}（{}）", ghostType,
                target.toShortString(), world.getRegistryKey().getValue());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------- 世界生成

    /**
     * 世界第一次被本模组接管时，在出生点附近布置若干鬼方块。
     *
     * <h2>为什么只在世界创建时做</h2>
     *
     * <p>按区块概率动态生成会让存档里的数据随探索无限增长，而且老存档会被
     * "事后补种" —— 玩家自己基地的地下突然冒出鬼，很难接受。
     *
     * <p>改成一次性：每个世界布置固定数量，位置由世界种子决定（同一个种子
     * 每次开出来一样，方便复现）。之后无论玩家探索多远，都不会再自动增加 ——
     * 想加只能靠 {@code /ghostmodules haunt}。
     */
    public static void initializeWorld(ServerWorld world) {
        com.example.smfsaddon.config.AddonConfig.Haunt config =
                com.example.smfsaddon.config.AddonConfig.get().haunt;
        if (!config.enabled) {
            return;
        }

        HauntStore store = HauntStore.of(world);
        if (store.isInitialized()) {
            return;
        }
        store.markInitialized();
        if (config.initialCount <= 0) {
            return;
        }

        BlockPos spawn = world.getSpawnPos();
        java.util.Random random = new java.util.Random(world.getSeed() ^ 0x5DEECE66DL);
        int placed = 0;
        int attempts = 0;
        int maxAttempts = Math.max(32, config.initialCount * 40);

        while (placed < config.initialCount && attempts < maxAttempts) {
            attempts++;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int minDistance = Math.min(config.minSpawnDistance, config.initialRadius);
            double distance = minDistance
                    + random.nextDouble() * Math.max(1, config.initialRadius - minDistance);
            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos target = pickSpot(world, x, z, config);
            if (target == null) {
                continue;
            }
            List<Identifier> pool = com.example.smfsaddon.crossing.GhostRuleCrossing
                    .ghostTypeIds(world);
            if (pool.isEmpty()) {
                return;
            }
            Identifier ghostType = pool.get(random.nextInt(pool.size()));
            store.put(target, new HauntStore.Entry(ghostType));
            placed++;
            com.example.smfsaddon.SmfsAddon.LOGGER.info(
                    "[smfsaddon] 野鬼方块：{} 埋在 {}（{}）", ghostType, target.toShortString(),
                    world.getRegistryKey().getValue());
        }

        com.example.smfsaddon.SmfsAddon.LOGGER.info(
                "[smfsaddon] 世界初始化完成，布置 {} 个鬼方块（尝试 {} 次）", placed, attempts);
    }

    /**
     * 在某一列找落点：地表往下 1~4 格，必须是实心、非流体。
     *
     * @return {@code null} 表示这一列不合适
     */
    private static BlockPos pickSpot(ServerWorld world, int x, int z,
                                     com.example.smfsaddon.config.AddonConfig.Haunt config) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        // 需要真实地形才能取表面高度，这里同步生成区块（只在世界创建时发生一次）
        world.getChunk(chunkX, chunkZ);

        int top = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= world.getBottomY() + 1) {
            return null;
        }
        BlockPos surface = new BlockPos(x, top - 1, z);
        var surfaceState = world.getBlockState(surface);
        if (surfaceState.isAir() || !surfaceState.isSolidBlock(world, surface)
                || !surfaceState.getFluidState().isEmpty()) {
            return null;
        }

        int depth = config.minDepth + world.getRandom().nextInt(
                Math.max(1, config.maxDepth - config.minDepth + 1));
        BlockPos target = surface.down(depth);
        var targetState = world.getBlockState(target);
        if (targetState.isAir() || !targetState.isSolidBlock(world, target)
                || !targetState.getFluidState().isEmpty()) {
            return null;
        }
        return target;
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
        // 不用每 tick 扫。判定本身是 100 tick 一次、粒子 20 tick 一次，
        // 这里 10 tick 一轮完全够，成本直接降到原来的十分之一。
        long now = world.getTime();
        if (now % 10L != 0L) {
            return;
        }

        processPending(world);

        HauntStore store = HauntStore.of(world);
        if (store.haunts().isEmpty()) {
            return;
        }

        // 以玩家为中心，而不是以方块为中心：
        // 只有玩家附近 3×3 区块里的鬼方块才需要被看，剩下的连遍历都省了。
        Map<Long, List<BlockPos>> buckets = new HashMap<>();
        for (BlockPos pos : store.haunts().keySet()) {
            buckets.computeIfAbsent(chunkKey(pos.getX() >> 4, pos.getZ() >> 4),
                    ignored -> new ArrayList<>()).add(pos);
        }

        List<BlockPos> drop = new ArrayList<>();
        List<BlockPos> touched = new ArrayList<>();

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            int centerX = player.getBlockX() >> 4;
            int centerZ = player.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<BlockPos> list = buckets.get(chunkKey(centerX + dx, centerZ + dz));
                    if (list == null) {
                        continue;
                    }
                    for (BlockPos pos : list) {
                        evaluate(world, store, pos, player, now, drop);
                        touched.add(pos);
                    }
                }
            }
        }

        // 这一轮没人经过的鬼方块：把预警时间清零，下次来人立刻重新响
        for (Map.Entry<BlockPos, HauntStore.Entry> item : store.haunts().entrySet()) {
            if (item.getValue().warnedAt != 0L && !touched.contains(item.getKey())) {
                item.getValue().warnedAt = 0L;
                store.markDirty();
            }
        }

        for (BlockPos pos : drop) {
            store.remove(pos);
        }
    }

    /** 单个玩家与单个鬼方块之间的一次检查 */
    private static void evaluate(ServerWorld world, HauntStore store, BlockPos pos,
                                 ServerPlayerEntity player, long now, List<BlockPos> drop) {
        HauntStore.Entry entry = store.get(pos);
        if (entry == null) {
            return;
        }

        // 方块被别的途径挖掉/替换了：数据跟着清掉，别留幽灵条目
        if (world.getBlockState(pos).isAir()) {
            drop.add(pos);
            return;
        }

        double distance = player.squaredDistanceTo(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distance > TRIGGER_RADIUS * TRIGGER_RADIUS) {
            return;
        }

        // 预警音：进范围就响，之后每 10 秒补一次
        if (now - entry.warnedAt >= WARN_INTERVAL) {
            entry.warnedAt = now;
            store.markDirty();
            world.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                    SoundCategory.HOSTILE, 1.0F, 0.8F + world.getRandom().nextFloat() * 0.4F);
        }

        if (player.isCreative()) {
            // 创造玩家打不掉血，硬判只是假象；但要说清楚，否则测试时以为功能坏了
            if (now % 200L == 0L) {
                player.sendMessage(Text.literal("§7（创造模式不参与鬼方块判定）"), true);
            }
            return;
        }

        if (now - entry.judgedAt < JUDGE_INTERVAL) {
            return;
        }
        entry.judgedAt = now;
        store.markDirty();

        if (judge(world, pos, entry, player) && entry.revival >= MAX_REVIVAL) {
            release(world, pos, entry.ghostType, player);
            drop.add(pos);
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

            // 走本体的灵异伤害结算，不是普通物理伤害。
            //
            // 本体对玩家的灵异伤害有一个统一入口 PlayerEvents.handleSpiritDamage：
            // 双曲线衰减（伤害² / (伤害 + 灵异抗性)），还会触发鬼寿衣、影子鬼这些被动。
            // 自己 player.damage 会绕过这一整套，玩家的灵异抗性堆了也没用。
            float spirit = ghost.getSpiritualDamage() * BLOCK_DAMAGE_RATIO;
            net.minecraft.entity.damage.DamageSource source =
                    com.xie.smfs.damage.ModDamageSources.ghost(world);
            float actual = com.xie.smfs.common.events.PlayerEvents
                    .handleSpiritDamage(player, spirit, spirit, source);
            // 返回 0 表示被鬼寿衣之类完全吃掉，这一下不算命中
            boolean hit = actual > 0.0F && player.damage(source, actual);

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
        processPending(world);

        HauntStore store = HauntStore.of(world);
        if (store.haunts().isEmpty()) {
            return;
        }
        if (world.getTime() % 20L != 0L) {
            return;
        }
        if (world.getPlayers().isEmpty()) {
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
        PENDING.clear();
    }
}
