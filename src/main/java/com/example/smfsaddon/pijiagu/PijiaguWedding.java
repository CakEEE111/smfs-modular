package com.example.smfsaddon.pijiagu;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.entity.PijiaguBoneEntity;
import com.example.smfsaddon.entity.PijiaguHallEntity;
import com.example.smfsaddon.entity.PijiaguSkinEntity;
import com.example.smfsaddon.entity.PijiaguUnionEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 皮嫁骨的婚礼：从"两只都在场"到"礼成体"这一段。
 *
 * <h2>触发</h2>
 *
 * <p>新娘、新郎、活人同时在场 —— 三者凑齐，礼堂就长出来。没有主动右键、
 * 没有前置条件，玩家往往是在打其中一只时突然发现脚下多了个礼堂。</p>
 *
 * <h2>为什么不可打断</h2>
 *
 * <p>这是设计定稿的选择：仪式一旦开始就演完，玩家唯一能做的是<b>看</b>。
 * 所以礼堂免疫一切伤害、不参与 AI，连攻击判定都不返回 true。</p>
 *
 * <h2>结束</h2>
 *
 * <p>{@link #finish} 在礼堂动画播完时调用：原地生成礼成体，礼堂与两只散去。
 * 剩下那只 S 级顶端的礼成体，就是这场婚礼的"结果"。</p>
 */
public final class PijiaguWedding {

    /** 触发半径：新娘与新郎彼此、以及玩家，都要在这个距离内 */
    private static final double TRIGGER_RANGE = 12.0D;

    /** 礼堂与玩家的最小距离：小于这个值会罩住玩家 */
    private static final double MIN_HALL_DISTANCE = 9.0D;

    /** 礼堂放在两者中点，抬高一点免得陷进地里 */
    private static final double HALL_LIFT = 0.0D;

    private PijiaguWedding() {
    }

    /**
     * 每 tick 检查一次能不能开场。
     *
     * <p>从新娘那边调用 —— 她通常是先到场的那个。</p>
     */
    public static void checkStart(ServerWorld world, net.minecraft.entity.Entity caller) {
        // 每 20 tick 检查一次，且只由其中一方发起，避免两边同时开场
        if (caller.age % 20 != 0 || !caller.isAlive()) {
            return;
        }

        // 两边谁先 tick 都行：直接从 caller 半径内把另一半找出来
        boolean callerIsBride = caller instanceof PijiaguSkinEntity;
        PijiaguSkinEntity bride = callerIsBride
                ? (PijiaguSkinEntity) caller
                : world.getEntitiesByClass(PijiaguSkinEntity.class,
                        caller.getBoundingBox().expand(TRIGGER_RANGE), Entity::isAlive)
                        .stream().findFirst().orElse(null);
        PijiaguBoneEntity groom = callerIsBride
                ? world.getEntitiesByClass(PijiaguBoneEntity.class,
                        caller.getBoundingBox().expand(TRIGGER_RANGE), Entity::isAlive)
                        .stream().findFirst().orElse(null)
                : (PijiaguBoneEntity) caller;

        // 心跳日志：每 5 秒一次，能看到到底卡在哪一环
        if (caller.age % 100 == 0) {
            SmfsAddon.LOGGER.info("[smfsaddon] 婚礼检查：新娘={} 新郎={}",
                    bride == null ? "无" : "在场", groom == null ? "无" : "在场");
        }
        // 主动献祭那条路：新郎缺席，但"已经剪掉自己皮的玩家"站在新娘面前，
        // 也算这场婚礼的另一半 —— 他和自己的皮结婚。
        ServerPlayerEntity boneGroom = null;
        if (bride != null && groom == null) {
            boneGroom = world.getPlayers().stream()
                    .filter(p -> !p.isSpectator()
                            && p.hasStatusEffect(SmfsAddon.FLESH_REMOVED)
                            && p.squaredDistanceTo(bride) <= TRIGGER_RANGE * TRIGGER_RANGE)
                    .min(java.util.Comparator.comparingDouble(p -> p.squaredDistanceTo(bride)))
                    .orElse(null);
        }
        // 玩家路线（和自己的皮成亲）不再自动触发 —— 必须自己潜行右键"起礼"，
        // 见 tryStartByPlayer()。这里只处理"新郎实体在场"的那条路。
        if (bride == null || groom == null) {
            return;
        }

        // 已经有一场在进行，别重复开
        var existing = world.getEntitiesByClass(PijiaguHallEntity.class,
                bride.getBoundingBox().expand(48.0D), Entity::isAlive);
        if (!existing.isEmpty()) {
            if (caller.age % 100 == 0) {
                SmfsAddon.LOGGER.info("[smfsaddon] 婚礼未触发：48 格内已有 {} 座礼堂（age={}）",
                        existing.size(), existing.get(0).age);
            }
            return;
        }

        ServerPlayerEntity player = null;
        for (ServerPlayerEntity candidate : world.getPlayers()) {
            if (candidate.isSpectator()) {
                continue;
            }
            if (candidate.squaredDistanceTo(bride) > TRIGGER_RANGE * TRIGGER_RANGE) {
                continue;
            }
            player = candidate;
            break;
        }
        if (player == null) {
            if (caller.age % 100 == 0) {
                SmfsAddon.LOGGER.info("[smfsaddon] 婚礼未触发：新娘 {} 格内没有可参与的玩家",
                        (int) TRIGGER_RANGE);
            }
            return;
        }

        Vec3d other = groom != null ? groom.getPos() : boneGroom.getPos();
        Vec3d center = bride.getPos().add(other).multiply(0.5D)
                .add(0.0D, HALL_LIFT, 0.0D);

        // 礼堂不能罩在玩家头上：太近就沿"远离玩家"的方向推开，
        // 否则玩家会站在模型内部，镜头再远也只能拍到梁柱。
        Vec3d away = new Vec3d(center.x - player.getX(), 0.0D, center.z - player.getZ());
        if (away.lengthSquared() < 1.0E-4D) {
            away = new Vec3d(-Math.sin(Math.toRadians(player.getYaw())), 0.0D,
                    Math.cos(Math.toRadians(player.getYaw())));
        }
        away = away.normalize();
        double distance = Math.sqrt(player.squaredDistanceTo(center));
        if (distance < MIN_HALL_DISTANCE) {
            center = center.add(away.multiply(MIN_HALL_DISTANCE - distance));
        }
        PijiaguHallEntity hall = SmfsAddon.PIJIAGU_HALL.create(world);
        if (hall == null) {
            return;
        }
        hall.refreshPositionAndAngles(center.x, center.y, center.z, bride.getYaw(), 0.0F);
        world.spawnEntity(hall);

        // 自然成亲（皮 + 骨）：两位主角都并入礼堂模型 —— 模型自带新娘与鬼新郎，
        // 由动画自己走进来、自己拜。玩家只是旁观者，不动他，也不留实体
        // （否则场上会出现第二个新娘）。
        bride.discard();
        groom.discard();

        SmfsAddon.LOGGER.info("[smfsaddon] 皮嫁骨婚礼开始：{} 在场，礼堂降临于 {}",
                player.getName().getString(), BlockPos.ofFloored(center).toShortString());
    }

    // ---------------------------------------------------------------- 主动起礼

    /** 礼堂摆位朝向偏移（度），可用 /pijiagu yaw 调整 */
    public static float YAW_OFFSET = 180.0F;

    /** 起礼需要的灵异强度 */
    private static final float COST_SPIRIT_STRENGTH = 3000.0F;
    /** 起礼需要的灵力 */
    private static final float COST_SPIRIT = 400.0F;
    /** 代价：生命上限 / 灵异抗性 / 灵力上限 */
    private static final int PENALTY_HEALTH = -4;
    private static final double PENALTY_RESISTANCE = -20.0D;
    private static final double PENALTY_MAX_SPIRIT_RATIO = -0.25D;

    /**
     * 玩家主动起礼：站在自己的皮面前，潜行 + 右键。
     *
     * <h2>为什么要主动</h2>
     *
     * <p>变成异类是这个模组里最大的转折，不能"走过去就发生了"。
     * 玩家必须自己下这个决心，并且付得起代价。</p>
     *
     * <h2>代价（一次性，永久）</h2>
     *
     * <ul>
     *   <li>生命上限 −4（叠在剪皮的 −2 之上）</li>
     *   <li>灵异抗性 −20（同样叠加）</li>
     *   <li>灵力上限 −25%：你已经不是"人"，灵力的容器也变了</li>
     * </ul>
     *
     * <p>前置条件：已剪皮、灵异强度 ≥ 3000、当前灵力 ≥ 400。</p>
     */
    public static boolean tryStartByPlayer(ServerWorld world, PijiaguSkinEntity bride,
                                           ServerPlayerEntity player) {
        if (!player.hasStatusEffect(SmfsAddon.FLESH_REMOVED)) {
            player.sendMessage(net.minecraft.text.Text.literal("§7你身上还有皮，这堂拜不了。"), true);
            return false;
        }

        // 面板上的数值存在玩家 NBT 的 spiritAttributes 里，
        // getSpiritAttribute(PlayerEntity, EntityAttribute) 读的是属性基础值（一直是 0），必须用字符串重载
        float strength = com.xie.smfs.common.events.PlayerEvents
                .getSpiritAttribute(player, "currentSpirit", 0.0F);
        float spiritMax = com.xie.smfs.common.events.PlayerEvents
                .getSpiritAttribute(player, "maxSpirit", 0.0F);
        SmfsAddon.LOGGER.info("[smfsaddon] 起礼检查：{} 灵力上限={} 当前={}",
                player.getName().getString(), spiritMax, strength);
        if (spiritMax < COST_SPIRIT_STRENGTH) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "§7供不起这场礼：灵力上限不足 " + (int) COST_SPIRIT_STRENGTH + "。"), true);
            return false;
        }
        if (strength < COST_SPIRIT) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "§7力竭：至少要有 " + (int) COST_SPIRIT + " 点灵力。"), true);
            return false;
        }

        // 已经有一场在进行
        var existing = world.getEntitiesByClass(PijiaguHallEntity.class,
                bride.getBoundingBox().expand(48.0D), Entity::isAlive);
        if (!existing.isEmpty()) {
            return false;
        }

        // ── 付代价
        applyPermanentCost(player);
        com.xie.smfs.common.events.PlayerEvents.subtractSpiritAttributeValue(player,
                com.xie.smfs.effect.SpiritAttributes.CURRENT_SPIRIT, COST_SPIRIT);

        player.sendMessage(net.minecraft.text.Text.literal("§d你把自己的骨交了出去。"), false);
        player.sendMessage(net.minecraft.text.Text.literal("§7礼堂要来了。"), false);
        beginCeremony(world, bride, player);
        return true;
    }

    /** 一次性永久代价 */
    private static void applyPermanentCost(ServerPlayerEntity player) {
        var health = player.getAttributeInstance(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    java.util.UUID.nameUUIDFromBytes("pijiagu_wedding_health".getBytes()),
                    "pijiagu_wedding_health", PENALTY_HEALTH,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
        }
        var resistance = player.getAttributeInstance(
                com.xie.smfs.effect.SpiritAttributes.SPIRIT_RESISTANCE);
        if (resistance != null) {
            resistance.addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    java.util.UUID.nameUUIDFromBytes("pijiagu_wedding_res".getBytes()),
                    "pijiagu_wedding_res", PENALTY_RESISTANCE,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADDITION));
        }
        var maxSpirit = player.getAttributeInstance(
                com.xie.smfs.effect.SpiritAttributes.MAX_SPIRIT);
        if (maxSpirit != null) {
            maxSpirit.addPersistentModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    java.util.UUID.nameUUIDFromBytes("pijiagu_wedding_maxspirit".getBytes()),
                    "pijiagu_wedding_maxspirit", PENALTY_MAX_SPIRIT_RATIO,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * 礼堂降临 —— 这次反过来：<b>玩家原地不动，礼堂搬到他脚下</b>。
     *
     * <p>之前是"让玩家走上动画里的那条路"，坐标系反复调不准。
     * 换思路之后只有一次对齐计算：</p>
     *
     * <pre>
     * 目标：礼堂的新郎锚点 = 玩家现在站的位置
     * 做法：hallPos = playerPos − R(hallYaw) · groomOffset
     *       hallYaw = playerYaw + 180（礼堂正面朝向玩家原本面对的方向）
     * </pre>
     *
     * <p>新郎锚点取模型里的 {@code groom_socket}（-10, 0, -4），单位换算 2/16。</p>
     */
    private static void beginCeremony(ServerWorld world, PijiaguSkinEntity bride,
                                      ServerPlayerEntity player) {
        Vec3d playerPos = player.getPos();
        float playerYaw = player.getYaw();

        // 模型里的新郎锚点（单位 → 格）
        double unit = 2.0D / 16.0D;
        // 与礼堂里的 standingSpot() 保持一致：groom_socket.x 再往左让 16 单位
        double mx = 10.0D * unit;       // 动画里新郎实际在 +x 侧
        double mz = 4.0D * unit;        // groom_socket.z 取反

        // 礼堂摆位的朝向偏移 —— 模型正面对着实体 yaw 的哪一侧不确定，
        // 用 /pijiagu yaw <度> 现场调，调到"从正门直线走进来"为止
        float hallYaw = playerYaw + YAW_OFFSET;
        double rad = Math.toRadians(hallYaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double wx = mx * cos + mz * sin;
        double wz = mx * sin - mz * cos;

        Vec3d hallPos = new Vec3d(playerPos.x - wx, playerPos.y, playerPos.z - wz);

        PijiaguHallEntity hall = SmfsAddon.PIJIAGU_HALL.create(world);
        if (hall == null) {
            return;
        }
        hall.refreshPositionAndAngles(hallPos.x, hallPos.y, hallPos.z, hallYaw, 0.0F);
        world.spawnEntity(hall);

        // 玩家钉在原地，朝向保持原样
        hall.setCeremonyGroom(player.getUuid(), playerPos, playerYaw);
        hall.setGroomAnchor(playerPos, playerYaw);

        // 这具"皮"不再需要了：礼堂模型里自带新娘（root），她会自己走进来拜堂。
        // 留着只会卡在桌椅里，还会和模型里的新娘重复。
        bride.discard();

        SmfsAddon.LOGGER.info("[smfsaddon] 主动起礼：{} 原地不动，礼堂搬到他脚下",
                player.getName().getString());
    }

    /** 测试用：无视前置条件与代价，直接起礼（附近没有皮就先召一具） */
    /** 清掉残留的礼堂与皮（测试时反复起礼会叠好几座，看起来像"位置偏了"） */
    public static int cleanup(ServerWorld world, net.minecraft.util.math.Vec3d around) {
        int removed = 0;
        for (PijiaguHallEntity hall : world.getEntitiesByClass(PijiaguHallEntity.class,
                new net.minecraft.util.math.Box(around, around).expand(128.0D), Entity::isAlive)) {
            hall.discard();
            removed++;
        }
        for (PijiaguSkinEntity skin : world.getEntitiesByClass(PijiaguSkinEntity.class,
                new net.minecraft.util.math.Box(around, around).expand(128.0D), Entity::isAlive)) {
            skin.discard();
            removed++;
        }
        return removed;
    }

    public static void forceStart(ServerWorld world, ServerPlayerEntity player) {
        int cleared = cleanup(world, player.getPos());
        if (cleared > 0) {
            SmfsAddon.LOGGER.info("[smfsaddon] 起礼前清场：移除 {} 个残留对象", cleared);
        }
        PijiaguSkinEntity bride = world.getEntitiesByClass(PijiaguSkinEntity.class,
                        player.getBoundingBox().expand(16.0D), Entity::isAlive)
                .stream().findFirst().orElse(null);
        if (bride == null) {
            bride = SmfsAddon.PIJIAGU_SKIN.create(world);
            if (bride == null) {
                return;
            }
            double yaw = Math.toRadians(player.getYaw());
            bride.refreshPositionAndAngles(
                    player.getX() + Math.sin(yaw) * 3.0D, player.getY(),
                    player.getZ() - Math.cos(yaw) * 3.0D, player.getYaw() + 180.0F, 0.0F);
            world.spawnEntity(bride);
        }
        SmfsAddon.LOGGER.info("[smfsaddon] 测试指令强制起礼：{}", player.getName().getString());
        beginCeremony(world, bride, player);
    }

    /** 礼堂演完：原地留下礼成体，其余散场 */
    public static void finish(ServerWorld world, PijiaguHallEntity hall) {
        if (hall.isRemoved()) {
            return;
        }
        Vec3d pos = hall.getPos();

        // 如果现场有位"已经剪掉自己的皮"的玩家，礼成体就归他 —— 这是主动献祭那条路
        ServerPlayerEntity owner = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity nearby : world.getPlayers()) {
            if (!nearby.hasStatusEffect(SmfsAddon.FLESH_REMOVED)) {
                continue;
            }
            double dist = nearby.squaredDistanceTo(pos);
            if (dist < best && dist < 48.0D * 48.0D) {
                best = dist;
                owner = nearby;
            }
        }

        if (owner != null && UnionCapture.capture(owner)) {
            // 驾驭成功：礼成体不再作为敌方存在，直接归玩家槽位
            SmfsAddon.LOGGER.info("[smfsaddon] 礼成体被 {} 驾驭，不生成敌对实体",
                    owner.getName().getString());
        } else {
            PijiaguUnionEntity union = SmfsAddon.PIJIAGU_UNION.create(world);
            if (union != null) {
                union.refreshPositionAndAngles(pos.x, pos.y, pos.z, hall.getYaw(), 0.0F);
                world.spawnEntity(union);
            }
        }

        // 新娘与新郎并入礼成体：它们已经不是独立的存在了。
        // 半径给到 96：玩家可能把皮召得比较远，或者路上又召了一具。
        int cleared = 0;
        for (PijiaguSkinEntity bride : world.getEntitiesByClass(PijiaguSkinEntity.class,
                hall.getBoundingBox().expand(96.0D), Entity::isAlive)) {
            bride.discard();
            cleared++;
        }
        SmfsAddon.LOGGER.info("[smfsaddon] 婚礼结束：清掉 {} 具皮", cleared);
        for (PijiaguBoneEntity groom : world.getEntitiesByClass(PijiaguBoneEntity.class,
                hall.getBoundingBox().expand(32.0D), Entity::isAlive)) {
            groom.discard();
        }

        // 视角锁可以解了 —— 该跑了
        for (ServerPlayerEntity nearby : world.getPlayers()) {
            CameraLock.release(nearby);
        }

        hall.discard();
        SmfsAddon.LOGGER.info("[smfsaddon] 礼成：礼成体出现于 {}",
                BlockPos.ofFloored(pos).toShortString());
    }
}
