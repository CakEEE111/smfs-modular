package com.example.smfsaddon.ghost.module;

import com.xie.smfs.entity.GhostEntity;
import net.minecraft.util.Identifier;

/**
 * 对外门面。
 *
 * <p>{@link GhostEntity} 在运行时通过 mixin 实现了 {@link LingyiHost}，
 * 但编译期它并不声明这个接口，所以外部访问必须写
 * {@code (LingyiHost) (Object) ghost} 这种双重转型。
 * 本类把这个丑陋的细节收在一处。
 *
 * <p>用法：
 * <pre>
 * GhostModules.attach(ghost, MyModule.ID);
 * GhostModules.dispatch... 由 mixin 自动完成，无需手动调用
 * </pre>
 */
public final class GhostModules {

    private GhostModules() {
    }

    public static LingyiHost of(GhostEntity ghost) {
        return (LingyiHost) (Object) ghost;
    }

    /**
     * 挂载模块，返回新生成的模块数据。
     *
     * @throws IllegalArgumentException 模块未注册
     */
    public static GhostModuleData attach(GhostEntity ghost, Identifier moduleId) {
        return GhostModuleDispatcher.attach(of(ghost), moduleId);
    }

    public static boolean detach(GhostEntity ghost, Identifier moduleId) {
        return GhostModuleDispatcher.detach(of(ghost), moduleId);
    }

    public static boolean has(GhostEntity ghost, Identifier moduleId) {
        return of(ghost).modules().has(moduleId);
    }

    public static GhostModuleData get(GhostEntity ghost, Identifier moduleId) {
        return of(ghost).modules().get(moduleId);
    }

    public static int count(GhostEntity ghost) {
        return of(ghost).modules().size();
    }

    public static boolean useAbility(GhostEntity ghost, Identifier moduleId, Identifier abilityId) {
        return GhostModuleDispatcher.useAbility(of(ghost), moduleId, abilityId);
    }
}
