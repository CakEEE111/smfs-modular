package com.example.smfsaddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Set;

/**
 * 把「某只鬼的某个技能」翻译成本体自己的 C2S 通道，并直接发出去。
 *
 * <h2>为什么不自己算冷却</h2>
 *
 * <p>本体的每个技能包长这样：
 *
 * <pre>
 * if (!checkAndSetJSkillCooldown(player, 键, 刻数, 技能名)) return;   // 冷却
 * if (canUseGhostSkill(...) == NO_GHOST) return;                     // 资格
 * handleXxxSkill(player);                                            // 行为
 * PlayerEvents.balanceRevivalDegree(player);                         // 收尾
 * </pre>
 *
 * <p>早先面板绕过包层直接反射调 {@code handleXxxSkill}，所以冷却整层都不在。
 * 如果照抄一份「技能 → 键/刻数」的表，本体每加一个技能就要补一行，
 * 而且表一旦对不上就静默失效 —— 那才是真正的泛化性差。
 *
 * <p>现在改成<b>把包打回去</b>：键、刻数、倍率、提示全用本体那份，
 * 我们只负责找到通道。通道也不是拼字符串猜的 —— 拼出候选后拿去和
 * 服务端实际声明的通道表对，对不上就走回退路径。
 *
 * <h2>为什么是后缀匹配</h2>
 *
 * <p>绝大多数通道名是 {@code <鬼种>_skill} / {@code <鬼种>_<按键>_skill}，
 * 但本体自己也有不守规律的，例如 {@code client_ghost_fire_j_skill}。
 * 用「以 tail 结尾」比较能连这种一起收进来，不必维护例外清单。
 */
public final class SkillChannels {

    private static final String NATIVE_NAMESPACE = "smfs";

    private SkillChannels() {
    }

    /**
     * 找到本体用来释放这个技能的通道；没有则返回 {@code null}。
     *
     * @param ghostType 鬼种短名（如 {@code qiaomen_ghost}）
     * @param key       按键标记（N/G/V/J），默认技能传空串
     */
    public static Identifier resolve(String ghostType, String key) {
        if (ghostType == null || ghostType.isEmpty()) {
            return null;
        }
        Set<Identifier> sendable;
        try {
            // 没进游戏时调用会抛异常，按「找不到」处理即可
            sendable = ClientPlayNetworking.getSendable();
        } catch (RuntimeException exception) {
            return null;
        }

        String tail = tailFor(ghostType, key);
        Identifier exact = new Identifier(NATIVE_NAMESPACE, tail);
        if (sendable.contains(exact)) {
            return exact;
        }
        for (Identifier id : sendable) {
            if (!NATIVE_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            if (matches(id.getPath(), tail)) {
                return id;
            }
        }
        return null;
    }

    /**
     * 发一个空包过去。这些技能包都不读参数（读参数的那几个是多参技能，
     * 本来就不在面板里）。
     */
    public static boolean dispatch(String ghostType, String key) {
        Identifier id = resolve(ghostType, key);
        if (id == null) {
            return false;
        }
        ClientPlayNetworking.send(id, PacketByteBufs.empty());
        return true;
    }

    private static String tailFor(String ghostType, String key) {
        if (key == null || key.isEmpty()) {
            return ghostType + "_skill";
        }
        return ghostType + "_" + key.toLowerCase(Locale.ROOT) + "_skill";
    }

    /**
     * {@code tail} 是完整路径，或以 {@code _tail} 结尾（允许 {@code client_} 这类前缀）。
     *
     * <p>默认技能 {@code x_skill} 不会误吞 {@code x_j_skill} —— 中间那个
     * {@code _j} 让后缀对不上。
     */
    private static boolean matches(String path, String tail) {
        return path.equals(tail) || path.endsWith("_" + tail);
    }
}
