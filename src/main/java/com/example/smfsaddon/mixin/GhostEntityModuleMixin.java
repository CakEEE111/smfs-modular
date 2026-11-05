package com.example.smfsaddon.mixin;

import com.example.smfsaddon.assembly.GhostAssembly;
import com.example.smfsaddon.assembly.GhostVariant;
import com.example.smfsaddon.ghost.module.AttackVerdict;
import com.example.smfsaddon.ghost.module.GhostModuleContainer;
import com.example.smfsaddon.ghost.module.GhostModuleDispatcher;
import com.example.smfsaddon.ghost.module.LingyiHost;
import com.example.smfsaddon.ghost.module.host.GhostEntityHost;
import com.example.smfsaddon.ghost.module.host.HostCapability;
import com.example.smfsaddon.ghost.module.host.TickHost;
import com.xie.smfs.entity.GhostEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 给本体 {@link GhostEntity} 挂上厉鬼模块系统。
 *
 * <p>这是整个方案的桥接层：模块框架本身不依赖 smfs，
 * 全部依赖都收敛在这一个 mixin 里。本体升级时只需要重新核对
 * 下面四个注入点，框架代码可以原样保留。
 *
 * <p>注入点：
 * <ol>
 *   <li>{@code tick} HEAD —— 每 tick 调度模块（仅服务端）</li>
 *   <li>{@code writeCustomDataToNbt} TAIL —— 模块数据存档</li>
 *   <li>{@code readCustomDataFromNbt} TAIL —— 模块数据读档</li>
 *   <li>{@code damage} HEAD —— 派发受伤事件</li>
 *   <li>{@code tick} 内对 {@code shouldAttackPlayer} 的调用 —— 杀人规律裁决</li>
 * </ol>
 *
 * <p>通过 Mixin 的接口注入，让 {@code GhostEntity} 在运行时
 * 实现 {@link LingyiHost}，外部即可用
 * {@code (LingyiHost) (Object) ghost} 访问模块容器。
 * 这样不必修改本体源码，也就不会产生需要同步维护的分支。
 */
@Mixin(GhostEntity.class)
public abstract class GhostEntityModuleMixin implements LingyiHost, GhostEntityHost, TickHost {

    /**
     * 延迟初始化，避免向目标类构造器注入字段初始化代码。
     */
    @Unique
    private GhostModuleContainer smfsaddon$modules;

    /**
     * 装配表是否已对这只鬼执行过。
     *
     * <p>只在运行时存在、不落盘：目的是「每只鬼只装配一次」，
     * 从而不会把手动卸载掉的模块每 tick 又加回来。
     */
    @Unique
    private boolean smfsaddon$assemblyApplied;

    /**
     * 当前实例抽中的装配变体索引，-1 表示还没抽。
     *
     * <p><b>必须持久化</b>：读档后沿用同一个变体。
     * 如果每 tick 重抽，鬼的规律会闪烁，玩家也无法推理。
     */
    @Unique
    private int smfsaddon$variantIndex = -1;

    /**
     * 能力查询。
     *
     * <p>不做包装对象、不做映射表，直接 {@code isInstance} ——
     * 因为本 mixin 把 {@link GhostEntityHost}、{@link TickHost}
     * 这些接口直接注入了目标类，运行时 {@code this} 本身就实现了它们。
     * 这样每次查询都是零分配，可以放心在每 tick 的热路径上调用。
     */
    @Override
    public <T extends HostCapability> Optional<T> capability(Class<T> type) {
        Object self = this;
        return type.isInstance(self) ? Optional.of(type.cast(self)) : Optional.empty();
    }

    @Override
    public GhostModuleContainer modules() {
        if (smfsaddon$modules == null) {
            smfsaddon$modules = new GhostModuleContainer();
        }
        return smfsaddon$modules;
    }

    // ==================== 宿主能力实现 ====================

    @Override
    public World world() {
        return ((GhostEntity) (Object) this).getWorld();
    }

    @Override
    public Vec3d position() {
        return ((GhostEntity) (Object) this).getPos();
    }

    @Override
    public LivingEntity living() {
        return (GhostEntity) (Object) this;
    }

    @Override
    public GhostEntity ghost() {
        return (GhostEntity) (Object) this;
    }

    @Override
    public long ticks() {
        return ((GhostEntity) (Object) this).age;
    }

    /**
     * 每 tick 调度。放在 HEAD，保证本体 tick 里的早退
     * （被移除、已死亡）不会让模块漏掉状态推进。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void smfsaddon$tickModules(CallbackInfo callbackInfo) {
        GhostEntity self = (GhostEntity) (Object) this;
        if (self.getWorld().isClient()) {
            return;
        }

        // 首次 tick 按装配表自动装配。
        // 键是实体类型 ID，所以任何附属的鬼只要继承了 GhostEntity 都会被适配，
        // 对方不需要写任何代码。
        if (!smfsaddon$assemblyApplied) {
            smfsaddon$assemblyApplied = true;
            smfsaddon$applyAssembly(self);
            // 交错在装配之后：先给它自己的模块，再借别人的规律。
            // 放在这里而不是 applyAssembly 里面，是因为没有装配表的鬼同样要交错。
            com.example.smfsaddon.crossing.GhostRuleCrossing.seed((LingyiHost) (Object) this, self);
        }

        GhostModuleDispatcher.tick((LingyiHost) (Object) this);
    }

    /**
     * 按装配表给这只鬼定型。
     *
     * <p>已存档的实例用 NBT 里的变体索引；新生成的按权重抽一个。
     * 抽中之后索引立刻固定，本次实例再也不会变。
     */
    @Unique
    private void smfsaddon$applyAssembly(GhostEntity self) {
        Identifier entityType = Registries.ENTITY_TYPE.getId(self.getType());
        if (entityType == null || GhostAssembly.variantsFor(entityType).isEmpty()) {
            return;
        }

        GhostVariant variant = smfsaddon$variantIndex >= 0
                ? GhostAssembly.byIndex(entityType, smfsaddon$variantIndex)
                : null;

        if (variant == null) {
            // 新生成的鬼，或数据包改动导致旧索引越界
            variant = GhostAssembly.pick(entityType, self.getRandom());
        }
        if (variant == null) {
            return;
        }

        smfsaddon$variantIndex = GhostAssembly.indexOf(entityType, variant);
        GhostAssembly.applyTo((LingyiHost) (Object) this, variant);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void smfsaddon$saveModules(NbtCompound nbt, CallbackInfo callbackInfo) {
        GhostModuleContainer container = ((LingyiHost) (Object) this).modules();
        if (!container.isEmpty()) {
            nbt.put("SmfsAddonModules", container.writeNbt());
        }
        if (smfsaddon$variantIndex >= 0) {
            nbt.putInt("SmfsAddonVariant", smfsaddon$variantIndex);
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void smfsaddon$loadModules(NbtCompound nbt, CallbackInfo callbackInfo) {
        // 变体索引先读：装配时要靠它决定用哪套模块
        if (nbt.contains("SmfsAddonVariant")) {
            smfsaddon$variantIndex = nbt.getInt("SmfsAddonVariant");
        }
        if (nbt.contains("SmfsAddonModules", NbtElement.COMPOUND_TYPE)) {
            ((LingyiHost) (Object) this)
                    .modules()
                    .readNbt(nbt.getCompound("SmfsAddonModules"));
        }
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void smfsaddon$dispatchDamaged(DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> callbackInfo) {
        GhostEntity self = (GhostEntity) (Object) this;
        if (self.getWorld().isClient()) {
            return;
        }
        GhostModuleDispatcher.damaged((LingyiHost) (Object) this, source, amount);
    }

    /**
     * 杀人规律裁决。
     *
     * <p>本体在 tick 里用 {@code this.shouldAttackPlayer(player)} 判断是否袭击，
     * 而 43 只鬼里有 35 只覆写了这个方法 —— 直接注入 {@code shouldAttackPlayer}
     * 只能覆盖基类实现，对覆写者无效。所以这里重定向「调用点」，
     * 无论子类怎么覆写都能被接管。
     *
     * <p>{@code require = 0}：本体改了这个调用点也不至于让游戏起不来，
     * 只是模块的裁决能力失效，其余功能照常。
     */
    @Redirect(
            method = "tick",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/xie/smfs/entity/GhostEntity;shouldAttackPlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z"
            )
    )
    private static boolean smfsaddon$resolveKillingRule(GhostEntity entity, PlayerEntity player) {
        AttackVerdict verdict = GhostModuleDispatcher.attackVerdict(
                (LingyiHost) (Object) entity, player);
        if (verdict == AttackVerdict.ALLOW) {
            return true;
        }
        if (verdict == AttackVerdict.DENY) {
            return false;
        }
        return entity.shouldAttackPlayer(player);
    }
}
