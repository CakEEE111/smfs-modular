package com.example.smfsaddon.ghost.module;

import com.xie.smfs.entity.GhostEntity;

import java.util.Objects;

/**
 * 单次能力释放的上下文。
 */
public final class GhostAbilityContext {

    private final GhostModuleContext moduleContext;
    private final GhostAbility ability;

    public GhostAbilityContext(GhostModuleContext moduleContext, GhostAbility ability) {
        this.moduleContext = Objects.requireNonNull(moduleContext, "moduleContext");
        this.ability = Objects.requireNonNull(ability, "ability");
    }

    public GhostModuleContext module() {
        return moduleContext;
    }

    public GhostAbility ability() {
        return ability;
    }

    public GhostEntity ghost() {
        return moduleContext.ghost();
    }
}
