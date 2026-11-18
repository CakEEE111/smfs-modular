package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.block.HallBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「席面」—— 礼堂从地里长出来。
 *
 * <h2>基准（唯一真理）</h2>
 *
 * <p>{@code base} = <b>玩家脚下那一格</b>（玩家站的地面）。整个建筑只用这一个基准，
 * 不扫地形、不重算、不做任何相对位置推断 —— 之前加过"扫描地表找平"，
 * 结果基准被改坏，建筑飘到天上去了。</p>
 *
 * <p>第 0 层 = 石砖地基（把脚下这一层直接换成实体，保证平地可站）</p>
 * <p>第 1 层 = 红毯（1 像素薄板）</p>
 * <p>第 2 层起 = 柱、墙、供桌</p>
 * <p>第 9 层起 = 天棚 + 瓦顶</p>
 */
public final class FeastSite {

    /** 半宽：17×17 */
    private static final int R = 8;
    /** 柱高（从红毯那一层数起） */
    private static final int COLUMN = 6;
    /** 每层延迟 tick */
    private static final int LAYER = 5;

    private static final List<Step> STEPS = new ArrayList<>();
    /** 已经放下去、还在场上的方块 —— 席散时按这份清单拆 */
    private static final List<Step> PLACED = new ArrayList<>();

    private record Step(ServerWorld world, BlockPos pos, BlockState state, long tickAt,
                        boolean remove) {
    }

    private FeastSite() {
    }

    /**
     * 在玩家脚下长出席面。
     *
     * <p>蓝图来自 {@link HallBlueprint}（17×17×12），按它给出时间表逐层放置：
     * 地面先铺 → 墙柱长高 → 红绸盖棺 → 梁架 → 屋顶收拢 → 正脊落下，全程约 4.5 秒。</p>
     */
    public static long rise(ServerWorld world, BlockPos feetBlock) {
        long now = world.getTime();
        BlockPos origin = feetBlock;      // 蓝图 y=0 就是玩家脚所在那一层

        BlockState stone = Blocks.STONE_BRICKS.getDefaultState();

        // 地形比 y=0 低的地方补石砖，避免红毯悬空
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                BlockPos column = origin.add(x, 0, z);
                int surface = surfaceY(world, column);
                for (int y = surface + 1; y < origin.getY(); y++) {
                    add(world, new BlockPos(column.getX(), y, column.getZ()), stone, now);
                }
            }
        }

        // 各层的放置时刻（tick），与蓝图文档的时间表一致
        int[] layerTick = {0, 20, 25, 30, 35, 40, 55, 60, 65, 70, 75, 90};

        int batched = 0;
        String[][] layers = HallBlueprint.LAYERS;
        final int LIFT = 2;          // 屋顶整体上移，净高从 5 提到 7~8
        final int SCALE = 3;         // 面积放大三倍（每个蓝图格铺成 3×3）
        for (int y = 0; y < layers.length; y++) {
            String[] rows = layers[y];
            // 墙只到 y=5，上移后中间会空两格 —— 用第 5 层的图案补齐
            if (y == 6) {
                for (int extra = 6; extra <= 5 + LIFT; extra++) {
                    for (int row = 0; row < layers[5].length; row++) {
                        String line = layers[5][row];
                        for (int col = 0; col < line.length(); col++) {
                            BlockState state = material(line.charAt(col));
                            if (state != null) {
                                int bx = (col - 8) * SCALE;
                                int bz = (row - 8) * SCALE;
                                for (int dx = 0; dx < SCALE; dx++) {
                                    for (int dz = 0; dz < SCALE; dz++) {
                                        add(world, origin.add(bx + dx, extra, bz + dz),
                                                state, now + layerTick[5] + (batched++ / 400));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (int row = 0; row < rows.length; row++) {
                String line = rows[row];
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    BlockState state = material(symbol);
                    if (state == null) {
                        continue;
                    }
                    int x = (col - 8) * SCALE;        // 列 → x（放大三倍）
                    int z = (row - 8) * SCALE;        // 行 → z（放大三倍）
                    int targetY = y >= 6 ? y + LIFT : y;   // 梁与屋顶上移
                    // 每个格铺成 SCALE×SCALE，并按索引分批（每批 400 块）避免一次性卡顿
                    for (int dx = 0; dx < SCALE; dx++) {
                        for (int dz = 0; dz < SCALE; dz++) {
                            long tickAt = now + layerTick[y] + (batched++ / 400);
                            add(world, origin.add(x + dx, targetY, z + dz), state, tickAt);
                        }
                    }
                }
            }
        }

        long end = now + layerTick[layerTick.length - 1] + 20;
        world.playSound(null, origin, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.HOSTILE, 1.6F, 0.55F);

        // 礼成之后：一个接一个，玩家越看越不对劲
        aftermath(world, origin, end);

        return end;
    }

    /**
     * 礼成之后的三件事（蓝图文档建议 1~3）。
     *
     * <ol>
     *   <li><b>吊灯熄灭</b> —— 头顶的光没了，只剩棺后两点烛火</li>
     *   <li><b>落帘封门</b> —— 再追加红帘把正门填满，退路没了</li>
     *   <li><b>身后多一个纸人</b> —— 站在新郎该站的位置，玩家转身才会发现</li>
     * </ol>
     */
    private static void aftermath(ServerWorld world, BlockPos origin, long end) {
        BlockState drape = HallBlocks.HALL_DRAPE.getDefaultState();
        BlockState wall = HallBlocks.HALL_WALL.getDefaultState();
        BlockState pillar = HallBlocks.HALL_PILLAR.getDefaultState();

        final int S = 3;      // 与主体一致的三倍放大
        final int LIFT = 2;   // 屋顶上移量

        // ① 1 秒后：中央吊灯换成红帘（蓝图 y=7 的吊灯，实际上移到了 y=9）
        add(world, origin.add(0, 7 + LIFT, 0), drape, end + 20);

        // ② 2 秒后：正门落帘封死（门在蓝图 z=4 → 放大后 z=12，宽 ±2 → ±6）
        long sealTick = end + 40;
        for (int x = -2 * S; x <= 2 * S; x++) {
            for (int y = 0; y <= 4; y++) {
                add(world, origin.add(x, y, 4 * S), drape, sealTick);
            }
        }
        // 门框上下各加一道，避免只封中间一段
        for (int x = -2 * S; x <= 2 * S; x++) {
            add(world, origin.add(x, 5, 4 * S), drape, sealTick);
        }

        // ③ 同一时刻：拜堂位背后多出一个纸人（按三倍尺寸：3×3 底座 + 3×3 衣 + 3×3 头）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                add(world, origin.add(dx, 0, 3 * S + dz), pillar, sealTick);
                add(world, origin.add(dx, 1, 3 * S + dz), drape, sealTick);
                add(world, origin.add(dx, 2, 3 * S + dz), wall, sealTick);
            }
        }

        world.playSound(null, origin.add(0, 2, 4 * S), SoundEvents.BLOCK_WOOL_PLACE,
                SoundCategory.HOSTILE, 1.4F, 0.6F);
    }

    /** 字符 → 方块 */
    private static BlockState material(char symbol) {
        return switch (symbol) {
            case 'P' -> HallBlocks.HALL_PILLAR.getDefaultState();
            case 'W' -> HallBlocks.HALL_WALL.getDefaultState();
            case 'D' -> HallBlocks.HALL_DRAPE.getDefaultState();
            case 'R' -> HallBlocks.HALL_ROOF.getDefaultState();
            case 'C' -> HallBlocks.HALL_CARPET.getDefaultState();
            case 'L' -> HallBlocks.HALL_LANTERN.getDefaultState();
            default -> null;
        };
    }

    /** 该列往下找地表 */
    private static int surfaceY(ServerWorld world, BlockPos pos) {
        BlockPos.Mutable probe = pos.mutableCopy();
        for (int i = 0; i < 8; i++) {
            if (!world.getBlockState(probe).isAir()) {
                return probe.getY();
            }
            probe.move(0, -1, 0);
        }
        return pos.getY() - 1;
    }

    /** 席散：从高到低拆 */
    public static void dissolve(ServerWorld world, BlockPos center) {
        long now = world.getTime();
        List<Step> mine = new ArrayList<>();
        for (Step step : PLACED) {
            if (step.world() == world && step.pos().isWithinDistance(center, 90.0D)) {
                mine.add(step);
            }
        }
        if (mine.isEmpty()) {
            return;
        }
        mine.sort((a, b) -> Integer.compare(b.pos().getY(), a.pos().getY()));
        // 拆场时间按块数分摊：整场固定约 4 秒（80 tick），不再随块数线性变长
        int total = mine.size();
        int batches = Math.max(1, 80);
        int i = 0;
        for (Step step : mine) {
            long tickAt = now + (long) i * batches / total;
            STEPS.add(new Step(world, step.pos(), step.state(), tickAt, true));
            i++;
        }
        com.example.smfsaddon.SmfsAddon.LOGGER.info("[smfsaddon] 席散：{} 块，4 秒内拆完", total);
        world.playSound(null, center, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 1.2F, 0.5F);
    }

    private static void add(ServerWorld world, BlockPos pos, BlockState state, long tickAt) {
        STEPS.add(new Step(world, pos, state, tickAt, false));
    }

    /** 允许覆盖已有方块（地基用） */
    private static void force(ServerWorld world, BlockPos pos, BlockState state, long tickAt) {
        STEPS.add(new Step(world, pos, state, tickAt, false));
    }

    public static void tick(MinecraftServer server) {
        if (STEPS.isEmpty()) {
            return;
        }
        Iterator<Step> it = STEPS.iterator();
        List<Step> done = new ArrayList<>();
        while (it.hasNext()) {
            Step step = it.next();
            if (step.world().getTime() < step.tickAt()) {
                continue;
            }
            if (step.remove()) {
                if (step.world().getBlockState(step.pos()).getBlock() == step.state().getBlock()) {
                    step.world().removeBlock(step.pos(), false);
                }
                PLACED.removeIf(p -> p.world() == step.world() && p.pos().equals(step.pos()));
            } else {
                step.world().setBlockState(step.pos(), step.state());
                PLACED.add(step);       // 登记，席散时要拆
            }
            done.add(step);
        }
        STEPS.removeAll(done);
    }

    public static boolean isBusy() {
        return !STEPS.isEmpty();
    }

    /** 清空待放置 / 已放置清单（停服或异常时用，不拆方块） */
    public static void reset() {
        STEPS.clear();
        PLACED.clear();
    }
}
