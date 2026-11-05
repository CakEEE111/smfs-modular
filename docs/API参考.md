# 神秘复苏（smfs）1.5.0 附属开发参考

本文基于对 `[XIE]神秘复苏-1.20.1-fabric-1.5.0.jar` 的反编译结果整理（反编译源码见 `C:\Users\CakEEEE\Desktop\_smfs_tools\smfs-src`）。

> 注意：Gitee 仓库 `mysterious-revival` 的源码版本是 **26.9.21**，比你现在跑的 1.5.0 新很多。
> 仓库里的 `GhostDomainAPI`、`GhostChildAPI`、`SkillGate`、`DeathProcessGate` 在 1.5.0 中**不存在**。
> 以本文（对应 1.5.0）为准写代码，升级本体后再迁移。

---

## 1. 依赖本体

本体 jar 是 **intermediary** 映射的，不能直接 `compileOnly`，否则你看到的全是 `class_2960`。
必须用 Loom 的 mod 配置，它会自动重映射成 yarn 名：

```groovy
modImplementation files("libs/smfs-1.5.0.jar")
```

版本必须与本体完全一致，否则链接失败：

| 项 | 值 |
|---|---|
| Minecraft | 1.20.1 |
| Yarn | 1.20.1+build.10 |
| Fabric Loader | ≥ 0.14.21（本体编译用 0.17.2） |
| Fabric API | 0.92.6+1.20.1 |
| GeckoLib | 4.2.4 |

`fabric.mod.json` 里必须声明依赖，Fabric 才会让本体先初始化：

```json
"depends": { "smfs": ">=1.5.0", "geckolib": ">=4.2.4" }
```

**别把同一个 jar 再丢进 `run/mods/`**，Loom 已经把它放上运行期 classpath，重复会报 duplicate mod。

---

## 2. 生命周期

| 阶段 | 可用的 API |
|---|---|
| `ModInitializer.onInitialize` | `GhostAPI`、`TameableItemAPI`、`ConfigAPI`、`GhostSpawnAPI`、`QuestAPI` |
| `ClientModInitializer.onInitializeClient` | `GhostRendererAPI` |

入口里建议先做一次自检：

```java
if (!GhostAPI.isRegistered()) throw new IllegalStateException("smfs 未初始化");
```

---

## 3. GhostAPI —— 自定义厉鬼

`GhostAPI.getInstance()` 拿到实例。全部方法：

**注册与生成**

| 方法 | 说明 |
|---|---|
| `<T extends GhostEntity> EntityType<T> registerCustomGhost(Identifier, Class<T>, EntityDimensions)` | 注册实体类型 |
| `GhostEntity spawnGhost(EntityType, World, x, y, z, boolean hasDomain, int domainLevel, double radius, char terrorLevel)` | 直接生成一只 |
| `boolean isGhostEntity(Entity)` | 判定 |
| `Builder createCustomGhostAttributes(maxHealth, speed, attackDamage, followRange)` | 自定义属性 |
| `Builder getDefaultGhostAttributes()` | 本体默认属性 |

**灵异数值**

| 方法 | 说明 |
|---|---|
| `int getSpiritualStrength / setSpiritualStrength` | 灵异强度（≈灵力上限） |
| `int getSpiritualResistance / setSpiritualResistance` | 灵异抗性 |
| `int getSpiritualDamage / setSpiritualDamage` | 灵异伤害 |
| `float getRecoveryFactor / setRecoveryFactor` | 恢复因子 |

**鬼域**

| 方法 | 说明 |
|---|---|
| `boolean hasGhostDomain` / `void enableGhostDomain` / `void disableGhostDomain` | 开关 |
| `int getGhostDomainLevel` / `setGhostDomainLevel` | 层数 |
| `float getGhostDomainRadius` / `setGhostDomainRadius` | 半径 |
| `int getPlayerGhostDomainLevel(PlayerEntity)` | 玩家当前鬼域层数（0 = 未开启） |

**行为定制（不写子类也能改）**

| 方法 | 说明 |
|---|---|
| `setCustomAttackLogic(GhostEntity, Consumer<PlayerEntity>)` | 换掉命中逻辑 |
| `setCustomTargetSelector(GhostEntity, Predicate<PlayerEntity>)` | 换掉选敌逻辑 |
| `registerCustomGoal(GhostEntity, int priority, Goal)` | 加 AI goal |
| `registerCustomTargetGoal(GhostEntity, int priority, Goal)` | 加 target goal |
| `clearCustomGoals(GhostEntity)` | 清空自定义 goal |
| `addStateChangeListener(GhostEntity, BiConsumer<GhostEntity,String>)` | 监听状态变化 |
| `triggerCustomEvent(GhostEntity, String, Object)` | 自定义事件 |
| `addCustomNbtSerializer(GhostEntity, key, serializer, deserializer)` | 把自定义字段写进 NBT |
| `void suppressGhost / unsuppressGhost / isGhostSuppressed` | 压制 |
| `boolean isGhostDeadlocked` | 死机判定 |
| `getGhostLevel / setGhostLevel` | 危险等级 1–5 |
| `getTerrorLevel / setTerrorLevel` | 恐怖等级 `A/B/C/D/S` |
| `isVisible / setVisible` | 可见性 |
| `getAttackCooldown / setAttackCooldown` | 攻击冷却（刻） |
| `setMovementSpeed / setFollowRange` | 移速与索敌范围 |
| `int getGhostTypeLevel(PlayerEntity, String ghostType)` | 玩家驾驭的某类鬼的等级（0 = 没有） |

---

## 4. 写一只自己的鬼

```java
public class MyGhost extends GhostEntity {
    public MyGhost(EntityType<? extends GhostEntity> type, World world) {
        super(type, world, true, 1, 6.0D, 'B'); // 有鬼域 / 1层 / 半径6 / 恐怖等级B
    }
}
```

约束与坑：

1. **必须**有 `public (EntityType, World)` 构造器 —— `registerCustomGhost` 用反射 `newInstance`。
2. `registerCustomGhost` **只注册 EntityType，不注册属性**，必须自己补：
   ```java
   FabricDefaultAttributeRegistry.register(MY_GHOST, GhostEntity.createGhostAttributes());
   ```
3. 恐怖等级只接受 `A/B/C/D/S`，其它值会被强制改成 `C`（本体构造器里的判断）。
4. 恐怖等级决定默认恢复因子，进而影响复苏速度。

可重写的钩子（父类 `protected`）：`applyDefaultEffects(PlayerEntity)`、`executeAttack(...)`、`shouldAttackGhost(GhostEntity)`、`getDefaultGhostDomainRadius()`、`handleMovementAnimations`、`handleAttackAnimations`。

---

## 5. TameableItemAPI —— 可驾驭物品（容器）

```java
Item item = TameableItemAPI.getInstance().registerCustomTameableItem(
        new Identifier(MOD_ID, "my_ghost"), MyGhostItem.class,
        800, 50, 60, 0, 0.3);   // 灵力上限 / 抗性 / 伤害 / 理智 / 复活系数
Registry.register(Registries.ITEM, new Identifier(MOD_ID, "my_ghost"), item);
```

物品类必须满足：

```java
public class MyGhostItem extends BaseGhostEyeItem {
    public MyGhostItem(Settings s, int a, int b, int c, int d, double e) { super(s, a, b, c, d, e); }
    @Override public String getGhostType() { return "my_ghost"; }        // 鬼种 ID
    @Override protected Text getBindSuccessMessage(int slot) { ... }
    @Override protected void sendSlotsFullMessage(PlayerEntity player) { ... }
}
```

坑：

1. 构造器签名必须**正好**是 `(Settings,int,int,int,int,double)` —— 反射构造，多一个参数就崩。
2. `registerCustomTameableItem` **不会**帮你注册进 `Registries.ITEM`，只负责登记鬼种，注册要自己做。
3. 绑定逻辑（找空槽→写 NBT→弹能力面板）由 `BaseGhostEyeItem#use` 全自动处理，不用自己写。
4. `getGhostType()` 返回的字符串要与后续技能系统识别的 ID 一致。

其它可用方法：`getGhostSlotItem`、`getGhostSlotLevel`、`getGhostSlotRevivalDegree`、`getGhostSlotRequiredRevivalDegree`、`getGhostTypeInSlot`、`setGhostSlotItem`、`setGhostSlotLevel`、`setGhostSlotRevivalDegree`、`clearGhostSlot`、`getTamedGhostCount`、`hasTamedGhost`。

---

## 6. GhostRendererAPI —— 客户端渲染

```java
GhostRendererAPI.getInstance().registerWithCustomResources(TYPE, new GhostRendererAPI.ResourceProvider() {
    public Identifier getModelResource(GhostEntity e)     { return id("geo/my_ghost.geo.json"); }
    public Identifier getTextureResource(GhostEntity e)   { return id("textures/entity/my_ghost.png"); }
    public Identifier getAnimationResource(GhostEntity e) { return id("animations/my_ghost.animation.json"); }
    public float getScale(GhostEntity e)                  { return 1.0f; }
    public boolean isTranslucent(GhostEntity e)           { return true; }
});
```

**坑（重要）**：不要用 `registerWithSimpleResources(...)` —— 它内部把命名空间写死成 `"smfs"`，附属模组传进去的路径会去本体资源里找，必然 404。

也可以 `registerWithRendererType(TYPE, RendererType.UNIVERSAL | HUMAN | CUSTOM)` 直接复用本体渲染器。

---

## 7. 其它 API

**GhostSpawnAPI**（静态类）

```java
GhostSpawnAPI.registerGhostSpawn(TYPE, "my_ghost", 3);                       // 第3天起，默认间隔/概率
GhostSpawnAPI.registerGhostSpawn(TYPE, "my_ghost", 3, 20, 600, true);        // 自定义概率/间隔/仅夜晚
GhostSpawnAPI.unregisterGhostSpawn(TYPE);
GhostSpawnAPI.lockGhostType(world, TYPE);   // 锁死，不自然刷新
```

**QuestAPI**：`registerQuestTemplate(QuestTemplate)`、`registerQuestTemplates(List)`、`registerSimpleQuest(...)`、`updateQuestProgress(player, questId, objectiveId, amount)`、`hasCompletedQuest`、`getActiveQuests`、`isQuestRegistered`、`unregisterQuestTemplate(s)`、`reloadAddonQuests`、`getAddonQuestCount`。任务模板类为 `QuestManager.QuestTemplate` / `QuestManager.QuestObjective`（本体内部类，可直接引用）。

**PopupAPI**：`showSimplePopup(title, lines)`、`showTwoColumnPopup`、`showCustomPopup`、`showNotification`、`showErrorPopup`、`showSuccessPopup`、`addPopupCloseListener`。

**ConfigAPI**：`addIntConfig/addDoubleConfig/addBooleanConfig/addStringConfig(module, key, default, desc)`、`getXxxConfig`、`setXxxConfig`、`saveConfig(module)`、`addConfigChangeListener`。

**PlayerEvents**（读写玩家灵异数据，静态方法，最常用）

```java
int  slot   = PlayerEvents.getUnlockedGhostSlot(player);      // 找空槽，-1 表示满
float spirit = PlayerEvents.getCurrentSpirit(player);
float max    = PlayerEvents.getMaxSpirit(player);
float sanity = PlayerEvents.getSpiritAttribute(player, SpiritAttributes.SANITY);
int  lvl    = PlayerEvents.getGhostSlotLevel(player, slot);
int  rev    = PlayerEvents.getGhostSlotRevivalDegree(player, slot);
boolean has = PlayerEvents.hasGhostType(player, "my_ghost");
PlayerEvents.setGhostSlotData(player, slot, stack);
PlayerEvents.addSpiritAttribute(player, SpiritAttributes.SPIRIT_RESISTANCE, 10f);
```

常量：`PlayerEvents.GHOST_SLOT_COUNT = 10`，`PlayerEvents.SMFS_DATA_KEY = "smfs_spirit_data"`。

灵异属性对象在 `com.xie.smfs.effect.SpiritAttributes`，包含 `SPIRIT_RESISTANCE`、`SPIRIT_DAMAGE`、`CURRENT_SPIRIT`、`MAX_SPIRIT`、`REVIVAL_FACTOR`、`SANITY`、`MAX_SANITY`、`TEMP_*` 等。

---

## 8. 调试建议

- 本体指令：`/xie`、`/xianwang`、`/guiniao`（本体自带，用于刷鬼/调试）。
- 物品里有个 `GhostDebugStickItem`，本体调试棒。
- 附属报错优先看 `logs/latest.log` 里 `smfs/` 前缀的 logger。
