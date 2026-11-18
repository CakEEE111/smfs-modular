package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.entity.PijiaguBoneEntity;
import com.example.smfsaddon.entity.PijiaguSkinEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

/**
 * 皮与骨的自然生成 —— 不用生物群系刷怪表，改成"玩家周围的黑暗里自己冒出来"。
 *
 * <h2>为什么不用 BiomeModifications</h2>
 *
 * <p>试过 {@code addSpawn + SpawnRestriction}，初始化阶段直接把客户端卡死（日志停在模组初始化之后）。
 * 改成服务端定时器：每 20 秒给每个玩家一次机会，在他周围 16~40 格的黑暗地面上放一只。
 * 好处是可控、可见、也不依赖注册表时机。</p>
 *
 * <ul>
 *   <li>只在主世界、只在夜晚</li>
 *   <li>方块光照 ≤ 7、脚下实心、该位置无流体</li>
 *   <li>玩家周围 64 格内已有 3 只同类就不再生成（防止围死）</li>
 *   <li>骨 : 皮 ≈ 3 : 1</li>
 * </ul>
 */
public final class PijiaguSpawns {

    /** 判定周期（tick），可用 /pijiagu spawnrate 调 */
    public static int PERIOD = 400;
    /** 单次判定的成功率 */
    public static float CHANCE = 0.35F;
    /** 附近最多几只 */
    public static int MAX_NEARBY = 3;

    private PijiaguSpawns() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % PERIOD != 0) {
                return;
            }
            for (ServerWorld world : server.getWorlds()) {
                if (world.getRegistryKey() != World.OVERWORLD || !world.isNight()) {
                    continue;
                }
                for (ServerPlayerEntity player : world.getPlayers()) {
                    if (world.getRandom().nextFloat() > CHANCE) {
                        continue;
                    }
                    trySpawn(world, player);
                }
            }
        });
    }

    /** 手动触发一次（指令用），返回是否成功 */
    public static boolean forceSpawn(ServerWorld world, ServerPlayerEntity player) {
        return attempt(world, player);
    }

    private static void trySpawn(ServerWorld world, ServerPlayerEntity player) {
        attempt(world, player);
    }

    private static boolean attempt(ServerWorld world, ServerPlayerEntity player) {
        long bones = world.getEntitiesByClass(PijiaguBoneEntity.class,
                player.getBoundingBox().expand(64.0D), Entity::isAlive).size();
        long skins = world.getEntitiesByClass(PijiaguSkinEntity.class,
                player.getBoundingBox().expand(64.0D), Entity::isAlive).size();
        if (bones + skins >= MAX_NEARBY) {
            return false;
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = world.getRandom().nextBetween(-40, 40);
            int dz = world.getRandom().nextBetween(-40, 40);
            if (Math.abs(dx) < 16 && Math.abs(dz) < 16) {
                continue;           // 别贴脸刷
            }
            BlockPos base = player.getBlockPos().add(dx, 0, dz);
            BlockPos pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, base);
            if (world.getLightLevel(LightType.BLOCK, pos) > 7) {
                continue;
            }
            if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                continue;
            }
            if (!world.getBlockState(pos).getFluidState().isEmpty()) {
                continue;
            }

            boolean spawnBone = world.getRandom().nextFloat() < 0.75F;
            Entity entity = spawnBone
                    ? SmfsAddon.PIJIAGU_BONE.create(world)
                    : SmfsAddon.PIJIAGU_SKIN.create(world);
            if (entity == null) {
                return false;
            }
            entity.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    world.getRandom().nextFloat() * 360.0F, 0.0F);
            world.spawnEntity(entity);
            SmfsAddon.LOGGER.info("[smfsaddon] 自然生成：{} 于 {}",
                    spawnBone ? "骨" : "皮", pos.toShortString());
            return true;
        }
        return false;
    }
}
