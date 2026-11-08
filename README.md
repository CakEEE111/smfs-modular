# 厉鬼模块化

> 《神秘复苏》的**非官方附属模组**。把厉鬼装进你手上的任何东西。

模组依赖本体 [神秘复苏](https://gitee.com/xiaoxieY/mysterious-revival)，本体需为 **1.5.0 / Fabric 1.20.1**。
本模组不包含本体的任何代码与素材。

---

## 它做什么

**万物皆可融合。** 剑、镐、锄头、盾牌、木棍——任何物品都能融进厉鬼，
变成带灵异词条的装置。

融进去之后拿到三样东西：

| 能力 | 说明 |
|---|---|
| **技能** | 按 `X` 打开面板，手上拿的是什么就只看什么的技能，点击释放 |
| **被动** | 物品在背包里就生效，不需要手持，也不需要鬼槽 |
| **灵异伤害** | 打鬼才有意义——原版攻击对鬼无效，这份伤害才掉它的灵异强度 |

**鬼也会变强。** 新世界生成的厉鬼会按难度借用别人的杀人规律（简单 2 / 普通 3 / 困难 4），
同一只鬼可能既按自己的规矩杀你，也按水鬼、抬头鬼的规矩杀你。

---

## 玩法要点

### 融合

从灵异主界面右侧进入「装置」，左槽放物品、右槽放厉鬼。

界面会算两个数：

```
压制值 = 最大灵异 + 灵异抗性 + 灵异伤害 × 0.5
需求值 = (入门费 + 各模块代价) × 30 × (1 + 0.75 × 已有鬼数)
```

- 压制够 → 直接成功
- 压制不够 → 让你赌一把，赢了规律照样进，输了挨反噬

**反噬四件套**：灵异强度、血量、鬼槽复苏度，外加素材鬼被消耗。
所以这不是免费加强，是拿鬼换武器。

### 灵异伤害

```
基础灵异伤害 = Σ(每只鬼的最大灵异 × 10%)
继承倍率     = 鬼数 × 50%
实际伤害     = 基础灵异伤害 + 玩家灵异伤害 × 继承倍率
```

融三只高数值的鬼约 700+ 基础，比本体最强的灵异武器还高一截。

---

## 配置

首次启动会在 `config/smfsaddon.json` 生成一份配置；也可以在游戏内**灵异主界面 →「配置」**里用加减按钮调，点保存立即生效，不用重启。

| 项 | 默认 | 说明 |
|---|---|---|
| `crossing.easyRules` / `normalRules` / `hardRules` | 2 / 3 / 4 | 各难度借几条规律 |
| `crossing.normalMechanisms` / `hardMechanisms` | 1 / 2 | 各难度借几个机制 |
| `crossing.allowCooldownTicks` | 40 | 交错出手间隔（tick），调大更温和 |
| `fusion.stealRatio` | 0.10 | 每只鬼贡献最大灵异的比例 |
| `fusion.inheritPerGhost` | 0.50 | 每只鬼增加的继承倍率 |
| `fusion.entryCost` / `costScale` | 50 / 30 | 融合需求 = (入门费 + 模块代价) × 系数 |
| `escape.enabled` | true | 关掉后融合失败不会复苏厉鬼 |
| `escape.baseChance` / `stepChance` | 0.05 / 0.10 | 复苏概率与每次失败的叠加 |

觉得太难：把 `hardRules` 调到 2、`allowCooldownTicks` 调到 60。
觉得太简单：反过来。

## 命令

```
/ghostmodules check            质量检测：规则总数、停用、白借的交错
/ghostmodules list             最近的鬼有哪些模块
/ghostmodules rules            最近的鬼的规则（优先级/代价/互斥组）
/ghostmodules attach <模块>     给最近的鬼手动挂载
/ghostmodules detach <模块>     卸载
/ghostmodules slots            鬼槽 API 视角（含融合物品投影）
/ghostmodules assembly         当前装配表
/ghostmodules spawn <实体ID>    就地生成一只鬼（会按当前难度自动交错）
/ghostmodules cross            给最近的鬼补上交错（老存档用）
/ghostmodules cross force      重抽（先卸再抽）
/ghostmodules haunt <实体ID>    把看着的方块变成鬼方块（里面关一只鬼）
```

按键：`X` 技能面板　`R` 变身（卡卡罗特鬼）　`C` 龟派气功

---

## 联机与服务器

**只能用「内嵌服务器」联机**：主机在自己的存档里开「对局域网开放」，或用 Essential / 联机工具。玩家都装上本体与本模组即可。

**独立服务器（dedicated server）暂时不行**，原因不在本模组 —— 本体 1.5.0 的 `ModNetwork.register` 在服务端初始化时引用了 `net.minecraft.client.gui.screen.Screen`，服务端环境加载该类直接抛异常：

```
java.lang.RuntimeException: Cannot load class net.minecraft.client.gui.screen.Screen in environment type SERVER
    at com.xie.smfs.network.ModNetwork.register(ModNetwork.java:627)
    at com.xie.smfs.Smfs.onInitialize(Smfs.java:121)
```

所以只要本体没能上服务端，任何附属都上不去。等本体修好这一处，本模组不需要改：客户端专用的 mixin 单独放在 `smfsaddon.mixins.json` 的 `client` 段，主入口不引用任何客户端类，所有状态按玩家 UUID 记账。

## 构建

```bash
./gradlew remapJar
```

产物在 `build/libs/`。

需要把本体 `smfs-1.5.0.jar` 放到 `libs/` 下。

---

## 声明

- 本模组为《神秘复苏》的**非官方同人二创**，与原著作者、本体模组作者均无隶属关系
- 完全免费，禁止售卖、禁止以本体名义接单
- 不包含本体或原著的任何美术、文本、音效素材
- 卡卡罗特相关内容属于玩梗，与龙珠版权方无关

## 许可

代码以 **MIT** 授权，见 [LICENSE](LICENSE)。随便用、随便改、随便再发布，保留版权声明即可。
