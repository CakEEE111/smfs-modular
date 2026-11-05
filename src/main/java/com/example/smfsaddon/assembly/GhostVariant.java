package com.example.smfsaddon.assembly;

import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 一个装配变体：同一只鬼的一种规律组合。
 *
 * <p>「灵异拼图」的随机性就落在这里：同一类型的鬼，每次生成可能拼出不同的组合。
 * 玩家在图鉴上见过的那套规律，下一次未必管用 —— 这是单体类永远做不到的事。
 *
 * @param id      变体名，只用于日志与调试，不参与逻辑
 * @param weight  权重，至少为 1
 * @param modules 该变体装配的模块
 */
public record GhostVariant(String id, int weight, List<Identifier> modules) {

    public GhostVariant {
        if (id == null || id.isBlank()) {
            id = "default";
        }
        if (weight < 1) {
            weight = 1;
        }
        modules = List.copyOf(modules);
    }

    public static GhostVariant of(String id, List<Identifier> modules) {
        return new GhostVariant(id, 1, modules);
    }
}
