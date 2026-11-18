package com.example.smfsaddon.entity;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.api.CustomGhostModelProvider;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 皮嫁骨 · 礼堂。
 *
 * <h2>它是什么</h2>
 *
 * <p>婚礼进行的场所。它不是建筑，而是一件"正在发生的事"：新娘与新郎同时在场、
 * 且有人闯进来时，礼堂从地上长出来，把看到的那个活着的人请进席间。</p>
 *
 * <h2>三条硬规则（按设计定稿）</h2>
 *
 * <ol>
 *   <li><b>不吃任何伤害</b>——它是仪式本身，不是可以被砍的东西；</li>
 *   <li><b>没有 AI</b>——不追人、不移动，只在原地放动画；</li>
 *   <li><b>仪式不可打断</b>——16.4 秒降临 + 38.6 秒拜堂，播完就散场，
 *       原地留下一只礼成体，礼堂与新娘新郎一起消失。</li>
 * </ol>
 *
 * <p>时长直接从动画文件取：{@code enter} 16.4s、{@code wedding} 38.6s，
 * 加起来约 55 秒，正好够玩家意识到"跑不掉"。</p>
 */
public class PijiaguHallEntity extends GhostEntity implements CustomGhostModelProvider {

    // 婚礼礼堂：带动画的原模型（新那份静态模型只给 G 技能的守护灵用）
    private static final Identifier MODEL = new Identifier(SmfsAddon.MOD_ID, "geo/entity/pijiagu_hall.geo.json");
    private static final Identifier TEXTURE = new Identifier(SmfsAddon.MOD_ID, "textures/entity/pijiagu_hall.png");
    private static final Identifier ANIMATION = new Identifier(SmfsAddon.MOD_ID, "animations/pijiagu_hall.animation.json");

    /** 降临：16.4 秒 */
    /** 守护灵模式：G 技能召出的那具，跟着主人走、按时散场，不演婚礼 */
    private java.util.UUID guardianOwner;
    private int guardianEndTick;
    private int guardianNextPulse;

    private static final int ENTER_TICKS = (int) (16.4D * 20.0D);

    /** 拜堂：38.6 秒 */
    private static final int WEDDING_TICKS = (int) (38.6D * 20.0D);

    /** 全部演完的时长（自然成亲：入场 + 拜堂） */
    public static final int TOTAL_TICKS = ENTER_TICKS + WEDDING_TICKS;

    /**
     * 这场仪式的实际总时长。
     *
     * <p>玩家婚礼：<b>第三次鞠躬结束就礼成</b>（26.4 秒 + 1 秒收势），
     * 不把 38.6 秒的动画放完 —— 后面那十几秒只是模型在原地，礼已经成了。</p>
     *
     * <p>自然成亲：入场 + 完整拜堂。</p>
     */
    public int ceremonyTicks() {
        if (groomPlayer != null) {
            return BOW_WINDOWS[BOW_WINDOWS.length - 1][1] + 20;
        }
        return TOTAL_TICKS;
    }

    private boolean preludePlayed;
    private boolean weddingPlayed;

    // ── 婚礼导演：这场仪式里的"新郎"是玩家本人
    private java.util.UUID groomPlayer;
    private double groomX, groomY, groomZ;
    private float groomYaw;

    /** 诊断：打印与站位相关的所有坐标 */
    public String debugInfo() {
        double[] spot = standingSpot();
        return String.format(
                "礼堂=(%.2f,%.2f,%.2f) yaw=%.1f%n站位=(%.2f,%.2f)%n锚点=(%.2f,%.2f) yaw=%.1f",
                this.getX(), this.getY(), this.getZ(), this.getYaw(),
                spot[0], spot[1], anchorX, anchorZ, anchorYaw);
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    /** 这一场是不是"玩家当新郎"——必须同步到客户端，否则渲染端永远拿不到 */
    private static final net.minecraft.entity.data.TrackedData<Boolean> PLAYER_WEDDING =
            net.minecraft.entity.data.DataTracker.registerData(
                    PijiaguHallEntity.class,
                    net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * 新郎站位：直接来自模型里的 {@code groom_socket}（模型单位 -10, 0, -4）。
     *
     * <p>不再用 yaw 推导"后退"和"左手边" —— 那样一符号错就把人塞进墙里。
     * 模型坐标是确定的：新娘在 +10，新郎在 -10，两人同一排。</p>
     */
    /** 这一场是不是"玩家当新郎"——必须同步到客户端，否则渲染端永远拿不到 */

    /**
     * 新郎站位。注意：模型里 {@code groom_socket} 在 -10，但动画里 {@code root2}（新郎）
     * 实际站到 +4.6 —— 名字和实际位置是反的。以动画为准，取 +10 这一侧。
     */
    private static final double SOCKET_X = 10.0D;
    private static final double SOCKET_Z = -4.0D;
    /**
     * 在 groom_socket 之外额外让开的模型单位（8 单位 = 1 格）。
     *
     * <p>模型里新郎位 -10、新娘位 +10，两者相距 20 单位 = 1.25 格，
     * 这才是"并肩"的距离。之前为了"再往左"加了 16 单位（2 格），
     * 结果把人推到礼堂外面、离新娘 4.5 格。</p>
     */
    private static final double LEFT_EXTRA_UNITS = 0.0D;
    /** 模型单位 → 格（礼堂整体 2 倍） */
    private static final double UNIT = 2.0D / 16.0D;

    /** 入场起点：站位正前方多少格 */
    private static final double ENTRY_DISTANCE = 6.0D;

    /** 站位（世界坐标），供入场收敛与站定共用 */
    private double[] standingSpot() {
        double mx = (SOCKET_X - LEFT_EXTRA_UNITS) * UNIT;
        double mz = -SOCKET_Z * UNIT;              // 与路径一致：z 取反
        double yawRad = Math.toRadians(this.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        return new double[] {
                this.getX() + mx * cos + mz * sin,
                this.getZ() + mx * sin - mz * cos,
        };
    }

    /** 三个鞠躬窗口（tick，相对于 wedding 动画起点） */
    private static final int[][] BOW_WINDOWS = {
            {40, 68},      // 2.0s ~ 3.4s
            {220, 248},    // 11.0s ~ 12.4s
            {500, 528},    // 25.0s ~ 26.4s
    };

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.getDataTracker().startTracking(PLAYER_WEDDING, false);
    }

    /** 把某个玩家登记为本场婚礼的新郎 */
    public void setCeremonyGroom(java.util.UUID player, net.minecraft.util.math.Vec3d spot, float yaw) {
        this.groomPlayer = player;
        this.getDataTracker().set(PLAYER_WEDDING, true);
        this.groomX = spot.x;
        this.groomY = spot.y;
        this.groomZ = spot.z;
        this.groomYaw = yaw;
    }

    /** 人钉住的位置（礼堂按这个点摆好，玩家原地不动） */
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw;

    public void setGroomAnchor(net.minecraft.util.math.Vec3d spot, float yaw) {
        this.anchorX = spot.x;
        this.anchorY = spot.y;
        this.anchorZ = spot.z;
        this.anchorYaw = yaw;
    }

    public PijiaguHallEntity(EntityType<? extends GhostEntity> entityType, World world) {
        super(entityType, world, false, 0, 0.0D, 'S', 99999, 0, 99999, 0.0F);
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.setAiDisabled(true);      // 礼堂没有任何 AI，是个"建筑"
        this.setSilent(false);
    }

    // ---------------------------------------------------------------- 模型

    @Override public Identifier getModelPath() { return MODEL; }
    @Override public Identifier getTexturePath() { return TEXTURE; }
    @Override public Identifier getAnimationPath() { return ANIMATION; }

    /** 客户端读同步标记：玩家婚礼用 wedding_player / enter_player */
    private boolean playerWedding() {
        return this.getDataTracker().get(PLAYER_WEDDING);
    }

    /** 婚礼礼堂保持原来的 2 倍，要罩住整个仪式场地 */
    @Override public float getScale() { return 2.0f; }

    /** 守护灵形态与任何实体都不发生碰撞（连推挤也不） */
    @Override public boolean collidesWith(net.minecraft.entity.Entity other) {
        return !isGuardian() && super.collidesWith(other);
    }

    /** 礼堂没有 AI —— 本体的鬼会自己接管 movement，这里直接重写判定并每 tick 清状态 */
    @Override public boolean isAiDisabled() { return true; }

    /** 礼堂是建筑，不参与碰撞：删掉碰撞体积，人可以直接穿过去 */
    @Override public boolean isCollidable() { return false; }

    @Override public boolean isPushable() { return false; }

    @Override
    protected PlayState handleAttackAnimations(AnimationState<GhostEntity> state) {
        return PlayState.STOP;
    }

    /**
     * 动画自己排：降临 → 拜堂（停在末帧）。
     *
     * <p>用 age 判断而不是分层控制器 —— 礼堂只有一个用途，时序写死更不容易出错。</p>
     */
    @Override
    protected PlayState handleMovementAnimations(AnimationState<GhostEntity> state) {
        if (state.getController() == null) {
            return PlayState.STOP;
        }
        boolean solo = playerWedding();
        String name;
        if (solo) {
            // 玩家婚礼：不放"入场"，直接拜堂；用隐藏了 root/root2 的那套
            name = "wedding_player";
        } else if (this.age < ENTER_TICKS) {
            name = "enter";
        } else {
            name = "wedding";
        }
        state.getController().setAnimation(RawAnimation.begin().thenPlay(name));
        return PlayState.CONTINUE;
    }

    // ---------------------------------------------------------------- 规则

    /** 仪式本身不吃伤害 —— 连"无敌帧"都不给，直接拒绝 */
    /** 让这具礼堂成为某人的守护灵 */
    public void becomeGuardian(java.util.UUID owner, int durationTicks) {
        this.guardianOwner = owner;
        this.guardianEndTick = this.age + durationTicks;
        this.guardianNextPulse = this.age;
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.setAiDisabled(true);      // 守护灵只跟随，不要任何 AI（否则会自己乱走/乱看）
        this.noClip = true;            // 穿一切：不撞方块、不参与实体推挤
    }

    public boolean isGuardian() {
        return guardianOwner != null;
    }

    private void tickGuardian(ServerWorld world) {
        var owner = world.getServer().getPlayerManager().getPlayer(guardianOwner);
        if (owner == null || this.age > guardianEndTick) {
            this.discard();
            return;
        }

        // 正后方 2 格。
        // MC 的朝向约定：yaw=0 面向 +Z，forward = (-sin(yaw), cos(yaw))，
        // 所以"身后"就是 (+sin, -cos) —— 之前写成 (cos, sin) 才跑到右边去。
        double yaw = Math.toRadians(owner.getYaw());
        double x = owner.getX() + Math.sin(yaw) * 2.0D;
        double z = owner.getZ() - Math.cos(yaw) * 2.0D;
        this.setPosition(x, owner.getY(), z);
        this.setYaw(owner.getYaw());
        // 碰撞箱压到几乎为零：否则它会把玩家推开（实体推挤不看 isCollidable）
        this.setBoundingBox(new net.minecraft.util.math.Box(
                x - 0.05D, owner.getY(), z - 0.05D,
                x + 0.05D, owner.getY() + 0.05D, z + 0.05D));

        // 三重保险：把 AI 侧的一切动作按死，否则它会自己走
        this.getNavigation().stop();
        this.setVelocity(0.0D, 0.0D, 0.0D);
        this.setTarget(null);

        if (this.age < guardianNextPulse) {
            return;
        }
        this.guardianNextPulse = this.age + 10;   // 每 0.5 秒一次

        net.minecraft.util.math.Vec3d center = this.getPos();
        var area = new net.minecraft.util.math.Box(center, center).expand(9.0D);
        for (var target : world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, area,
                e -> e != owner && e.isAlive())) {
            com.example.smfsaddon.combat.SpiritStrike.deal(owner, target, 6.0F);
            target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS, 30, 2, false, false, false));
            world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
                    target.getX(), target.getBodyY(0.5), target.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
        }
    }

    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.entity.damage.DamageSource source) {
        return true;
    }

    @Override
    public boolean shouldAttackPlayer(PlayerEntity player) {
        return false;
    }

    /** 守护灵形态：不跑任何 goal（本体鬼的 AI 全在 mobTick 里） */
    @Override
    protected void mobTick() {
        if (isGuardian()) {
            return;
        }
        super.mobTick();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // 守护灵模式：跟着主人、周期性压场，不播婚礼流程
        if (guardianOwner != null) {
            tickGuardian(world);
            return;
        }

        // 降临的瞬间起前奏，拜堂一开始切曲子 —— 与动画时间点严格对齐。
        // 用标记位而不是 age == 0：baseTick 会先把 age 加一，等我们读到的时候它已经是 1。
        // 玩家婚礼没有"入场"这一段，所以不放前奏，直接起拜堂曲；
        // 自然成亲照旧：先前奏，等新人走进来（ENTER_TICKS）再切拜堂曲。
        boolean soloWedding = groomPlayer != null;
        if (!preludePlayed) {
            preludePlayed = true;
            if (!soloWedding) {
                world.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SmfsAddon.PIJIAGU_PRELUDE, net.minecraft.sound.SoundCategory.HOSTILE,
                        1.6F, 1.0F);
            }
        }
        if (!weddingPlayed && (soloWedding || this.age >= ENTER_TICKS)) {
            weddingPlayed = true;
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SmfsAddon.PIJIAGU_WEDDING, net.minecraft.sound.SoundCategory.HOSTILE,
                    1.6F, 1.0F);
        }

        // ── 新郎：不动，钉在原地；礼堂围着他摆好
        if (groomPlayer != null) {
            var groom = world.getServer().getPlayerManager().getPlayer(groomPlayer);
            if (groom != null) {
                // 站定：钉在站位上（与 21:38 那版一致）
                double[] fixed = standingSpot();
                if (groom.squaredDistanceTo(fixed[0], this.getY(), fixed[1]) > 0.04D) {
                    groom.requestTeleport(fixed[0], this.getY(), fixed[1]);
                }
                groom.setVelocity(0.0D, 0.0D, 0.0D);
                groom.velocityModified = true;

                // 拜堂节拍：动画里是 2.0~3.4s / 11.0~12.4s / 25.0~26.4s
                boolean bowing = false;
                long bowBase = (groomPlayer != null) ? 0L : ENTER_TICKS;
                for (int[] window : BOW_WINDOWS) {
                    if (this.age >= bowBase + window[0] && this.age < bowBase + window[1]) {
                        bowing = true;
                        break;
                    }
                }
                groom.setSneaking(bowing);
                groom.setPitch(bowing ? 22.0F : 8.0F);
                groom.setYaw(anchorYaw);
            }
        }

        // 演完：散场，原地留下礼成体
        if (this.age >= ceremonyTicks()) {
            com.example.smfsaddon.pijiagu.PijiaguWedding.finish(world, this);
        }
    }
}
