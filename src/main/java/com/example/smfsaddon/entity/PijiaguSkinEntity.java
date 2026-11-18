package com.example.smfsaddon.entity;

import com.example.smfsaddon.SmfsAddon;
import com.xie.smfs.api.CustomGhostModelProvider;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 皮嫁骨 · 皮（鬼新娘）。
 *
 * <h2>她不打人</h2>
 *
 * <p>本体里鬼靠"规律"杀人，不靠数值。这只也一样：<b>她不造成任何伤害</b>，
 * 只做一件事 —— 伸手碰你。被碰到的人「骨骼尽失，只剩人皮」，
 * 从此被皮嫁骨·骨盯上。</p>
 *
 * <p>所以她的威胁不是血量，是<b>位置</b>：你必须不让她靠近，而她会一直贴过来。</p>
 *
 * <h2>唢呐</h2>
 *
 * <p>16 格内每 4 秒响一次。原作里听见迎亲唢呐就会神志错乱、自己走上红毯 ——
 * 我们暂时只用声音做预警与压迫感，真正的"软控制"（画面发红、视野模糊）留到下一版。</p>
 */
public class PijiaguSkinEntity extends GhostEntity implements CustomGhostModelProvider {

    private static final Identifier MODEL = new Identifier(SmfsAddon.MOD_ID, "geo/entity/pijiagu_bride.geo.json");
    private static final Identifier TEXTURE = new Identifier(SmfsAddon.MOD_ID, "textures/entity/pijiagu_bride.png");
    private static final Identifier ANIMATION = new Identifier(SmfsAddon.MOD_ID, "animations/pijiagu_bride.animation.json");

    /** 碰到之后的印记时长（10 秒）*/
    private static final int MARK_DURATION = 200;

    /** 触碰判定距离 */
    private static final double TOUCH_RANGE = 1.6D;

    private static final int TOUCH_COOLDOWN = 160;

    /** 她走得很慢：寻路速度系数（本体追击时用 2.4） */
    private static final double SHE_MOVE_SPEED = 1.0D;

    private int touchCooldown;
    private int callCooldown;

    public PijiaguSkinEntity(EntityType<? extends GhostEntity> entityType, World world) {
        super(entityType, world,
                false,   // 无鬼域
                0, 0.0D,
                'A',     // 恐怖等级：A 级厉鬼
                1200,    // 灵异强度
                0,       // 灵异伤害：她不需要这个数字
                200,     // 灵异抗性：物理与灵异都很难"打死"
                0.3F);
        this.ghostLevel = 2;
        // 本体自己的袭击逻辑对她无意义，把冷却拉到很长，实际靠下方 tick 里的触碰判定
        this.attackCooldown = 20 * 60;
    }

    // ---------------------------------------------------------------- 模型

    // ---------------------------------------------------------------- 归属

    /** 这具皮是从谁身上剪下来的；null 表示自然生成的无主之皮 */
    private java.util.UUID ownerUuid;

    public void setOwner(java.util.UUID owner) {
        this.ownerUuid = owner;
    }

    public java.util.UUID getOwner() {
        return ownerUuid;
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) {
            nbt.putUuid("OwnedSkin", ownerUuid);
        }
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("OwnedSkin")) {
            ownerUuid = nbt.getUuid("OwnedSkin");
        }
    }

    @Override public Identifier getModelPath() { return MODEL; }
    @Override public Identifier getTexturePath() { return TEXTURE; }
    @Override public Identifier getAnimationPath() { return ANIMATION; }
    @Override public float getScale() { return 1.0f; }

    /** 模型没有攻击动画，直接 STOP，避免 GeckoLib 刷 "Unable to find animation: attack" */
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
     * 潜行 + 右键 = 起礼。
     *
     * <p>变成异类必须是玩家自己下的决心，所以不做"走近自动开场"，
     * 只认这一个动作。</p>
     */
    @Override
    public net.minecraft.util.ActionResult interactMob(PlayerEntity player,
                                                       net.minecraft.util.Hand hand) {
        if (player.isSneaking() && player instanceof net.minecraft.server.network.ServerPlayerEntity sp
                && this.getWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            // 只有"从自己身上剪下来的皮"才能拜堂；自然生成的无主之皮不行
            if (ownerUuid == null || !ownerUuid.equals(sp.getUuid())) {
                sp.sendMessage(net.minecraft.text.Text.literal("§7这不是从你身上剪下来的皮。"), true);
                return net.minecraft.util.ActionResult.FAIL;
            }
            return com.example.smfsaddon.pijiagu.PijiaguWedding.tryStartByPlayer(world, this, sp)
                    ? net.minecraft.util.ActionResult.SUCCESS
                    : net.minecraft.util.ActionResult.FAIL;
        }
        return super.interactMob(player, hand);
    }

    @Override
    public boolean shouldAttackPlayer(PlayerEntity player) {
        // 她不通过本体那套袭击杀人
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            return;
        }

        // 婚礼检查放在最前面：不能被死机/压制判定挡住，
        // 否则被打过的鬼永远不会开场，现象就是"站在旁边没反应"。
        if (this.getWorld() instanceof ServerWorld early) {
            com.example.smfsaddon.pijiagu.PijiaguWedding.checkStart(early, this);
        }

        if (touchCooldown > 0) {
            touchCooldown--;
        }
        if (callCooldown > 0) {
            callCooldown--;
        }

        if (!(this.getWorld() instanceof ServerWorld world)) {
            return;
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

        PlayerEntity target = world.getClosestPlayer(this, 24.0D);
        if (target == null || target.isCreative() || target.isSpectator()) {
            return;
        }

        // 唢呐：靠近就响，给玩家"它在附近"的预警
        if (callCooldown == 0 && this.squaredDistanceTo(target) < 16 * 16) {
            callCooldown = 80;
            world.playSound(null, this.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.HOSTILE,
                    1.2F, 0.75F + world.getRandom().nextFloat() * 0.15F);
        }

        // 走本体同一套：寻路 + 注视。这样朝向、动画判定、跨越台阶都由本体接管，
        // 不再需要自己推速度、自己设 yaw。
        this.getLookControl().lookAt(target, 30.0F, 30.0F);
        if (this.squaredDistanceTo(target) > 2.4D && this.getNavigation().isIdle()) {
            this.getNavigation().startMovingTo(target, SHE_MOVE_SPEED);
        }

        // 触碰：骨骼尽失
        if (touchCooldown == 0 && this.squaredDistanceTo(target) <= TOUCH_RANGE * TOUCH_RANGE) {
            touchCooldown = TOUCH_COOLDOWN;
            target.addStatusEffect(new StatusEffectInstance(
                    SmfsAddon.SKIN_SHED, MARK_DURATION, 0, false, true, true));

            world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                    SoundCategory.HOSTILE, 1.0F, 1.4F);
            world.spawnParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + 1.0D, target.getZ(),
                    24, 0.3D, 0.6D, 0.3D, 0.02D);

            // 得手之后退开：她不是战士，她只负责"标记"
            var away = this.getPos().subtract(target.getPos()).normalize().multiply(6.0D);
            this.requestTeleport(this.getX() + away.x, this.getY(), this.getZ() + away.z);
        }
    }
}
