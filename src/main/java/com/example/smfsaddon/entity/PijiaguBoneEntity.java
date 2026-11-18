package com.example.smfsaddon.entity;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.effect.SkinShedEffect;
import com.xie.smfs.api.CustomGhostModelProvider;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
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
 * 皮嫁骨 · 骨（白骨新郎）。
 *
 * <h2>他只在找"同类"</h2>
 *
 * <p>血肉之躯他根本不理 —— 因为那不是新郎。只有被皮嫁骨·皮碰过、
 * 身上挂着 {@link SkinShedEffect} 的人，他才会走过来。</p>
 *
 * <p>所以这两只的配合是：<b>皮负责让你变成"骨"，骨负责来娶你</b>。
 * 单只都不致命，凑在一起才要命。</p>
 *
 * <h2>拖拽</h2>
 *
 * <p>抓到人不是直接杀，而是往自己这边拽 —— 给玩家一段挣扎的时间（拖拽阶段）。
 * 真正的"拜堂"（红毯 + 仪式完成）留到下一版，现在先做拽与击退。</p>
 */
public class PijiaguBoneEntity extends GhostEntity implements CustomGhostModelProvider {

    private static final Identifier MODEL = new Identifier(SmfsAddon.MOD_ID, "geo/entity/pijiagu_groom.geo.json");
    private static final Identifier TEXTURE = new Identifier(SmfsAddon.MOD_ID, "textures/entity/pijiagu_groom.png");
    private static final Identifier ANIMATION = new Identifier(SmfsAddon.MOD_ID, "animations/pijiagu_groom.animation.json");

    /** 抓取距离 */
    private static final double GRAB_RANGE = 2.2D;

    /** 他比皮快：寻路速度系数 */
    private static final double HE_MOVE_SPEED = 1.8D;

    private int grabCooldown;

    public PijiaguBoneEntity(EntityType<? extends GhostEntity> entityType, World world) {
        super(entityType, world,
                false,
                0, 0.0D,
                'A',
                1400,    // 灵异强度
                90,      // 灵异伤害
                180,     // 灵异抗性
                0.35F);
        this.ghostLevel = 2;
        this.attackCooldown = 40;
    }

    // ---------------------------------------------------------------- 模型

    @Override public Identifier getModelPath() { return MODEL; }
    @Override public Identifier getTexturePath() { return TEXTURE; }
    @Override public Identifier getAnimationPath() { return ANIMATION; }
    @Override public float getScale() { return 1.0f; }

    @Override
    protected PlayState handleAttackAnimations(AnimationState<GhostEntity> state) {
        return PlayState.STOP;
    }

    /**
     * 移动动画自己决定。
     *
     * <p>本体的默认判定看的是寻路/肢体摆动状态，而我们这两只是靠每 tick 直接推速度移动的，
     * 不走寻路 —— 于是默认判定永远认为"站着没动"，播放的一直是 idle。
     * 这里改成按实际水平速度二选一，动画文件里的 key 是 {@code walk} / {@code idle}。</p>
     */
    @Override
    protected PlayState handleMovementAnimations(AnimationState<GhostEntity> state) {
        if (state.getController() == null) {
            return PlayState.STOP;
        }
        boolean moving = Math.abs(this.getVelocity().horizontalLength()) > 0.005D;
        state.getController().setAnimation(software.bernie.geckolib.core.animation.RawAnimation
                .begin().thenLoop(moving ? "walk" : "idle"));
        return PlayState.CONTINUE;
    }

    // ---------------------------------------------------------------- 行为

    /**
     * 只对"被剥皮"的玩家出手。
     *
     * <p>这是两只鬼配合的核心：没被皮碰过的人，站在他面前他也不会动手。</p>
     */
    @Override
    public boolean shouldAttackPlayer(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        if (!SkinShedEffect.isMarked(player)) {
            return false;
        }
        return super.shouldAttackPlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld world)) {
            return;
        }
        // 两边都能发起：谁先 tick 谁检查
        com.example.smfsaddon.pijiagu.PijiaguWedding.checkStart(world, this);

        if (grabCooldown > 0) {
            grabCooldown--;
        }

        // 灵异强度打空 = 死机，被镇压手段压住 = 压制。
        // 这两条本体写在 shouldAttackPlayer 里，但我们的移动是自己推的，
        // 不检查它就会"打空了照样贴过来"。发现强度见底时顺便把死机标记补上，
        // 让本体那套 UI / 表现也同步。
        if (this.getSpiritualStrength() <= 0) {
            this.setDeadlocked(true);
        }
        if (this.isDeadlocked() || this.isSuppressed()) {
            this.setVelocity(0.0D, this.getVelocity().y, 0.0D);
            this.velocityModified = true;
            return;
        }


        // 只找被标记的人
        PlayerEntity target = world.getPlayers().stream()
                .filter(SkinShedEffect::isMarked)
                .filter(player -> player.squaredDistanceTo(this) < 32.0D * 32.0D)
                .min((a, b) -> Double.compare(a.squaredDistanceTo(this), b.squaredDistanceTo(this)))
                .orElse(null);
        if (target == null) {
            return;
        }

        this.setTarget(target);

        // 与本体一致：寻路追击 + 始终注视目标
        this.getLookControl().lookAt(target, 30.0F, 30.0F);
        if (this.squaredDistanceTo(target) > 2.0D && this.getNavigation().isIdle()) {
            this.getNavigation().startMovingTo(target, HE_MOVE_SPEED);
        }

        // 抓取：把人往自己这边拽，不是直接杀
        if (grabCooldown == 0 && this.squaredDistanceTo(target) <= GRAB_RANGE * GRAB_RANGE) {
            grabCooldown = 60;
            Vec3d pull = this.getPos().subtract(target.getPos()).normalize().multiply(0.45D);
            target.setVelocity(pull.x, 0.18D, pull.z);
            target.velocityModified = true;

            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SKELETON_AMBIENT,
                    SoundCategory.HOSTILE, 1.0F, 0.6F);
            world.spawnParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0D, target.getZ(),
                    8, 0.2D, 0.3D, 0.2D, 0.05D);
        }
    }
}
