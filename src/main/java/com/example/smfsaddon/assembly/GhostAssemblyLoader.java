package com.example.smfsaddon.assembly;

import com.example.smfsaddon.SmfsAddon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 装配表的数据包加载器。
 *
 * <p>读取 {@code data/<任意命名空间>/ghost_assembly/*.json}：
 *
 * <pre>
 * {
 *   "entity": "某附属:某只鬼",
 *   "modules": ["smfsaddon:step_ghost", "smfsaddon:domain_presence"]
 * }
 * </pre>
 *
 * <p>文件夹命名空间不参与解析 —— 谁都能在自己的数据包里写，
 * 也能由整合包统一覆盖，这正是「自动适配别人的鬼」的实现方式。
 *
 * <p>服务端数据包重载（进世界、{@code /reload}）时刷新。
 */
public final class GhostAssemblyLoader implements SimpleSynchronousResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("smfsaddon/assembly");

    private static final String FOLDER = "ghost_assembly";

    public static final Identifier ID = new Identifier(SmfsAddon.MOD_ID, FOLDER);

    public static void register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new GhostAssemblyLoader());
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        GhostAssembly.clear();

        Map<Identifier, Resource> found =
                manager.findResources(FOLDER, path -> path.getPath().endsWith(".json"));

        int entries = 0;
        for (Map.Entry<Identifier, Resource> resource : found.entrySet()) {
            entries += loadOne(resource.getKey(), resource.getValue());
        }

        if (entries > 0) {
            LOGGER.info("装配表已载入 {} 条鬼类型配置（来自 {} 个文件）", entries, found.size());
        }
    }

    private int loadOne(Identifier fileId, Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            if (!root.isJsonObject()) {
                LOGGER.warn("装配表 {} 不是 JSON 对象，已跳过", fileId);
                return 0;
            }
            JsonObject object = root.getAsJsonObject();

            Identifier entityType = readIdentifier(object, "entity");
            if (entityType == null) {
                LOGGER.warn("装配表 {} 缺少合法的 entity 字段，已跳过", fileId);
                return 0;
            }

            List<GhostVariant> variants = readVariants(object, fileId);
            if (variants.isEmpty()) {
                LOGGER.warn("装配表 {} 没有可用的变体，已跳过", fileId);
                return 0;
            }

            GhostAssembly.put(entityType, variants);
            return 1;
        } catch (Exception exception) {
            LOGGER.error("读取装配表 {} 失败", fileId, exception);
            return 0;
        }
    }

    /**
     * 解析变体。
     *
     * <p>兼容两种写法：
     * <pre>
     * { "entity": "...", "modules": [...] }                    // 单变体
     * { "entity": "...", "variants": [ { "id": "a", "weight": 3, "modules": [...] } ] }
     * </pre>
     */
    private List<GhostVariant> readVariants(JsonObject object, Identifier fileId) {
        List<GhostVariant> variants = new ArrayList<>();

        if (object.has("variants") && object.get("variants").isJsonArray()) {
            JsonArray array = object.getAsJsonArray("variants");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    LOGGER.warn("装配表 {} 中有非对象的变体项，已忽略", fileId);
                    continue;
                }
                JsonObject variantObject = element.getAsJsonObject();

                String id = variantObject.has("id") && variantObject.get("id").isJsonPrimitive()
                        ? variantObject.get("id").getAsString()
                        : "variant_" + variants.size();

                int weight = variantObject.has("weight") && variantObject.get("weight").isJsonPrimitive()
                        ? variantObject.get("weight").getAsInt()
                        : 1;

                List<Identifier> modules = readModules(variantObject, fileId);
                if (modules.isEmpty()) {
                    LOGGER.warn("装配表 {} 的变体 {} 没有模块，已忽略", fileId, id);
                    continue;
                }
                variants.add(new GhostVariant(id, weight, modules));
            }
            return variants;
        }

        // 旧格式：整个文件就是一个变体
        List<Identifier> modules = readModules(object, fileId);
        if (!modules.isEmpty()) {
            variants.add(new GhostVariant("default", 1, modules));
        }
        return variants;
    }

    private Identifier readIdentifier(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        return Identifier.tryParse(object.get(key).getAsString());
    }

    private List<Identifier> readModules(JsonObject object, Identifier fileId) {
        if (!object.has("modules") || !object.get("modules").isJsonArray()) {
            return List.of();
        }
        JsonArray array = object.getAsJsonArray("modules");
        List<Identifier> modules = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                LOGGER.warn("装配表 {} 中有非字符串的模块项，已忽略", fileId);
                continue;
            }
            Identifier moduleId = Identifier.tryParse(element.getAsString());
            if (moduleId == null) {
                LOGGER.warn("装配表 {} 中有非法模块 ID：{}", fileId, element.getAsString());
                continue;
            }
            modules.add(moduleId);
        }
        return modules;
    }
}
