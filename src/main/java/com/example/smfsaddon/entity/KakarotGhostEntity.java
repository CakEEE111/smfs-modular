package com.example.smfsaddon.entity;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.api.CustomGhostModelProvider;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 卡卡罗特鬼。
 *
 * 视觉策略：不依赖骨骼动画（龙珠模型只有 idle/walk），攻击与鬼域全部用粒子特效表现。
 * 好处是不用改 DMZ 的动画文件，也不会有 GeckoLib "Unable to find animation" 的刷屏。
 */
public class KakarotGhostEntity extends GhostEntity implements CustomGhostModelProvider {

    private static final Identifier MODEL     = new Identifier(SmfsAddon.MOD_ID, "geo/entity/kakarot_ghost.geo.json");
    private static final Identifier TEXTURE   = new Identifier(SmfsAddon.MOD_ID, "textures/entity/kakarot_ghost.png");
    /** 超赛形态：头发染成金色（同模型、同 UV，仅替换贴图） */
    private static final Identifier TEXTURE_SSJ = new Identifier(SmfsAddon.MOD_ID, "textures/entity/kakarot_ghost_ssj.png");
    private static final Identifier ANIMATION = new Identifier(SmfsAddon.MOD_ID, "animations/kakarot_ghost.animation.json");

    /** 气焰金色 */
    private static final DustParticleEffect KI_GOLD =
            new DustParticleEffect(new Vec3d(1.0, 0.82, 0.12).toVector3f(), 1.6f);
    /** 气功波青白 */
    private static final DustParticleEffect KI_CYAN =
            new DustParticleEffect(new Vec3d(0.55, 0.9, 1.0).toVector3f(), 1.4f);

    public KakarotGhostEntity(EntityType<? extends GhostEntity> entityType, World world) {
        super(entityType, world,
                false,   // 无鬼域：只保留袭击本身，不展开域
                0,       // 鬼域等级
                0.0D,    // 鬼域半径
                'S',     // 恐怖等级
                3200,    // 灵异强度
                180,     // 灵异伤害
                160,     // 灵异抗性
                0.5F);   // 恢复因子（越高复苏越快）

        // S 级鬼的行为参数：出手慢但一击致命
        this.ghostLevel = 3;
        this.attackCooldown = 40;
    }

    // ---------------------------------------------------------------- 模型

    @Override public Identifier getModelPath()     { return MODEL; }
    /** 变身中切金色头发贴图 —— 每帧都会被渲染器查询，所以能实时切换 */
    @Override
    public Identifier getTexturePath() {
        return this.hasStatusEffect(SmfsAddon.SUPER_SAIYAN) ? TEXTURE_SSJ : TEXTURE;
    }
    @Override public Identifier getAnimationPath() { return ANIMATION; }
    @Override public float getScale()              { return 1.0f; }

    /**
     * 模型里没有 attack 动画。
     * 直接返回 STOP，避免 GeckoLib 每次挥拳都在控制台打印 "Unable to find animation: attack"。
     * 攻击的观感由 executeAttack 里的粒子负责。
     */
    @Override
    protected PlayState handleAttackAnimations(AnimationState<GhostEntity> state) {
        return PlayState.STOP;
    }

    // ---------------------------------------------------------------- 特效

    /** 鬼域气焰：常驻体表粒子，让它一眼看出来是"烧着气的鬼" */
    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            return;
        }

        // 冲击判定不受粒子频率影响，单独走
        fallbackRuleAttack();

        if (this.age % 3 != 0) {
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        double angle = this.getGhostRandom().nextDouble() * Math.PI * 2.0;
        double radius = 0.45 + this.getGhostRandom().nextDouble() * 0.35;
        double x = this.getX() + Math.cos(angle) * radius;
        double z = this.getZ() + Math.sin(angle) * radius;
        double y = this.getY() + this.getGhostRandom().nextDouble() * 2.2;

        world.spawnParticles(KI_GOLD, x, y, z, 1, 0.0, 0.06, 0.0, 0.01);

        // 每 4 次补一颗上升的电火花，增加"能量感"
        if (this.getGhostRandom().nextInt(4) == 0) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.05, 0.18, 0.05, 0.02);
        }
    }


    // ---------------------------------------------------------------- 袭击

    /** 无鬼域时的袭击半径（格） */
    private static final double RULE_ATTACK_RANGE = 12.0D;

    /** 判定间隔（tick）：一秒一次，够快也不至于每 tick 扫玩家 */
    private static final int RULE_ATTACK_INTERVAL = 20;

    /**
     * 兜底袭击。
     *
     * <h2>为什么需要这个</h2>
     *
     * <p>本体那套"遍历域内玩家 → 问 shouldAttackPlayer → executeAttack"的循环，
     * 整体挂在 {@code ghostDomainEnabled && hasGhostDomain} 这个前提之下。
     * 这只鬼去掉了鬼域，那段循环就再也不会执行——它会变成一只站着不动的摆设。
     *
     * <p>所以这里补一条最小规则：12 格内、非创造/旁观、没被本体保护拦下，
     * 就按本体自己的 {@link #executeAttack} 出手（伤害、冷却、灵异强度结算全部沿用本体）。
     *
     * <p>仍然尊重 {@code isSuppressed()} / {@code isDeadlocked()}：被压住或死锁的鬼
     * 不该因为走了另一条路就还能打人。
     */
    private void fallbackRuleAttack() {
        if (this.age % RULE_ATTACK_INTERVAL != 0) {
            return;
        }
        if (this.isSuppressed() || this.isDeadlocked()) {
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        PlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        double rangeSq = RULE_ATTACK_RANGE * RULE_ATTACK_RANGE;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (this.isPlayerProtected(player)) {
                continue;
            }
            double distanceSq = this.squaredDistanceTo(player);
            if (distanceSq > rangeSq || distanceSq >= best) {
                continue;
            }
            best = distanceSq;
            nearest = player;
        }

        if (nearest != null && this.getTarget() != nearest) {
            this.executeAttack(nearest, false);
        }
    }

    /**
     * 攻击：保留本体伤害判定，额外加"出拳气浪 + 命中冲击"两层特效。
     */
    @Override
    protected void executeAttack(PlayerEntity player, boolean isTimeoutAttack) {
        super.executeAttack(player, isTimeoutAttack);

        if (this.getWorld().isClient() || this.isSuppressed() || this.isDeadlocked()) {
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // 1) 拳风气浪：在身体正前方 1 格
        Vec3d front = this.getPos()
                .add(Vec3d.fromPolar(0.0f, this.getYaw()).multiply(1.0))
                .add(0.0, 1.2, 0.0);
        world.spawnParticles(KI_CYAN, front.x, front.y, front.z, 8, 0.18, 0.18, 0.18, 0.12);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, front.x, front.y, front.z, 5, 0.2, 0.2, 0.2, 0.25);

        // 2) 命中冲击：只有真的打到（距离够近）才在玩家身上炸开
        if (this.squaredDistanceTo(player) < 16.0) {
            world.spawnParticles(KI_GOLD, player.getX(), player.getBodyY(0.6), player.getZ(),
                    18, 0.35, 0.45, 0.35, 0.22);
            world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(0.6), player.getZ(),
                    6, 0.25, 0.35, 0.25, 0.05);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0f, 0.7f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.35f, 1.6f);
        } else {
            world.playSound(null, front.x, front.y, front.z,
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 0.8f);
        }
    }
}
