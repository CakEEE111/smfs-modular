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
 * 皮嫁骨 · 礼成体。
 *
 * <h2>婚礼的结果</h2>
 *
 * <p>它不是一个"新敌人"，而是<b>已经完成的事</b>：皮找到了她的骨，礼成了。
 * 所以它不再需要追人、不再需要判断规律 —— 它只需要把看到的人请进席间。</p>
 *
 * <p>数值上给到 S 级顶端（灵异强度 6000 / 伤害 300 / 抗性 500），
 * 意味着这不是一场能打赢的架，而是一段"该跑了"的体验。</p>
 *
 * <h2>它只有一个技能</h2>
 *
 * <p>「请入席」—— 每 12 秒一次，26 格内最近的玩家会被整座礼堂围住。
 * 没有第二形态、没有阶段：它从头到尾都是这一件事，反复做。</p>
 */
public class PijiaguUnionEntity extends GhostEntity implements CustomGhostModelProvider {

    private static final Identifier MODEL = new Identifier(SmfsAddon.MOD_ID, "geo/entity/pijiagu_union.geo.json");
    private static final Identifier TEXTURE = new Identifier(SmfsAddon.MOD_ID, "textures/entity/pijiagu_union.png");
    private static final Identifier ANIMATION = new Identifier(SmfsAddon.MOD_ID, "animations/pijiagu_union.animation.json");

    /** 成形动画 1.5 秒 */
    private static final int FORM_TICKS = 30;

    /** 请入席的间隔与范围 */
    private static final int CAST_INTERVAL = 240;
    private static final double CAST_RANGE = 26.0D;

    public PijiaguUnionEntity(EntityType<? extends GhostEntity> entityType, World world) {
        super(entityType, world,
                true,    // 有鬼域：礼成之后，它站着的地方就是它的席面
                3,
                16.0D,
                'S',
                6000,
                300,
                500,
                0.1F);
        this.ghostLevel = 3;
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

    /** 头 1.5 秒播 form（成形），之后正常走/待机 */
    @Override
    protected PlayState handleMovementAnimations(AnimationState<GhostEntity> state) {
        if (state.getController() == null) {
            return PlayState.STOP;
        }
        if (this.age < FORM_TICKS) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("form"));
            return PlayState.CONTINUE;
        }
        boolean moving = Math.abs(this.getVelocity().horizontalLength()) > 0.005D;
        state.getController().setAnimation(RawAnimation.begin()
                .thenLoop(moving ? "walk" : "idle"));
        return PlayState.CONTINUE;
    }

    // ---------------------------------------------------------------- 行为

    /** 每 12 秒：把最近的玩家请进席间 */
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }
        if (!(this.getWorld() instanceof ServerWorld sw)) {
            return;
        }
        if (this.age < FORM_TICKS) {
            return;
        }
        // 杀人规律：每 10 tick 检查一次
        if (this.age % 10 == 0) {
            com.example.smfsaddon.pijiagu.UnionRules.tick(this, sw);
        }

        if (this.age % CAST_INTERVAL != 0) {
            return;
        }
        PlayerEntity target = sw.getClosestPlayer(this, CAST_RANGE);
        if (target instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && target.isAlive()) {
            boolean started = com.example.smfsaddon.pijiagu.FeastCeremony.start(sw,
                    this.getBlockPos(), serverPlayer);
            if (started) {
                SmfsAddon.LOGGER.info("[smfsaddon] 礼成体对 {} 施放「请入席」",
                        serverPlayer.getName().getString());
            }
        }
    }

    /**
     * 规律的惩罚：亲自过来打人。
     *
     * <p>不是站在远处放技能 —— 它会走到你面前，先扇两下，再把你按进席里。
     * 这里放在实体内部，因为 {@code tryAttack} 是 protected。</p>
     */
    public void punishMelee(PlayerEntity target) {
        this.getNavigation().startMovingTo(target, 1.2D);
        this.getLookControl().lookAt(target, 30.0F, 30.0F);
        this.tryAttack(target);
        this.tryAttack(target);
    }

    /** 规律四：席间动手 */
    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        if (!this.getWorld().isClient()
                && source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            com.example.smfsaddon.pijiagu.UnionRules.onAttacked(this, player);
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean shouldAttackPlayer(PlayerEntity player) {
        return super.shouldAttackPlayer(player);
    }
}
