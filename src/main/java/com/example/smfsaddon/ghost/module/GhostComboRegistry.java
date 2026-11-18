package com.example.smfsaddon.ghost.module;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协同注册表。
 *
 * <p>注册时会同时登记进 {@link GhostModuleRegistry} —— 因为协同就是模块，
 * 规则查找、图鉴反查都走同一张表。
 */
public final class GhostComboRegistry {

    private static final Map<Identifier, GhostCombo> COMBOS = new LinkedHashMap<>();

    private GhostComboRegistry() {
    }

    public static void register(GhostCombo combo) {
        if (COMBOS.containsKey(combo.id())) {
            throw new IllegalStateException("Duplicate combo id: " + combo.id());
        }
        COMBOS.put(combo.id(), combo);
        GhostModuleRegistry.register(combo);
    }

    public static Collection<GhostCombo> all() {
        return Collections.unmodifiableCollection(COMBOS.values());
    }

    public static boolean contains(Identifier id) {
        return COMBOS.containsKey(id);
    }

    /**
     * 注册内置协同。
     */
    public static void registerBuiltins() {
        register(com.example.smfsaddon.ghost.module.impl.CorneredCombo.create());
    }
}
