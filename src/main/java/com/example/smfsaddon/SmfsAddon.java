package com.example.smfsaddon;

import com.example.smfsaddon.effect.SuperSaiyanEffect;
import com.example.smfsaddon.entity.KakarotGhostEntity;
import com.example.smfsaddon.item.KakarotGhostItem;
import com.example.smfsaddon.manager.KakarotMeleeHandler;
import com.example.smfsaddon.network.KakarotKamehamehaPacket;
import com.example.smfsaddon.network.KakarotTransformPacket;
import com.xie.smfs.api.GhostAPI;
import com.xie.smfs.api.GhostSpawnAPI;
import com.xie.smfs.api.TameableItemAPI;
import com.xie.smfs.common.events.PlayerEvents;
import com.xie.smfs.entity.GhostEntity;
import com.xie.smfs.util.GhostUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 附属模组主入口。
 *
 * fabric.mod.json 里声明了 depends.smfs，Fabric 会先初始化本体再初始化我们，
 * 所以这里可以安全地调用 GhostAPI / TameableItemAPI。
 */
public class SmfsAddon implements ModInitializer {

    public static final String MOD_ID = "smfsaddon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 供静态回调（mixin）取回入口实例 */
    private static SmfsAddon INSTANCE;

    /** 本体晚于我们初始化时，内容注册是否处于挂起状态 */
    private static boolean pendingContentRegistration = false;

    public static EntityType<KakarotGhostEntity> KAKAROT_GHOST;
    public static Item KAKAROT_GHOST_ITEM;
    public static StatusEffect SUPER_SAIYAN;

    @Override
    public void onInitialize() {
        INSTANCE = this;

        // 厉鬼模块系统不依赖本体 API，先注册，保证后面即使降级它依然可用。
        com.example.smfsaddon.ghost.module.GhostModuleRegistry.registerBuiltins();
        com.example.smfsaddon.command.GhostModulesCommand.register();
        com.example.smfsaddon.command.GhostCodexCommand.register();
        // 非实体宿主验证方块：不依赖本体 API，放在这里注册
        com.example.smfsaddon.block.ModHostBlocks.register();
        // 非实体宿主验证道具：同上
        com.example.smfsaddon.item.ModHostItems.register();
        // 装配表：从数据包读取「鬼类型 → 模块」，可适配任何附属的鬼
        com.example.smfsaddon.assembly.GhostAssemblyLoader.register();
        // 灵异装置：界面类型 + 打开请求通道
        com.example.smfsaddon.fusion.FusionScreens.register();
        com.example.smfsaddon.fusion.FusionOpen.registerServer();
        com.example.smfsaddon.fusion.FusionGraft.registerServer();
        // 玩家退出时释放图鉴缓存与虚拟鬼槽缓存
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.example.smfsaddon.ghost.module.RuleCodex.forget(handler.getPlayer().getUuid());
            com.example.smfsaddon.item.FusedGhostSlots.forget(handler.getPlayer().getUuid());
        });
        // 世界卸载时清掉交错用的影子实例，别让 ServerWorld 被静态表攥住
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
                server -> com.example.smfsaddon.crossing.GhostRuleCrossing.clearCache());
        // 开服就先建好交错候选池，避免第一只鬼生成时卡一下
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(
                server -> com.example.smfsaddon.crossing.GhostRuleCrossing.warmUp(server.getOverworld()));

        if (GhostAPI.isRegistered() && TameableItemAPI.isRegistered()) {
            registerContent();
            return;
        }

        // Fabric 不保证 main 入口按依赖顺序执行：实测 smfsaddon 有时会跑在 smfs 之前。
        // 直接抛异常会让整个客户端起不来，所以改为挂起，
        // 由 SmfsInitializerMixin 在 smfs.onInitialize 结束时回调补注册。
        pendingContentRegistration = true;
        LOGGER.warn("[smfsaddon] 本体尚未初始化，内容注册已挂起，将在 smfs 初始化完成后执行");
    }

    /**
     * 由 {@code SmfsInitializerMixin} 在 smfs 初始化结束时回调。
     *
     * <p>只在「我们跑在本体前面」这条路径上真正做事；
     * 如果本体本来就先初始化，这里直接返回，不会重复注册。
     */
    public static void onSmfsReady() {
        if (!pendingContentRegistration || INSTANCE == null) {
            return;
        }
        pendingContentRegistration = false;

        if (!GhostAPI.isRegistered() || !TameableItemAPI.isRegistered()) {
            LOGGER.error("[smfsaddon] smfs 初始化结束但 API 仍不可用（GhostAPI={} TameableItemAPI={}），跳过内容注册",
                    GhostAPI.isRegistered(), TameableItemAPI.isRegistered());
            return;
        }
        INSTANCE.registerContent();
    }

    private void registerContent() {
        registerSuperSaiyanEffect();
        registerKakarotGhost();
        registerKakarotGhostItem();
        registerGhostUtilsMappings();
        KakarotTransformPacket.register();
        KakarotKamehamehaPacket.register();
        registerOrphanRestore();
        KakarotMeleeHandler.register();

        // 技能表改为按通道推导（client/GhostSkillChannels），
        // 这里是服务端，通道表要等客户端连上来才有，故不在此处扫描

        LOGGER.info("[smfsaddon] 卡卡罗特鬼加载完成");
    }

    /**
     * 崩溃兜底：变身会把玩家 NBT 里的灵异伤害放大 50 倍，正常退出时会还原。
     * 若服务器在变身期间异常崩溃，残留的放大值会跟着存档走 —— 玩家上线时检测到
     * "有备份但没在变身" 就自动还原，避免永久 50 倍。
     */
    private void registerOrphanRestore() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player.hasStatusEffect(SUPER_SAIYAN)) {
                return;
            }
            if (PlayerEvents.getSpiritAttributes(player).contains(SuperSaiyanEffect.BACKUP_KEY)) {
                SuperSaiyanEffect.restoreSpiritDamage(player);
                LOGGER.warn("[smfsaddon] 检测到残留的变身伤害备份，已为 {} 还原", player.getName().getString());
            }
        });
    }

    /**
     * 把我们的鬼种登记进本体的 GhostUtils。
     *
     * 不注册的后果（实测）：驾驭成功弹窗会显示"未知鬼魂"，技能列表和鬼域全部为"无"。
     * 本体这四张表就是给附属准备的，注册后本体的驾驭面板、物品映射、实体创建都会认识我们的鬼。
     */
    private void registerGhostUtilsMappings() {
        GhostUtils.registerAddonDisplayNameMapping("kakarot_ghost", "卡卡罗特");

        GhostUtils.registerAddonAbilityMapping("kakarot_ghost", List.of(
                "代号：卡卡罗特",
                "被动：气焰领域，变身后体表常驻金色气焰",
                "袭击：气焰冲击，命中附加金色爆发",
                "",
                "主动技能：龟派气功（C 键，消耗复苏度）",
                "",
                "十级技能：超级赛亚人（R 键，随开随放，金发 + 灵异伤害 ×50）",
                "",
                "鬼域：是",
                "",
                "§6成神：变身期间灵异伤害 ×2.5，移速 +30%"
        ));

        GhostUtils.registerAddonGhostMapping("kakarot_ghost", new ItemStack(KAKAROT_GHOST_ITEM));
        GhostUtils.registerAddonEntityCreationMapping("kakarot_ghost", KAKAROT_GHOST);
    }

    /** 0) 超赛变身 buff —— 必须在物品/网络包之前注册好 */
    private void registerSuperSaiyanEffect() {
        SUPER_SAIYAN = new SuperSaiyanEffect();
        Registry.register(Registries.STATUS_EFFECT, new Identifier(MOD_ID, "super_saiyan"), SUPER_SAIYAN);
    }

    /** 1) 注册实体 + 属性 + 刷新规则 */
    private void registerKakarotGhost() {
        KAKAROT_GHOST = GhostAPI.getInstance().registerCustomGhost(
                new Identifier(MOD_ID, "kakarot_ghost"),
                KakarotGhostEntity.class,
                EntityDimensions.fixed(0.7f, 2.5f)   // 模型实测 2.53 格高
        );

        // GhostAPI 只注册 EntityType，属性必须自己补
        FabricDefaultAttributeRegistry.register(KAKAROT_GHOST, GhostEntity.createGhostAttributes());

        // 第 10 天起，夜晚 5% 概率，间隔 1 游戏日
        GhostSpawnAPI.registerGhostSpawn(KAKAROT_GHOST, "kakarot_ghost", 10, 5, 24000, true);
    }

    /** 2) 注册可驾驭的容器物品 */
    private void registerKakarotGhostItem() {
        KAKAROT_GHOST_ITEM = TameableItemAPI.getInstance().registerCustomTameableItem(
                new Identifier(MOD_ID, "kakarot_ghost"),
                KakarotGhostItem.class,
                2000,   // 灵力上限加成
                80,     // 灵异抗性加成
                200,    // 灵异伤害加成
                0,      // 理智值加成
                0.6     // 复苏系数
        );

        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "kakarot_ghost"), KAKAROT_GHOST_ITEM);
    }
}
