package com.example.smfsaddon.client;

import com.example.smfsaddon.SmfsAddon;
import com.example.smfsaddon.network.KakarotKamehamehaPacket;
import com.example.smfsaddon.network.KakarotTransformPacket;
import com.xie.smfs.api.GhostRendererAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：渲染器注册 + 两个技能按键。
 *
 * 实体实现了 CustomGhostModelProvider，本体的 UniversalGhostRenderer 会自动
 * 从实体身上取模型/贴图/动画路径和缩放，所以只需要指定 UNIVERSAL。
 *
 * 注意：不要用 registerWithSimpleResources —— 它把命名空间写死成 "smfs"。
 */
public class SmfsAddonClient implements ClientModInitializer {

    /** 超赛变身（与本体共用"神秘复苏"分类；本体已占用 J/K/G/N/V，这里用 R） */
    public static KeyBinding TRANSFORM_KEY;

    /** 龟派气功（避开原版的 F=副手交换 与本体的 J/K/G/N/V） */
    public static KeyBinding KAMEHAMEHA_KEY;

    /** 灵异技能面板（避开原版与本体的按键） */
    public static KeyBinding SKILL_PALETTE_KEY;

    /** 打开配置界面，默认不绑定 */
    public static KeyBinding CONFIG_KEY;

    /**
     * 自己的按键分类。
     *
     * <p>原来挂在 {@code category.smfs.general} 下，和本体的 J/K/G/N/V 挤在一起，
     * 玩家在按键列表里根本分不清哪几个是我们的。独立分类之后：
     * 一目了然、可以整组改键、也不会因为本体加键而起冲突。
     */
    private static final String KEY_CATEGORY = "key.categories.smfsaddon";

    @Override
    public void onInitializeClient() {
        registerRenderer();
        registerKeyBindings();
        registerFusionScreen();
        registerOwnSkillChannels();

        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.example.smfsaddon.block.HallBlocks.HALL_DRAPE,
                net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.example.smfsaddon.block.HallBlocks.HALL_LANTERN,
                net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.example.smfsaddon.block.HallBlocks.HALL_CARPET,
                net.minecraft.client.render.RenderLayer.getCutout());

        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                SmfsAddon.PIJIAGU_HALL_PHANTOM,
                com.example.smfsaddon.client.HallPhantomEntityRenderer::new);

        // 守护灵礼堂：服务端只发开始通知，客户端自己画（没有实体 → 不会推玩家）
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.example.smfsaddon.network.UnionBowFxPacket.ID,
                (client, handler, buf, sender) -> {
                    byte action = buf.readByte();
                    if (action != 1) {
                        return;
                    }
                    int ownerId = buf.readVarInt();
                    int ticks = buf.readVarInt();
                    client.execute(() -> {
                        HallPhantomClient.start(ownerId, ticks);
                        com.example.smfsaddon.SmfsAddon.LOGGER.info(
                                "[smfsaddon] 礼堂幻影：收到开始通知 owner={} ticks={}", ownerId, ticks);
                    });
                });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> HallPhantomClient.tick(client.world));

        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!HallPhantomClient.isActive()) {
                return;
            }
            var client = net.minecraft.client.MinecraftClient.getInstance();
            var owner = HallPhantomClient.owner(client.world);
            if (owner == null) {
                return;
            }
            double yaw = Math.toRadians(owner.getYaw());
            double hx = owner.getX() + Math.sin(yaw) * 2.0D;
            double hz = owner.getZ() - Math.cos(yaw) * 2.0D;
            var cam = context.camera().getPos();
            int light = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(
                    client.world, owner.getBlockPos());
            var buffers = client.getBufferBuilders().getEntityVertexConsumers();
            HallPhantomRenderer.get().draw(context.matrixStack(), buffers, light,
                    hx - cam.x, owner.getY() - cam.y, hz - cam.z, owner.getYaw(), 0.35F);
            // 关键：世界渲染事件里拿到的 Immediate 必须自己 draw，否则顶点不会提交
            buffers.draw();
            if (!phantomLogged) {
                phantomLogged = true;
                com.example.smfsaddon.SmfsAddon.LOGGER.info(
                        "[smfsaddon] 礼堂幻影：已提交渲染 pos=({},{},{})", hx, owner.getY(), hz);
            }
        });

        // 席面镜头演出：震屏 / 视线下垂
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.example.smfsaddon.network.UnionFxPacket.ID,
                (client, handler, buf, sender) -> {
                    byte action = buf.readByte();
                    int ticks = buf.readVarInt();
                    client.execute(() -> {
                        switch (action) {
                            case com.example.smfsaddon.network.UnionFxPacket.SHAKE ->
                                    UnionFxClient.shake(ticks);
                            case com.example.smfsaddon.network.UnionFxPacket.SEATED ->
                                    UnionFxClient.seated(ticks);
                            default -> UnionFxClient.release();
                        }
                    });
                });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> UnionFxClient.tick(client.player));

        // 礼成体的技能键：复用本体的鬼域技能键（V/G）。
        // 本体的 GhostSkillSystem 对不认识的鬼种是空转，不会重复触发。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen != null) {
                return;
            }
            boolean hasFleshRemoved = client.player.hasStatusEffect(SmfsAddon.FLESH_REMOVED);
            boolean hasUnion = com.xie.smfs.common.events.PlayerEvents.hasGhostType(client.player, "pijiagu_union");
            boolean hasSkin = com.xie.smfs.common.events.PlayerEvents.hasGhostType(client.player, "pijiagu_skin");
            boolean hasBone = com.xie.smfs.common.events.PlayerEvents.hasGhostType(client.player, "pijiagu_bone");
            if (!hasFleshRemoved && !hasUnion && !hasSkin && !hasBone) {
                return;
            }
            pollUnionKeys(hasUnion, hasSkin, hasBone);
        });
    }

    private static boolean phantomLogged;
    private static boolean unionKeyV;
    private static boolean unionKeyG;

    /** 皮嫁骨三只鬼的技能键：V / G（本体「鬼域」键） */
    private static void pollUnionKeys(boolean union, boolean skin, boolean bone) {
        var v = com.xie.smfs.event.KeyEventHandler.GHOST_DOMAIN_TELEPORT;
        if (v.isPressed()) {
            if (!unionKeyV) {
                unionKeyV = true;
                if (union) {
                    com.example.smfsaddon.network.UnionSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.UnionSkillPacket.ACTION_BONE_HAND);
                } else if (skin) {
                    com.example.smfsaddon.network.PetSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.PetSkillPacket.SKIN_V);
                } else {
                    com.example.smfsaddon.network.PetSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.PetSkillPacket.BONE_V);
                }
            }
        } else {
            unionKeyV = false;
        }

        var g = com.xie.smfs.event.KeyEventHandler.GHOST_DOMAIN_TELEPORT_ENTITY;
        if (g.isPressed()) {
            if (!unionKeyG) {
                unionKeyG = true;
                if (union) {
                    com.example.smfsaddon.network.UnionSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.UnionSkillPacket.ACTION_BOW);
                } else if (skin) {
                    com.example.smfsaddon.network.PetSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.PetSkillPacket.SKIN_G);
                } else {
                    com.example.smfsaddon.network.PetSkillPacket.sendFromClient(
                            com.example.smfsaddon.network.PetSkillPacket.BONE_G);
                }
            }
        } else {
            unionKeyG = false;
        }
    }

    /**
     * 找出「这次按 X 该看哪件物品」：主手优先，其次副手。
     *
     * <p>只认手里那件 —— 面板是"这件武器有什么技能"，不是"我有什么技能"。
     */
    private static net.minecraft.item.ItemStack pickFusionSource(net.minecraft.entity.player.PlayerEntity player) {
        if (player == null) {
            return null;
        }
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) {
                continue;
            }
            if (!com.example.smfsaddon.item.ItemGhostHost.boundGhostTypes(stack).isEmpty()) {
                return stack;
            }
        }
        return null;
    }

    /**
     * 把我们自己那两个不叫 {@code _skill} 的通道登记进技能面板。
     *
     * <p>卡卡罗特的变身 / 龟派气功走的是 {@code smfsaddon:kakarot_transform} 和
     * {@code kakarot_kamehameha}（键位 R / C 直接发这两个包），从名字上看不出是技能，
     * 按通道后缀扫不到。与其改协议名，不如自己登记。
     */
    private void registerOwnSkillChannels() {
        // 面板通道带一个 true：从面板走 = 允许融合物品；按键走 = 只认鬼槽（见两个包的 gate）
        java.util.function.Consumer<net.minecraft.network.PacketByteBuf> fromPanel =
                buf -> buf.writeBoolean(true);
        GhostSkillChannels.registerExtra("kakarot_ghost", "R",
                com.example.smfsaddon.network.KakarotTransformPacket.ID, "变身（R 键）", fromPanel);
        GhostSkillChannels.registerExtra("kakarot_ghost", "C",
                com.example.smfsaddon.network.KakarotKamehamehaPacket.ID, "龟派气功（C 键）", fromPanel);

        // 礼成体：走本体鬼域技能的那四个键（V/G/J/N），先接 V 和 G
        java.util.function.Consumer<net.minecraft.network.PacketByteBuf> boneHand =
                buf -> buf.writeByte(com.example.smfsaddon.network.UnionSkillPacket.ACTION_BONE_HAND);
        java.util.function.Consumer<net.minecraft.network.PacketByteBuf> bow =
                buf -> buf.writeByte(com.example.smfsaddon.network.UnionSkillPacket.ACTION_BOW);
        GhostSkillChannels.registerExtra("pijiagu_union", "V",
                com.example.smfsaddon.network.UnionSkillPacket.ID, "骨手（V 键）", boneHand);
        GhostSkillChannels.registerExtra("pijiagu_union", "G",
                com.example.smfsaddon.network.UnionSkillPacket.ID, "拜堂（G 键）", bow);
    }

    /**
     * 把灵异装置的界面挂到我们自己的 ScreenHandler 类型上。
     *
     * <p>这一步只在客户端做：服务端只知道「有个界面要开」，
     * 具体画成什么样是客户端的事。
     */
    private void registerFusionScreen() {
        HandledScreens.register(
                com.example.smfsaddon.fusion.FusionScreens.TYPE,
                com.example.smfsaddon.fusion.GhostFusionScreen::new
        );
        SmfsAddon.LOGGER.info("[smfsaddon] 灵异装置界面已绑定");
    }

    private void registerRenderer() {
        boolean ok = GhostRendererAPI.getInstance().registerWithRendererType(
                SmfsAddon.KAKAROT_GHOST,
                GhostRendererAPI.RendererType.UNIVERSAL);

        if (!ok) {
            SmfsAddon.LOGGER.error("[smfsaddon] 卡卡罗特鬼渲染器注册失败");
        } else {
            SmfsAddon.LOGGER.info("[smfsaddon] 卡卡罗特鬼渲染器注册完成");
        }

        // 婚礼视角锁定：收包 + 每 tick 递减剩余时长
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.example.smfsaddon.pijiagu.CameraLock.ID,
                (client, handler, buf, responseSender) -> {
                    int entityId = buf.readVarInt();
                    int ticks = buf.readVarInt();
                    boolean takeCamera = buf.readBoolean();
                    client.execute(() -> CameraLockClient.accept(entityId, ticks, takeCamera));
                });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> CameraLockClient.tick());

        // 皮嫁骨的两只必须补注册渲染器。
        // 它们的 EntityType 是在本体初始化之后才创建的（服务端延迟注册），
        // 客户端初始化这一刻还是 null，所以只能等到有了再注册 ——
        // 漏注册的表现不是"不显示"，而是 EntityRenderDispatcher 拿到 null
        // 渲染器直接 NPE 崩游戏。
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> {
                    if (pijiaguRenderersReady) {
                        return;
                    }
                    if (SmfsAddon.PIJIAGU_SKIN == null || SmfsAddon.PIJIAGU_BONE == null
                            || SmfsAddon.PIJIAGU_HALL == null || SmfsAddon.PIJIAGU_UNION == null) {
                        return;
                    }
                    if (!GhostRendererAPI.isRegistered()) {
                        return;
                    }
                    var api = GhostRendererAPI.getInstance();
                    boolean skin = api.registerWithRendererType(
                            SmfsAddon.PIJIAGU_SKIN, GhostRendererAPI.RendererType.UNIVERSAL);
                    boolean bone = api.registerWithRendererType(
                            SmfsAddon.PIJIAGU_BONE, GhostRendererAPI.RendererType.UNIVERSAL);
                    boolean hall = api.registerWithRendererType(
                            SmfsAddon.PIJIAGU_HALL, GhostRendererAPI.RendererType.UNIVERSAL);
                    boolean union = api.registerWithRendererType(
                            SmfsAddon.PIJIAGU_UNION, GhostRendererAPI.RendererType.UNIVERSAL);
                    pijiaguRenderersReady = true;
                    SmfsAddon.LOGGER.info("[smfsaddon] 皮嫁骨渲染器注册：皮={} 骨={} 礼堂={} 礼成={}",
                            skin, bone, hall, union);
                });
    }

    /** 皮嫁骨渲染器只能在 EntityType 就绪后注册，注册过就不再重复 */
    private static boolean pijiaguRenderersReady;

    private void registerKeyBindings() {
        TRANSFORM_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.transform",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KEY_CATEGORY
        ));

        KAMEHAMEHA_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.kamehameha",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                KEY_CATEGORY
        ));

        SKILL_PALETTE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.skill_palette",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                KEY_CATEGORY
        ));

        CONFIG_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smfsaddon.config",
                InputUtil.Type.KEYSYM,
                // 默认不绑定：玩家自己指定，避免和任何东西撞
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            while (CONFIG_KEY.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new com.example.smfsaddon.client.AddonConfigScreen(null));
                }
            }

            while (SKILL_PALETTE_KEY.wasPressed()) {
                // 面板跟着物品走：拿哪件就只看哪件的鬼。
                // 背包里可能有好几把融合武器，扫全背包会混在一起。
                net.minecraft.item.ItemStack source = pickFusionSource(client.player);
                if (source == null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.translatable("message.smfsaddon.skill_palette.need_item"),
                            true);
                } else {
                    client.setScreen(new GhostSkillPaletteScreen(source));
                }
            }

            // 是否满足条件交给服务端判断，客户端只负责把请求发出去
            // 按键路径带 false：只认真的养在鬼槽里的卡卡罗特。
            // 融合在物品上的从技能面板（X）放，那边带 true。
            while (TRANSFORM_KEY.wasPressed()) {
                var buf = PacketByteBufs.create();
                buf.writeBoolean(false);
                ClientPlayNetworking.send(KakarotTransformPacket.ID, buf);
            }
            while (KAMEHAMEHA_KEY.wasPressed()) {
                var buf = PacketByteBufs.create();
                buf.writeBoolean(false);
                ClientPlayNetworking.send(KakarotKamehamehaPacket.ID, buf);
            }
        });
    }
}
