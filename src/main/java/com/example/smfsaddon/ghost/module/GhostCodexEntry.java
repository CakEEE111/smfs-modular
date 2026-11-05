package com.example.smfsaddon.ghost.module;

/**
 * 图鉴条目。
 *
 * <p>规则要在图鉴里被看见，就得先能描述自己。三档信息按观察次数逐步揭示 ——
 * 这正是原型里「猜规律」的节奏：先知道有这条规律，再猜它何时触发，
 * 最后才确认它的代价。
 *
 * @param title        条目名，观察一次即可见，例如「低处踩踏」
 * @param hint         规律描述，观察 {@code hintRevealAt} 次后才可见
 * @param hintRevealAt 揭示 {@code hint} 所需的观察次数，小于 1 时视为 1
 */
public record GhostCodexEntry(
        String title,
        String hint,
        int hintRevealAt
) {

    public GhostCodexEntry {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("codex title must not be blank");
        }
        if (hintRevealAt < 1) {
            hintRevealAt = 1;
        }
    }

    /**
     * 两档信息的便捷构造：观察 3 次后可见描述。
     */
    public static GhostCodexEntry of(String title, String hint) {
        return new GhostCodexEntry(title, hint, 3);
    }
}
