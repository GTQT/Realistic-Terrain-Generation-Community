## [1.0.33]

> 按用户要求**不再递增 `mod_version`**；本节内的后续修补都沿用 1.0.33 这一个版本号，
> jar 就地重建。分辨构建版本看日志里有没有对应的新行（见下文）。

### 追加（同版本）：客户端群系 provider 的根因

**1.0.33 的客户端探针实测：**

```
[RTG-CLIENT] pos(-6,94,60) F3=minecraft:mushroom_island | 布局=biomesoplenty:alps_foothills
             provider=minecraft:plains(BiomeProvider)
             int[MC=202]=14 int[布局=172]=14 转置命中=false
             字节[MC]=113 字节[布局]=113 reidChunk=true
```

两条读数定性：

1. **客户端的群系数组整块 256 格是同一个编号**（202 / 172 / 240 / 15 / 239 / 254 全等于 14，
   下一区块全等于 24）—— 真实区块的数组不可能均匀，这是"单一来源的一次采样填满"的占位值；
   **转置假设因此被否掉**。
2. **`provider类=BiomeProvider`** —— 客户端用的是**原版** provider，所以那个采样来自原版 GenLayer，
   而它的稀有产物正是**蘑菇岛与深海**（与"F3 只显示 Ocean 和两个 mushroomIsland"吻合）。

⇒ **根因**：`WorldTypeRTG.getBiomeProvider` 里有 `if (!world.isRemote)` 守卫，
**客户端一律拿到原版 `BiomeProvider`**。于是地表/地形由服务端的 rtgc 布局生成，
而客户端手上的群系来自原版 GenLayer —— **两边从此分家**。

**修法**：去掉该守卫，让客户端也拿到 `BiomeProviderBOP`（或 `BiomeProviderRTG`），
整段包 `try/catch`，失败则退回原版 provider 并打 `ERROR`（附"这会让客户端群系与实际地形不符"的说明）。
`getChunkGenerator` 的 `!world.isRemote` 守卫**保持不动** —— 客户端不生成区块，那里不该动。

**新增日志**（用来确认修好没有）：

```
[RTG] 群系 provider：side=… world=… dimType=… → <provider 类名>
```

应看到 **CLIENT 与 SERVER 两条**，且类名都是 `BiomeProviderBOP`。

### 追加（同版本）：**撤销**上一版的客户端 provider 改动，并加结构性防护

上一版去掉 `WorldTypeRTG.getBiomeProvider` 的 `!world.isRemote` 守卫，**造成了灾难性后果，已全部撤销**。

```
[13:22:00] [Server thread] 建立布局：seed=7175654573957494923
[13:22:09] [Client thread] 建立布局：seed=0  旧seed=7175654573957494923     ← 客户端用 seed 0 覆盖了全局布局
```

1.12.2 的客户端世界是 `new WorldClient(..., new WorldSettings(0L, ...), ...)` 建起来的 ——
**服务端从不把真实世界种子发给客户端，所以 `world.getSeed()` 在客户端恒为 0**。
客户端构造 `BiomeProviderBOP` 会调 `RtgLayoutAccess.forSeed(0)`，**把服务端正在用的全局布局整体覆盖**。
于是"玩家加入之后生成的所有区块"换了布局，与加入之前生成好的**出生点区块（约 25×25 区块）**
之间出现**垂直断层** —— 这正是用户实测到的现象。**这是我引入的回归。**

**修法（两层）：**

- `WorldTypeRTG.getBiomeProvider`：恢复 `!world.isRemote` 守卫（客户端仍用原版 provider）。
- `RtgLayoutAccess.forSeed`：新增**结构性防护** —— 若 `layout` 已存在、种子不同、且当前是客户端，
  **拒绝重建并返回现有布局**，同时打 `WARN` 说明原因（客户端 `getSeed()` 恒为 0）。
  另把"布局被换掉"从 `INFO` 升级为 `WARN`，附上新旧种子与"会导致垂直断层"的说明。

### 追加（同版本）：**mixin 从未被加载** —— 这才是 F3 一直不对的真正原因

前一版把 `ChunkGetBiomeMixin` 登记在 `mixins.rtgc.json` 里，**那是空转**。三条证据：

1. jar 的 MANIFEST **没有** `MixinConfigs` 属性（只有 `Manifest-Version` 与 `FMLAT`）——
   RFG 的 `mixin_configs` 只生成 json 文件，**不写 MANIFEST**；
2. 日志里 **`mixins.rtgc.json` 一次都没出现**；
3. `EarlyMixin`（`IEarlyMixinLoader`）**不在 MixinBooter 的 early loader 列表里**
   （只有 jeid / stripforgereq / osxnarratorblocker）⇒ 它的 `getMixinConfigs()` 从未被调用。

而且用户是在 **IDE 里直接 Run Client**，用的是 classes 目录 —— 连 jar 的 MANIFEST 都不参与，
"靠 MANIFEST 注册 mixin"这条路在 dev 下**根本不存在**。

⇒ 于是唯一在起作用的只有"每 2 秒回填一次"。而 REID **持续把客户端数组写回它的占位值**
（日志里 `玩家区块 … 不符（有=113…）已回填` 反复出现即为此），
所以 **F3 只在回填后的那一瞬是对的，其余时间显示的是 REID 的占位编号（14 = mushroom_island）** ——
与用户实测完全一致。此前我"F3 = 布局"的读数，正是因为探针**紧跟在回填之后**采样，属于误导性证据。

**修法**（走唯一被证实会加载的那条路 —— MixinBooter 的 late loader）：

- 新增 `mixins.rtg_core.json`：放打**原版类**的 mixin（`core.ChunkGetBiomeMixin`），**无条件加载**；
- `mixins.rtg_late.json`：恢复为只放 BOP 专用 mixin（BOP 未安装时不能加载）；
- `LateMixin.getMixinConfigs()` 返回这两份；`shouldMixinConfigQueue` 只对 BOP 那份做门控；
- 顺手修掉 `mixins.rtg_late.json` 里坏掉的 refmap 字面量 `${mixin_refmap}`
  （日志里 `Reference map '${mixin_refmap}' … could not be read` 就是它），改为真实的
  `mixins.rtgc.refmap.json`。

**验收标准**：下次日志里必须出现

```
[MixinBooter]: Adding [mixins.rtg_core.json] mixin configuration.
```

出现即说明 `Chunk.getBiome` 的注入真的生效了（此前从未出现过）。

### 追加（同版本）：客户端接上 rtgc 的 provider —— 但**只读、不建**

用户问得直白：既然客户端拿到的不是 rtgc 的 provider，**那改掉不就行了**。改了，但改法有讲究。

**问题**：原先 `WorldTypeRTG.getBiomeProvider` 有 `if (!world.isRemote)`，客户端一律拿到原版
`BiomeProvider` ⇒ 客户端所有 provider 查询（`getBiomes` / `areBiomesViable` / `findBiomePosition` /
直接问 provider 的 mod）得到的都是**原版 GenLayer** 的答案。实测日志即
`provider类=BiomeProvider`、`provider=minecraft:forest`，而布局是 `orchard`。

**不能简单去掉守卫**（1.0.33 试过，造成出生点 25×25 垂直断层）：客户端的
`world.getSeed()` 恒为 0，`BiomeProviderBOP(world)` 会 `forSeed(0)` 把服务端的全局布局覆盖掉。

**这次的改法 —— "只读、不建"：**

| 位置 | 改动 |
|---|---|
| `BiomeProviderBOP` | 新增私有 4 参构造器带 `buildLayout`；`world.isRemote` 时 `buildLayout = false` ⇒ `this.layout = RtgLayoutAccess.current()`，**不调 `forSeed`** |
| `BiomeProviderRTG` | 同理：`rtgWorld.world().isRemote` 时取 `current()`，不 `forSeed` |
| `WorldTypeRTG.getBiomeProvider` | 去掉 `!world.isRemote` 守卫，客户端也返回 rtgc 的 provider；整段包 `try/catch`，失败退回原版并打 ERROR |

**为什么"构造时布局可能还没就绪"不成问题**：`getBiome` 读的是**静态**的 `RtgLayoutAccess`，
所以同一个 provider 实例只要**调用时**布局已就绪就走布局；构造那一刻没有则 fail-soft 回落到
BOP 的 GenLayer，之后自动生效 —— 自愈，无需重建。

外加 `RtgLayoutAccess.forSeed` 里"客户端不得用不同种子重建"的护栏做双保险
（即使将来有人误在客户端调 `forSeed`，也会被拒绝并记 WARN）。

新增日志：

```
[RTG] 群系 provider：side=CLIENT world=WorldClient dimType=0 → BiomeProviderBOP
[RTG] BiomeProviderBOP 构造：side=CLIENT 是否建立布局=false（布局当前就绪）
```

**验收标准**：日志里应出现 **CLIENT 那条**、类名 `BiomeProviderBOP`、`是否建立布局=false`；
且**不再出现** 客户端那条 `建立布局：seed=0`。

### 追加（同版本）：**在 F3 的读取点上直接回答**（Mixin）

用户的批评是对的：F3 不对，就该先查 **F3 是怎么查群系的**。读完原版源码，链路是确定的：

```java
// GuiOverlayDebug.java:142 —— F3 的原文，只有这一句
list.add("Biome: " + chunk.getBiome(blockpos, this.mc.world.getBiomeProvider()).getBiomeName());

// World.getBiome(pos) 也是同一条链
public Biome getBiome(BlockPos pos) { return this.provider.getBiomeForCoords(pos); }
public Biome getBiomeForCoordsBody(BlockPos pos) { ... return chunk.getBiome(pos, this.provider.getBiomeProvider()); }
```

⇒ **客户端一切"这个坐标是什么群系"的提问，最终都落到 `Chunk.getBiome(BlockPos, BiomeProvider)`**。
而原版方法体读的是区块的 **byte 数组**（那里是 113 = `jeid:error_biome`），实际却返回
`mushroom_island_shore`(15) = **int 数组**的值 ⇒ **REID 把这个方法体也换掉了**。

**与其在包、数组、REID 的同步路径上追（都不可控），不如在读取点回答：**

- 新增 `rtg.mixins.core.ChunkGetBiomeMixin`：`@Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)`，
  客户端且 `RtgLayoutAccess.current() != null`（单人游戏）时直接返回布局群系；
- **只读**：不写数组、不碰 REID、不改生成结果；
- 服务端与多人游戏客户端**原样放行**，行为不变。
- 注册在**主** mixin 配置 `mixins.rtgc.json`（不是 BOP 门控的那个 late 配置），
  `refmap` 为真实的 `mixins.rtgc.refmap.json`。构建产物里的 refmap 已确认映射正确：
  `getBiome → Chunk.func_177411_a(BlockPos, BiomeProvider)Biome` —— **正是 F3 调的那一个**。

先前那两层（`ChunkEvent.Load` 回填 + 玩家区块防覆盖）**保留**作为保险：
Mixin 的注入有被 REID 的 ASM 覆盖的可能，两层互补。

### 追加（同版本）：回填判据放宽 + 防覆盖层

上一次实测：**回填确实执行了**（`已按布局回填 chunk(-8,-8) …` 三行），但探针里
`F3` 仍是垃圾值。原因是我把"看起来是占位数据"的判据写窄了：

- 判据只看 `biomeArray[0]`（是否整块同值 / 首格是否 113），而实测**字节数组只有部分格子是 113**、
  **int 数组也不均匀**（`int[139]=15` 与 `int[184]=14` 不同值）⇒ 判据不成立，于是那些区块没被修。
- 更要紧的是这次读数暴露了垃圾值的性质：它们是**随索引跳动的小数字**
  （14 / 15 / 24 / 0，即蘑菇岛 / 蘑菇岛岸 / 深海 / 海洋）—— 这是**包内字节错位后读出来的数据**
  （REID 按 4 字节 int 写、客户端按别的步长读），所以 F3 显示的是"随便撞上的小编号"，
  这也解释了为什么反复只出现这几个群系。

**改法：**

- 单人游戏里**无条件**按布局重算（不再用判据）—— 这份数据按定义就不可信，且重算幂等、有缓存兜着。
- 新增**防覆盖层**：每 40 tick 核对一次**玩家所在区块**，发现与该列布局不符就整块回填
  （`ChunkEvent.Load` 的回填可能被 REID 后续的同步覆盖）。

新增日志：

```
[RTG-CLIENT] 已按布局回填 chunk(x,z) 的群系数组
[RTG-CLIENT] 玩家区块 chunk(x,z) 的数组与布局不符（有=<id>(<群系>) 应=<id>(<群系>)），已回填
```

### 追加（同版本）：客户端按布局回填群系数组（F3 的正面修法）

前提：**REID 不能移除**（rtgc 硬 import `org.dimdev.jeid.INewChunk`，且世界数据可能已存它扩展过的 id）。

既然客户端的群系数组拿不到服务端数据，就**在客户端自己算**。单人游戏里客户端与服务端同进程，
共用同一个静态布局 —— 客户端完全有能力算出该区块正确的群系：

- **`EventHandlerClient.onChunkLoad`**（`ChunkEvent.Load`）：客户端、且 `RtgLayoutAccess.current() != null`
  （单人游戏）时，按布局重算该区块 256 列的群系，写入 **int 数组**（F3 读的就是它）与字节数组；
  多人游戏里没有布局，直接跳过，行为不变。
- **只在"看起来是占位值"时才动手**：整块 256 格同值、或值等于 REID 的 error biome(113)。
  正常数据不重复改写，也避免为每个区块白算 256 次布局查询。
- **`RtgBiomeLayout.biomeCache` 由 `HashMap` 换成 `ConcurrentHashMap`** ——
  客户端线程与服务端线程现在都会调 `getBiomeDataAt`，普通 `HashMap` 的并发 put/resize 可能损坏内部结构。
  这是**此前就存在**的隐患，现在必须修。

新增日志（前 3 个区块）：

```
[RTG-CLIENT] 已按布局回填 chunk(x,z) 的群系数组（原值看起来是占位数据：<id>(<群系>)）
```

### F3 的结论（同一个回合内确定，附上排除项）

- 服务端：`[RTG-BIOME]` 证明**写进区块的数组是对的**（`期望=int数组=字节=读回`）。
- 客户端：`[RTG-CLIENT]` 证明 F3 = **REID 的 int 数组**，而且那个数组**整块 256 格是同一个值**
  （202/172/240/15/239/254 全等于 24），**整场游戏都不变** —— 这是"客户端会话开始时用一次采样填满"的
  占位值，**从未收到服务端的数据**。
- 日志里**没有**任何 `布局未命中` / `布局尚未建立` / `没有可用群系` 告警 ⇒ 回落路径没跑，
  所以那个占位值也不是从 rtgc 的回落路径来的。
- rtgc 侧能做的已经做完：**两个数组都写**、int 数组**传副本**。剩下的在 REID 的网络路径里。

⇒ **判定：装了 REID 时，客户端的区块群系数据不由服务端提供，F3 因此无法反映实际地形。**

### Fixed

**找到并修掉"F3 与实际生成不一致"的根因：传给 REID 的是复用字段的引用，不是副本。**

### 证据（1.0.32 的客户端探针，新世界 seed −5348801550149631508）

```
[RTG-BIOME]  chunk(0,0) 期望=biomesoplenty:glacier(99) int数组=99 字节=99 读回=99   ← 服务端，正确
[RTG-CLIENT] {x=14,y=66,z=7}    F3=minecraft:mushroom_island provider=布局=minecraft:forest  一致=false
[RTG-CLIENT] {x=-12,y=84,z=-32} F3=minecraft:deep_ocean      provider=布局=minecraft:forest  一致=false
[RTG-CLIENT] 加入游戏时 rtgc@1.0.32                                                ← 版本号修好了
```

**关键判读**：客户端读到的是 `mushroom_island`(14) / `deep_ocean`(24) ——
**都是真实存在的编号**，看起来完全合理，但**与同一列的布局值不符**（provider 说 `forest`）。
这不是垃圾数据，而是**别的区块的真实群系编号**。服务端立刻回读是对的（99），
说明写进去的那一刻是对的，**坏在"稍后构造网络包"这一步**。

### 根因

```java
// 旧写法（1.0.32 及以前）
((INewChunk) chunk).setIntBiomeArray(this.intBiomeArray);   // ← 传的是【复用字段本身】
```

`intBiomeArray` / `byteBiomeArray` 是**生成器实例字段**，每个区块生成时被原地覆写。
`Chunk.setBiomeArray(byte[])` 内部是 `System.arraycopy`（自己会拷），
**但 REID 的 `INewChunk.setIntBiomeArray(int[])` 不保证拷贝** —— 若它保存引用，那么：

1. 生成区块 A → 把共享数组指针交给 A；
2. 生成区块 B → **共享数组被原地覆写成 B 的编号**；
3. 稍后为 A 构造 `SPacketChunkData`（由 REID 走 int 数组路径）→ 发出的是 **B 的编号**；
4. 客户端把 B 的编号存进 A 的区块 → **F3 显示 B 的群系**（合理但错），
   而地表/地形是 A 生成时按布局算的 → **两边分家**。

这解释了全部现象：为什么只有少数几个编号（B 的群系都是真实编号）、
为什么服务端回读是对的、为什么改成"两个数组都写"也不管用（写的是同一份共享数组）。

### Fixed

- **传给 REID 的 int 数组改为传副本**：`setIntBiomeArray(this.intBiomeArray.clone())`。
  `setBiomeArray(byte[])` 不必 clone（原版自己 `System.arraycopy`），已在注释里写明原因。
- **新增"最大编号 > 255"的告警**：原版 `Chunk` 的群系存储是 `byte[256]`，
  `SPacketChunkData` 发的就是它；REID 装了以后编号可以超过 255，此时 `(byte)` 会**截断**
  （`270 & 255 = 14` → 客户端显示 `mushroom_island`），而服务端读 int 数组一切正常。
  这一行把该情况直接点明，免得下次又把"截断"当成"随机垃圾"。
  **本次数据无法区分"别名"与"截断"两种成因** —— 两者都会给出"
  合理但错误的编号"，所以**两个都修**：别名已修，截断则至少在日志里显式告警。

### 同时升级的客户端探针（1.0.32 的那条太粗）

```
[RTG-CLIENT] <pos> F3=<群系> provider=<群系> 布局=<群系> | 字节=<id>(<群系>) int=<id>(<群系>) reidChunk=<bool> | provider类=<类名> 布局就绪=<bool>
```

一次就能分清：**F3 与「字节」还是与「int」一致**（→ 说明客户端读的是哪条路）、
以及与「布局」差多少。1.0.33 装好后若 `F3 == 布局 == provider`，这条就闭合了。

### 追加（同版本）：F3 闭合、装饰正常 —— **清除全部探针**

排查结束时用户确认：**F3 显示的群系 = 地表实际生成的群系**、**地表装饰正常**。
因此把本轮为定位问题而加的所有探针删掉，只保留"正常游玩永远不会触发"的安全网。

#### 保留（不是探针，是安全网 / 按需开关）

| 保留项 | 位置 | 触发条件 |
| --- | --- | --- |
| `⚠ 客户端试图用种子 0 重建布局 —— 已忽略` | `RtgLayoutAccess.forSeed` | 只在客户端真去重建布局时（当前不会） |
| `⚠ 布局被**换掉**了` | `RtgLayoutAccess.forSeed` | 只有 seed 真的变了 |
| `⚠ 某气候核心池为空` / 海洋槽位空缺 | `RtgBiomeCategorizer.logReport` | 只有归类规则坏掉时 |
| `⚠ 建立 RTG 群系 provider 失败，退回原版` | `WorldTypeRTG.getBiomeProvider` | 只有构造 provider 抛异常时 |
| `[RTG-DECO]`（`-Drtg.debugDecorations`） | `ChunkGeneratorRTG` | **默认关闭**，需要时开 |
| `[RTG]` 池大小 / 池成员 / D3 气候核对（`-Drtg.debugLayout`） | `RtgLayoutAccess`、`RtgBiomeCategorizer` | **默认关闭**，需要时开 |

#### 删除（本轮加的探针）

- `[RTG-BIOME]` 群系数组回读探针（连带 `maxBiomeId` 统计）。
- `[RTG-CLIENT]` 客户端每 2 秒的轮询与"回填后回读"探针、以及那段 2 秒修复循环
  （`EventHandlerClient` 现在只剩 `ChunkEvent.Load` 回填与世界类型 GUI 通知）。
- `[RTG-BLEND]` 过渡带统计 —— 连带把它在**最热的内层循环**里无条件维护的 `maxWeight`
  一并删掉（那是为诊断付的每列开销）。
- `[RTG-DECO]` 的"前 5 个区块无条件打印"，恢复为只看 `-Drtg.debugDecorations`。
- `[RTG] 群系 provider：side=…`、`[RTG] BiomeProviderBOP 构造：…`、`[RTG] 建立布局：…`
  三条 INFO（连同 `RtgLayoutAccess` 里那条被复制成两行的 `layout = fresh;` 一并清掉）。
- `ChunkGeneratorRTG.nameOrDash`（只服务探针日志）。

#### 本轮**保留**的正面修法（与探针无关，是功能修复）

1. `rtg.mixins.core.ChunkGetBiomeMixin` —— 在 `Chunk.getBiome` 的 HEAD 处直接回答布局的群系，
   这是 F3 与实际地形一致的原因（由 MixinBooter 的 `mixins.rtg_core.json` 加载）。
2. `ChunkGeneratorRTG` **两个数组都写** + 给 REID 的 int 数组**传副本**。
3. `WorldTypeRTG.getBiomeProvider` 客户端也返回 rtgc 的 provider（**只读**，`buildLayout = false`），
   配 `RtgLayoutAccess.forSeed` 的"客户端不得用 seed 0 重建布局"守卫。

#### 仍然在案的（未做）

- rtgc 侧 `SurfaceXxx` 内层类尚有约 120 个未从 RWG 移植（RWG 约 40 个里剩下的还没对象化）；
  已接线后失效的旧内层地表类也没删。
- 4 个海滩群系（`VanillaBeach`/`VanillaStoneBeach`/`BOPGravelBeach`/`BOPOriginBeach`）
  仍用原地表（RWG 的 `CoastDunes` 没有可共用的 `Surface*`）。
- `rDecorateAfterIce`（海洋群系的原版装饰）未移植；地表抖动采样点错位（`ChunkGeneratorRTG:297-301`）已记录未修。
- `LARGE_ISLAND` 池为空；D5 深海深度群系缺口；气候带偏斜（`COLD 倍率 1.52`）。
- `terrainDunes` 与忠实的 `terrainPolar(int,int,RTGWorld,float)` 仍是 0 调用。
- 1.0.10–1.0.31 的改动、地表接线的外观、性能**均未在游戏内验证**。

### 追加（同版本）：`/rtg probe` 现在能判定「地下河到底开凿了没有」

此前 `/rtg probe` 只能报 `river/tunnel/junction` 三个**布局强度**，而真正决定生死的
`mountainChainRiverHost > 0.10` 存在生成器的 `ChunkLandscape` 里 —— 探针自己的输出里就写着
"本探针不重算"。于是"地下河没看见"永远只能是猜。现在补上：

- `ChunkGeneratorRTG.probeTunnelColumn(biomeProvider, x, z)` —— **只读**读取该区块的
  `ChunkLandscape`（缓存未命中时按同一套确定性算法重算一份临时对象，**不写回缓存**，
  免得一条诊断命令顶掉别的区块的缓存项），返回「链权重 / 链宿主 / 洞顶 y / 是否命中缓存」。
- `/rtg probe` 新增一行 `[tunnel]`，并对该列直接给结论：
  `✅ 确实开凿过（洞顶 y=…）` / `✗ 没有开凿（链宿主不足 ⇒ 不是山地链）` /
  `✗ 没有开凿（链宿主够，但不在河网边界或顶点上）`；缓存被淘汰时明确标注"洞顶读数不可信"。

口径：**地下河与洞厅是山地链专有地貌**。离线实测（`gradlew calibrateLayoutApportionment`，
seed 1234567891011121、38 万样本）极端气候边界带 = **5.60%** 的世界面积，
即山地链的覆盖面；再加上 48 格邻域链影响，隧道只可能出现在这一带里的河网边界/顶点上。

### 追加（同版本）：清掉两处会误导后来人的**过期文档**

- `ChunkGeneratorRTG` 的 `TUNNEL_MIN_SURFACE` 上叠了**两段** javadoc，前一段（1.0.10 的
  "干高度 ≥ 76"门控）描述的门控早已被 1.0.13 换掉，却还写着"这才是当前门控"。现已合并为
  一段，并写明"它只是洞厅天窗的**权重**，硬门控是 `MOUNTAIN_CHAIN_RIVER_HOST_MIN`"，
  附带 F-39 的历史（免得再被"改回去修一下"）。
- `LayoutApportionmentCalibration` 的 ④ 判读文字里硬编码了 51%/16%/12%/2.5%，
  与本次实测（52.27%/13.05%/**5.60%**/2.13%）已不一致 —— 布局的边界距离常量改过。
  文字改为量级描述 + 明确以 ①②③ 的实测数字为准。**这条最危险**：一个会自己打脸的报告，
  比没有报告更容易骗人。

### 追加（同版本）：**推翻阶段 C1 的边界池填法** —— 它建立在两个事实错误上

长任务的第一轮。目标：把「地形好看」剩下的问题全部收敛到 **w（权重）侧**。

#### 先纠正记录在案的三件事

上一版 `docs/rwg-port-gaps.md` §14 里给 C1（`mirrorCoreIntoBorders()`：
边界池 = 本气候核心池）写的理由**不成立**：

1. **"三池皆空 ⇒ 边界列拿到 null ⇒ 回落原版 GenLayer"是错的。**
   `RtgBiomeLayout.getLandBiomeAt` 在三个池都空时会落到 `selectBiome(core[climate], …)`
   —— 这正是 RWG `ChunkManagerRealistic:785-798` 的 fall-through，
   而 rtgc 那段本来就是逐行移植。边界列**从来不会**因为池空而拿到 null。
2. **那个镜像对群系选择是恒等变换。**
   `selectCombinedBiome(shared=core, directional=core)` 的长度是 `2×|core|`，
   前半段取 `shared[i]`、后半段取 `directional[i-|core|]`，两者是同一个列表 ⇒
   与 `selectBiome(core)` **逐点相同**。所以"严格改善"这个说法没有依据。
3. **范围错了：能抄的标注远不止 SMALL / SMALL_ISLAND。**
   `SupportBOP.java` 里还有 `COLD_BORDER`（chaparral / meadow / rainforest /
   tropicalRainforest）、`HOT_BORDER`（borealForest / jadeCliffs / landOfLakesMarsh）、
   `LITTORAL`（bayou / deadSwamp / lushSwamp / mangrove / sludgepit / tropics）共 12 条
   同样明确、同样可抄的标注。当时只抄了 5 条，把另外 12 条当成了"没有数据"。

#### 镜像唯一真实的作用，是把山地链放大了 17 倍

`rebuildExtremeBorderMountains` 从 `coldBorder` / `hotBorder` 镜像出山地链池。
核心池被灌进去之后，RWG 那边的 **6 个**山地链在 rtgc 变成**每个陆地群系各一个** ——
实测 `RtgRealisticIndex.syntheticCount()` = **104**，即"每种地形都可能变成山地链"。

#### Fixed

- **删除 `RtgBiomeLayout.mirrorCoreIntoBorders()`**，改走 RWG 的 fall-through。
- **新增 `RtgBiomeCategorizer.RWG_PLACEMENTS`**：照抄 RWG `Support*.java` 的显式 placement
  （22 行，含 21 行有 RWG 出处 + 1 行标明是 rtgc 特有）。
  匹配改为**精确**匹配（注册名小写去下划线），不再用 `contains` ——
  否则 `tropicalrainforest ⊃ rainforest`、`mushroomislandshore ⊃ mushroomisland`
  这类会取决于表的书写顺序。与 RWG 一致：`placement != CORE` 的群系**只进该池，不进 core**
  （`Support.addBiome` 在 `Support.java:218-221` 直接 `return`）。
- **海滩群系不再进 `LITTORAL`，改走 core。** 见下条。
- `Report.special` → `Report.rwgPlaced`；`landByClimate` 改为统计**所有**陆地方位
  （core + 边界 + 滨海 + 小型 + 岛屿），这样 D3 的"群系数 vs 带面积"才是它字面的意思。

#### 顺带发现的、面积更大的一处偏离：滨海带 13% 全被填成海滩

RWG 的海岸线只有 `continent < 24` 那一圈，用两个**专用**群系 `coastIce` / `coastDunes`
（rtgc 已接成 `COLD_BEACH` / `BEACH`）。而 RWG 的 `LITTORAL` 池（`continent < 432` 的覆盖，
占世界 **13.05%**）里装的是**沼泽/红树**，且**只有 WET 有成员** ——
RWG 的四个气候里，雪/冷/热的 432 带就是**普通陆地**。

旧写法把"字典带 BEACH 或名字含 beach/shore"的群系一律塞进 LITTORAL，
于是四个气候的池都非空 ⇒ 滨海覆盖在**全世界 13% 的面积**上生效，把那一圈填成海滩群系。
这是"地形不好看"里面积最大的一块。现在只有 WET 保留沼泽/红树的滨海带。

#### 新增（可复核的机器检查）

`tools/rwg-placement-check.ps1`：把 `RWG_PLACEMENTS` 的每一行与 RWG `Support*.java` 的
**真实 `addBiome(...)` 调用点**逐一比对（并排除 `Support.listFor` 方法体里那些
"看起来像注册、其实不是"的常量）。**任何一行在 RWG 里查无出处就 exit 1。**

实跑结果：

```
RWG registration rows parsed : 30
Java table rows parsed       : 22
copied from RWG : 21
rtgc-specific   : 1     (mushroomislandshore)
gaps (RWG 有、rtgc 无对应群系): baserivercold / baseriverwet / fungiforest
PASS: every RwgPlace row is backed by an RWG Support*.java registration.
```

顺带纠正一处工具自身的问题：`tools/rwg-placement-inventory.ps1` 会把 `listFor` 方法体里
的 placement 常量误当成注册（上一版 §14 的 "BORDER 池只有 1 条" 就是这么来的），
已在文件头标注为历史工具、并指向新的 check 脚本。
**实测 RWG 的 `border`（shared）池其实是全空的** —— 有内容的只有 `coldBorder` / `hotBorder`。

#### 已知缺口（本轮**不能**靠抄补上，记录在案）

- `jadeCliffs` / `tropics` / `garden` / `fungiForest` / `sludgepit` / `landOfLakesMarsh`
  在 BOP 1.12.2 里已不存在（`fungi_forest` 还在，但它是下界群系，rtgc 只处理主世界）。
  ⇒ `veryHotBorder[COLD]` 与 `LARGE_ISLAND` 仍为空，按 RWG 的 fall-through 落到核心池。
  启动日志 `-Drtg.debugLayout` 会逐条列出未匹配项。
- **未在游戏内验证。** 本轮的验收证据是源码级：新增的机器检查（上）＋启动日志
  （`-Drtg.debugLayout` 会打印各气候的三个边界池成员、LITTORAL 成员、
  RWG placement 覆盖率、山地链池大小）。

#### 新增：D3 的对照依据（**RWG 自己就是按带面积分配的**）

D3 的"各气候群系数应与气候带面积成正比"此前一直像是 rtgc 自造的口径。本轮把它量了：
统计 RWG `Support*.java` 的真实 `addBiome(...)` 调用（排除 `Support.listFor` 方法体），
RWG 共 **97** 条带分类的注册：

```
COLD 30   WET 27   HOT 24   SNOW 13   (SMALL 3 是全局池)
```

去掉 3 条全局 SMALL 后按 94 条折算，与实测带面积（14.0/29.8/28.4/27.8）对比：

```
SNOW 13.8%/14.0% = 0.99     COLD 31.9%/29.8% = 1.07
HOT  25.5%/28.4% = 0.90     WET  28.7%/27.8% = 1.03
```

**RWG 作者本人就是按带面积分配群系的**（最大偏离 10%）。所以去偏斜有对照、不是发明。
rtgc 现状（1.0.33 实测）是 0.89 / **1.52** / 0.71 / 0.80 —— 明显偏离，也说明
`climateFor` 拿"其余一切 → COLD"当兜底这件事要改。D3 的诊断行现在同时打印
`/RWG` 倍率，便于直接看"我们离 RWG 有多远"。

#### 新增：定位 D3 误判所需的**名单**（以及一处误导人的日志）

`-Drtg.debugLayout` 现在会打印**每个气候的 CORE 成员名单**。只报计数不够 ——
偏斜的根因是"哪些群系被规则误判进了 COLD"，没有名单就只能靠猜（上一轮的教训）。

同时修掉一处**会误导人的日志**：`建立布局：… rtgBiomes=169` 里的 169 不是群系数。
`RTGAPI.RTG_BIOMES` 是 `SparseList extends ArrayList`，它的 `size()` 是**按群系 id
索引的数组长度**（最大 id + 1，中间用 null 填充），真实注册数是 **127**。
本轮为此白查了一轮（一度以为 42 个群系没进任何池）。现改为
`RTGAPI.rtgBiomeCount()`，并在其 javadoc 里写明这个坑。

### 追加（同版本）：D5 海洋 patch 钩子**接线了但没接上** + 死码清理

#### Fixed：RWG 的"海洋斑块"此前完全不存在

`RtgBiomeLayout.getOceanBiome` 里有两分支是 RWG `ChunkManagerRealistic:677-692` 的逐行移植：

```java
if ((climate == 1 || climate == 2) && continent < -90f  && patch > 0f)   return oceanShallowKelp  ...
if ((climate == 3 || climate == 4) && continent > -150f && continent < -20f && patch > .07f) return oceanShallowCoral ...
```

而 `setOceanShallowKelp` / `setOceanShallowCoral` **全仓零调用者** ——
两个字段恒为 `null`，两条分支永远走 `: oceanShallow[climate - 1]` 的兜底。
即：**接线了但没接上**，RWG 的"海带/珊瑚斑块"在 rtgc 一格都没有。

现在按 RWG `Support.java:166-168` 的字面做法把它们都指向 COLD 的浅海槽位
（RWG 里 `oceanShallowKelp = oceanShallowCold`、`oceanShallowCoral = oceanShallowTemperate
= oceanShallowCold`，1.7.10 没有独立的 kelp/coral 群系）。
**备选未采用并记录在案**：rtgc 其实有真实的 `BOPKelpForest` / `BOPCoralReef`，
接上去会让斑块"名副其实"，但那是偏离 RWG，且这两个群系已各占着某气候的浅海槽位。

同时把**海洋槽位**（D5）加进 `-Drtg.debugLayout` 的日志：逐气候打印浅海/深海用的是谁，
以及两个 patch 钩子指向谁。`fillMissingOceanSlots` 的跨气候借用是**静默的**，
不打印就永远查不出"全世界的深海长得一样"。

#### Removed：`rtg.api.world.terrain.heighteffect` **整包**（12 个类 / 291 行）

上一轮文档记的是"7 个类仍被引用、合计 2000+ 行"。**实测推翻了它**：整包在包外
**零真实调用者**，那个"仍被引用"是把**已死的字段声明与 import** 当成了调用。

三条判据（都可复核）：

1. `.added(` 的调用**全部在包内**（包内几个类互相调用）；
2. 包外只剩：`BOPBog` / `BOPMarsh` / `BOPBorealForest` 里**只赋值、从不读取**的字段
   （这三个群系的地形早已改成 `terrainMarsh` / `terrainMountainSpikes`），
   以及 `BOPBambooForest` / `BOPHighland` / `BOPMountainFoothills` / `BOPMountainPeaks` /
   `VanillaExtremeHills` 里**无用的 import**；
3. `src/preview` 无引用。

同批删除 RTG 自造的零调用者助手 `TerrainBase.unsignedPower`（RWG 里没有此函数）。
`TerrainBase.terrainDunes` 与 `toWorldBlocks` 虽然也是 0 调用者，但**保留**并写明理由
（前者是 RWG 的逐行移植，删了要重抄；后者是 preview 标定工具的对照公式）。

顺带修掉一处**工具的失真**：`tools/reachability.ps1` 的 LEGACY HELPERS 名单里列着
`hills` / `bayesianAdjustment` 两个**根本不存在**的方法，输出成 `extCalls=`（空），
看起来像"0 调用者"，本轮差点据此去删不存在的东西。现已据实更新，
并加了 `NOT-A-static-float-METHOD` 状态位，让"没这个方法"与"没人调用"不再长得一样。

**验证**：`BUILD SUCCESSFUL`；删除后全仓 `grep heighteffect` 只剩三行注释；
`tools/reachability.ps1` 现报 `parsed blocks: 35`、UNREACHABLE 只剩 `terrainDunes`（已标注原因）。

### 追加（同版本）：D5 深海缺口 —— **否定结论（补不了，且不是遗漏）**

D5 一直记作"深海深度群系不足"。本轮去补，先按 1.13 的印象写了
`RealisticBiomeVanillaFrozenDeepOcean`，**编译器当场报"找不到符号：
Biomes.FROZEN_DEEP_OCEAN"** —— 该群系是 1.13 才有的。

据实核查：

- MC **1.12.2** 的 `Biomes`（查 MCP stable 39 的 `fields.csv`）只有 3 个海洋：
  `OCEAN` / `DEEP_OCEAN` / `FROZEN_OCEAN`；21 个 `MUTATED_*` 里没有任何海洋突变体。
- BOP 1.12.2 的 `BOPBiomes`（`javap` 实测）只有 `kelp_forest` / `coral_reef`，
  也没有深海变体。

所以 rtgc 的海洋就是 **1 深 + 4 浅**（与启动日志 `深海=1 浅海=4` 完全吻合）。
RWG 的四个深海来自它**自己注册的** `baseOceanHot/Wet/Cold` 群系，rtgc 无对应物。
⇒ `fillMissingOceanSlots` 的跨气候借用**就是 1.12.2 下的正确适配**；要做成 RWG 那样
必须**新增 MC 群系**（带 id / lang / 生成规则），属"发明新群系"，超出"照抄"，需单独决定。

**本轮的处置是"让借用看得见"**：见上条（`-Drtg.debugLayout` 逐气候打印
`浅海=… 深海=…` 与两个 patch 钩子）。那个新建的类**已删除**，相关 import/注册也已还原。

#### ⚠ 修正：上一轮那句"镜像对群系选择是恒等变换"**是错的**

新增离线标定 `gradlew calibrateBorderPools`
（`src/preview/java/rtg/world/biome/BorderPoolEquivalenceCalibration.java`）：
它跑**真正的** `RtgBiomeLayout`，用 `java.lang.reflect.Proxy` 造身份可区分的
`IRealisticBiome` 桩 —— `getLandBiomeAt` 全程不碰 `baseBiome()`，所以不需要 MC 运行时。
同一 seed 建四个布局逐列比对（步长 31、±11000、**50.4 万列**）：

| 命题 | 做法 | 实测 |
| --- | --- | --- |
| A | 镜像核心池 vs 池留空（都不建极端池） | **差异 19.73%**；边界列成员占比两边都≈25% ⇒ **分布相同、逐点不同** |
| B | 旧（镜像核心池） vs 新（RWG 方向池成员），都建极端池 | **差异 10.65%**，且**只在边界族**：极端 28308 + 普通边界 25398、**核心列 0**、小型圆盘 0 |
| C | 极端边界池大小 | 旧 16（4 气候 × 4 成员）→ 新 4；生产量级即 **104 → ~4** |

上一轮写的是"`selectCombinedBiome(core, core)` 与 `selectBiome(core)` 逐点相同"。
**错了**：前者把选择器切成 8 段（`core[0..3]` 各出现两次），后者切成 4 段，
**分段边界不在同一位置** ⇒ 同一列会换一个成员，只是各成员的宏观占比不变。
正确说法是"**分布相同、逐点不同**"，即旧镜像是一次**等分布的重新洗牌**，
既不是恒等变换，也不是"严格改善"。

**这不改变"删除镜像"的结论** —— 那个结论靠的是另外三条：
① 它从来不是防 null 所必需（fall-through 本来就在 rtgc 的逐行移植里）；
② 它唯一的真实作用是喂极端边界池，把 RWG 的 6 条链放大成 104 条（命题 C 已量化）；
③ RWG 的方向池是**手挑的少数成员**，不是整个核心池（`rwg-placement-check.ps1` 已核）。

**标定的价值恰恰是它推翻了我自己上一轮的一句话** —— 判据要能证伪才算判据。
顺带记下工具自身踩的坑（写进代码注释）：第一版拿**对象身份**跨两个布局比较，
两边各自 `new` 了一批桩 ⇒ 100% "差异"，看起来像"命题 A 被推翻"，其实是工具错了。

### 追加（同版本）：D3 —— 把**自造的温雨阈值**换成 Forge 自己的标签

上一轮把 `has(WET)` 这条线索留下没做，本轮做了，因为找到了**出处**：
本仓库里就有 Forge 的源码
（`E:/.gradle/.../forge/1.12.2-14.23.5.2847/unpacked/src/main/java/net/minecraftforge/common/BiomeDictionary.java`），
`makeBestGuess`（第 263-367 行）写明它**自己**按阈值打标签：

| 标签 | Forge 的判据 |
| --- | --- |
| WET | `rainfall > 0.85f` |
| DRY | `rainfall < 0.15f` |
| HOT | `temperature > 0.85f` |
| COLD | `temperature < 0.15f` |
| SAVANNA | `topBlock != sand && temp >= 1.0f && rainfall < 0.2f` |
| SWAMP | `isHighHumidity() && heightVariation < 0 && baseHeight ∈ [0,0.3]` |
| SANDY / MESA | `topBlock == sand` / `fillerBlock == hardened_clay` |

**这套阈值本来就在手上，我们却在 `climateFor` 里另写了一套。** 两套不一致的地方
就是"误判"的来源：自造的 WET 判据多了 `temp >= 0.8f` 一个条件，于是
**雨量很高但不算热**的沼泽族（bog / fen / moor / marsh / wetland / bayou …）
既不是 WET 也不是 HOT，全部落进 COLD 兜底 —— 这正是 COLD 倍率 ~1.5 的来源。

**Fix**：WET 分支改用 `BiomeDictionary.Type.WET`，删掉自造的
`rain >= 0.9f && temp >= 0.8f`。保留 HOT 的 `temp >= 0.7f && rain <= 0.35f`
（比 Forge 的 DRY 宽，去掉会让 HOT 更少）；**不加** `SANDY → HOT`
（温带海滩顶方块也是沙，会把海滩整体推到 HOT）。

**同时纠正一句写错的旧注释**：本 Forge 的 `BiomeDictionary.Type` **有** `MESA`
（实测 `javap net.minecraftforge.common.BiomeDictionary$Type`：HOT/COLD/SPARSE/DENSE/WET/DRY/
SAVANNA/CONIFEROUS/JUNGLE/SPOOKY/DEAD/LUSH/NETHER/END/MUSHROOM/MAGICAL/RARE/OCEAN/RIVER/
WATER/**MESA**/FOREST/PLAINS/MOUNTAIN/HILLS/SWAMP/SANDY/SNOWY/WASTELAND/BEACH/VOID），
没有的只有 `DESERT`。旧注释写的"没有 DESERT / MESA 常量"里 MESA 那半句是错的，
而**这个错误结论被我当依据用了两轮**。

#### 新增：D3 的定位依据（每个陆地群系一行）

`-Drtg.debugLayout` 现在会为每个陆地群系打一行：

```
[RTG]   [类] COLD   bog       temp=0.50 rain=0.90 标签=WET,SWAMP
```

即"归到哪个气候 + 名字 + 温度 + 雨量 + 命中的标签"。上一轮加了"每个气候的 CORE 成员名单"，
但那只说明**谁在 COLD**，说不清**是哪条判据把它漏进去的**；有了这一行，误判可以直接指到规则上。
**D3 的最终数字仍需一次运行**（`climateFor` 依赖 `BiomeDictionary` 的运行时标签，
离线复刻要把 BOP 群系的属性从字节码抠出来、还要猜 BOP 有没有显式 `addTypes`——
那是"推断"，本项目吃过这个亏，不拿推得当依据）。

#### D5 结案（用户裁定）

用户：「少几种海就少呗，无所谓的事情」。⇒ **D5 关闭**，不再尝试新增深海变体，
也不再为"四个气候共用同一个深海"做改动。文档里保留原始记录只为防止以后再被当待办翻出来。
（`fillMissingOceanSlots` 的借用保留 —— 它是 1.12.2 下的正确适配；两个海洋 patch 钩子已接通，
`-Drtg.debugLayout` 会逐气候打印 `浅海=… 深海=…`，借用不再静默。）

### 追加（同版本）：D3 去偏斜 —— **实测通过**（附运行日志）

用户用 `-Drtg.debugLayout` 跑了一次（世界 `…new`，seed −7524561990694487647）。实测结果：

| | SNOW | COLD | HOT | WET |
| --- | --- | --- | --- | --- |
| **旧**（1.0.33 前一版实测） | 0.89 | **1.52** | 0.71 | 0.80 |
| **今**（本次实测） | **0.95** | **1.23** | **0.73** | **1.05** |
| 对 RWG 自身分布的倍率 | 0.96 | 1.15 | 0.82 | 1.02 |
| RWG 自己的倍率（对照） | 0.99 | 1.07 | 0.90 | 1.03 |
| 验收区间 | [0.6, 1.6] ✓ | ✓ | ✓ | ✓ |

**四项全部落回 0.6–1.6**，去偏斜这一项目标达成。主要贡献来自把 WET 的自造阈值
换成 `BiomeDictionary.Type.WET`：COLD 的核心池 47 → **39**，WET 23 → **25**。

原始日志行：

```
[RTG] D3 气候归类核对（倍率 = 我们的群系占比 / 该带面积；/RWG = 对 RWG 自身分布的倍率）：
      SNOW=16(占比13.3%/带面积14.0%,倍率0.95/RWG0.96)
      COLD=44(占比36.7%/带面积29.8%,倍率1.23/RWG1.15)
      HOT =25(占比20.8%/带面积28.4%,倍率0.73/RWG0.82)
      WET =35(占比29.2%/带面积27.8%,倍率1.05/RWG1.02)
```

**其余几项的实测确认（同一次运行）**：

```
[RTG] 建立布局：… rtgBiomes=127                      ← 不再是误导人的 169（id 上界）
[RTG] 具名计数：… RWG显式归位=17 … 陆地=99 …          ← 表的 17 条全部命中
[RTG]   SNOW COLD_BORDER= HOT_BORDER=biomesoplenty:boreal_forest BORDER=
[RTG]   COLD COLD_BORDER=biomesoplenty:meadow …
[RTG]   HOT  COLD_BORDER=biomesoplenty:chaparral …
[RTG]   WET  COLD_BORDER=biomesoplenty:rainforest, biomesoplenty:tropical_rainforest …
[RTG]   WET  LITTORAL 成员：bayou, dead_swamp, lush_swamp, mangrove    ← 只有 WET，与 RWG 一致
[RTG]   未匹配：garden / jadecliffs / landoflakesmarsh / sludgepit / tropics
[RTG] 山地链池（极端边界）：… 共 4 个变体，合成编号 4 个。               ← 旧版是 104
```

即 **(1) 方向性边界池、(2) 去偏斜、(3) 池填充、(4) 死码清理** 全部有了实际数字的验收。

**顺带修掉我自己在诊断里犯的一个错**：`[类]` 那行第一版写成了
`Logger.info("[类] {:<6} {:<34} temp={} rain={} 标签={}", …)` ——
`{:<6}` 这种宽度写法 SLF4J 不认、会原样打印，而 `{}` 只认相邻的一对花括号，
于是**五个参数只对上三个、后两个被丢掉**，打出来是
`[类] {:<6} {:<34} temp=COLD rain=plains 标签=0.80`（temp 位置印的是气候、
rain 位置印的是名字、标签位置印的是温度）。现已改为 `String.format`。
**这是本次运行唯一"白跑"的部分 —— 但偏斜的验收数字不受影响**（它来自另一行）。

#### 仍偏低但**已达标、且不再追**的一项

HOT 的倍率 0.73（RWG 自身 0.90）是四者里最远的。原因是 rtgc/BOP 的群系名册里
"热而干"的群系本来就比 RWG 少；要把它拉到 0.90 只能把温带群系改判成热带 ——
那是发明，不是移植。目标区间是 0.6–1.6，**已达标即收**。

### 追加（同版本）：接回 RWG 的**地表侧**群系边界抖动（`randBiome`）

用户裁定：**「允许在交接地带出现 F3 与实际不统一的情况，本来就是交界处，无可厚非」**
—— 这解开了此前不做它的唯一障碍（拆分数组会让过渡带内 F3 与地表不一致）。

**做了什么**（`ChunkGeneratorRTG.getNewerNoise` + `ChunkLandscape` + `BiomeAnalyzer.newRepair`）：

RWG `ChunkGeneratorRealistic:443-499` 用一张 **15 格尺度**的噪声去扫各群系的**累积权重区间**：

```java
bRand = clamp(0.5f + perlin.noise2((x + i) / 15f, (y + j) / 15f), 0f, 0.99999f);
if (bCount <= 1f) { bCount += smallRender[l][k];
                    if (bCount > bRand) { biomes[j*16+i] = getBiome(k); bCount = 2f; } }
```

- 新增 `ChunkLandscape.surfaceBiome[256]`：**地表**该列用哪个群系。与 `biome[]`（F3／区块群系数组）
  **分开** —— 这是"允许不一致"的落地方式。与 `mountainChainWeight` 同理必须随区块保存。
- 抖动在高度求和的**同一个循环**里算完（累积必须排在高度累加**之前**，与 RWG 同一段代码内的先后一致），
  用的是 `rtgWorld.simplexInstance(0)`（= RWG 的 `perlin`：同一算法、同一种子偏移）。
- 地表替换（`jitteredBiomes`）改读 `landscape.surfaceBiome`；F3 仍读 `landscape.biome`。

**两处与 RWG 的**有意**差异**（都写进了代码注释）：

1. RWG 扫不过去时 `biomes[]` 保留的是**上一个区块**留在复用数组里的值（`biomes` 是它的成员字段）
   —— 那是个隐患。rtgc 明确回落到「第一个非零权重的群系」，结果确定、不依赖调用顺序。
2. **被替换过的列必须跟随主群系**：`BiomeAnalyzer.newRepair` 会把河面下的列换成河流群系、
   把旱河换成陆地回落群系；这些列的地表要按河流群系刷成沙/砾，**不能**拿过渡带抖出来的陆地群系去刷。
   故 `surfaceBiome[i]` 只在未发生替换时保留抖动结果。这是对既有行为的保真，不是新偏离。

**为什么不会重演 1.0.29 的"孤立圆形斑块"**：那一版的选取依赖**特征点采样**（所以是圆的）；
本实现只依赖 ①该列自己的权重向量 ②一张 15 格尺度的 2D 噪声，两者都不产生圆形几何。
**也不会"森林里冒出蘑菇岛"**：单群系列只有一个权重 1.0 的成员，
而 `bRand ≤ 0.99999`，必然先被自己跨过 ⇒ 该列不可能改选别人
（权重归一化误差约 1e-7，与 1e-5 的余量差两个数量级）。

**代价（用户已认可）**：过渡带内 F3 显示的群系与地表方块可能不同 —— F3 是"权重最大的那个"，
地表是"被噪声挑中的那个"。

**未在游戏内验证**：观感（边界是否真的呈噪声状、有没有新的副作用）需要一次运行。

### 追加（同版本）：清掉 **119 个已失效的内层地形/地表类**（约 8435 行）

`1.0.30/1.0.31` 把 RWG 的共享 `rtg/api/world/surface/Surface*` 类移植进来、
并把各群系的 `initSurface()` 改成返回共享类，**但旧的内层实现留在原处没删**；
A3 迁地形时也留下了 3 个同类残留。本轮按"能证明是死的才删"把它们清掉：

| 类别 | 数量 | 行数 |
| --- | --- | --- |
| 内层 `Surface* extends SurfaceBase` | **116** | 8372 |
| 内层 `Terrain* extends TerrainBase` | **3** | 63 |
| （连带删掉的文件内悬空 import） | — | — |

判据（新工具 `tools/sweep-dead-inner-surfaces.ps1`，支持 `-WhatIf`）：
一个内层类**在声明文件之外零引用**、**且在声明文件之内除自己那一块之外也零引用**，才删。
用大括号配平取块、并吞掉紧邻的 javadoc 与一个空行。

实测结果：

```
inner Surface* classes found : 130
  dead (safe to delete)      : 116
  still referenced (kept)    : 14        ← 其中 4 个是"仍在自己文件里被 initSurface() 用"
files touched      : 116
lines removed      : 8372
```

那 14 个"保留"里有一部分是**名字在不同文件里重复**造成的保守误判
（例如 `SurfaceBOPRedwoodForest` / `SurfaceBOPTundra` 各有两个同名声明）——
反正是保守方向，留着不碍事。另外 121 个"外部零引用"的非 Surface 嵌套类逐个查了
**本文件内**引用：118 个活着（`initTerrain()` 返回它们），只有上面那 3 个是真死。

**验证**：`BUILD SUCCESSFUL`（编译器即证明）；
`terrain-wiring-check` 报 `NOT routing into RWG terrain: (none)`（0 个断线）；
`surface-wiring-check` 仍是 `shared=125 still-inner=4 no-new=1`（那 4 个海滩是**有意**保留的，
RWG 的 `CoastDunes` 没有可共用的 `Surface*`）。

### 本轮完结时**仍未在游戏内验证**的清单（交给用户验收）

1. **`randBiome` 接上后的过渡带**（本轮唯一的新机制）：边界是否真的呈噪声状、
   有没有新的副作用；
2. **滨海带**：从"全世界 13% 全是海滩群系"改成"只有湿带有沼泽/红树"之后的观感；
3. **山地链**：现在是 RWG 的 4 种（`boreal_forest` / `chaparral` / `rainforest` /
   `tropical_rainforest`），旧版是 104 种；
4. **地下河/洞厅**：`/rtg probe` 站到山地里的河消失处，会给 ✅/✗ 与原因；
5. **性能**：`RTGConfig` 里的 profiler 打开即可（从未跑过）。

### 追加（同版本）：用户报「又出现越界小圆点」—— 定位到一个**取错列**的旧 bug

> **症状**：接上 `randBiome` 之后，出现了孤立的小斑块（1.0.29 也出现过，当时整体回滚了）。

这次没有再猜，先查了三处**可证**的事实：

1. `SURFACE_BLEED_IN` / `SURFACE_BLEED_OUT` 默认 **false**，全仓只有 **3 个群系**在
   `initConfig()` 里置 true：`VanillaBeach` / `VanillaStoneBeach` / `BOPGravelBeach`
   —— 也就是"地表渗透"只在**海滩**上生效。
2. `surfaceBlendRadius` 默认 **32**（范围 8–32）⇒ 采样点最多离当前列 **32 格 = 2 个区块**。
3. 旧代码这么取"渗透源"的群系：

```java
jitterbiome = landscape.biome[(pX & 15) * 16 + (pZ & 15)];
```

`pX` / `pZ` 是**世界坐标**，而 `& 15` 只给出"该位置在**它自己**所在区块内的偏移"，
却拿它去索引**本区块**的 `landscape.biome[]` ⇒ **取到的是另一列**。
于是开了渗透的海滩附近，地表会刷上一个与周围毫无关系的群系；而该列的位置随噪声
在 16 格周期上跳变 ⇒ **孤立小斑块**。

**为什么现在才显形**：以前 `actualbiome` 是"权重最大的群系"（很少是海滩），
这个 bug 的分支极少进入；接上 `randBiome` 后 `actualbiome` 变成噪声挑中的**地表**群系，
过渡带里海滩被挑中的频率上升 ⇒ 分支频繁进入 ⇒ 症状"又出现了"。
**所以它不是 `randBiome` 的错，是被 `randBiome` 照亮的一个旧 bug。**

#### Fixed

- 改为直接问布局"那个世界坐标上是哪个群系"：`RtgLayoutAccess.biomeAt(pX, pZ)`。
  这是唯一可行的正确读法 —— 半径最大 32 格，邻居区块的 `landscape` 未必在缓存里。
- 顺带把 `multiEval2D` 挪进 `if (SURFACE_BLEED_IN)` 里（原来**无条件**算、
  结果绝大多数列被丢弃）：没开渗透的列省掉一次噪声求值，每区块最多省 256 次。

**本轮没有叠加第二处猜测性改动**（例如给 `randBiome` 加权重下限）。
先让这一处归零；若小圆点仍在，下一个候选是 `randBiome` 用**极低权重**群系抽地表
产生的 15 格尺度散点，处理方式是把参与抖动的成员限制在"权重真正参与混合"的那些
（届时再改，并单独报告）。

### 追加（同版本）：**生物指南针搜不到 BOP 群系** —— provider 的批量查询没接布局

> **症状**：用生物指南针（Nature's Compass）搜 `bamboo_forest` 搜不到。

#### 根因（字节码实证，不是推测）

反编译 `NaturesCompass-1.12.2-1.5.1` 的 `util/BiomeUtils`，它每个候选点调的是：

```
invokevirtual BiomeProvider.func_76931_a([Lnet/minecraft/world/biome/Biome;IIIIZ)[Lnet/minecraft/world/biome/Biome;
  前置：aload 6 (provider) / aconst_null / iload x / iload z / iconst_1 / iconst_1 / iconst_0
```

即 **`provider.getBiomes(null, x, z, 1, 1, false)`**（`func_76931_a` = `getBiomes`，1×1、不缓存）。

而 rtgc 的两个 provider **只覆写了逐点查询** `getBiome(BlockPos)` / `getBiome(BlockPos, Biome)`；
`getBiomes(...)` 落到**父类的 GenLayer 实现**，读的是 `biomeIndexLayer` ——
而 `BiomeProviderBOP` 把它设成了 **BOP 自己那套 GenLayer**（`setupRTGGenLayers` 建的，
类里第 49 行的注释"它服务于下面未覆写的批量查询与结构选址"说的就是这件事）。
那是一张**与 RTG 布局毫无关系的群系表**，而且它的 `BOPWorldSettings` 还是用
**rtgc 的 generator options 字符串**构造的。

⇒ **指南针搜的是"另一个世界"。** 它能不能搜到某个 BOP 群系，取决于 BOP 那套 GenLayer 的分布，
与玩家脚下的地形无关 —— 所以这一类搜索结果一直是不可靠的（不是本轮改动引入的：
`grep` 全仓 rtgc **没有任何内部调用方**，我这几轮也没碰 provider 的这条路径）。

#### Fixed

两个 provider 都补上 RWG 早就覆写的那两个批量查询：

| 1.12.2 | RWG 1.7.10 的对应物 | 现在的行为 |
| --- | --- | --- |
| `getBiomes(...)` | `loadBlockGeneratorData(...)`（`ChunkManagerRealistic:1024`） | 逐格走 `RtgLayoutAccess.mcBiomeAt` |
| `getBiomesForGeneration(...)` | `getBiomesForGeneration(...)`（`:1009`） | 同上 |

fail-soft 与 `getBiome` 一致（布局未就绪才回落到父类 GenLayer）。
**验证**：两个方法都带 `@Override` 且编译通过 —— 编译器即证明签名与父类一致、覆写真的生效；
`grep` 确认 rtgc 内部零调用方，所以影响面只有"外部做批量查询的模组"。

#### 有意**没改**的（结构选址，另案）

`areBiomesViable(...)` / `findBiomePosition(...)` 仍是父类实现。RWG 那边这两个是**地形判据**
（`ChunkManagerRealistic:1044` 看中心高度 ≥62 且周围 5×5 的起伏 <22；`:1073` 直接返回 `null`），
rtgc 现在把它们留给了原版 GenLayer ⇒ **村庄/神庙/要塞的选址依据与实际群系图不一致**。
这是结构放置的取舍（改了就会挪结构），与"群系查询"分开处理，留给下一轮决定。

### 追加（同版本）：结构选址改成走 RTG 布局 —— 但**没有**照抄 RWG 的那两个方法体

用户的指令是"按 RWG 接上"。查完 1.12.2 的实际调用方之后，**照抄会坏事**，所以这里要交代清楚。

#### 先解决"没有依据"的问题：MC 源码其实就在仓库里

`build/rfg/mcp_patched_minecraft-sources.jar`（2894 个 `.java`）—— 1.12.2 的完整反编译源码。
把它解开、直接查调用方，不再靠记忆：

```
BiomeProvider.areBiomesViable(x, z, radius, allowed)
  MapGenVillage.java:76           半径 0    VILLAGE_SPAWN_BIOMES
  StructureOceanMonument.java:83  半径 16   SPAWN_BIOMES
  StructureOceanMonument.java:88  半径 29   WATER_BIOMES
  WoodlandMansion.java:59         半径 32   ALLOWED_BIOMES

BiomeProvider.findBiomePosition(x, z, range, biomes, random)
  WorldServer.java:971            出生点搜索（(0,0) 周围 256 格）
  MapGenStronghold.java:155       要塞选址（半径 112）
```

#### 为什么**不能**照抄 RWG 的 `areBiomesViable` / `findBiomePosition`

RWG 1.7.10 的这两个方法（`ChunkManagerRealistic:1044` / `:1073`）是：

- `areBiomesViable`：**忽略 radius 与候选列表**，只要"中心高度 ≥ 62 且周围 5×5（间隔 16 格）
  极差 < 22"；
- `findBiomePosition`：**直接返回 `null`**。

放在 1.12.2 上：

1. **海底神殿会一个都不生成**：它传的是 `WATER_BIOMES`、半径 16/29，
   而海洋列的高度是 **34–52**，永远过不了"≥ 62"那道闸。林地府邸同理（黑森林不保证 ≥ 62）。
   1.7.10 根本没有海底神殿/林地府邸，RWG 那条规则当时只服务村庄。
2. **出生点会塌到 (8, y, 8)**：`WorldServer:971` 拿到 `null` 会打
   `"Unable to find spawn biome"` 并把出生点放回默认坐标。

#### 实际做法：**保留原版语义，只换数据源**

`RtgTerrainQuery`（新类）逐行对应**原版** `BiomeProvider` 的两个方法
（4 格采样、`allowed.contains`、蓄水池抽样 `random.nextInt(k1+1)==0`），
只把 `this.genBiomes.getInts(...)` 换成"直接向 RTG 布局取每一格的群系"。

这才是"按 RWG 接上"的要害：**RWG 的全部意义就是让结构/出生点选址读它**自己的**群系图**，
而不是原版 GenLayer —— 至于用不用地形平坦度当判据，是 1.7.10 与 1.12.2 调用方不同导致的
**不可移植**部分。

| | 1.12.2 结果 |
| --- | --- |
| `areBiomesViable` | MapGenVillage / OceanMonument / WoodlandMansion 全部按**真实群系图**判定 |
| `findBiomePosition` | WorldServer 出生点、Stronghold 要塞按**真实群系图**选址 |

⚠ 布局存在时 `findBiomePosition` **不回落父类**：父类查的是 BOP 那张无关的表，
返回的坐标上真实群系未必在候选列表里（出生点会被丢到海里/冰原）。找不到就老实返回 `null`，
由 `WorldServer` 走它自己的兜底。

#### 验证

- 6 个覆写全部带 `@Override` 且编译通过（编译器即证明签名与父类一致）；
- 新增字段/参数在建完后**又删掉了**（`RTGWorld rtgWorld` / `World world` 在最终实现里用不到）——
  本轮刚清掉 119 个死类，不留新的死代码；
- 出生点候选列表是原版的 `BiomeProvider.allowedBiomes` =
  `{forest, plains, taiga, taiga_hills, forest_hills, jungle, jungle_hills}`，
  逐个对照 15:01 那次运行日志的 CORE 名单：**7 个全部在布局里** ⇒ 出生点搜索能在真实世界里找到落点
  （不需要再覆写 `getBiomesToSpawnIn`）。

#### 验收时留意

- **海底神殿**应仍然生成、且在海里（这一项是本次最容易被"照抄 RWG"搞坏的）；
- 村庄/神庙/要塞的落点应与地形和真实群系一致；
- 出生点不应固定在 (0,0) 附近；若日志出现 `Unable to find spawn biome`，告诉我。

### ✅ 验收记录：用户游戏内确认"一切正常"（1.0.33 同版本全部改动）

用户跑完这一批改动后回复：**「验收一切正常」**。据此把此前标着"未在游戏内验证"的项逐个结清：

| 项 | 状态 |
| --- | --- |
| `randBiome` 地表群系边界抖动（过渡带呈噪声状） | ✅ 已验 |
| 「越界小圆点」修复（地表渗透取错列 + 只对开启者生效） | ✅ 已验（不再出现） |
| 滨海带：只有湿带有沼泽/红树，其余气候 432 带回归普通陆地 | ✅ 已验 |
| 山地链：RWG 的 4 种（`boreal_forest`/`chaparral`/`rainforest`/`tropical_rainforest`） | ✅ 已验 |
| 生物指南针能搜到 BOP 群系（`getBiomes` 接布局） | ✅ 已验 |
| 结构选址走 RTG 布局（村庄/神庙/要塞/出生点/海底神殿） | ✅ 已验 |
| 119 个死的内层 `Surface*`/`Terrain*` 类清理（约 8435 行） | ✅ 编译 + 接线检查 + 游戏内无异常 |
| `heighteffect` 整包删除、`unsignedPower` 删除 | ✅ 同上 |
| D3 气候归类去偏斜（倍率 0.95/1.23/0.73/1.05） | ✅ 数字已验（见上文实测行） |

**仍然没有数据的（不是"已验"，是没测）**：

- **性能**：`RTGConfig` 里的 profiler 一次都没跑过，本批改动（每列多一次 15 格噪声、
  每区块最多省 256 次 `multiEval2D`、结构选址改成走布局）的净影响没有数字；
- **地下河/洞厅**：`/rtg probe` 没跑过，"是否真的开凿"仍无实测；
- **HOT 倍率 0.73**：已在验收区间内（0.6–1.6），要再拉只能靠发明，**按"达标即收"结案**。

**仍在案、且我建议就停在这里的**（都是一次性小项，收益低于风险）：

- 4 个海滩群系仍用各自的内层 `Surface*`（`surface-wiring-check`：`shared=125 still-inner=4`；
  RWG 的 `CoastDunes` 没有可共用的 `SurfaceBase` 子类，硬接会变成发明）；
- `rDecorateAfterIce`（海洋群系的原版装饰）未移植 —— 全仓 `grep` **0 处**；
- 山地链的装饰缩放（RWG 按"平缓采样点比例"缩放）未移植；
- `terrainDunes` 与忠实的 `terrainPolar(int,int,RTGWorld,float)` 仍是 **0 调用者**（已移植、待接线；
  沙漠现在走 RTG 配方以保住"沙丘高度"滑条）；
- 约 13 个群系的地形映射是**推断**而非照抄（RWG 无对应物；`docs/rwg-port-gaps.md` 里
  带 `**推断**` 标记的有 5 条，另有 §10/§11 表格里以"⚠ 近亲/推断"标注的若干）；
- `LARGE_ISLAND` 空池（1.12.2 无对应群系）、D5 海洋种类（用户已裁定不追）、
  HOT 倍率 0.73（用户已裁定不管）。

**顺手查清、确认**不是**缺口的**（免得以后被当待办翻出来）：

- RWG 覆写了 `getRainfall(...)` / `getTemperatureAtHeight(...)`，rtgc 没有 ——
  查 1.12.2 源码：`BiomeProvider.getTemperatureAtHeight` 本体就是 `return 入参`，
  与 RWG 的覆写**逐字相同**；而 `BiomeProvider` 在 1.12.2 **已经没有** `getRainfall` 方法
  （只剩 `Biome.getRainfall()`，与 provider 无关）。⇒ 无需覆写。
- RWG 还覆写 `getBiomesToSpawnIn()`，rtgc 不需要：原版出生点候选列表
  `{forest, plains, taiga, taiga_hills, forest_hills, jungle, jungle_hills}` **7 个全在 rtgc 布局里**
  （已逐个对 15:01 的运行日志核过）。

**代码里剩下的 TODO 一律是 RWG/RTG 1.12 上游自带的**（全部带 `[1.12]` 前缀，位于
`IRealisticBiome` / `DecoBase` / `Deco*` 等），不是本项目新欠的账；追它们属于重构而不是移植。

### 追加（同版本）：新增 `/rtg tunnels [半径]` —— 地下河/洞厅**定位器**（HOT 0.73 结案）

用户裁定 **HOT 倍率 0.73 不再追**（已在验收区间内，再拉只能靠发明）。

验收剩余项时发现：**"地下河/洞厅到底有没有开凿"没有可操作的验收手段** ——
隧道只在**山地链**内的河网边界/顶点开凿、洞顶还压在地表以下 ≥10 格，
"走进世界找一条地下河"基本靠运气。这正是本项目反复吃过亏的验收方式，所以补一个命令。

`/rtg tunnels [半径(区块, 默认8, 上限24)]`：

- **只读**扫 {@code ChunkGeneratorRTG.landscapeCache} 里**已有**的条目
  （新增 `cachedLandscape(cx, cz)` —— 与 `getLandscape` 的区别是**绝不新建**、不生成、不污染 LRU）；
- 把 `riverCaveCeiling[k] > 0` 的列（= **真的被开凿过**的列）按距离排序报出来：
  `x / z / 洞顶 y / 直线距离`，最多 8 个；玩家照着坐标往下挖到"洞顶 y"即可；
- 一个都没有时说明两种可能并给出下一步：① 这里不是山地链；② 是链但没走到河网边界/顶点。

这样"测试地下河"从"漫游找洞"变成"一条命令 + 往下挖"。
配合 `/rtg probe`（报链权重/链宿主/是否开凿）可以判断自己是不是站在链里。

### 追加（同版本）：性能**首测**结果 —— 地形很健康，瓶颈全在装饰；并拆分装饰阶段

用户开启 profiler（`-Drtg.enableProfiling=true -Drtg.profilerLogInterval=200
-Drtg.profilerSlowThresholdMs=5`）跑了一次，2400 个区块。**第一批真数字**：

```
Summary after 2400 chunks | Slow(>5ms): 2053 (85.5%) | Slowest: [2,16] 4795.48ms
Top phases (avg): Pop: decoration=16.77ms  Landscape get/gen=1.26ms
                  Chunk finalize + skylight=0.94ms  Pop: snow & ice=0.43ms
                  Structure generation=0.31ms
```

各阶段在慢区块里的平均耗时（2114 条分解）：

| 阶段 | avg | max |
| --- | --- | --- |
| **Pop: decoration** | **~17ms** | **4785ms** |
| Landscape get/gen（混合晶格，含新加的 `randBiome`） | 1.23ms | 16.6ms |
| Chunk finalize + skylight | 0.93ms | 6.9ms |
| Biome surface replace | 0.28ms | 1.6ms |
| Cave generation | 0.17ms | 5.2ms |
| **River tunnels + chambers** | **0.14ms** | 0.35ms |
| Structure generation | 0.12ms | 1.1ms |
| Terrain fill | 0.09ms | 0.6ms |
| Ravine generation | 0.05ms | 3.6ms |
| **Surface jitter calc（新加的 `randBiome`）** | **0.01ms** | 0.44ms |

分布：中位数 **8.53ms**、P90 35ms、P99 **530ms**、最大 **4795ms**；>500ms 的有 23 个。

**判读**：

1. **rtgc 自己的地形管线健康**。典型区块 `Chunk generate total` 只有 **2.6–5.3ms**
   （同一区块的 `Populate total` 是 46.75ms）。本轮新加的 `randBiome` 在地表抖动那一格是
   **0.01ms**，`River tunnels + chambers` 是 **0.14ms** —— 两个新机制都不是负担。
2. **瓶颈是装饰（populate），而且有严重的尾部**。最慢的那个区块 `[2,16]` 4.795 秒里
   **99.8% 是 `Pop: decoration`**，而同一区块的地形只有 **2.59ms**。
   它出现在 `Preparing spawn area: 66%` 附近。
3. `Slow(>5ms) 85.5%` 这个数字**不必恐慌**：总耗时含装饰，而装饰平均就 17ms，
   5ms 的阈值对这张表本来就偏低。

**下一步（已实现，等一次运行）**：把装饰阶段拆成三格，好把"谁的 4.8 秒"钉死：

- `Pop: deco PreEvent` —— `DecorateBiomeEvent.Pre` 的发出耗时，**里面跑的是别的模组的处理器**；
- `Pop: decoration` —— 剩下的（rtgc 自己的 `rDecorate` + 9×9 邻域采样）；
- `Pop: deco PostEvent` —— `DecorateBiomeEvent.Post` 同理。

⚠ 一个可查的相关性：**这两个事件是本轮才补上发出的**（此前 rtgc 的 populate 取代了原版的
`ChunkProviderServer#populate`，它们是全仓唯一发出点却从未发出）。也就是说
**BOP 等模组的装饰处理器是从本批改动才开始跑的** —— 秒级尖峰很可能出现在那里。
拆分之后一次运行就能确认，不必猜。

### 追加（同版本）：装饰尖峰**归因完成** —— 不是别人的模组，是"开局那十几秒"

拆开装饰阶段之后又跑了一次（4000 区块）。新增两格的实测：

```
Pop: deco PreEvent   平均 0.01ms   最大 0.32ms     ← 别的模组的事件处理器
Pop: deco PostEvent  平均 0.00ms   最大 0.05ms     ← 同上
Pop: decoration      平均 17.21ms  最大 3498ms     ← 剩下的：全是 rtgc 自己的 rDecorate
```

⇒ **上一轮"可能是 BOP 等模组的装饰处理器"的猜测被自己的仪表否掉了。**
两个事件加起来最大 0.37ms，秒级尖峰 100% 在 rtgc 自己的装饰路径里。
（这正是"先加仪表再下结论"的价值 —— 否则这一轮就会去改错的地方。）

再按时间与坐标切开 3616 条 SLOW 记录：

```
>500ms 的全部出现在 15:59:21–15:59:30 这 9 秒内，坐标距原点 19–32 区块（= 出生点区域）
>100ms 的 51 个全部落在 15:59 这一分钟；之后 ~3600 个区块里 >100ms 的**一个都没有**
```

而两轮运行的形态一致（上一轮最慢的 `[2,16]` 4795ms 也在 `Preparing spawn area` 附近）。

**结论**：这不是持续性的性能问题，而是**开局生成出生点区域时的一次性爆发**
（约 10–50 个区块），之后中位区块 8.5ms、地形管线 ~3ms，完全健康。
爆发最可能的成分是**首次使用的初始化 + JIT + GC**（装饰阶段分配量最大），
但**这一点尚未证明**，所以又补了一行归因日志：

`[RTG-DECOPROF] chunk(cx,cz) Nms decoBiomes=[…] rtgDecos=..ms vanillaDecorate=..ms slowest=[DecoX=..ms xN, …]`

只在**本区块装饰超过 200ms** 时打印（平均才 17ms，正常游玩不会出现），
一次"新建世界"的运行就能把爆发里最慢的几个装饰器点名。

### 追加（同版本）：海洋装饰延迟（`rDecorateAfterIce`）+ BOP 珊瑚/海草清理

#### 先纠正我上一轮列错的一项

我在"还有什么没做的"里把**山地链的装饰缩放**列为未移植 —— **那是错的**。
代码里 `RealisticBiomeMountainChain.gentleFraction(noise)`（第 254-292 行）已经实现了
RWG `:61-78` 的那套"平缓采样点比例"，并在 `rDecorate`（第 226-241 行）里按
`fraction` 做概率缩放。**它早就做完了**，我是照抄了文档里的旧记录而没有读代码。
（差异只有采样点：RWG 用 8/12/16/20（会跨出区块），rtgc 用 4/8/12（只有本区块的 256 列高度），
这一点代码注释里已写明。）

#### 本轮真正做的：`rDecorateAfterIce`

RWG 里海洋群系的 `rDecorate` 是**空的**，它唯一的装饰是 `rDecorateAfterIce`：
把 `Biome.decorate`（原版/BOP 的装饰器，在 BOP 的 `kelp_forest`/`coral_reef` 上放的就是珊瑚与海草）
**推迟到"水面结冰 + 铺雪层"pass 之后**再跑 —— 否则随后铺的冰会把刚放下的装饰压掉。
门控是 `decorateBaseBiome && strength > 0.3f`（只有占主导的那个海洋群系才动手），
而 RWG 里 `decorateBaseBiome` 为 true 的**只有 BOP 那两个**
（`Support.java:156-174` 里原版海洋变体传的都是 `false`）。

**实现**：

- `IRealisticBiome` 新增三个默认方法：`vanillaDecorate(...)`（把原版装饰尾段抽出来，
  供两条路径共用，避免漂移）、`defersVanillaDecorateUntilAfterIce()`（默认 false）、
  `rDecorateAfterIce(...)`（默认空）。
  `rDecorate` 的尾段改为 `if (!defers...) vanillaDecorate(...)`。
- `BOPKelpForest` / `BOPCoralReef` 覆写为 `true`，并在 `rDecorateAfterIce` 里
  `strength > 0.3f` 时跑 `vanillaDecorate` + 清理。
- `ChunkGeneratorRTG` 新增 `deferredOceanDecorations[]`：装饰循环里把这些群系的权重记下来
  （RWG `:997-998`），**冰雪 pass 之后**再逐个调 `rDecorateAfterIce`（RWG `:1115-1120`）。
- 新增 `rtg/api/util/OceanDecorationSanitizer`：RWG `RealisticBiomeBOPOcean.sanitizeColumn`
  的等价物 —— 对区块原点外扩 −3…+30 的 34×34 区域、y=1..62 逐格扫，
  群系不匹配的列跳过，`BOPBlocks.coral`/`plant_0`/`plant_1` 里**站不住**的换成水。

**两处与 RWG 的有意差异（1.12.2 上没有对应物，硬做就是发明）**：

1. **不重写海草柱的 metadata**。RWG 1.7.10 的海草是 `coral1` 的 meta 8/9/10/11（底/中/顶/单节），
   所以它能"重排整柱"。BOP **1.12.2** 的珊瑚是 `BOPBlocks.coral`，变体枚举
   （实测 `javap BlockBOPCoral$CoralType`）只有 `PINK/ORANGE/BLUE/GLOWING/ALGAE`，
   **没有海草变体**；1.12 的海草由 BOP 自己的生成器按其状态机放置。
   ⇒ 只做"站不住就删"这一半（可见症状的来源）。
2. **"站得住"问 BOP 自己**：1.12 把判据统一成了 `BlockBush#canBlockStay(World, BlockPos, IBlockState)`，
   而 BOP 的装饰块覆写了它（实测 `javap` 确认 `BlockBOPCoral` / `BlockBOPPlant` 都有）。
   故先确认是 `BlockBush` 再问它，不是就**不动** —— 不替别的方块猜判据。

**原版海洋群系保持原样**（不推迟、不清理）：RWG 里它们 `decorateBaseBiome=false` 等于完全不装饰，
但 rtgc 的 `Biome.decorate` 里**还带着矿物生成**，照着关掉会连矿一起没了 —— 那是回归，不是移植。

**未在游戏内验证**：需要飞到 `kelp_forest` / `coral_reef` 的海底看珊瑚/海草是否还有悬空/戳出水面的。

### 评估：4 个海滩的内层地表、4 个海洋群系 —— **两项都能做，而且都不是"发明"**

用户问"能不能自己发明"。查完 RWG 源码后结论是：**不需要发明，两项都有可抄的原物**。

#### ① 4 个海滩（`VanillaBeach` / `VanillaStoneBeach` / `BOPGravelBeach` / `BOPOriginBeach`）

上一轮的文档里写着"RWG 的 `CoastDunes` 没有可共用的 `SurfaceBase` 子类"——
**这句话误导了方向**。事实是：`RealisticBiomeCoastDunes.rReplace`（`:66-106`）
**有一份完整的地表实现，只是内联在群系类里**，从没被提取成共享类：

```
cliff > 1.3        → 崖壁：cobblestone / stone
否则 depth == 0    → k > 68 ? grass : sand（并记住 sand=true）
     depth < 5     → sand 分支：depth<4 → sand，depth==4 → sandstone
                     否则 → dirt
```

而 rtgc 这边**恰恰相反**：地形那半提成了 `TerrainBase.terrainCoastDunes`，
**地表那半没提**，于是这 4 个群系各自带一份内层 `Surface*`。

⇒ **做法**：把 RWG 这份 `rReplace` **逐行**提成共享类（`SurfaceCoastDunes`），
按 rtgc 既有的口径用**构造参数**传 top/filler（与已经移植的 `SurfaceGrassland` /
`SurfaceDesert` 同一套约定），这样 BOP 的白沙/砾石身份也能保留。
**这是照抄 + 现有约定，不是发明**；判据是"4 个内层类是否只在方块/参数上不同"，
若是则替换后行为可逐位对齐（用 `surface-wiring-check` 复核到 `still-inner=0`）。

#### ② 4 个海洋群系（D5：四个气候各自的深海）

上一轮我写的是"RWG 的四个深海是它自造的群系，rtgc 没有对应物 ⇒ 抄不到"——
**前半句对，后半句错**。RWG **确实定义了**自己的海洋群系，而且定义就在源码里：

```java
// rwg/biomes/base/BaseBiomes.java:25-26
RWGBiomes.baseOceanHot = new BaseBiomeOcean(ConfigRWG.biomeIDs[9], 3, "Hot Ocean");
RWGBiomes.baseOceanWet = new BaseBiomeOcean(ConfigRWG.biomeIDs[10], 4, "Wet Ocean");
// :78-80 显式注册字典类型
.registerBiomeType(baseOceanHot, Type.OCEAN, Type.BEACH, Type.HOT, Type.DRY, Type.SANDY);
.registerBiomeType(baseOceanWet, Type.OCEAN, Type.BEACH, Type.HOT, Type.WET, Type.JUNGLE);
```

有 `BaseBiomeOcean` 类、有 id、有名字、有字典类型 ⇒ **照抄即可**：
移植 `BaseBiomeOcean`（其 top/filler/颜色/温度）+ 为 hot/wet（要更全的话再加 ice/temperate）
各建一个 rtgc 群系 + `RealisticBiome*` 包装 + 注册进布局的海洋槽位。

**代价与风险**（这才是需要用户拍板的部分，不是"能不能"）：

- 要**新增生物群系**（id、lang、字典类型），**id 必须 < 256** —— 因为原版
  `Chunk.blockBiomeArray` 是 `byte[256]`（已实测当前最大 id 89，所以有空间，但不能再往后挤）；
- 新群系会出现在**所有**读群系列表的地方（小地图、其他模组的群系筛选、F3）；
- 要给它们配 `RealisticBiome*` 包装与地表/地形，并让 `RtgBiomeCategorizer` 把它们归进
  各气候的海洋槽位（按 `isOcean` + 名称里的 deep/shallow）。

⇒ **可以做，是移植而非发明；工作量中等（4 个群系类 + 注册 + 语言文件 + 接线）。**
用户此前对 D5 说过"少几种海无所谓"，所以这一项**等用户重新拍板**再动。

### 追加（同版本）：4 个海滩的共享地表 + 4 个 rtgc 自己的深海群系

用户对上一轮的评估回复"做"，两项都做了。

#### ① 4 个海滩：把 RWG **内联**的 `rReplace` 提成共享类

RWG 的 `RealisticBiomeCoastDunes.rReplace`（`:66-106`）是**内联写在群系类里**的，
所以 rtgc 只移植了它的地形（`TerrainBase.terrainCoastDunes`）、地表那半从没移植 ——
于是 4 个滨海群系各自带一份内层地表。

- 新增 `rtg/api/world/surface/SurfaceCoastDunes`：**逐行**照抄那份 `rReplace`
  （崖壁 `cliff > 1.3f` → 1/3 概率 cobblestone、其余 stone；非崖壁 `k > 68` → grass，
  否则 sand 并把以下 4 格刷 sand/sandstone；非沙处刷 dirt）。
  **块写死、`k > 68` 保留字面量**（与 `SurfaceDesert` 保留 `k > 61` 同一口径）；
  唯一的管线适配是悬崖判定改用 `TerrainBase.calcCliff(x, z, noise, river)`。
- `VanillaBeach` / `VanillaStoneBeach` / `BOPGravelBeach` / `BOPOriginBeach` 改为返回它，
  并删掉 5 个已成死码的内层 `Surface*`（含 `VanillaColdBeach` 那个同名残留），共 223 行 + 34 条悬空 import。

**验证**：`tools/surface-wiring-check.ps1` 从 `shared=125 still-inner=4` 变成
**`shared=130 still-inner=0`** ✓（工具的白名单也补上了 `SurfaceCoastDunes`）。

⚠ **可见变化（验收时留意）**：这 4 个海滩的**地表**现在按 RWG 的 `CoastDunes` 来 ——
上方草、下方沙、沙下砂岩、崖壁碎石。因为 RWG 的单一 `CoastDunes` 本来就**不区分**
BOP 的砾石滩/白沙，所以 `gravel_beach` / `origin_beach` 在地表上会与普通沙滩一致。

#### ② 4 个 rtgc 自己的深海群系（D5 收口）

- 新增 `rtg/world/biome/RtgOceanBiome`：RWG `BaseBiomeOcean` 的移植，温度/降雨/动物/禁雨**逐条照抄**
  （ICE 0.0/0.1 + Wolf 8,4,4；COLD 0.5/0.4 + Wolf 8,1,2；HOT 0.8/0.2 禁雨；WET 0.9/0.9 + Ocelot 2,1,1）。
- 新增 `rtg/api/world/surface/SurfaceOcean`：RWG `RealisticBiomeOcean.rReplace` 的逐行移植
  （浅海刷沙 / 深海刷砾石，`depth < 6`）—— 因为 RWG 的 `BaseBiomeOcean` **不设 top/filler**，
  海底完全由它决定。
- 新增 `rtg/world/biome/realistic/rtg/RealisticBiomeRtgDeepOcean` 包装：
  地形 `terrainOcean(..., shallow=false)`（海底 y≈34），地表 `SurfaceOcean(false)`，
  装饰沿用 rtgc 既有海洋口径（`DecoCollectionOcean` + 默认原版装饰 —— RWG 那边海洋完全不装饰，
  但 rtgc 的原版 `Biome.decorate` 里带着**矿物生成**，关掉会连矿一起没，故不照做）。
- `BiomeInit` 新增 `init_rtgc_oceans()`，注册名
  `rtgc:deep_ice_ocean` / `deep_cold_ocean` / `deep_hot_ocean` / `deep_wet_ocean`，
  并显式登记字典类型（OCEAN+COLD+SNOWY / OCEAN+COLD / OCEAN+HOT+DRY+SANDY / OCEAN+WET+JUNGLE）。

**归位是自动的**：`RtgBiomeCategorizer` 按 `name.contains("ocean")` 判海洋、
按 `name.contains("deep")` 判深海槽位；气候由温度/降雨自动落位
（0.0→SNOW、0.5→COLD、0.8/0.2→HOT、0.9/0.9→WET，与 RWG 的四个气候一一对应）——
所以**不用改分类器**。

#### ⚠ 这一项我**无法离线验证**，要你跑一次看两处

1. **注册是否被接受**。1.12.2 的 `IForgeRegistry` **没有**显式 id 的重载
   （实测：只有 `register(V)` / `registerAll(V...)`），所以 id 由 Forge 分配、
   我**不能**像原计划那样钉在 120–123。故整段包了 try/catch **fail-soft**：
   - 成功 → 日志出现 4 行 `[RTG] 注册深海群系 rtgc:deep_xxx_ocean（id=N）`
   - 失败（例如注册表已冻结）→ 一行 ERROR + **整体跳过**，布局像以前一样借用，不会崩。
   注册放在 `preInit()`（不是 `init()`）—— `init()` 阶段注册表多半已冻结，那是我第一版的位置，已改。
2. **布局里四个气候的深海是否各归各家**。`-Drtg.debugLayout` 会打
   `SNOW/COLD/HOT/WET 海洋：浅海=… 深海=…` 四行；深海那侧应出现新的 4 个 `rtgc:deep_*_ocean`，
   而不再是四行都是 `minecraft:deep_ocean`。

**id 的隐患（记录在案）**：若 Forge 分配的 id ≥ 128，`Chunk.blockBiomeArray`（`byte[256]`）
存的是负字节 —— 原版读的时候会 `& 255` 取回，且 rtgc 现在**两个数组都写**、
客户端由 `ChunkGetBiomeMixin` 在读取点按布局作答，所以 F3/群系表不受影响；
但这条链路的实际表现仍以那次运行的日志为准（日志会打出 id）。

#### 第一次运行的结果：注册被 **rtgc 自己的兼容层**挡下（已修）

```
[Client thread/ERROR] [RTGC]: [RTG] ⚠ 注册 rtgc 自己的深海群系失败，已整体跳过 ——
  four climates… : java.lang.NullPointerException:
  ModCompat.Mods does not have a value for the mod that added this biome.
```

**这条日志本身很有信息量**：抛点在 `RealisticBiomeBase.getConfigFile()` 的
`Mods.get(baseBiomeResLoc().getNamespace())` —— 也就是说
**`ForgeRegistries.BIOMES.register(biome)` 已经成功了**（否则到不了这一步），
preInit 的时机与注册表状态都没问题；被挡下的是 rtgc 自己的枚举里没有 `rtgc` 这一项。

**Fix**：`ModCompat.Mods` 增加 `rtgc`。
副作用已核：`Mods.values()` **只**在 `Mods.init()` 里被遍历（标记 loaded），
别处全是按常量显式引用（`biomesoplenty` / `geographicraft` / `thaumcraft`），
所以这一项只让 `Mods.get("rtgc")` 能命中；配置目录因此是
`config/RTGC/rtgc/<群系>.cfg`（与原版走 `config/RTGC/minecraft/…` 同一套命名）。
另外给 `init_rtgc_oceans()` 加了**幂等标志**（注册表不允许同名注册两次）。

**同时确认 fail-soft 按设计工作**：那次失败只打了一行 ERROR，世界生成完全正常
（同一次日志里 D3 倍率 0.95/1.23/0.73/1.05、各池计数、海洋槽位全部与上一版一致）。

**其余读数无变化**（说明这次改动没有副作用）：海洋槽位仍是四行 `minecraft:deep_ocean`
（因为注册被跳过，布局继续借用 —— 与预期一致）。

#### 第二次运行：**注册成功，四个气候各归各家** —— 但发现它会挤掉 `minecraft:deep_ocean`

```
[RTG] 注册深海群系 rtgc:deep_ice_ocean（id=113）
[RTG] 注册深海群系 rtgc:deep_cold_ocean（id=114）
[RTG] 注册深海群系 rtgc:deep_hot_ocean（id=115）
[RTG] 注册深海群系 rtgc:deep_wet_ocean（id=116）

[RTG] 具名计数：深海=5 浅海=4 …
[RTG]   SNOW 海洋：浅海=minecraft:frozen_ocean 深海=rtgc:deep_ice_ocean
[RTG]   COLD 海洋：浅海=biomesoplenty:kelp_forest 深海=rtgc:deep_cold_ocean
[RTG]   HOT  海洋：浅海=biomesoplenty:kelp_forest 深海=rtgc:deep_hot_ocean
[RTG]   WET  海洋：浅海=biomesoplenty:kelp_forest 深海=rtgc:deep_wet_ocean
```

- id 落在 **113–116**（没有像担心的那样跑到 128 以上）✓
- 四个气候的深海**各归各家**，`fillMissingOceanSlots` 不再借用 ✓（`深海=1` → `深海=5`）

**但接着查出一个真问题**：四种深海全换掉之后，`minecraft:deep_ocean`
**一列都不会生成了**，而 1.12.2 的海底神殿**只认它**：

```java
// StructureOceanMonument（1.12.2 源码实测）
public static final List<Biome> SPAWN_BIOMES = Arrays.<Biome>asList(Biomes.DEEP_OCEAN);
if (!world.getBiomeProvider().areBiomesViable(i*16+8, j*16+8, 16, SPAWN_BIOMES)) return false;
```

由于 `areBiomesViable` 现在走**真实布局**（本轮早些时候刚接上），
"没有一列是 deep_ocean" ⇒ **海底神殿会彻底消失**。
这正是我上一轮把"海底神殿仍应生成"列为头号风险的那件事 —— 只是这次踩中的路径不同
（不是改了判据，而是**把那个唯一的候选群系从世界里挤掉了**）。

**Fix（同时更忠实）**：冷带的深海**继续用原版 `minecraft:deep_ocean`**，
新群系只给雪/热/湿三带用（**3 个**）。依据两条，都已查证：

1. **RWG 自己就是这么分的**：`Support.java:172`
   `oceanDeepCold = new RealisticBiomeOcean(BiomeGenBase.deepOcean, …)` ——
   原版 `deepOcean` 用在冷带，`baseOceanCold` 只给雪带；
2. 海底神殿只认 `deep_ocean`（上面那段源码）。

于是 `deep_cold_ocean` **不再注册**（避免留一个永远不出现的死群系）。
最终四个气候的深海是：雪带 `rtgc:deep_ice_ocean`、冷带 `minecraft:deep_ocean`、
热带 `rtgc:deep_hot_ocean`、湿带 `rtgc:deep_wet_ocean` —— **四带各不相同**，
且海底神殿仍在（只是此后只出现在**冷带的深海**里）。

⚠ **一处有意偏离 RWG**：RWG 给雪带深海用的是 `baseOceanCold`（0.5/0.4，水面不结冰），
rtgc 用 `deep_ice_ocean`（0.0/0.1，水面结冰）—— 因为 rtgc 雪带的**浅海**是
`minecraft:frozen_ocean`，而 MC 1.12.2 没有 `frozen_deep_ocean`（1.13 才有），
这个新群系正是补上那个洞，让雪带深海与浅海一致。

**默认没有被采纳的备选**（要的话说一声）：若希望**所有**深海都能长海底神殿，
需要 mixin 去扩展 `StructureOceanMonument.SPAWN_BIOMES` —— 那是**改原版规则**，我不擅自动。

#### 第三次运行：**收口确认**

```
[RTG] 注册深海群系 rtgc:deep_ice_ocean（id=113）
[RTG] 注册深海群系 rtgc:deep_hot_ocean（id=114）
[RTG] 注册深海群系 rtgc:deep_wet_ocean（id=115）      ← 3 个（deep_cold_ocean 已不再注册）

[RTG] 具名计数：深海=4 浅海=4 …                        ← 深海回到 4 = 四个气候各一个
  SNOW 深海=rtgc:deep_ice_ocean       ✓ 新
  COLD 深海=minecraft:deep_ocean      ✓ 回到原版 ⇒ 海底神殿保住
  HOT  深海=rtgc:deep_hot_ocean       ✓ 新
  WET  深海=rtgc:deep_wet_ocean       ✓ 新
```

**四个气候的深海各不相同**，且 `minecraft:deep_ocean` 仍在世界里（只在冷带）✓

**顺带把一处容易误判的 WARN 查清了**：日志里有

```
[Client thread/WARN] || 116 | BiomeError | jeid:error_biome ||
No types have been added to Biome jeid:error_biome, types have been assigned on a best-effort guess: [PLAINS]
```

这是 **REID 自己的占位群系**（`jeid:error_biome`）在 rtgc 的
`ModCompat.doBiomeCheck()`"找不到现实主义版本"清单里 —— 预期之内、且**先前就存在**，
不需要（也不应该）给它配现实主义包装。

**顺便得到一条实证**：上一轮 4 个新群系占 113–116，这一轮 3 个占 113–115，
而 `jeid:error_biome` 的 id 相应从 117 变成 **116** —— 说明 Forge 按注册顺序分配、
**保证唯一**（不会与 REID 的占位群系撞号），我原先担心的"id 冲突/越界"没有发生。
另外 id 都在 113–116，**远低于 128**，所以 `Chunk.blockBiomeArray`（`byte[256]`）
的符号位问题也不会碰到。

### 追加（同版本）：浅海三槽位拿到自己的群系 + kelp/coral 改回 **patch**（**推翻我的"否定结论"**）

**上一轮我写过"深海缺口在 1.12.2 里补不上，跨气候借用就是正确适配"。那条结论是错的**，
错在把问题定成"MC 1.12.2 有没有更多海洋群系"；真正的问题是
"**RWG 的 `baseOcean*` 在 rtgc 有没有对应物**"。答案是**有**：
`rwg/biomes/base/BaseBiomeOcean.java` 是 RWG **自己的源码定义**
（温度/降雨/动物/禁雨逐条写在里面），把它逐行移植成 rtgc 的 `RtgOceanBiome` 属于**照抄**，不是发明。

#### 权威数据：RWG `Support.java:156-175` 的完整槽位表

| 槽位 | RWG 用的是 |
|---|---|
| `oceanShallowSnow` | `baseOceanCold`（`shallow=true`） |
| `oceanShallowCold` | `baseOceanCold`（`shallow=true`） |
| `oceanShallowKelp` | `oceanShallowCold` → **被 `SupportBOP.java:39-43` 覆盖为 BOP `kelpForest`** |
| `oceanShallowTemperate` | `oceanShallowCold` |
| `oceanShallowCoral` | `oceanShallowTemperate` → **被 `SupportBOP.java:44-48` 覆盖为 BOP `coralReef`** |
| `oceanShallowHot` | `baseOceanHot`（`shallow=true`） |
| `oceanShallowWet` | `baseOceanWet`（`shallow=true`） |
| `oceanDeepSnow` | `baseOceanCold`（`shallow=false`） |
| `oceanDeepCold` | `BiomeGenBase.deepOcean`（**原版**，rtgc 照抄 ⇒ 海底神殿保住） |
| `oceanDeepHot` | `baseOceanHot`（`shallow=false`） |
| `oceanDeepWet` | `baseOceanWet`（`shallow=false`） |

两条要点：

1. **RWG 的浅海冷/热/湿用的是它自己的海洋群系**，不是原版 `ocean`，更不是海带森林；
2. **`kelpForest` / `coralReef` 在 RWG 里根本不是槽位群系** —— 整个 `SupportBOP` 里它们只出现在
   那两行 patch 钩子的赋值上（`BOP` 的 `kelpForest`/`coralReef` 从不进 `Support.addBiome`）。

#### 改了什么

1. **新增 3 个浅海群系**（`RtgOceanBiome` + `RealisticBiomeRtgOcean(shallow=true)`，
   海底 y≈52、刷沙 6 格 —— 即 RWG `RealisticBiomeOcean.rNoise/rReplace` 的逐行移植）：
   `rtgc:shallow_cold_ocean`（`Kind.COLD`，0.5/0.4）、`rtgc:shallow_hot_ocean`（`Kind.HOT`，0.8/0.2 禁雨）、
   `rtgc:shallow_wet_ocean`（`Kind.WET`，0.9/0.9）。仍在 `preInit` 注册、仍 fail-soft。
2. **槽位优先级**：rtgc 自己的海洋（`base instanceof RtgOceanBiome`）在**循环结束后统一覆盖**槽位。
   原因是原来的"谁最后写入谁赢"取决于 `RTGAPI.RTG_BIOMES` 的插入顺序，而 rtgc 的海洋在 preInit
   就注册了（**最早**）⇒ 会被后来的原版 `minecraft:ocean` 顶掉。
3. **`kelpForest` / `coralReef` 不再占槽位**：新增 `OceanPatch KELP/CORAL` 两个 patch 钩子
   （判据是注册名 `kelpforest` / `coralreef`，依据 `SupportBOP.java:39-47`），
   并据此接上 `setOceanShallowKelp/Coral`（BOP 在场时）或退回 COLD 浅海（BOP 不在场时 = RWG 的默认值）。
   这正是上一轮**接线了但接错**的那一处：旧注释写"两个钩子都是 COLD 浅海的别名"，于是 kelp/coral
   既没做 patch、又靠"最后写入"抢走了 COLD/HOT/WET 三个浅海槽位。
4. **两个 BOP 海洋包装的地表**从陆地版 `SurfaceMountainSnow(topBlock, fillerBlock, …)`
   换成 `SurfaceOcean(config, true)`（沙、6 格），并补上与其他海洋群系一致的
   `initConfig()`（无河/无湖/水湖 0/海绵）。依据同上：RWG 的 `RealisticBiomeBOPOcean`
   **没有覆写 `rReplace`**，用的是海洋那份。
5. **两个类合并**：`RealisticBiomeRtgDeepOcean` → `RealisticBiomeRtgOcean(biome, shallow)`
   （RWG 的海洋本来就只有一个类、用 `shallow` 参数分深浅）。同时修掉 `BOPKelpForest` 地形注释里
   一处**错误的依据**：`TerrainSwampMountain(135f,300f)` 属于 bambooForest/eucalyptusForest/fungiForest，
   `kelpForest` 走的是 `RealisticBiomeBOPOcean`。
6. **日志**：`具名计数` 改为 `深海槽位=n/4 浅海槽位=n/4 海洋patch=n`（数的是**最终占位**的，不是参与竞争的）。

> 本节**改写了上面「4 个 rtgc 自己的深海群系」一节的三处**：`deep_cold_ocean` 已撤销
> （冷带深海回到原版 `deep_ocean`）、包装类由 `RealisticBiomeRtgDeepOcean` 改名并合并为
> `RealisticBiomeRtgOcean(biome, shallow)`、kelp/coral 的槽位归属更正为 **patch**。
> 那一节里"深海=4（含 deep_cold_ocean）"与"浅海=biomesoplenty:kelp_forest"的日志是**改前的快照**。

#### 追加：地表审计（独立只读核验）又抓到 **3 处海洋地表接错 + 1 处海滩接反** + 1 处工具盲区
对 `SurfaceCoastDunes` / `SurfaceOcean` / 4 个海滩与 6 个海洋群系做了一次**逐行只读核验**
（对照 RWG 原文 + 追 `paintTerrain` 的调用链）。结论：新地表**都已真实接线**
（`RealisticBiomeBase.rReplace:197-203 → surface.paintTerrain`，per-群系实例，无死接线），
`SurfaceCoastDunes` 与 RWG `RealisticBiomeCoastDunes.rReplace:66-106` **零处不一致**
（含 `cliff > 1.3f`、`k > 68`、`nextInt(3)`、砂岩在 `depth 4` 等常量）。但抓到四处旧接线
（3 个海洋 + `BOPWhiteBeach` 这个海滩）：

| # | 位置 | 原来 | 现在 | 依据 |
|---|---|---|---|---|
| C1 | `RealisticBiomeVanillaDeepOcean.initSurface` | `SurfaceMountainSnow(top, filler, true, SAND, 0.2f)` | `SurfaceOcean(config, false)`（**砾石**） | RWG `Support.java:172` `oceanDeepCold = RealisticBiomeOcean(BiomeGenBase.deepOcean, false, …)`；冷带面积最大（约 30%），此前整片深海海底是沙 |
| C2 | `RealisticBiomeVanillaFrozenOcean.initSurface` | 同上（只有顶层 1 格沙、下面 5 格砾石） | `SurfaceOcean(config, true)`（**6 格沙**） | RWG `Support.java:156-160` `oceanShallowSnow = RealisticBiomeOcean(baseOceanCold, true, …)` |
| C3 | `RealisticBiomeVanillaOcean.initSurface` | 同上 | `SurfaceOcean(config, true)` | 与其余海洋群系统一；它当前不占槽位（COLD 浅海由 `shallow_cold_ocean` 占），只在"布局未就绪"回落时用，免得回落时海底出现第二种配方 |
| C4 | `RealisticBiomeBOPWhiteBeach`（地形 **和** 地表） | `terrainCoastIce` + `SurfaceGrassland(packed_ice×3, ice)` | `terrainCoastDunes` + `SurfaceCoastDunes` | **RWG 自己的分派规则**：`ChunkManagerRealistic:590-593` 在 `continent < 24` 时 `temperature < 0.15f ? coastIce : coastDunes`。BOP 白沙滩实测 **温度 1.00 / 雨量 0.95**（热带白沙，见启动日志 `[类] HOT white_beach temp=1.00 rain=0.95`）⇒ 属于 coastDunes。此前被归到 coastIce（与 `cold_beach` 同配方）⇒ **热带地表是浮冰**；叠在"海滩群系进 CORE 池"这个适配之上，就会在内陆热带随机出现一片冰面。现在 coastIce 那一侧只剩 `RealisticBiomeVanillaColdBeach`（`cold_beach` 温度 0.05、SNOWY 标签，正是 RWG 的 `temp < 0.15f` 分支） |

**工具盲区（这才是 C1/C2 能潜伏这么久的原因）**：`tools/surface-wiring-check.ps1` 只判
"是不是共享类"，判不出"**该用 `SurfaceOcean` 却用了 `SurfaceMountainSnow`**" —— 两者都是共享类，
都刷沙/砾石，只有海底才看得出差别。本轮给它加了 **Rule 2**：

- 表里列出 6 个海洋群系（`RtgOcean` / `VanillaDeepOcean` / `VanillaOcean` / `VanillaFrozenOcean` /
  `BOPKelpForest` / `BOPCoralReef`）**必须**返回 `SurfaceOcean`，并核对第 2 个实参
  （`false` = 深海砾石、`true` = 浅海沙）；
- 顺带修掉一个**同类陷阱**：原先正则直接在**含注释的原文**里找 `new Xxx(`，
  于是 javadoc 里引用的 RWG 源码（`RealisticBiomeOcean(baseOceanCold, true, …)`）会被当成返回值。
  现在先剥掉 `/* */` 与 `//` 再匹配。同样的剥注释也补进了 `tools/surface-wiring.ps1`
  （那个 `-Apply` 会重写 `initSurface()`，会被注释误导 ⇒ 危险），并给它加了"历史一次性工具"的警示头。

实测：`shared=130 still-inner=0 no-new=1` + `RULE2 PASS (6 ocean files)`；
`docs/_surface_wiring_report.csv` 已重生成（海洋/海滩家族现在是 `SKIP`，不再谎报 `RULE:SurfaceMountainSnow`）。

**记录未做**（写进 `docs/rwg-port-gaps.md` §0.5.1）：RWG `RealisticBiomeCoastDunes.rDecorate`
的 `DecoWaterGrass`（海草/树叶/高草，`11 × strength` 次）仍未移植 —— 1.7.10 的
`Blocks.double_plant`＋metadata 与 `canBlockStay` 需要一次单独的 API 映射。

#### 追加：**自检"接错"** —— 又抓到 3 处「RWG 的地形/地表，但不是 RWG 给它的那一份」

用户问了一句"White Beach 是你加的？"。查来源（不是——初始提交就有）之后，顺手把**同一类错误**
系统性地扫了一遍：此前所有检查器都只问"是不是 RWG 的地形函数 / 是不是共享地表类"，
**没有一处**在问"这个配对是不是 RWG 给它的那一个"。补齐工具后抓到 3 处（C5–C7）：

| # | 群系 | 原来 | 现在 | RWG 依据 |
|---|---|---|---|---|
| C5 | `RealisticBiomeVanillaColdBeach`（`cold_beach`，**coastIce 槽位本体**） | `SurfaceGrassland(packed_ice×3, ice)` | 新共享地表 `SurfaceCoastIce`（逐行移植） | RWG `RealisticBiomeCoastIce` **覆写了 `rReplace`**（L58-92）：非崖壁是**雪**（+ `SnowheightCalculator`）与**砾石**，只有崖壁才是 `packed_ice/ice`；而被抄的那个 `surface` 字段在 RWG 里**再没被读过**（死代码）。⇒ 此前整片冰海岸的地面是浮冰 |
| C6 | `RealisticBiomeVanillaJungle`（`minecraft:jungle`） | `terrainHilly(230,120,50,260,68)` | `terrainHighland(0,140,68,200)` | RWG `Support.java` 里 **`BiomeGenBase.jungle` 的 active 条目**就是 `TerrainHighland(0f,140f,68f,200f)`；原注释引的是 RWG **另一个**群系 `land/RealisticBiomeJungleHills` 的家族 |
| C7 | `RealisticBiomeBOPMountainPeaks`（`biomesoplenty:mountain`） | `terrainMountain` + `SurfaceTundra` | `terrainMountainRiver` + `SurfaceMountainStone(top, filler, true, sand, 0.75f)` | RWG `SupportBOP.java:576-588`（`// MOUNTAIN`，**active** 条目）|

**新增/加强的工具**（这才是"以后不会再漏"的部分）：

1. `tools/rwg-support-map.ps1`：重新抽取 RWG `Support*.java` 时**多抽一列 `Terrain`** ——
   此前只有 `Surface`，所以"地形配对"从来没被机器比对过。
2. `tools/terrain-surface-audit.ps1`（新）：逐群系把「代码的地形函数 + 地表类」同时对照
   ① `run/logs/latest.log` 里该群系的**实测温度/降雨/标签**（抓"冰配方配热带"这类气候矛盾）、
   ② `docs/_rwg_support_map.csv` 里 RWG 的显式条目（抓"配错家族"）。产出 `docs/_terrain_surface_audit.csv`。
   ⚠ 两个坑都踩过并已处理：温度可以是**负数**（`temp=-0.50`）⇒ 正则要带 `-?`；
   地表调色板只能看**前两个实参**（top/filler），`SurfaceMountainSnow` 尾部的 `Blocks.SAND`
   只是水线附近的 beach 参数（否则雪原会被误判成"沙在雪上"）。
3. `tools/surface-wiring-check.ps1`：RULE2 从"6 个海洋"扩到 **12 个海岸/海洋**群系，
   明确要求 `SurfaceCoastIce` / `SurfaceCoastDunes` / `SurfaceOcean`（含浅海标志），
   且匹配前先剥注释（javadoc 里引用的 RWG 源码会被正则误当返回值）。

**自检结果**：RWG `Support*.java` 里有显式条目的 **41** 个群系，**地形 + 地表配对不一致 = 0**；
气候 × 地表矛盾只剩 `RealisticBiomeVanillaDesert` 那条**已记录的** `terrainPolar` 当沙丘配方（§20）。
边界（写进 §0.5.4）：其余约 90 个群系在 RWG 里没有显式条目，工具只能做气候/家族一致性检查。

#### 追加：自检**第二遍**（独立审计复核）—— 又修 7 处，并把"配对审计"做成工具

第一遍是我自己的机械核对。第二遍交给一个子代理**独立**从 RWG 源码重推「群系 → (地形, 地表)」配对
（`Support*.java` / `SupportTC.java` + `coast/*`、`ocean/*` 的内联 `rNoise`/`rReplace` +
`ChunkManagerRealistic` 的海岸/海洋分派），再逐个对照代码。两遍结论交叉印证，第二遍多抓到 7 处：

- `VanillaIcePlainsSpikes`（`mutated_ice_flats`）：`terrainMountainSpikes` → **`terrainHighland(0,140,68,200)`**
  （RWG `Support.java:127-139`，`icePlainsSpikes = icePlains.biomeID + 128`；原注释引的是 `SnowHills`，
  那是 RWG 另一个群系类，且它的地表是 `SurfaceMountainSnow` —— 所以**只拨地形**）。
- `VanillaSunflowerPlains`（`mutated_plains`）：`terrainGrasslandFlats` → **`terrainMarsh(...)`**
  （RWG `Support.java:140-152`；原注释"RWG 无 MC 平原对应物"**前提不成立**）。
- `VanillaSavanna`：地表 `SurfaceGrassland` → **`SurfaceGrasslandMix1(grass,dirt,sand,stone,cobble,13f,0.27f)`**
  —— 地形那一半早就照 RWG `RealisticBiomeSavanna:35-42` 接了，地表那一半**只接了一半**。
- `TCMagicalForest`：`terrainSmallSupport`（那是 RWG 给 **Tainted Land** 的）→
  **`terrainSwampMountain(135f,300f)`**，并在 `RWG_PLACEMENTS` 补 `magicalforest`/SNOW/SMALL_ISLAND
  （`SupportTC.java:38-50`，RWG 按**显示名**解析 Thaumcraft）。
- `TCEerie`：加注释说明它借的是 **Tainted Land** 的配方（RWG `SupportTC` 里没有 Eerie 条目）。
- `BOPCrag`：注释写"改用 canyon 配方 `(true, 35f, 160f, …)`"，代码实际传的是 **crag 注释条目的参数**
  ⇒ 改正，并写明"地形取 crag 注释条目、地表取 canyon 的 active 条目"是**有意混用**。
- `BOPVolcanicIsland`：注释写"火山渣地表（`SurfaceBOPVolcanicIsland`）保持不变"，代码返回的却是岛屿地表
  ⇒ 改正，并记录 RWG 的 `SurfaceVolcanoAsh` **未移植** + 该内层类**零调用**（§0.5.1 第 10 条）。

同时把"配对审计"工具化（这才是不会重犯的部分）：

| 工具 | 变化 |
|---|---|
| `tools/rwg-support-map.ps1` | 重新抽取时**多抽 `Terrain` 列**（此前只有 `Surface` ⇒ 地形配对从未被机器比对过） |
| `tools/terrain-surface-audit.ps1` | **新增**：气候×地表矩阵 + RWG 显式条目的（地形+地表）双向核对，产出 `docs/_terrain_surface_audit.csv` |
| `tools/surface-wiring-check.ps1` | RULE2 从 6 个海洋扩到 **12 个海岸/海洋**群系（含 `SurfaceCoastIce`），并在匹配前剥注释 |
| `tools/rwg-placement-check.ps1` | 支持 `SupportTC` 的**按显示名**解析；并修掉它自己把 `RWGBiomes.baseRiverXxx`（河道参数）误当群系名的 bug（会造出两条假"缺口"） |

**最终读数**：RWG 显式条目 **43 个，地形+地表配对不一致 = 0**；`RWG_PLACEMENTS` 校验 **PASS**
（copied=22 / rtgc-only=1 / 缺口=1）；气候×地表矛盾只剩 `VanillaDesert` 那条已记录的 `terrainPolar` 偏离（§20）。
独立审计另确认：**没有**"冰雪/浮冰配方用在暖群系"或"沙漠/台地硬编码到雪带"的实害案例，也**没有**
海洋/海岸地形配内陆群系（或反之）的实例；`SurfaceMesa` 只用于 mesa*/savanna*（HOT 1.0–2.0），
真实 `ice/packed_ice` 只出现在 `cold_beach`(0.05)。

**仍未闭合**（写进 §0.5.1 第 9/10 条）：约 88 个"最近亲"推断里仍有 7 条依据不足
（4 个 M 变体用草原森林族、`ExtremeHillsPlus` 用 jungleHills 的 `50`、`MushroomIslandShore` 用沼泽地形、
savanna 三兄弟用 mesa、`BOPSnowyForest` 不落雪、`BOPFlowerIsland` 的暖岛地形、`BOPCrag` 的有意混用），
以及一批死类 / 未接线的 RWG 地表（`SurfaceVolcanoAsh` 未移植等）。

#### 有意保留的口径差异（不是遗漏）

- **雪带浅海仍用原版 `frozen_ocean`**。RWG 那边雪带浅海是 `baseOceanCold`（温度 0.5、**不结冰**），
  而 rtgc 的槽位是**按群系温度自动归类**的 ⇒ 温度 0.5 占不住 SNOW 槽；要占住只能用 `Kind.ICE`（0.0），
  那会与雪带深海 `deep_ice_ocean` 重复。保留 `frozen_ocean`：浮冰与雪带观感一致，且它是原版群系。
- **冷带深海仍用原版 `minecraft:deep_ocean`**：RWG 也是原版（`oceanDeepCold`），
  且海底神殿 `SPAWN_BIOMES` **只认 `deep_ocean`**。

#### 文档侧同时做的清理（这就是本会话两次误报的根因）

`docs/rwg-port-gaps.md` 是**按轮次追加**的：写下的 `### 仍未做` 从不回头改写，
于是照着旧小节标题判断"还剩什么"必然误报（`gentleFraction` 装饰缩放、`rDecorateAfterIce`
都已实现却仍被当成欠账）。本轮新增 **§0.5「剩余欠账（唯一权威）」**，并把
4 处 `### 仍未做`、§14 的 D5 否定结论、§15 的 D5 结案、§17 的决策清单**逐条标注为历史快照**，
§8 的偏离表加了"现状"列。

### 追加（同版本）：**撤回"隧道按地表下移"的适配** —— 地下河与外界河恢复联通

用户问的两个问题：**"为什么我没看到地下河/天窗"** 与 **"能不能让地下河与外界河自然联通"**。
先量，再答（新增离线标定 `gradlew calibrateRiverTunnels`，跑真正的 `RtgBiomeLayout`）：

```
采样 1,002,001 列（seed=1234567891011121 步长 4 范围 ±2000）
① 河网带 river<0                56.37%
② 隧道带 tunnel>0                2.93%      交汇盘 junction>0   2.42%
③ 山地链列（离散下界）            7.69%
③∩①∩② 真会被开凿的列          0.230%      ← 隧道有，但只占 0.23%
天窗第一因子 junction>0.70（全体） 0.236%     ∧ 链列 = 0.0004%（100 万列里 4 列）
```

天窗要 `junction × mountainHost × overheadHost > 0.70`（三个 ≤1 因子相乘，另两个要 chainHost ≳ 0.5、
地表 ≳ 100）⇒ **实际一个都不会生成**。这是 RWG 原公式在这套池子下的必然结果（链只占 7.7%），
**不是 bug、也不改**。隧道本体则在，只是**被强制埋深** ⇒ 从地表永远看不见。

**根因（第二问的答案）**：RWG 的地下河**本来就是与地表水联通的** ——
水面固定 `y ≤ 62`，而链宿主门控带 ±48 格邻域影响 ⇒ 链边缘那圈里地表河已被雕到河床 ≈59（< 62），
于是挖出来的水柱**高于河床**、与外界河同面，看上去就是"河在这里沉下去／地下河在这里冒出来"。
rgtc 此前为了"洞顶至少留 10 格岩层"加了 `center = min(62, surface-10-11)`（整条隧道按地表下移），
同一列 `surface=59` ⇒ 水面被压到 38，**比河床低 21 格** ⇒ 联通被彻底切断。
另外那处"把 `min(surface-10)` 挪到 `max()` 之后"的修正，同样是在关掉 RWG 的联通机制（它的钳制
本来就会被随后的 `max(ceiling, tunnelCeiling)` 顶回去）。

**改动（选项 A：撤回适配，与 RWG 逐行一致，只动 `carveRiverTunnels` 第 ③ 步）**：

| | 旧（适配） | 新（RWG 原样） |
|---|---|---|
| 隧道中心 | `min(62, surface-21)` | **恒 `62`** |
| 洞厅基准 | `center+1` | **写死 `63`**（RWG L606-607） |
| 洞顶钳制顺序 | `max()` 之后（钳制生效） | RWG 原序（`min` 后仍被 `max` 顶回） |
| `ceiling <= floor` 跳过 | 有 | 去掉（RWG 没有；`ceiling ≥ floor` 恒成立） |

**几何对照**（最强洞厅，`tunnel=junction=1`；数值 = 洞顶到地表的岩层厚度，≤0 表示与地表连通）：

```
surface  旧(下移)      新(RWG)
   59    +10           -14      ← 河床高度：水柱露头 14 格，与外界河同面
   63    +10           -10
   70    +10            -3
   76    +10            +3      ← 地表高于 73 就重新埋起来
   83    +10           +10      ← 与旧版逐位一致（高山不受影响）
  110    +37           +37
```

⇒ 高山里隧道照样埋在深处（RWG 行为），**只有链边缘的河谷处**才露头 → "自然联通"。

**同时修掉两处过期注释**（它们很可能就是"以为没实现"的原因）：`carveRiverTunnels` 的 javadoc
还写着"rtgc 尚无山地链概念，因此改用海拔门控"（其实早就在用 `mountainChainRiverHost`）；
`RiverCaveVines` 的 javadoc 同样写着"待山地链移植完成后再补 RWG 的邻域判定"（山地链已落地，
现在的"本列真开凿过"门控是**有意选择**，不是待办）。

**复跑**：`gradlew calibrateRiverTunnels`（已加进 `build.gradle`），既打印四级门控命中率，
也打印上面那张几何对照表。

#### 追记：**A 的副作用与最终形态**（用户实测"暗河变少了 + 水面只有一层、上下都是实体方块"）

用户实机反馈：按 A 改完之后，地下暗河**变少**了；找到的那条**水面只有一层**、上下都是实体方块。
逐条回溯，是**两个独立原因**，其中一个正是我在 A 里顺手删掉的那行守卫：

1. **A 的隐藏前提**：RWG 把隧道中心写死 `62`，前提是"山地链一定很高（≥ 83）"，58–73 那段永远在
   岩石里。而 rtgc 的链里包的是 chaparral / meadow / rainforest 这类**中低山地**，地表常在 60–80
   ⇒ 写死 62 之后，那些列的洞体（58..73）整段跑到了**地面之上**：地下什么都没挖到，而
   `riverCaveCeiling` 照记、`/rtg tunnels` 照报 ⇒ 表现就是"暗河变少了"。
   实测几何（`calibrateRiverTunnels`，地表 = 洞顶 + 埋深）：

   | 地表 | 下移(无联通) | RWG(写死 62) | 最终(下移+联通) |
   |---|---|---|---|
   | 50 | 7..40 埋 +10 | 40..73 埋 **−23** | 7..50 W=50 埋 +0 ← 湖底：凿通 |
   | 59 | 16..49 埋 +10 | 40..73 埋 **−14** | 16..59 W=59 埋 +0 ← 河床：凿通 |
   | 70 | 27..60 埋 +10 | 40..73 埋 **−3** | 27..60 埋 +10 ← 与"下移"逐位相同 |
   | 90 | 40..80 埋 +10 | 40..80 埋 +10 | 40..80 埋 +10 |

2. **我删掉的那行守卫**：`ceiling <= floor` 时循环只会写出**孤零零一格水在石头里**（上下皆实心）。
   而隧道带强度是**由弱到强**的 —— 离玩家最近的那一列往往正是这个最外侧的薄边，于是
   `/rtg tunnels`（按距离排序）报出来的最近命中就是它 ⇒ 挖下去只看到一格水线。
   实测：纯隧道列里 `floor == ceiling` 占 **0.175%**、水层 <2 格占 **1.583%**。

**最终形态（rtgc 适配，共 3 处；门控/走线/宽度/层高/洞厅/天窗与 RWG 逐位一致）**：

- **整条隧道按地表下移** `center = min(62, surface - 21)` ⇒ 地下**一定**挖得到隧道、一定被记录
  （不再有"变少"）；地表 ≥ 83 时中心回到 RWG 的 62。
- **第 ④ 步：与地表水的联通口** —— 地表 ≤ 62（河床基准 59 / 湖底）时把洞顶凿到地表并**灌满水**
  ⇒ 河床的水与暗河是同一片水，看上去就是"河在这里沉进地下 / 暗河在这里冒出来"。这一条替代了
  RWG"靠钳制被顶回"的隐式行为（那个行为在低地形处会把整段隧道抬到地面之上）。
- **两条守卫**：`ceiling <= floor` 与 `floor >= surface` 直接跳过，**且不写 `riverCaveCeiling`**
  ⇒ 被记录的隧道 = 真的挖出了东西的隧道。

节标题里的"撤回适配、与 RWG 逐行一致"请以本节为准：**门控与几何常量照抄，摆放方式按上表适配**。

#### 追加（同版本）：把暗河从"只有山地链"扩展到**普通山地**（用户要求）

原来的门控是 RWG 原样的 `mountainChainRiverHost > 0.10` —— 也就是**只有山地链**才有暗河。
而山地链只出现在气候交界（实测 7.7% 的列，且 8 个「气候×方向」组合里只有 3 个填得满），
于是**普通的山（极端山丘、BOP alps / glacier / mountain / crag…）一滴暗河都没有**。

**关键坑（这是 F-39 当年死掉的原因，别再踩）**：想用"这座山有多高"开门控，必须用**干高度**
（河道雕刻**之前**的地形）。隧道带完全落在河网带内部，那里的地表已经被河流压到河床 ≈59 ——
拿**已雕刻**的地表去比 `76` **永远不成立**，整段代码就是死路径（当年 profiler 实测
`RIVER_TUNNELS` 仅 0.01 ms/区块，与之一致）。

**改动**：

| | 内容 |
|---|---|
| `ChunkLandscape` | 新增 `dryHeight[256]`：河道雕刻前的地形高度（两条地形路径都写：`getNewerNoise` 与 `getNewerNoiseSingleBiome`） |
| 门控 | `max(链宿主, smoothstep((干高度 − 76) / 24)) > 0.10` ⇒ **链内行为完全不变**（取大），链外凡是"山"就有暗河；因 0.10 这道门，有效阈值是**干高度 ≳ 81** |
| 洞厅/天窗 | `overheadHost` 仍按**已雕刻地表**算 ⇒ 普通山地的暗河**基本不开天窗**（地表是河谷 ≈59），只有地表真的高时才开；`mountainHost` 随门控上升，所以在高山脊下仍可能出现洞厅 |
| 观测 | `/rtg probe` 的 `[tunnel]` 行新增 **`山体门控`** 与 **`干高度`**；`/rtg tunnels [半径]` 照旧列出被开凿的列 |

**为什么把它记为"偏离"**：RWG 的暗河是**山地链专有**地貌（它的链是那 6 个 `RealisticBiomeMountainChain`
实例）。这条扩展是用户明确要求的行为增强，不是照抄；`mountainChainWeight` / fade / 链内不雕河的
逻辑一律未动。

**未在游戏内验证**（离线标定测不到"地形高度分布"）：需要一次实机抽样 ——
站到普通山地（极端山丘 / alps 等）的河谷里 `/rtg probe`，应看到 `山体门控 ≥ 0.10`、`干高度 ≳ 81`；
挖下去应是水道 + 气道，且河床处与水同面。

#### 追加（同版本）：**移除 `/rtg tunnels`** ＋ 把联通口改成真正的**水面**接通

用户三条要求，逐条落地：

1. **移除 `/rtg tunnels`**（含它专用的 `ChunkGeneratorRTG.cachedLandscape()` 只读访问器，一并删掉不留死码）。
   现在 `/rtg` 只剩两个子命令：`/rtg whereami`、`/rtg probe`（后者仍打印
   `链权重 / 链宿主 / 山体门控 / 干高度 / 洞顶`）。定位暗河改回"顺着山里的河找"。
2. **水面接通**：此前联通口把水灌到**地表**（河床 ≈59），而外面的水停在 **62** ⇒ 洞里的水面比外面
   低 3 格，看上去不连通。现在改成灌到 **`WaterLevel.waterSurfaceTop()`（默认 62）**，
   并把洞体一路凿到水面那一层 ⇒ **洞里与外面的水是同一个水面平面**，顺着河游过去就能进洞。
   触发条件仍是"该列地表已降到水面及以下"（= 这里本来就是水：河床/湖底/海底），
   地表高于水面的列一根手指都不动（只留气道、无悬空水）。
3. **普通山的暗河**：上一节已实现（`max(链宿主, smoothstep((干高度−76)/24))`），本节未改动；
   这条扩展让**极端山丘 / alps / glacier / mountain** 这些非链山地也有暗河。

顺带把 `docs/rwg-port-gaps.md` §0.5.4 里"用 `/rtg tunnels` 定位"的说法同步成 `/rtg probe`。

#### 追加（同版本）：**提高暗河密度**（用户反馈"很多山里都没暗河"）

两个旋钮都卡得太紧，一起放开（都在"判定链"上，见 `docs/rwg-port-gaps.md`）：

| 旋钮 | 原值（RWG） | 现值 | 效果 |
|---|---|---|---|
| **隧道带宽度**（`RtgBiomeLayout.TUNNEL_BAND_WIDTH`） | `9/1250`（9 格） | **`25/1250`（25 格）** | 实测落在隧道带内的列 **2.93% → 8.06%**；"链 ∧ 河网 ∧ (隧道\|交汇)"的联合命中 **0.230% → 0.611%**（×2.66） |
| **山体阈值**（`MOUNTAIN_RIVER_MIN_DRY_HEIGHT`，新常量） | 复用 `TUNNEL_MIN_SURFACE = 76`（有效 ≈81） | **68**（有效 ≈**73**） | 山腰（70–80）不再被排除；`TUNNEL_MIN_SURFACE = 76` 保留给**洞厅/天窗**的"上方够不够厚"，两者分开 |

另外把"算不算山"抽成 `ChunkGeneratorRTG.dryHeightHost(landscape, k)`，并在同区块内取
**3×3 个 ±8 格采样点的最大干高度**：山脊侧翼的小凹陷不再把隧道断成几截。
**开凿与 `/rtg probe` 共用这一个函数**（避免诊断与代码漂移 —— 这个项目吃过这个亏）。

复跑 `gradlew calibrateRiverTunnels` 可看到新的分布：

```
① 河网带 river<0                56.37%   （不变）
② 隧道带 tunnel>0                8.06%   （原 2.93%）
   交汇盘 junction>0              2.42%   （不变）
   合并 (tunnel|junction)>0      8.92%   （原 4.70%）
③ 山地链列（离散下界）            7.69%   （不变）
③∩①∩② 链 ∧ 河网 ∧ (隧道|交汇) 0.611%  （原 0.230%）
退化列（floor==ceiling）         0.178%   （不变，仍被守卫挡掉）
```

⚠ 地形那一半（"干高度 ≥73 的山有多少"）**离线测不到**：仍需一次实机抽样 ——
`/rtg probe` 现在会打印 `山体门控` 与 `干高度`，站到山腰上应能看到 `干高度 ≈70–80`、
`山体门控 ≥ 0.10`。若还是偏少，下一个旋钮就是 `MOUNTAIN_RIVER_MIN_DRY_HEIGHT`（再降到 64）。

#### 追加（同版本）：**修掉"刷日志"** + 联通口收窄（河床不再被挖成深沟）

用户反馈三条，两条是 bug：

1. **游戏在刷日志 —— 是我埋的单位 bug。** `[RTG-DECOPROF]`（装饰尖峰归因）用
   `System.nanoTime() - tPopDeco` 算耗时，而 `ChunkGenerationProfiler.start()` 在**计时关闭**时
   返回 **0** ⇒ 算出来是**绝对值**（用户日志里写着 `28420861ms`，正是 `nanoTime` 的绝对值），
   永远 > 200ms 阈值 ⇒ **每个区块刷一行**。实测用户 `latest.log` **3560 行里 3287 行**是它
   （同一行里 `decoBiomes=[] rtgDecos=0.00ms` 也印证了累加那半边本来就被 `isEnabled()` 挡住）。
   修法：条件加 `ChunkGenerationProfiler.isEnabled()` 守卫（注释里写清了为什么必须有）。
2. **"外部河河床两边变成了悬崖"** —— 联通口（第 ④ 步）原来对**整条 25 格宽的隧道带**生效，
   等于把河床挖成一条两边是垂直岩壁的深沟。新增 `CONNECT_MIN_TUNNEL_STRENGTH = 0.7f`：
   只有带子中心线附近（约 1/10 带宽）才开口 ⇒ 河床上一处**几格宽的落水洞**，水面依旧连续，
   岩壁不再横贯整条河。
3. **"水面高度一样，但只是水下接通"** —— 这是几何本身的结果，不是 bug：联通口的做法是
   "把洞体凿到**水面那一层**（62）并灌水"，所以从外面看是**河面上一个水洞**，进水口在水面
   以下 ⇒ 要潜下去才进得去；暗河的气道（63+）仍在山体岩石之下。想要"水面之上也有洞口"
   （山脚洞口、能站着看暗河水面）需要让隧道在接近地表处**抬升**，那是 RWG 没有的机制
   （发明），待定。

#### 追记（同版本）：**撤销"按地表整体下移"，回到 RWG 原样** —— 划船进山本来就该有

用户一句"我想从水面进去有那么难吗？在河里划船，知道山里有个暗河，不就该直接划进去吗"，
把问题点破了：**RWG 本来就是这么设计的**，是我那次"下移"把三件事一起毁掉的。

RWG 的关键只有一条：**水面恒在水面高度 62**（`blockY <= 62 ? water : air`），洞体也以 62 为中心。
于是沿同一条河网：

| 地表 | 现行（= RWG） | 形态 |
|---|---|---|
| 50–70 | 洞体 40..73，**水面 62**，洞顶高出地表 3–23 格 | **洞顶冒出地表 ⇒ 天然河口/峡谷 —— 从河面直接划船进去** |
| 73–76 | 埋深 0..+3 | 屋顶很薄，接近河口 |
| ≥83 | 埋深 +10 以上 | 带顶棚的暗河（地道） |

而"按地表下移"版（已撤销）在同一张表里是：水面被压到 29–60（**接不上外面的河**）、
洞底被挖到 y≈34（**河床被掏成两边深沟**）、洞顶恒在地表下 10 格（**永远进不去，只能潜水**）。

**本次改动**：

- `center` 从 `min(62, surface-21)` 改回**写死 62**；洞厅基准回到 RWG 的 **63**；
  水面填充回到 RWG 的 `y <= 62 ? water : air`（**不要再把它改成跟着洞心走**）。
- **删掉我自造的"第 ④ 步联通口"与 `CONNECT_MIN_TUNNEL_STRENGTH`** —— RWG 的机制已经覆盖它，
  不需要额外发明。
- **保留两条守卫**（`ceiling <= floor` 一格水缝、`floor >= surface` 悬空水）—— 这两条只挡垃圾，
  与"能不能划船进去"无关。
- 保留用户要求的三项：门控扩展到普通山（干高度门控）、隧道带 `9→25`、以及本次的 RWG 几何。

`gradlew calibrateRiverTunnels` 的输出表格已按三列改写为
「下移(已撤销) / 现行 = RWG 原样 / 形态」，可随时复核。

#### 追加（同版本）：隧道断面在门控边缘**渐隐** —— 山外河与普通河的接壤不再有台阶

用户反馈（其余都满意）：**"暗河衍生出来的山外河与普通河的接壤不太自然，宽度不同，对河床的处理也不同"**。
两个差异的来源是我这边的"硬开挖"与 RWG 的"平滑混合"不同一族：

| | 地表河（RWG `calculateRiver`） | 隧道（我按用户要求加宽到 25 格） |
|---|---|---|
| 手法 | **高度混合**：`biomeHeight*(c+1) + 噪声河床*(-c)`，宽度 `50/1300` | **方块开挖**：`y ≤ 62 水 / 以上空气`，宽度 `25/1250`，直壁 |
| 河床 | 噪声底 `59 ± 3.5` | 平底（`center − round(√t·4)` = 58–62） |
| 边缘 | 平滑收窄 | 门控一刀切 ⇒ **25 格宽、十几格深的钝头** |

**修法**（不动地表河）：把断面按门控权重**渐隐** —— `taper = mountainHost`
（门控边缘 0、内部 1），`tunnelCurve = √tunnel × taper`：

- 尾部逐格变浅，直至"不足一格"被守卫跳过 ⇒ 硬开挖平滑地**交回给地表河的平滑河床**；
- 水面仍恒在 62（RWG），所以水不会断；
- **链内部（host ≥ 0.5）taper = 1 ⇒ 与 RWG 逐位一致**，只有最外面那圈过渡带变了。

#### 追加（同版本）：侧切收一点点 —— 洞顶起伏 `11 → 8`

用户反馈（其余满意）：**"很像是暗河的河流会侧切山体，和水面齐平的山体被侵蚀进去一部分，虽然挺符合现实"**，
要求"收一点点"。

那个凹槽的高度**就是洞顶起伏**：洞顶 = `62 + round(√tunnel × 洞顶起伏)`，所以

| | RWG | 现在 | 说明 |
|---|---|---|---|
| 普通隧道段 水面以上掏空 | ≤ **11** 格（洞顶最高 73） | ≤ **8** 格（洞顶最高 70） | 咬痕 −27%；划船头顶仍有 6 格以上 |
| 河口出现的上限（地表高度） | ≤73 | ≤70 | 敞口的地方略少一点 |
| 洞厅段 | `surface − 10` | **未动**（RWG 原值） | 汇流处的深咬痕保留（那是"暗河出口盆地"的观感） |

`TUNNEL_FLOOR_DROP = 4` 未动 ⇒ 河床依旧是水面下 4–5 格，不会重新变成深沟。
`gradlew calibrateRiverTunnels` 的对照表已同步（洞顶 8）。

### 追加（同版本）：地下暗河逻辑**封装成 `UndergroundRiver`**（纯搬移，无行为变更）

用户要求："封装一下地下暗河相关吧，别到时候写乱了。"

暗河在 C13–C18 六轮修补里改过门控、断面、守卫、天窗，代码却一直摊在 `ChunkGeneratorRTG` 里。
现在**全部收进 `rtg/world/gen/UndergroundRiver.java`**：

| 内容 | 从 | 到 |
|---|---|---|
| 常量（洞心 62 / 洞顶起伏 8 / 洞底下探 4 / 洞厅留厚 10 / 地表基准 76 / 门控下限 0.10 / 干高度 68 / 3×3 步长 8） | `ChunkGeneratorRTG` 的 `TUNNEL_*`、`MOUNTAIN_RIVER_MIN_DRY_HEIGHT`、`DRY_HEIGHT_DILATION_STEP`、`MOUNTAIN_CHAIN_RIVER_HOST_MIN` | `UndergroundRiver`（`public static final`，每个常量注明 RWG 原值 + 哪几处是 rtgc 微调） |
| 四级判定链 + 断面几何 + 两条守卫 + 天窗 + 灌水 | `ChunkGeneratorRTG.carveRiverTunnels` | `UndergroundRiver.carve(...)` |
| `dryHeightHost(...)` / `smoothstep(...)` | `ChunkGeneratorRTG` | 同上（`smoothstep` 全工程只被暗河用，随之下移） |
| 山体门控读数 | `probeTunnelColumn` 自己调 `dryHeightHost` | `UndergroundRiver.mountainHostAt(...)` —— **开凿与 `/rtg probe` 共用同一函数**（消掉"读数与实现漂移"的隐患） |

**顺带清掉的死重量**：生成器字段 `riverStrengths`（改为 `carve` 内的局部数组）、
`carveRiverTunnels` 里从未被读的 `mpos.setPos(...)`。

**"无行为变更"的证据**（不是嘴上说）：迁移是逐行搬移，只有 4 处等价改写
（`air` 局部变量 → `AIR` 常量、`center` 局部变量 → `CENTER_Y`、字面量 `63` → `CHAMBER_BASE_Y`、
`0.70f/24f/0.40f/0.15f` → 具名常量）。搬移后复跑全套：

```
gradlew build                      -> BUILD SUCCESSFUL
gradlew calibrateRiverTunnels      -> 河网带 56.373% / 隧道带 8.0586% / 交汇盘 2.4162%
                                      合并 8.9170% / 链列 7.686% / 联合命中 0.61118%
                                      天窗 0.00040% / 退化 0.376%,1.547%   （与封装前逐个数字相同）
tools/surface-wiring-check.ps1     -> RULE2 PASS (12)
tools/terrain-wiring-check.ps1     -> 131/131
tools/terrain-surface-audit.ps1    -> matched=43, mismatched=0
tools/rwg-placement-check.ps1      -> PASS (copied=22, rtgc-only=1, gaps=1 fungiforest)
tools/reachability.ps1             -> 仍只有 terrainDunes DEAD
```

**规约**：以后改暗河的密度/几何/守卫**只进 `UndergroundRiver.java`**；
`ChunkGeneratorRTG` 那边只剩一行调用（外加"必须在地表替换之后"的注释 —— 先开凿会把隧道底刷成草/沙）。
详见 `docs/rwg-port-gaps.md` §0.5.5。

### 追加（同版本）：洞穴藤蔓改成**随机长藤，最长垂到水面**

用户要求："让藤蔓长一点，最好最长能到水面，当然是随机长度。"

原来 `RiverCaveVines` 写死 `MAX_VINE_LENGTH = 4`（1–4 格）。现在长度由**可用空间**决定：

```
可用空间 available = startY − MIN_Y + 1     // startY = 洞顶下方第一格空气，MIN_Y = 63
长度       length  = 1 + rand.nextInt(available)   // 均匀随机
```

`MIN_Y = 63` 不是随手取的数：开凿阶段把 `y <= 62` 填成水、以上填空气（`UndergroundRiver.CENTER_Y = 62`），
所以 **y=63 就是紧贴水面的那一格** —— 最长的那一株正好触到水面。

| 段 | 洞顶高度 | 可用空间 | 效果 |
|---|---|---|---|
| 隧道段 | `62 + round(√tunnel × 8)` ≤ **70** | 1–8 格 | 长度均值 ≈4.5（原来是固定 1–4）；约 **1/8** 的藤触到水面 |
| 洞厅段 | 可到 80–100 | 十几到三十几格 | 偶尔出现垂到水面的**长藤帘** |

中途撞到非空气方块（洞底、石柱、别的藤）就停下 ⇒ 实际"最长"是**到水面或洞底，先到者为准**；
干洞（洞底高于水面）里的藤就停在自己那层的洞底上方。

**两个已核对的前提**（免得以后误判成 bug）：
1. 这些藤**不会自己变长**。1.12.2 原版 `BlockVine.updateTick` 的"向下长"那一支要求
   "随机朝向为 DOWN **且** 该藤还保留至少一个**水平**面"（那是给**墙面**藤用的）；
   我们挂的是纯洞顶藤（只有 `UP=true`）⇒ 永远不满足。
2. 长藤链**不会掉**。`neighborChanged → recheckGrownSides` 只重查水平面，且仅当
   `getNumGrownFaces(state) == 0` 才掉落，而 `UP` 被算作"已长出的面" ⇒ 链上每一格都成立。

RWG 那边**没有可抄的长度**：它只算到"洞顶那一格空气"，实际摆放交给 EFR 的 `WorldGenCaveVines`
（`Et-Futurum-Requiem`，1.7.10 专属）。这条已作为**有意差异**记入 `docs/rwg-port-gaps.md` §0.5.2。
`gradlew build` 通过。

### 追加（同版本）：**火山与地标全部写回**（RWG 逐行移植）

用户要求："开始写回火山的全部内容" ＋ "全做完"。

此前火山与地标是**唯一的照抄豁免**（用户先前明确要求删除，`docs/rwg-port-gaps.md` §0 记着，
并靠 `RwgLayoutConfig.averageLandmarksPerTypeAndContinent = 0` 一个数把整支关掉）。
现在整条链路按 RWG 逐行移植并接线，§0 的豁免作废。

**新文件**

| 文件 | RWG 出处 |
|---|---|
| `rtg/world/biome/realistic/land/RealisticBiomeIslandVolcano.java` | `rwg/biomes/realistic/ocean/RealisticBiomeIslandVolcano.java`（锥体/岩缘/熔岩口/岩浆房/山顶门控） |
| `rtg/api/world/surface/SurfaceVolcanoAsh.java` | `rwg/surface/SurfaceVolcanoAsh.java` |
| `rtg/world/gen/MapVolcano.java` | `rwg/map/MapVolcano.java`（热带岛小火山） |
| `rtg/world/gen/LavaCaveLandmark.java` | `rwg/map/LavaCaveLandmark.java`（熔岩洞本体/通风口锥体/冒烟草） |
| （不移植）`LandmarkDecorations` | **整类删除**（用户裁定）；RWG 的 `rwg/support/LandmarkDecorations.java` 四类装饰全部依赖 1.12.2 不存在的模组 |
| `src/preview/java/rtg/api/util/noise/VolcanoPlacementCalibration.java` | 新增标定工具（`gradlew calibrateVolcanoPlacement`） |

**改动**

- `RtgBiomeLayout`：新增火山/熔岩洞查询族（`getVolcanoCoordinates` / `getVolcanoVicinityCoordinates` /
  `getLavaCaveCoordinates` / `getLavaCaveCenterCoordinates` / `canGenerateVolcanoAt`（含"火山附近有河就不生成"）/
  `getVolcanoBaseHeight` / `getVolcanoUnderlyingHeight` / `getVolcanoUnderlyingBiome` / `isBorderlessAt` /
  `getNoiseWithRiverOceanAt`）—— 对应 `ChunkManagerRealistic:402-476/607-655/859-861/945-963`。
- `ChunkGeneratorRTG`：① 高度叠加（火山锥**替换**该列高度、写 `volcanoSurfaceDepth`、
  火山渣/熔岩口判定，位置在河道雕刻之前）；② `replaceBiomeBlocks` 的火山地表三分支；
  ③ 岩浆房（**结构之后**，黑曜石外壳要封住洞穴/结构挖开的口子）；④ 熔岩洞 `generate` /
  `surfaceHeight` / `decorateSurface`；⑤ 地标装饰；⑥ mapgen 调度（`generateTerrain` 之后、
  地表替换之前，传 `landscape.noise` **本身** —— `MapVolcano` 会就地抬高锥体侧翼）。
- `IRealisticBiome` / `RealisticBiomeBase`：新增 `rMapGen` / `generateMapGen` 钩子链（RWG `:186-201`，
  k=5 的 11×11 候选中心循环）；`RealisticBiomeBOPTropicalIsland` 覆写 `rMapGen` 调 `MapVolcano`。
- `RtgLayoutAccess` / `ChunkGeneratorRTG`：把 `RTGWorld` 注入布局（火山的"基座/底层高度"要算 `rNoise`；
  RWG 的 `ChunkManagerRealistic` 自带噪声源，rtgc 的布局只按种子建立）。
- `RwgLayoutConfig`：`largeIslandVolcanoChance` 0 → **0.15**、`averageLandmarksPerTypeAndContinent` 0 → **0.25**（RWG 原值）。
- `BiomeInit`：BOP `volcanic_island` 改由 `RealisticBiomeIslandVolcano` 包装（= RWG `SupportBOP:49-53`）；
  **删除** `RealisticBiomeBOPVolcanicIsland` —— RWG 对同一 MC 群系只有一个包装，两个会让 `RTGAPI` 的 Map 静默顶掉一个。
- `RtgBiomeCategorizer`：火山群系**不进任何池**（RWG 的 `Support.volcanoIsland` 从不 `addBiome`）。
  ⚠ 不修的话 BOP 的 `volcanic_island` 会因名字含 "island" 进 ISLAND 池 ⇒ 普通岛屿长出 baseHeight=61 的火山锥，
  且 `getVolcanoBaseHeight`/`getVolcanoUnderlyingBiome` 的岛屿分支会取到火山自身（污染锥体基座）。

**四处偏离**（1.12.2 无对应物 / 用户裁定，均已在代码注释与 §0 记录）

1. **`LandmarkDecorations` 整类不移植**（用户裁定，原话："**就是彻底删掉，什么都没有，别判断模组行不行**"）：
   RWG 的这个类产生四类可选装饰（深板岩柱 / LootGames 拼图大师 / 暮色门 / Natura 发光蘑菇），
   全部依赖 1.12.2 不存在的模组。用户先裁定"深板岩 → 黑曜石"，继而要"凑不齐就什么都别生成"，
   最后明确"彻底删掉、**别判断模组行不行**" ⇒ **类已删除**，`ChunkGeneratorRTG` 里连同
   `landmarkDecorations` 字段、初始化与调用点一并移除，**不做任何 `Loader.isModLoaded` 判断**。
   `LavaCaveLandmark` 不受影响（零可选模组依赖，冒烟草用 BOP 的 `biomesoplenty:grass`）。
2. **熔岩洞"标记群系"跳过**：RWG 用 BOP 的 `phantasmagoric_inferno` 标开口列（`markLavaCaveOpeningBiome`），
   而 BOP 7.0.1.2445 **既无该群系类也无该 lang 名**（已核对 jar）⇒ 不发明群系。
3. **`isBorderlessAt` 桶宽**：RWG 写死 `float[256]`；rtgc 用 `RtgRealisticIndex.idFor` / `biomeIdBound()`
   （REID 下 MC 编号可 >256）。不能用 `baseBiomeId()`（山地链与其备份群系共用 MC 编号，会折叠成一个桶）。
4. **mapgen 去重**：RWG 用独立 `mapGenBiomes[256]` 标记数组；rtgc 照同一判据（中心列 `smallRender[312]`）
   读、用本区块 `activeBiomeIds` 去重 —— **不**清零 `smallRender`（会破坏金字塔）。

**验证**

```
gradlew build -x test              BUILD SUCCESSFUL
tools/terrain-wiring-check.ps1     131/131（火山群系占位地形仍走 terrainIslandTropical ⇒ 无需白名单）
tools/terrain-surface-audit.ps1    matched=43, mismatched=0
tools/rwg-placement-check.ps1      PASS（copied=22, rtgc-only=1, gaps=1 fungiforest）
tools/reachability.ps1             仍只有 terrainDunes DEAD（未新增死码）
gradlew calibrateVolcanoPlacement  种子 123456789 / 20000² 窗口：火山中心 4 座（最近邻 p50 ≈ 7925 格）、
                                   火山锥列 0.048%、熔岩洞中心 6 个（≈ 8165 格）
```

**按用户裁定最终定下的三条**

- 熔岩洞"**标记群系**"：1.12.2 无对应物 ⇒ **没有就没有**，不发明群系（`markLavaCaveOpeningBiome` 整块跳过）。
- `LandmarkDecorations` 的**四类可选装饰**（深板岩柱 / LootGames 拼图大师 / 暮色门 / Natura 发光蘑菇）：
  用户最终裁定"**就是彻底删掉，什么都没有，别判断模组行不行**" ⇒ 类已删除、调用点与字段一并移除，
  **不做任何模组判断**（既不生成，也不留半成品）。
- `LavaCaveLandmark`（熔岩洞本体 + 冒烟草 + 通风口锥体）与上述无关，正常生成。


## [1.0.32]

**读了 `run/logs/latest.log`（12:55 那次，新世界）。服务端侧全部正确；同时修掉两处
让排查无法进行的东西。**

### 从日志得到的确证（第一条把此前一个假设**推翻**了）

| 日志证据 | 结论 |
|---|---|
| `[RTG] 建立布局：side=CLIENT thread=Server thread seed=6319311872485634088 rtgBiomes=169`，**只有一条** | 布局只建立了一次，**不存在客户端/服务端两套布局**（此前担心的 client seed=0 未发生） |
| **没有** `⚠ 布局未命中，F3/群系查询回落到…GenLayer` | **此前"F3 回落到 BOP GenLayer"的假设被日志推翻** —— 回落路径根本没跑 |
| **没有** `⚠ 布局尚未建立就收到群系查询`、也没有 `布局在 (…) 没有可用群系` | 池非空、null 路径未触发 |
| `[RTG-BIOME] chunk(0,0) 期望=biomesoplenty:temperate_rainforest(88) int数组=88 字节=88 读回=88 reid=true` | **服务端写入的区块群系数组是对的**，且服务端 `chunk.getBiome` 读回来的也是对的 |
| `海岸群系：coastIce=minecraft:cold_beach coastDunes=minecraft:beaches` | 1.0.28 的显式海岸指定生效（`minecraft:beaches` 是 1.12.2 的真实注册名，见 `Biome.java` `registerBiome(16, "beaches", …)`，不是笔误） |
| `WET LITTORAL 成员：biomesoplenty:white_beach`（**不含** mushroom_island_shore） | 1.0.28 的 `specialFor` 提前生效 |

⇒ 服务端已被日志证明无误，**剩下的只可能在客户端**：F3 读的是客户端手上的区块群系数组，
而它来自网络包。服务端的 `[RTG-BIOME]` 证明不了客户端收到的是对的。

### Fixed

- **`RTG.VERSION` 此前硬编码为 `"1.0.0"`** ⇒ 无论装的是哪一版，日志里都写
  `rtgc@1.0.0`，排查时**无法从日志确认玩家跑的哪个构建**（本次就因此无法判断
  12:55 那次是否已含 1.0.31 的地表接线）。现改为构建期生成的
  `rtg.rtgc.Tags.VERSION`（来源 `gradle.properties` 的 `mod_version`）。

### Added

- **客户端 F3 探针**（`EventHandlerClient.onClientTick`，每 40 tick 一条、最多 10 条）：

  ```
  [RTG-CLIENT] BlockPos{x,y,z} F3读到的(客户端区块数组)=<群系> provider(布局)=<群系> 一致=<bool>
  ```

  这是**唯一**能回答"F3 到底读到了什么"的位置：F3 = `world.getBiome(pos)` = 客户端区块数组。
  判读：
  - 两者**不一致** ⇒ 客户端手上的区块数组不是服务端写的那份（网络 / REID 路径问题），
    下一步就查 REID 的 `SPacketChunkData`/`Chunk` 补丁；
  - 两者**一致但与地表不符** ⇒ 与数组无关，回到布局或地表。

## [1.0.31]

**接线上线：125 个群系的地表改用 RWG 的共用地表类。** 1.0.30 移植的 18 个 `Surface*`
此前没有被引用，本版把它们真正接上 —— 各群系的崖壁阈值、雪线、粘土层理与噪声混合
**这才第一次对齐 RWG**（此前 129 个群系各自是手写的近似实现）。

### Changed

- **125/129 个群系的地表改为返回 RWG 共用地表类**（`tools/surface-wiring-check.ps1` 实测
  `shared=125 still-inner=4`）。做法是替换每个群系 `initSurface()` 的返回值，
  共用地表类的 top/filler 仍取该群系自己的方块，其余阈值/噪声照 RWG 实参。

  接线来源分两类，都在 `docs/` 里有可复核的落地文件：

  | 来源 | 条数 | 依据 |
  |---|---|---|
  | `SUPPORT:` | 42 | RWG `support/Support*.java` 里 `addBiome(new RealisticBiomeSupport(Biome, terrain, surface))` 的**直接配对**（BOP/EBXL/TC/CC）。落盘于 `docs/_rwg_support_map.csv` |
  | `RULE:` | 83 | 原版群系在 RWG 里没有直接对应物（RWG 用的是它自己的自定义群系），按**地形族 → RWG 地表**规则定。规则写在 `tools/surface-wiring.ps1` 的 `$rules` 表里，每条都写明取自哪个 RWG 群系 |

- **规则表的每一条都有 RWG 出处**，例如：
  - `terrainMountainRiver`（18 个群系）→ `SurfaceMountainSnow(grass, dirt, true, sand, 0.2f)`：
    RWG 的 3 个 `TerrainMountainRiver` 使用者（`WoodMountains`/`SnowRivers`/`TaigaHills`）
    **共用同一组地表实参**，故无歧义。
  - `terrainHilly(230,120,0)` → `SurfaceMountainStone(grass, dirt, false, null, 0, 1.5, 60, 65, 1.5)`
    （RWG `WoodHills`）；`terrainHilly(230,120,50)` → 同式但 `min = 1f`（RWG `JungleHills`）；
    `terrainHilly(150,50,0)` → `SurfaceDesertMountain`（RWG `Desert`）。
  - `terrainMountainSpikes` → `SurfaceMountainSnow(grass, dirt, false, null, 0.2f)`（RWG `SnowHills`）。
  - `terrainMesa` → `SurfaceMesa(sand, sand, (byte) 1)`（RWG `Mesa`）。
  - `terrainMarsh`/`terrainSwampRiver`/`terrainSmallSupport`/`terrainSmallIsland`/`terrainGrasslandHills`
    → `SurfaceGrassland(grass, dirt, stone, cobble)`：这几族在 RWG 里压倒性地配 `SurfaceGrassland`。
  - `terrainCoastIce` → `SurfaceGrassland(packed_ice, packed_ice, packed_ice, ice)`（RWG `CoastIce`）。

### Fixed（接线过程中抓到并修掉的两处）

- **RWG 的 `SupportBOP.java` 里有被 `/* */` 注释掉的注册**。第一次提取把它们当成了真实映射，
  于是 `grassland`/`steppe`/`wasteland` 拿到了一份**已作废**的实参。
  提取器现在**先去注释再提取**（`tools/rwg-support-map.ps1`），行数由 96 降到 **92**（去掉 4 条注释掉的）。
- **接线脚本的两个自身缺陷**（都已修，值得记）：
  1. 替换 `initSurface()` 的正则只匹配到**第一个** `}`，而很多群系在这个方法里有
     `if (DISABLE_RTG_SURFACES) { return …; }` 的提前返回 ⇒ 只替换了一半、留下游离语句。
     改为按**括号配平**重建整个方法（`tools/fix-surface-init.ps1`），并吞掉尾随残留。
  2. 只改方法体没加 `import` ⇒ 125 个文件报"找不到符号: 类 SurfaceXxx"。
     生成器现在会按需要补 `Surface*` / `Blocks` / `BlockUtil` / `EnumDyeColor` 的 import。

### 新增工具（可复核）

- `tools/rwg-support-map.ps1` —— 从 RWG `support/Support*.java` 提取「MC 群系 → 地表」表（**去注释**）。
  参考路径通过 `-RwgSupport` 参数传入：脚本无 BOM，内嵌 CJK 路径字面量会被按 ANSI 读坏。
- `tools/surface-wiring.ps1` —— 按来源表 + 规则表改写各群系的 `initSurface()`（默认 dry-run，`-Apply` 才落盘）。
- `tools/fix-surface-init.ps1` —— 括号配平重建 `initSurface()`。
- `tools/surface-wiring-check.ps1` —— 核对每个群系是否真的返回共用地表类。

### 未做（记录在案）

- **4 个海滩族群系仍用原实现**：`VanillaBeach` / `VanillaStoneBeach` / `BOPGravelBeach` /
  `BOPOriginBeach`。RWG 的 `RealisticBiomeCoastDunes` 是一个**独立群系类**、没有共享 `Surface*`
  对应物（它的 `rDecorate` 与 `rNoise` 是自带的），故不强行套用共享类。
- **129 个旧的逐群系内部地表类现在成了死代码**（不再被 `initSurface()` 引用）。
  本轮**没有删除**它们 —— 删除是纯清理、风险独立，留待下一轮一次做完，避免与接线混在一起难定位。
- 规则表里属于「推断」的条目（原版群系在 RWG 无对应物，例如 `VanillaPlains`）已在
  `tools/surface-wiring.ps1` 与 `docs/rwg-port-gaps.md` 里注明依据，**不等于**已验证与 RWG 一致。

## [1.0.30]

**开始补最大的一笔欠账：RWG 的 `Surface*` 地表类。本轮把 18 个类逐行移植完，
并抽出了接线用的映射表。⚠ 这批类目前还没有被任何群系引用 —— 本版不改变任何生成结果。**

### Added（移植，尚未接线）

RWG 的 `rwg/surface/` 共 21 个非抽象类，rtgc 此前只有 3 个
（`SurfaceBase` / `SurfaceGeneric` / `SurfaceMountainStoneMix1` / `SurfaceRiverOasis`）。
本轮把**剩下的 18 个**按逐行移植补齐到 `rtg/api/world/surface/`：

| 类 | RWG 里被使用次数 |
|---|---|
| `SurfaceGrassland` | 60 |
| `SurfaceMountainStone` | 28 |
| `SurfaceMountainSnow` | 12 |
| `SurfaceDuneValley` | 5 |
| `SurfaceDesertMountain` | 4 |
| `SurfaceGrasslandMix1` | 4 |
| `SurfaceCanyon` | 4 |
| `SurfaceTundra` | 3 |
| `SurfaceIslandMountainStone` | 2 |
| `SurfaceGrasslandMixBig` | 2 |
| `SurfaceDesertOasis` | 2 |
| `SurfacePolar` | 2 |
| `SurfaceDesert` / `SurfaceMesa` / `SurfaceRedDesert` / `SurfaceMarshFix` / `SurfaceGrassCanyon` / `SurfaceMountainPolar` | 各 1 |

（`SurfaceVolcanoAsh` 未移植 —— 火山按既定要求已删除。）

### 移植口径与记录在案的适配

- **坐标口径**：RWG 的 `paintTerrain(..., int i, int j, int x, int y, ...)` 里
  `i`/`j` 是世界坐标、`x`/`y` 是区块内局部坐标，但**名字是错位的**
  （`blocks[(y * 16 + x) * 256 + k]` 说明名为 `x` 的其实是 z）。
  已逐处核对，`CliffCalculator.calc(x, y, noise)` ↔ rtgc `TerrainBase.calcCliff(x, z, noise, river)`
  的索引公式**等价**（RWG `noise[y*16+x]` = rtgc `noise[x*16+z]`），故移植后不产生转置。
- **1.7.10 元数据 → 1.12.2 blockstate**：
  - 染色粘土元数据 9/14/1/8/0 → `BlockUtil.getStateClay(EnumDyeColor.…)`（青/红/橙/银/白）；
  - 沙的元数据 1（红沙）→ `Blocks.SAND` 的 `RED_SAND` 变体；
  - 泥土/沙的其它元数据（1、2）在 1.12.2 无对应，**无效果故省略**，并在各类注释里标明。
- **`SurfaceCanyon` / `SurfaceMesa` 的粘土层理表**：RWG 用固定种子 `2L` 的经典 Perlin
  一维噪声生成 100 项颜色表。rtgc 的 `PerlinNoise` 有逐字移植的 `noise1`，
  故这张表是**逐位相同**的（不是近似）。
- **`SurfaceMountainPolar`** 在 RWG 里 `paintTerrain` 就是**空实现**（不做地表替换），
  照抄为空，没有"顺手补全"。
- **`SurfaceRedDesert`** 的底部沿用 RWG 的**普通**砂岩（1.7.10 没有红砂岩方块），
  没有替换成 1.12.2 的红砂岩。
- **`SurfaceDuneValley`** 里 RWG 写 `base[x*16+y] = RWGBiomes.baseHotDesert`；
  rtgc 没有自定义群系，改写为 `Biomes.DESERT`（保持 RWG 的转置写法）。
  `base` 在 rtgc 里唯一消费者是 `RealisticBiomeMountainChain`，那里会自行改写，
  所以这一写不影响结果，保留只为结构同构。

### Added（接线用的工件）

- `docs/rwg-surface-map.tsv`：从 `rwg/biomes/realistic/**` 逐类提取的
  **RWG 群系 → 地形签名 → 地表类与完整实参**表（44 行，未经人工修改）。

### ⚠ 本版**不改变**任何生成结果

18 个地表类目前**没有被任何群系引用**。接线（把 rtgc 的 129 个逐群系地表类
改为委托到这些类，并按 RWG 的实参传入 top/filler/cliff/阈值）是下一步。
接线完成后，各群系的崖壁阈值、雪线、层理与噪声混合才会真正对齐 RWG ——
这也是"交界处观感生硬"最可能的所在。

## [1.0.29]

**撤回 1.0.28 的"噪声边界抖动"—— 它在群系交界两侧造出了一堆孤立圆点。**
原因是我只搬了 RWG 那条抖动的**阈值那一半**，漏掉了它外面那道闸门。

### Reverted

- **`randBiome` 噪声抖动（1.0.28 的 Fixed ②）整个撤回。**
  1.0.28 的实现是：`bRand = 0.5 + perlin(x/15, z/15)`，按升序累加各群系权重，
  第一个让累积权重超过 `bRand` 的群系就是这一列的地表群系。
  实测表现（你的反馈）：**群系交界两侧都出现一堆孤立圆点，圆点里生成邻居群系的方块。**

  成因说得清：RWG 在 `ChunkGeneratorRealistic:356-363` 里，先找
  `hugeRender[4*9+4][biomeID] > 0.95f` 的那个群系 `b`，然后

  ```java
  if (b != null) { randBiome = false; for (i = 0; i < 256; i++) biomes[i] = b; }
  ```

  ——**主群系权重 >95% 时，整个区块不允许抖动**。
  我只实现了阈值扫描，没实现这道闸门。于是在"主群系 90% / 邻居 10%"的带里，
  邻居只有在 `bRand > 0.9`（即噪声**极值**处）才会被选中，而噪声极值区是孤立小团
  ⇒ 就是圆点。RWG 里权重在边界上是势均力敌的（≈50/50，累积区间各占一半），
  才会连成犬牙交错的一条带。

  已完整移除：`ChunkLandscape.surfaceBiome` 字段、`getNewerNoise` 里的挑选、
  以及 `generateChunk` 对它的使用。**地表回到 `landscape.biome`（布局给的该列群系）**，
  与 1.0.26 的 F3 一致性保持。
  （如果以后要重开，必须**连同 `>0.95` 闸门一起**搬，不能只搬一半。）

### Added

- **`[RTG-BLEND]` 混合统计探针**（`-Drtg.debugDecorations` 开启，每区块一行）：

  ```
  [RTG-BLEND] chunk(x,z) 参与混合的群系数=N 过渡带列数=M/256 最小主权重=0.xxxx
  ```

  「主权重」= 该列权重最大的群系占比。

  | 读数 | 含义 |
  |---|---|
  | `过渡带列数 = 0`、`最小主权重 = 1.0000` | 混合窗完全落在单个群系格子内 ⇒ **不存在过渡带**，地形在格子边界上是台阶 —— 这才是"生硬"的判定证据 |
  | `过渡带列数` 有几十~上百、`最小主权重` 明显小于 1 | 混合确实在工作，过渡带有宽度；此时若看着仍生硬，问题不在高度混合，而在**地表材质**或**相邻群系的地形落差本身** |

  这一行是只读的，不改变任何生成结果。

### 本轮已核对为"与 RWG 一致"的部分（所以不再往这些方向猜）

| 项 | RWG | rtgc | 结论 |
|---|---|---|---|
| 混合核 | `0.445f / sqrt(j²+k²+0.3f)`，j,k ∈ [-8,8] 归一化 | 同式同范围 | ✓ 一致 |
| 四层金字塔 | HUGE 1/2 + SMALL 1/2/3/4 | 逐段一致（§28 表格） | ✓ 一致 |
| 群系单元尺度 | `biomeWidth = 500f`、`climateWidth = 1400f` | 同值 | ✓ 一致 |
| 边界扭曲 | `BIOME_WARP_* = .4f / .175f` + 两个手工偏移 | 同值同偏移 | ✓ 一致 |
| 高度→方块 | `h = (int) testHeight[x*16+z]`（RWG 的 `i/j` 是反的，但块索引证明口径相同） | 同 | ✓ 一致 |
| 通道 | RWG `stride` 只用于独立入口 `getNewNoise`，真实路径 `getNewerNoise` 逐列 | 逐列 | ✓ 一致 |

**因此"生硬"若在 1.0.29 依旧，最可能的剩下两处是：**
1. **`SurfaceXxx` 未移植**：RWG 约 40 个 `Surface*` 类，rtgc 这边对应约 120 个群系
   仍在用近似/通用地表（此前记录的**最大一笔欠账**）。RWG 的崖壁/混合地表
   （如 `SurfaceMountainStoneMix1`、各 `SurfacePolar`）会在边界附近做噪声混合，
   rtgc 的通用地表是硬切 ⇒ 交界处观感更"生硬"。
2. **相邻群系的地形落差本身**：A3 迁移把 130 个群系接到 20 个 RWG 地形函数上，
   若某两地相邻群系的落差远大于 RWG 的对应组合，同样的混合宽度就会显得更陡。

需要你选一个方向，或者给我一处具体坐标（F3 的群系名 + 看着生硬的位置），
我按那处直接查，不再泛泛推断。

## [1.0.28]

**两条具体反馈都定位到真因并修掉了：**
① "冰刺之地显示 MushroomIslandShore" = `mushroom_island_shore` 被名字规则判成海滩 + 抢走了全局 `coastDunes`；
② "生物群系边界依旧过度生硬" = RWG 的**噪声边界抖动**（`randBiome`）此前完全没移植。

### Fixed ①：`MushroomIslandShore` 出现在冰刺之地一带

链路（每一环都有源码依据）：

1. `MUSHROOM_ISLAND_SHORE`（id 15）的温度是 **0.9**、降雨 1.0，
   而 Forge 的 `BiomeDictionary` 对它写的是
   **`addTypes(Biomes.MUSHROOM_ISLAND_SHORE, MUSHROOM, BEACH, RARE)`** —— 它**真的带 BEACH 标签**。
2. rtgc 的 `isBeach` 是 `has(BEACH) || name.contains("beach") || name.contains("shore")`
   —— **三条全中**（字典 BEACH、以及名字里的 "shore"）。
3. 而 `isBeach` 分支排在 `specialFor` / `islandBiome` **之前** ⇒
   `mushroom_island_shore` 永远走不到 RWG 给它定的位置，而是作为**海滩**进了 `LITTORAL`。
4. 更糟的是它顺手抢走了 `coastDunes`：原实现用
   "本气候 LITTORAL 池里第一个 temp≥0.15 的成员" 来定 `coastDunes`，
   而 `coastIce`/`coastDunes` 是**全局单字段**（不是按气候分的），
   各气候的"第一个成员"会互相覆盖 ⇒ 最终胜出者与 RWG 毫无关系。
5. `continent < 24` 的海岸列里，只要该列群系温度 ≥0.15 就会落到 `coastDunes`
   ⇒ **暖海岸成片变成 Mushroom Island Shore**。冰刺之地一带正是这样被命中的。

**修法**（对齐 RWG）：
- 把 `specialFor(key)` 提到 `isBeach` **之前**。`mushroom_island_shore` 的 key 含
  `"mushroomisland"` ⇒ 归入 `SPECIALS` 的 **WET / SMALL_ISLAND**，
  与 RWG `Support.java:104-115`（`addBiome(mushroomIsland, WET, SMALL_ISLAND)`）一致。
  只提前 special、**不动** `islandBiome`，所以"同时带岛屿与海滩标签"的群系不受影响。
- **`coastIce` / `coastDunes` 改为显式指定**，不再用启发式：
  RWG 的 `RealisticBiomeBase:132/136` 是两个**固定实例**（`new RealisticBiomeCoastIce()` /
  `new RealisticBiomeCoastDunes()`），由 `ChunkManagerRealistic:590-593` 二选一。
  rtgc 对应到自己的两个移植：
  `coastIce = RTGAPI.getRTGBiome(COLD_BEACH)`（即 RWG `RealisticBiomeCoastIce` 的移植，
  `RealisticBiomeVanillaColdBeach` 的注释已写明）、
  `coastDunes = RTGAPI.getRTGBiome(BEACH)`（其 `terrainCoastDunes` 即 RWG `CoastDunes.rNoise` 的逐行移植）。

### Fixed ②：群系边界过渡生硬 —— 补上 RWG 的噪声边界抖动

> ⚠ **本条已在 1.0.29 撤回**：它只在群系交界两侧造出一堆孤立圆点。
> 原因见我漏搬了 RWG 的 `>0.95` 闸门，详见 1.0.29 条目。下面保留原始记录以便追溯。

RWG 的地表群系**不是**"这一列属于哪个群系"，而是用一张 **15 格尺度的 Perlin 噪声场**
去扫各群系的**累积混合权重区间**（`ChunkGeneratorRealistic:456-495`）：

```java
bRand  = clamp(0.5f + perlin.noise2((x+i)/15f, (y+j)/15f), 0f, 0.99999f);
bCount = 0f;
for (activeBiomeIndex ...) {           // 升序
    if (smallRender[l][k] > 0f) {
        if (randBiome && bCount <= 1f) {
            bCount += smallRender[l][k];
            if (bCount > bRand) { biomes[...] = getBiome(k); bCount = 2f; }
        }
    }
}
```

哪个群系的累积区间跨过 `bRand`，这一列的地表就用哪个群系。**这就是"用噪声混合群系"
的视觉那一半** —— 边界变成噪声状的犬牙交错，而不是一条直线。此前 rtgc 完全没移植它，
所以边界是"一刀切"。

现已实现：新增 `ChunkLandscape.surfaceBiome[]`，在 `getNewerNoise` 里按上述规则逐列求出
（`rtgWorld.simplexInstance(0)` 就是经典 Perlin，见 `RTGWorld:86`），
`generateChunk` 的地表替换改用它。
- **只在权重本来就混合的过渡带内改写**：内陆单群系处累积权重恒为 1.0，
  阈值必然被它跨过 ⇒ 不可能改选别的群系。**所以不会出现"森林里冒出蘑菇岛"那类远处群系。**
- `landscape.biome[]`（F3 / 区块群系数组 / 装饰）**不受影响** —— 1.0.26 修的
  F3 一致性保持不变。
- 必须存进 `ChunkLandscape`：`smallRender` 是生成器字段，而 `landscape` 可能来自缓存
  （与 `mountainChainWeight` 同理）。

### Verified

- `generateTerrain` 逐列 `(int) noise[x*16+z]`，**没有任何额外平滑或量化** ⇒
  混合后的高度场是直接落到地形的，不存在二次台阶来源。
- 冷/暖海岸群系名、以及四个气候的 `LITTORAL` 成员名，现在**每次启动都打进日志**
  （`[RTG] 海岸群系：…` / `[RTG]   SNOW LITTORAL 成员：…`），
  下一次日志就能直接核对本轮的修正是否生效，不需要再猜。

### Fixed ③（同一批）：装了 REID 时**只写了 int 数组，没写原版字节数组**

你给的另一个观测是**关键指纹**：

> F3 显示的生物群系只有 Ocean 和两个 mushroomIsland

`Ocean`=id **0**、`MushroomIsland`=**14**、`MushroomIslandShore`=**15**。
而 `GenLayerAddMushroomIsland` 正是**往海洋里撒蘑菇岛 + 由 `GenLayerShore` 补岸**的那一层
（`BiomeProviderBOP:190`）。这个组合只可能来自**BOP 自己的 GenLayer**，
而 rtgc 只在"布局未命中"时才回落到它。两个问题因此是同一个：

**F3 读的群系不是 rtgc 布局给的。**

而且顺带查出一处真缺陷。原代码是：

```java
if (this.useIntBiomeArray) {
    ((INewChunk) chunk).setIntBiomeArray(this.intBiomeArray);   // REID 路径
} else {
    chunk.setBiomeArray(this.byteBiomeArray);                   // 原版路径
}
```

`useIntBiomeArray = Loader.isModLoaded("jeid") || ...`（`ChunkGeneratorRTG:219`），
你装了 REID ⇒ **走 int 分支，原版字节数组永远停在初值**。
而原版 `Chunk` 的存储是 `byte[256] blockBiomeArray`，
初值 `(byte)-1 = 255`（`Chunk.java:111`，255 = "未知"哨兵），
**客户端是从网络包里读这个字节数组的**（`Chunk.java:1263 buf.readBytes(this.blockBiomeArray)`）。
于是"F3 读哪一个数组"完全取决于 REID 有没有把 int 数组同步过去 —— 这是个不该存在的赌博。

**已改为无条件两个数组都写**：int 给 REID 的扩展路径，字节给原版 `SPacketChunkData`/`fillChunk`。
编号 <256 时两者一致；≥256 时字节会截断，但那种编号本来也只能靠 REID 的 int 数组承载。

### 新增 5 条探针：让"F3 从哪取值"可判定

我没有你的运行环境，所以把判定点全部做成日志。装 1.0.28 后请搜这几个关键字：

| 关键字 | 含义 | 判读 |
|---|---|---|
| `[RTG] 建立布局：side=` | 布局建立时的**端**、种子、已注册群系数 | 应出现 CLIENT 与 SERVER 两条且 **seed 相同、rtgBiomes>0**；若客户端那条 `rtgBiomes=0` 或 seed 不同 ⇒ 客户端与服务端用了两套群系表 |
| `[RTG] ⚠ 布局尚未建立就收到群系查询` | `biomeAt` 在布局就绪前被调用 | 出现即说明 F3 走了回落路径 |
| `[RTG] ⚠ 布局未命中，F3/群系查询回落到…GenLayer` | provider 回落到了 GenLayer（含端与线程） | **出现 = 根因确认**；日志会打印回落到的群系名（若大量是 `minecraft:ocean` / `mushroom_island`，与你的观测完全吻合） |
| `[RTG-BIOME] chunk(…) 中心列：期望=… 读回=…` | 生成末尾**回读**区块群系数组 | 「期望」与「读回」不一致 ⇒ 数组路径问题；两者一致但 F3 仍不对 ⇒ 问题在客户端读数 |
| `[RTG] 布局在 (…) 没有可用群系` / `海岸群系：…` | 池为空 / coast 选择 | 见 ①② |

**我没法在本地跑游戏，所以这三条（不含 ①②，那两条是按 RWG 源码对齐的确定性修正）
属于"把未知变成可读"的一步，不是在声称已修好。** 哪一条出现，就说明卡在哪一段。

## [1.0.27]

**"过渡太生硬" 的根因：四层 HUGE→SMALL 噪声混合被一条自己加的"中心群系短路"绕过了。
现在每一列都走完整混合，并且按 RWG 的 activeBiomeIds 遍历（更快）。**

### Fixed

- **删掉高度上的"中心群系短路"** —— 这就是"过渡生硬"的来源。
  本类此前用 `hugeRender[40]`（**中心那一个 HUGE 节点**）判定"该区块由单一群系主导"，
  命中后整块 256 列只算 `h_dom(1.0)`，跳过四层混合的加权求和。
  **这个判定在几何上是错的**：

  | | 采样窗覆盖的 21×21 网格范围 |
  |---|---|
  | `hugeRender[40]`（判定用） | 行/列 **2..18** |
  | `smallRender[(i+4)*25+(j+4)]`（每列真正用的） | 由 HUGE→SMALL 四层 mix4 从**整张 9×9 HUGE 格**平均而来，覆盖 **0..12 与 4..20** |

  后者比前者**更宽**，所以"中心节点只有一个非零项"并不等于"该列求和只有一项"：
  过渡带上的列完全可能含第二个群系的真实权重，而短路把它们一刀切掉。
  于是同一片过渡带上，被短路的区块是纯 `h_dom(1.0)`、相邻区块是混合值，
  断差正好落在**区块边界**上（16 格长的直线台阶）。

  **参照 RWG**：`ChunkGeneratorRealistic:356-363` 确实也有一个 `b != null` 判定，
  但它**只用于覆盖地表 `biomes[]` 数组**（`:443-451`）并关掉噪声抖动；
  高度求和（`:485-510`）**永远无条件执行**。本类把这个判定挪用到了高度上，
  属于实现偏离。现已删除。

  **性质**：均匀区块（单群系窗口）下 `smallRender` 权重仍是 1.0，结果是
  `h(1.0)*1.0`，与原短路**逐位相同** —— 所以这次只改过渡带，内陆地形一点没动。

- **恢复 RWG 的 `activeBiomeIds` 遍历（`ChunkGeneratorRealistic:320-340`）**，
  同时补回被短路吃掉的那部分性能：
  - 采样网格建好后一次性求出本区块**实际出现过**的群系（升序，RWG `:337` 注明的浮点累加顺序契约）；
  - `mix4` / `clearActiveBiomes` / `copyActiveBiomes` / 高度求和**全部只遍历这张表**。
    一张 21×21 采样网通常只命中 1–8 个群系，而此前是 0..256。
  - 数学等价性：非 active 编号在整张 HUGE/SMALL 金字塔里恒为 0（数组初始值），
    且每个被写入的格子都会先对当前 active 表清零，所以从不被读、也不影响结果。
  - 净效果：`mix4` 从 559 次 × 256 项降到 559 次 × activeCount；
    高度求和从 256 列 × 256 项降到 256 列 × activeCount。**这一项是提速，不是减速。**

### Verified

- 四层金字塔**逐阶段**与 RWG 对照过（HUGE 1 / HUGE 2 / SMALL 1 / SMALL 2 / SMALL 3 / SMALL 4
  的源索引、XOR 奇偶条件、目标索引、循环上界全部一致），`mix4` 与
  `clearActiveBiomes`/`copyActiveBiomes` 与 RWG `:646-666` 一致。
  **金字塔本身此前就是忠实移植，问题只在"有时不跑它"。**

### Known gap（未做，需要你定）

- RWG 还有一处**噪声驱动的群系边界抖动**（`ChunkGeneratorRealistic:456-495` 的 `randBiome`）：
  用 15 格尺度的 Perlin 噪声 `bRand` 去扫各群系的**累积权重区间**，决定该列地表用哪个群系的方块。
  它让群系边界呈噪声状而不是直线，且**只会在权重本来就混合的过渡带内**改写（内陆单群系处
  权重恒为 1，抖动不可能改选别的群系）。
  **没做**，因为它会让混合带内的 F3 群系与地表方块不一致 —— 而"F3 与地表不一致"
  正是你上一轮报的问题（1.0.26 已修）。要不要开，见 `docs/rwg-port-gaps.md` §28 末尾。

## [1.0.26]

**"F3 显示蘑菇群系、地上却是正常树林" —— 根因找到了：布局在某些列返回 `null`，
而群系 provider 的 `null` 回退落到了 BOP 自己的 GenLayer（那张表里有 `GenLayerAddMushroomIsland`）。**

### Fixed

- **布局永不返回 `null`，且回退改成"气候正确的布局成员"。**（根因修复）
  排查链（每一步都已在 `docs/rwg-port-gaps.md` §27 记录依据）：

  1. `Chunk.getBiome`（原版 1.12.2）用的是 `blockBiomeArray`（`byte[256]`），
     而 `SPacketChunkData` 会把这 256 字节发给客户端 ⇒ **客户端 F3 就是服务端数组的值**，
     不是客户端自己重算的。
  2. 该数组由 `ChunkGeneratorRTG:344-351` 从 `baseBiomesList` 填，
     而 `baseBiomesList[i] = landscape.biome[i].baseBiome()`（`:284-286`）。
  3. `landscape.biome[]` 由 `generateLandscape:970-978` 用
     `biomeProvider.getBiome(pos)` 填，再经 `BiomeAnalyzer.newRepair` 映射 —— 而**地形**
     用的是同一个函数里 `getNewerNoise` 填的 `biomeData`（走 `RtgLayoutAccess.biomeAt`）。
     **两条路都经过 `RtgLayoutAccess`**，所以本不该不一致。
  4. 但 `WorldTypeRTG:43-49` 在 BOP 已加载时返回的是 **`BiomeProviderBOP`**，
     而它的 `getBiome` 在布局返回 `null` 时会回落到 **`super.getBiome`**，也就是 BOP 自己的
     GenLayer —— 那条链里有 `GenLayerAddMushroomIsland`（`BiomeProviderBOP:190`）。
     ⇒ 这些列的 F3/地表/装饰会拿到一个**与本列地形毫无关系**的群系，最典型的就是蘑菇岛。

  修法：在唯一入口 `RtgLayoutAccess.biomeAt` 上保证有布局时**不返回 null**；
  拿不到时改用新增的 `RtgBiomeLayout.lastResortAt(x,z)` —— 本气候核心池的第一个非 null 成员
  （与 `selectIslandBiome` 已有的 C3 适配同一条原则：宁可给气候正确的群系，
  也不要回落到原版 GenLayer）。异常路径同样走这个兜底（原先是返回 null）。
  兜底触发有**节流日志**（前 5 次 + 每 10 万次一条，含坐标与累计次数），
  所以如果池里真的有空位，下一次日志就能看见，不会再次无迹可寻。
- **`selectBiome` / `selectCombinedBiome` 现在跳过 null 成员。**
  `List<IRealisticBiome>` 的成员可能是 null（某个群系没能被分类/注册），而
  `biomes.get(i)` 原先会把 null 直接返回出去，变成"该列没有群系"⇒ 触发上面的回落链。
  现在从选中位置起环形查找第一个非 null 成员。

### Changed

- **装饰按 9×9 区块邻域分摊**（原 1.0.25 条目，同批交付）。详见下方 1.0.25。

## [1.0.25]

**"很多地块的群系观与 F3 不一致" —— 装饰侧也有一处：整块地只用一个群系的装饰，
而且取的是正北东 16 格外的下一个区块原点。**

### Fixed

- **装饰按 9×9 区块邻域分摊（RWG `ChunkGeneratorRealistic:971-1015`）。**
  改前 `populate` 是 `biomeAt(x + 16, z + 16)` —— 也就是**本区块东北方向下一个区块的原点**
  —— 取到那**一个**群系，然后整个区块全用它装饰。后果是：只要群系边界从区块的东北角
  擦过，本区块就会**长满隔壁群系的树/花/草，自己群系的植被一棵都没有**。
  这就是"F3 显示 A、地上长的却是 B 的树"。
  RWG 不是这么做的：它以本区块中心为起点，对 9×9 个区块各采一个点（`x+24+bx*16`，
  `+24` 使采样点落在区块内部而非角上），每命中一次给该群系加 `0.01234569f`（= 1/81）。
  权重之和就是该群系在本区块的装饰强度。
  已按同样方式实现：新增字段 `decoWeights`，在 `DecorateBiomeEvent.Pre` **之前**累加
  （RWG:971 早于 RWG:977），然后按真实群系编号升序逐个装饰。
  - RWG 把权重作为 `strength` 形参交给各装饰器按 `count * strength` 缩放数量；rtgc 的
    `rDecorate` 没有 `strength` 形参，所以这里用**概率等价**：以 w 的概率整份装饰。
    期望量与 RWG 一致，方差不同（已在代码注释里写明）。
  - 典型情形（区块完全落在一个群系内）9×9 邻域只命中一个群系、权重 = 81/81 = 1，
    这时与改动前**完全一致**；只有跨边界的区块才会分摊。
  - `-Drtg.debugDecorations` 的诊断行新增 `decoBiomes=`（本区块分摊到几个群系）
    和 `centreWeight=`（区块中心群系拿到的权重）—— 这两个值能直接看出分摊是否生效。

### Known gaps (本次未修，已记录)

- **`rDecorateAfterIce` 从未移植。** RWG 的海洋群系 `rDecorate` 是**空实现**，真正的海洋
  装饰（`RealisticBiomeOcean:48-52`，`decorateBaseBiome && strength > 0.3f` 时调
  `baseBiome.decorate`）在 `rDecorateAfterIce` 里，由 `ChunkGeneratorRealistic:1115-1120`
  在原版结冰之后统一调用。rtgc 里全库**没有**这个方法 ⇒ 海洋群系（含 BOP 的 kelp/coral）
  的原版装饰一直没有发生。本次改动不影响它（海洋 `rDecorate` 本来就是空实现）。
- **地表抖动（surface bleed）的取样点算错了。** `ChunkGeneratorRTG:297-301` 把抖动后的
  **世界坐标** `pX/pZ` 又用 `(pX & 15)` 折回**本区块**的列去索引 `landscape.biome[]`，
  而本区块的数组里没有邻区块的群系 ⇒ 想表达"邻区块群系渗进来"却取到了本区块的另一列。
  实际上只有 3 个海滩群系（`VanillaBeach`/`VanillaStoneBeach`/`BOPGravelBeach`）会把
  `SURFACE_BLEED_IN/OUT` 打开，而本区块内绝大多数列是同一个群系，所以这个 bug 目前
  **近似空操作**（不产生可见错误），故未在本次修改，仅记录。

## [1.0.24]

**读了你的启动日志，抓到一个很严重的问题：全图 51% 的海洋只有 1 格水。**
另外修掉日志里能直接看到的两个 bug。

### Fixed

- **海洋地形接错了（海洋只有 1 格水）。**
  rtgc 的 5 个海洋群系接的是 `terrainFlatLakes`（`62f + h`）—— 而那是 RWG 给**陆地**用的地形
  （RWG 里它的使用者是 `SnowLakes` / `TaigaPlains` / `TundraPlains`）。
  RWG 的海洋用的是 `biomes/realistic/ocean/RealisticBiomeOcean.rNoise`：
  `height = shallow ? 52f : 34f`，再加两个噪声项 —— 即**浅海海底 y≈52、深海 y≈34**
  （海平面 63 ⇒ 水深约 **11 / 29 格**）。已新增 `TerrainBase.terrainOcean(x, y, rtgWorld, shallow)`
  并把 5 个海洋群系改接过去：
  `VanillaOcean` `VanillaFrozenOcean` `BOPKelpForest` `BOPCoralReef` → 浅海；
  `VanillaDeepOcean` → 深海。`terrainFlatLakes` 现在只剩 `VanillaIcePlains` 一个使用者（陆地群系）✓。
- **"陆地上 F3 显示海洋群系"（你实测发现的）。**
  根因同上：BOP 的 `kelp_forest` 带 `BiomeDictionary.Type.OCEAN` 标签，被分类器当成了**浅海槽位**，
  而 rtgc 的 `RealisticBiomeBOPKelpForest` 用的是**陆地版的山地地形**
  `terrainSwampMountain(135f, 300f)` ⇒ `continent < 0` 的列 F3 显示海洋、地形却是山。
  RWG 的做法（`SupportBOP.java:39-48`）是把 kelp/coral **包进 `RealisticBiomeBOPOcean`**
  再放进海洋槽 —— 它与陆地版是两个对象，差别正在地形。
  修好海洋地形后此问题一并消失。
- **C2/C3 的名字匹配被下划线挡住（启动日志直接可见）。**
  `SPECIALS` 里的片段写作 `flowerfield` / `ominouswoods` / `mushroomisland`，
  而注册名是 `flower_field` / `ominous_woods` / `mushroom_island` ⇒ 那三条**永远匹配不上**。
  1.0.23 的实测日志正好印证：`SMALL` 只有 HOT(oasis)/WET(quagmire) 命中，
  COLD 的 flower_field 与 SMALL_ISLAND 两条全是 0。现已先去掉下划线再匹配。
- **启动日志里的池大小是"填充前"的，会误导。**
  1.0.23 的日志显示 `BORDER=0 COLD_BORDER=0 HOT_BORDER=0`，但那是因为 `logReport`
  在 `mirrorCoreIntoBorders()` **之前**执行。已把日志挪到两次池填充之后。

### Changed

- `run/config/RTGC/rtgc.cfg` 清理：删掉三个本会话**已经不存在**的键
  （`biomeSearchRepair` / `cellDistanceFix` / `riverTunnels`）及其过时注释。
  原文件已备份为 `rtgc.cfg.bak-*`。

### Known issues

- **仍未在游戏内验证**（这一轮的修复来自你 1.0.23 那次启动的日志）。
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`、§17③ 的邻域装饰分摊、D5 深海不足。
## [1.0.23]

继续用参数元组审计扫**非字面量**实参（数值藏在构造函数里、上一轮被跳过的那些），又修两处。

### Fixed

- **`RealisticBiomeBOPCrag` 的峡谷阶地多了一级。**
  RWG `SupportBOP.java:231-237` 的 **crag** 条目（作者以 `/* */` 注释掉，属"作者原意"）是
  `new TerrainCanyon(false, new float[]{2.0f, 0.5f, 6.5f, 0.5f, 14.0f, 0.5f, 19.0f, 0.5f}, 35f, 80f, 60f, 40f, 69f)`
  —— **4 级**阶地。rtgc 的数组多了一对 `23.0f, 0.5f`（RTG 时代加的第 5 级），已去掉。
  （注：不要把 L164 那条**未注释**的 `new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)` 当成依据
  —— 那是 `BOPCBiomes.canyon`，而 rtgc 没有 canyon 群系。）
- **`RealisticBiomeVanillaExtremeHillsPlus` 用了无出处的 `width=240f`。**
  RWG 全仓 `TerrainHilly` 的 hills width **恒为 230f**（唯一例外是 `SupportCC` 的 `100f`）。
  改为 `terrainHilly(230f, 120f, 50f, 260f, 68f)` —— 即 RWG
  `land\RealisticBiomeJungleHills -> TerrainHilly(230f, 120f, 50f)` 的元组
  （3 参写法按附录 E6 补齐 260f/68f）。死掉的构造参数与字段一并删除。

### Known issues

- **仍未在游戏内验证。**
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`、§17③ 的邻域装饰分摊、D5 深海不足。
# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.22]

把"沙漠族错配"那类问题**工具化**，又抓出**两个家族共 9 个群系**的地形参数在 RWG 里查无此文。
其中 7 个 Hills 变体**比它们的基准群系还平** —— 方向是反的。

### Fixed

- **沙漠族：`VanillaDesertHills` / `VanillaDesertM`。**
  原用 `terrainGrasslandHills(70f, 200f, 7f, 100f, 38f, 260f, base)`。
  RWG 全仓**只有** steppe/thicket 的 `GrasslandHills(70f, 180f, …)`，
  **而且那两条都被 `/* */` 注释掉了** —— `(70f, 200f, 7f, …)` 这个组合在 RWG 里不存在。
  RWG 沙漠族只有四个地形：`Desert(150,50,0)` / `DesertMountains(230,120,0)` /
  `DuneValley(TerrainDunes)` / `Oasis(230,120,20,60,63)`。
  **已改为** `terrainHilly(230f, 120f, 0f, 260f, 68f)`（= `DesertMountains` 的配方；
  `red\RedDesertMountains` 用同一组参数）。死掉的构造参数与字段一并删除。
- **7 个 "Hills" 变体比基准群系还平（方向反了）。**
  `VanillaForestHills` `VanillaBirchForestHills` `VanillaBirchForestHillsM` `VanillaJungleHills`
  `VanillaTaigaHills` `VanillaMegaTaigaHills` `VanillaRedwoodTaigaHills`
  全都用同一条在 RWG 查无此文的 `GrasslandHills(70f, 180f, 7f, 100f, 38f, 260f, 68f)`；
  而它们的**基准**群系用的是 `terrainHilly(230f,120f,0f)` / `(230f,120f,50f)` /
  `terrainMountainRiver()`。即 Hills 变体的起伏只有基准的约 **1/8**（`varHeight=7` vs `strength=120`）。
  RWG 的 Hills 族与其基准共用同一地形（`forest\WoodHills` / `land\JungleHills` / `land\TaigaHills`），
  **已改为各自镜像基准群系**：
  - `VanillaForestHills` `VanillaBirchForestHills` `VanillaBirchForestHillsM`
    → `terrainHilly(230f, 120f, 0f, 260f, 68f)`
  - `VanillaJungleHills` → `terrainHilly(230f, 120f, 50f, 260f, 68f)`
  - `VanillaTaigaHills` `VanillaMegaTaigaHills` `VanillaRedwoodTaigaHills`
    → `terrainMountainRiver(x, y, rtgWorld, river)`

### Added

- `tools/terrain-param-audit.ps1` —— 把每个 rtgc 群系的地形调用参数元组（去掉
  `x, y, rtgWorld, river` 四个）与 RWG 全仓的 `new TerrainXxx(...)` 比对，
  列出**在 RWG 里查无此文**的元组。这正是本轮的沙漠族与 Hills 族被发现的途径。
  **终态：0 个字面量元组无匹配**（余下 20 条为表达式实参，已逐条判定，见文档 §25）。

### 工具自身也踩了两个坑（已修，记在文档里）

1. 判断"是否字面量"时用了 `[A-Za-z_]\w*`，于是 **`180f` 里的 `f` 被当成标识符** ⇒
   全部 74 条都被标成 `[NON-LITERAL]`，工具看起来毫无用处。
2. RWG 写 `.3f`、rtgc 写 `0.3f`，归一化后 `.3` ≠ `0.3` ⇒ 把 `BOPGrove` 那条**误报**为无匹配。

**教训**：审计工具本身也会骗人。两次都是"输出里 100% 都是同一类告警"或
"告警与我手工核对过的结论矛盾"才暴露 —— 与此前"常量序列比对误报"同一类问题。

### Known issues

- **仍未在游戏内验证。**
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`、§17③ 的邻域装饰分摊、D5 深海不足。

## [1.0.21]

补上 **D4 一直没装的那一半仪表**。

### Added

- **`-Drtg.debugDecorations` 装饰诊断**（默认关闭）。每个区块装饰结束后打一行：

  ```
  [RTG-DECO] chunk(12,-3) biome=biomesoplenty:lavender_fields chain=false \
             hasDecos=9 invoked=7 river=0.042 riverBranch=false rtgDecoOff=false \
             vanillaTrees=true mapFeatures=true
  ```

  目标 D4 的原文要求"在 populate 里临时记录**区块中心群系**与 **rDecorate 放置数**"。
  1.0.13 定位并修掉了根因（`ChunkInfo` 高度图转置，让全部 32 个装饰类定位错列），
  但**那个仪表从未装上** —— 本轮补齐。

### 设计

- 计数放在 **`ChunkInfo`** —— 它是**唯一**会传进每一个 deco 的对象，
  所以只需在**两个** `deco.generate(...)` 调用点前各加一行 `ChunkInfo.noteInvocation()`
  （`IRealisticBiome#rDecorate` 的默认实现 + 山地链的同名覆写），
  就能覆盖全部装饰，**不必去改约 32 个装饰类**。
- **为什么"有多少 deco"与"调用了几个"要分开打**：三条路径会把它们拉开 ——
  `deco.preGenerate(river)` 的按河强过滤、山地链的概率缩放（1.0.16）、
  以及 `RTG.decorationsDisable()` / `DISABLE_RTG_DECORATIONS` 的**整段跳过**。
  只打一个数会分不清"没配装饰"还是"被过滤光了"。

### 日志逐项含义

| 字段 | 含义 |
|---|---|
| `biome` | **区块中心群系**（取样点与 RWG 一致：`(x+16, z+16)`），装饰实际用的那个 |
| `chain` | 是否山地链（链用备份群系的 deco，并按平缓点比例缩放） |
| `hasDecos` | 该 `IRealisticBiome` 的 deco 列表长度 |
| `invoked` | **本区块实际调用了几次 `deco.generate`** |
| `river` / `riverBranch` | 装饰期河强，及是否走了 `getRiverBiome()` 分支（阈值 0.8） |
| `rtgDecoOff` | 是否因开关**只跑原版装饰** |
| `vanillaTrees` | `allowVanillaTrees()`（false 时 RTG 会先清零原版树/草/花密度） |
| `mapFeatures` | 影响洞穴藤蔓与结构 |

**排查口诀**：`hasDecos>0 且 invoked==0` ⇒ 被过滤或走了关闭分支；
`hasDecos==0` ⇒ 该群系没配装饰；两者都正常却仍看不到植被 ⇒ 问题在**落点**
（回到 1.0.13 的高度图转置）；`rtgDecoOff=true` ⇒ 是配置/启动参数关掉了 RTG 装饰。

### Known issues

- **仍未在游戏内验证。**
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`、§17③ 的邻域装饰分摊、D5 深海不足。

## [1.0.20]

把五个**离线标定工具全部跑了一遍**（目标要求"凡能离线验证的跑一遍"，这一路一直没跑），
并把结果接成运行期诊断。顺带修正一处此前的模糊说法。

### Verified

- **`compilePreviewJava` 通过，五个标定工具全部跑通** —— 也就是说本会话删掉
  `bayesianAdjustment` / `VoronoiBasinEffect` / `VoronoiPlateauEffect` / `actualBiomeIdBound`
  并没有破坏标定工具。
- 实测关键值：
  - **气候带面积：SNOW 13.99% / COLD 29.77% / HOT 28.45% / WET 27.79%**
  - 气候边界带（`border` 三池）**27.31%**；极端边界（**山地链**）**12.03%**（均占全图）
  - 海洋 51.27% / 陆地 48.73%；深海 36.82% / 浅海 14.45%；岛屿 2.473%
  - `rwgCellDistance()` 高度比 **rtgc/RWG = 1.000**；`borderDistance` 均值比 **1.1710**
  - 隧道带 6.5 格 = 全图 **4.276%**，带内 `riverStrength` min **0.637** / mean **0.943**
  - **地标确认失效**：火山/熔岩洞坐标全部 `Long.MIN_VALUE`、命中 0
    —— 火山与地标豁免**由实测确认**，且 `ContinentalNoise` 未改一行

### Docs

- 修正 §14 里"边界带占陆地很大一块"这处**没有数据的模糊说法**。
  实测：气候边界带占**全图 27.31% ≈ 陆地 56%**，极端边界（山地链）
  **12.03% ≈ 陆地 25%**。即 C1 与 B4 影响的**不是边缘情况** ——
  过半的陆地列走边界池，四分之一的陆地列是山地链。

### Added

- `RtgBiomeCategorizer.logClimateSkew` —— 把每个气候的
  **「群系占比 / 带面积 / 倍率」**并排打进日志，倍率偏离 1 超过 ±60% 就 `WARN`。
  带面积用 `calibrateClimateBands` 的实测值（运行期算不出 Voronoi 划分面积，故硬编码并注明来源）。
  这样 **D3 的修正效果不需要再跑标定工具就能从游戏日志确认**。

### 为什么加这个

D3（气候归类偏斜）此前只有一句"实测 COLD=53 / HOT=20，而带面积 30% / 28%"，
**没有目标值也没有量化指标**。现在有了：COLD 以 29.8% 的地表占了 46.9% 的群系（倍率 1.58），
HOT 只有 17.7%（倍率 0.62）。修 D3 后应回到 1.0 附近 —— 游戏日志会直接告诉你。

### Known issues

- **仍未在游戏内验证。**
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`、§17③ 的邻域装饰分摊、D5 深海不足。

## [1.0.19]

布局与地表的逐行核对。**本轮没有代码改动** —— 全部是"打印原文并排比对"的验证，
结论是两者都忠实，并把三处**已记录的适配**（不是偏离）写清楚。

### Verified

- **`RtgBiomeLayout` 的常量与 RWG `ChunkManagerRealistic` 1:1 对应**：
  `SHALLOW_OCEAN_WIDTH` 300f、`CLIMATE_WARP_SCALE/STRENGTH` .4f/.8f、
  `BIOME_WARP_SCALE/STRENGTH` .4f/.175f、气候界限 .16875/.545/.78、
  `CLIMATE_BORDER_DISTANCE_DIFFERENCE` 288D、`LITTORAL_WIDTH` 432f、
  `SMALL_BIOME_RADIUS` 75D、`climateWidth`/`biomeWidth` 1400f/500f。
  RWG 多出的 `THREE_CLIMATE_*` 是**三气候模式**用的（rtgc 选 4 气候）；
  `VOLCANO_RIVER_SAMPLE_SPACING` 属火山（豁免）。
- **rtgc 多出的两个常量经核对也都来自 RWG**：
  `CLIMATE_SHIFT = 4000D` ← RWG `:707`；`COAST_WIDTH = 24f` ← RWG `:590`。
- **选择逻辑逐条对应**（缓存 → `continent < 0` 海洋 → 火山 → 岛屿 →
  陆地 → 滨海(`< LITTORAL_WIDTH`) → 海岸(`< COAST_WIDTH`)），
  以及 `getClimateValue` / `warpClimateCoordinates` / `sampleBiomeSelector` 的域扭曲偏移、
  `getTerrainOceanValue`、`getOceanBiome` 的全部阈值。
- **`SurfaceMountainStoneMix1`（1.0.12 新写）与 RWG 同名类逐行一致**：
  `depth == 0` 段的 beach/`p`/`cliff` 判定/`nextInt(3)` 石-卵石混合/染色粘土/mix 分支，
  与 `depth < 6` 段的五路分支全部对应。
  等价替换：`CliffCalculator.calc(x,y,noise)` → `TerrainBase.calcCliff(x,z,noise,river)`；
  `stained_hardened_clay + metadata 9` → `getStateClay(CYAN)`（元数据 9 就是青色）；
  `Blocks.stone/cobblestone` → `hcStone()/hcCobble()`（备份群系 config，默认即 STONE/COBBLESTONE）。

### 三处已记录的适配（有意，不是偏离）

1. **不缓存 null**：RWG 无条件 `biomeDataMap.put(coords, output)`，会**永久缓存**
   "该列拿不到群系"。rtgc 刻意不缓存，让核心池未注册时每列都能重试（fail-soft）。
2. **火山分支省略**：RWG 的条件是
   `Support.volcanoIsland instanceof RealisticBiomeIslandVolcano && …`，
   在 `volcanoIsland == null`（火山豁免）时本就为假 ⇒ **省略是行为保持的**。
3. **海洋槽 null 兜底**：RWG 的 8 个海洋实例是按气候写死的，rtgc 的海洋群系不齐
   （§17 ① 的 D5），故槽位为 null 时退化并记日志。

### Known issues

- **仍未在游戏内验证。** `1.0.10` 起累积的全部改动均未实机确认。
- 余下三条已判定"抄不了"：C3 的 `LARGE_ISLAND`（无对应群系）、
  §17③ 的邻域装饰分摊（缺 `strength` 形参）、D5 深海不足（属发明）。

## [1.0.18]

把**全部 20 个 RWG 地形函数**做了一次系统性并排比对（工具化，见下）。
抓到两类**系统性数值偏差** —— 都不是"公式抄错"，而是"公式抄对了却喂错了数值"。

### Fixed

- **`terrainMarsh` 的基准高度低了 0.5 格（10 个群系）。**
  RWG 是 `return 62f + h;`，而 **62 是它的水面顶**
  （`ChunkGeneratorRealistic.generateTerrain:292`：`if (k < 63) blocks[p] = Blocks.water`
  ⇒ 水占 `y ≤ 62`）。rtgc 把水位做成可配置（F-41）并把它提成形参 —— 这是对的，
  但 **10 个调用点全部传了 `WaterLevel.current().riverSurface()`**，那是 `seaLevel - 1.5` = **61.5**；
  正确值是 `waterSurfaceTop()` = `seaLevel - 1` = **62**（默认海平面下与 RWG 逐位相同）。
  受影响：`BOPBog` `BOPDeadSwamp` `BOPFlowerField` `BOPMarsh` `BOPOasis` `BOPQuagmire`
  `BOPWetland` `VanillaSwampland` `VanillaSwamplandM` `VanillaMushroomIslandShore`
  —— 整个沼泽/湿地族**整体低 0.5 格**，水面覆盖面积因此偏差。
- **cell 坐标的精度写法与本文件自己声明的约定矛盾（10 处）。**
  文件在 `terrainDunes` 的注释里明确写过"cell 项同样用 RWG 的 `x / 25D` 双精度写法，
  而非 `x * 0.04f`"，但有 10 处没守：
  `x * 0.04f`(5)、`x * 0.005f`(1)、`x * 0.02f`(1)、`x * INV_70`(1)、`x * INV_30`(2)。
  RWG 用的是 `x / 25D` / `x / 200D` / `x / 50D` / `x / 70D` / `x / 30f`。
  单精度乘法与双精度除法在 x 较大时足以把 **Voronoi 采样点挪过单元边界**，留下可见接缝。
  **已全部改为 RWG 的字面写法。**
- 4 处 `(h - 35f) * 0.66666667f` 改回 RWG 的字面写法 `(h - 35f) / 1.5f`
  （除法与乘倒数的结果可能差 1 ulp）。

### Added

- `tools/terrain-fidelity-diff.ps1` —— 把 RWG `rwg/terrain/TerrainXxx.java` 与 rtgc 的
  `TerrainBase.terrainXxx` 按**已记录的移植约定**归一化后并排打印。
  刻意**不**做自动序列比对（早期两次自动比对都误报），只归一化
  `perlin.noise2(`→`noise2f(` 与 `cell.noise(a,b,1D)`→`CELLDIST(cell.eval2D(a,b))`，
  其余原样打印供人读。
  ⚠ 该脚本必须保持**纯 ASCII**：PowerShell 5.1 按 ANSI 读取无 BOM 的 `.ps1`，
  中文注释会把解析器搞崩（这个坑踩到第三次了）。

### Verified（20/20 逐行核对完毕）

`terrainHilly` `terrainMountain` `terrainMountainRiver` `terrainMountainSpikes`
`terrainSwampMountain` `terrainSwampRiver` `terrainGrasslandHills` `terrainGrasslandMountains`
`terrainCanyon` `terrainDuneValley` `terrainFlatLakes` `terrainHighland`（两重载）
`terrainDunes` `terrainSmallIsland` `terrainSmallSupport` `terrainMesa`
—— 全部为**等价写法**，无实质偏离。
（`terrainCanyon` 的 `sb` 曾是疑点：RWG 的 `sb < 0f ? 0f : sb` 位于 `if (b > 0f)` 内，
是**死分支**，故 rtgc 的 `Math.min(b, 7f)` 等价。）

### Known issues

- **仍未在游戏内验证。**
- `terrainDunes` / 未参数化的 `terrainPolar` 仍为 0 调用者（已移植、待接线，§20）。
- `RealisticBiomeVanillaDesert` 仍用沙丘地形而非 RWG 沙漠族的 `TerrainHilly(150f,50f,0f)`
  —— 有意保留（它是 `sandDuneHeight` GUI 滑条的唯一消费者），§20 写明了改法。

## [1.0.17]

A1 / A2 逐行复核（"凡能离线验证的跑一遍"）。抓到并修掉一处**未真正完成的 A2 项**。

### Fixed

- **`terrainPolar` 的钳制值两处不一致。** 忠实重载
  `terrainPolar(int,int,RTGWorld,float)` 早已是 `Math.max(st, 0.2f)`，
  但**参数化重载**（`RealisticBiomeVanillaDesert` 用的那个）仍写着 `0.1f`
  —— 同一条公式在两处不一致，而 A2 明确要求 `0.1f→0.2f`。**已统一为 `0.2f`**。
  这个下限直接决定沙丘脊线的厚度，故对 `VanillaDesert` 的观感有实际影响。

### Verified（逐行打印比对，无改动）

| 函数 | 结果 |
|---|---|
| `terrainDunes` | 与 RWG `TerrainDunes` 逐行一致 |
| `terrainSmallIsland` | 一致（`6/120/58/200`、`/4.5f`、`1.5f` 上限 15、`max(58, 58+h)`） |
| `terrainSmallSupport` | 一致（`100/8`、`30/4`、`15/2`、`7/1`、`70 + 20*river + h`） |
| `terrainMesa` | 一致（含唯一需要 `border` 的 `c2` 支路） |

### Known issues

- **`terrainDunes` 与未参数化的 `terrainPolar` 仍为 0 调用者**（"已移植、待接线"）：
  RWG 里 `TerrainDunes` 的唯一使用者是 `RealisticBiomeDuneValley`，
  rtgc 的 BOP 1.12.2 **没有** dunes / dune valley 群系；
  而 RWG 的 `RealisticBiomePolar` **不在** `Support.java` 里 ——
  它在 RWG 的大陆模式下也从不被使用，所以无人调用是**忠实**的。
- **`RealisticBiomeVanillaDesert` 仍在用沙丘地形**（参数化的 `terrainPolar`）
  而不是 RWG 沙漠族的 `TerrainHilly(150f,50f,0f)`。**有意保留**，理由两条：
  它是 `sandDuneHeight` GUI 滑条（1–10）的**唯一**消费者（换掉会让滑条变死配置）；
  且它产出的沙丘观感与"MC 沙漠"相符。源码注释里写明了"要完全照抄就把这行换成
  `terrainHilly(150f,50f,0f,260f,68f)`"，随时可切。
- **附带发现**：RWG 的 `brushland` 在 `SupportBOP` 里被 `addBiome` **两次**
  （`GrasslandHills` + `TerrainDuneValley`），即同一 MC 群系挂两种地形。
  rtgc 的模型是"一 MC 群系 ↔ 一个 `IRealisticBiome`"，**无法表达重复**；
  当前取第一个（`GrasslandHills`）。结构性不可照抄，已记录。
- **仍未在游戏内验证。**

## [1.0.16]

**B1 收尾**：山地链的装饰缩放。这是目标清单里最后一条被显式要求、却一直欠着的子项。

### Added

- **`RealisticBiomeMountainChain.rDecorate`：按「平缓采样点比例」缩放备份群系的装饰**
  （RWG `RealisticBiomeMountainChain.java:55-88`）。
  效果：**陡峭的山体上不长成片森林**，平缓处照常 —— 这是山地链视觉上"像山"
  而不是"长满树的石堆"的关键。
  - 量法：在区块内 3×3 个采样点上量地表起伏，数出"高度变化 ≤ 4"的平缓点比例。
  - 缩放：rtgc 的 `IRealisticBiome.rDecorate` **没有 `strength` 形参**，
    无法表达按比例缩放，故改用**概率等价** —— 逐个 deco 以该比例为概率触发，
    **期望装饰量与 RWG 一致**；代价是单区块确定性不同（多消耗随机数）。
  - 采样网格适配：RWG 用区块内偏移 `8,12,16,20`（跨到下一区块），
    而 `rDecorate` 只拿得到**本区块**的 256 列高度，故改用 `4,8,12`（3×3 = 9 点），
    越界的 ±4 邻居按 RWG 的方式跳过。
  - 与 RWG 一致：deco 收到的是**备份群系**而非链本身；原版装饰复刻
    `IRealisticBiome#rDecorate` 的默认实现。

### Fixed

- **装饰群系改从布局取（否则上面这段代码是空转）。**
  `ChunkGeneratorRTG.populate` 原先用
  `RTGAPI.getRTGBiome(biomeProvider.getBiome(blockPos.add(16,0,16)))`。
  山地链与备份群系**共用同一个 MC 群系**，这条往返只会拿回备份群系，
  链的 `rDecorate` **永远不会被调用**。已改为先查
  `RtgLayoutAccess.biomeAt(x + 16, z + 16)`、查不到再退回原路。
  对**非链**的列两种取法结果完全相同，故这行改动只对山地链生效。

### Known issues

- **仍未在游戏内验证。** `1.0.10` 起累积的全部改动均未实机确认。
- §17 ③ 描述的**更广的**邻域装饰分摊（RWG 对每个区块都按 9×9 区块邻域把总量 1.0
  分摊给各相邻群系）**仍未做** —— 本轮只实现了 B1 明确要求的山地链那部分。
- C3 的 `LARGE_ISLAND` 池仍为空（rtgc 无 `fungiForest` / `hotPlainsCanyonIsland` 对应物）。
- D5 深海不足：按目标要求**只记录不动手**。

## [1.0.15]

阶段 C2 / C3：填 `SMALL` / `ISLAND` / `SMALL_ISLAND` 池。至此**目标清单上的 A/B/C/D 四阶段
全部落地**，余下的是明确记为"发明或需跨接口重构"的项（见下）。

### Changed

- **C2 / C3：`SMALL` / `ISLAND` / `SMALL_ISLAND` 池按 RWG 的真实成员填充。**
  依据 `tools/rwg-placement-of.ps1` 从 RWG `Support*.java` **精确提取**的池成员
  （此前那种"窗口搜索"会串到相邻条目）：

  | RWG | 气候 | rtgc 落点 |
  |---|---|---|
  | `flowerField` | COLD | `BOPFlowerField` → `SMALL` |
  | `quagmire` | WET | `BOPQuagmire` → `SMALL` |
  | `oasis` | HOT | `BOPOasis` → `SMALL` |
  | `ominousWoods` | SNOW | `BOPOminousWoods` → `SMALL_ISLAND` |
  | `mushroomIsland` | WET | `VanillaMushroomIsland` → `SMALL_ISLAND` |

  并**沿用 RWG 给的气候**而非重新分类（`BOPOminousWoods` 按温度会落到 COLD，
  但 RWG 把它放在 SNOW 的 `SMALL_ISLAND`）。
  另外把 rtgc 自带的四个岛屿群系（`BOPOriginIsland` / `BOPFlowerIsland` /
  `BOPVolcanicIsland` / `BOPTropicalIsland`）移入 `ISLAND` 池 ——
  它们此前被当普通陆地群系撒在大陆上。
- **`RtgBiomeLayout.selectIslandBiome` 逐级退化**：`smallIsland/largeIsland` → `island`
  → 本气候核心池。RWG 在池空时返回 `null`，那会让岛屿列**回落到原版 GenLayer 群系**
  （与所在气候无关），比拿到气候正确的群系更糟。这是适配，已在 javadoc 标明。

### Fixed

- **纠正一处语义误解**：`Support.addBiome` 对 `placement != CORE` 的条目会**直接 return**
  —— 即 RWG 的 `core` 与 `border`/`ISLAND`/`SMALL` 等池是**互斥**的。
  故 C2/C3 的落点忠实实现"只进该池、不进 core"。
  这也说明 **C1 的 `border = core` 是一处有意的语义偏离**（rtgc 没有作者标注，
  无法知道哪些群系属于边界），已补进文档。

### Added

- `tools/rwg-placement-of.ps1` —— 精确定位每个 `BiomePlacement.X` 所属的
  `addBiome(...)` 及其**首个群系实参**，取代此前会串条的窗口式提取。
- `RtgBiomeCategorizer.Report` 新增 `island` / `special` 计数，
  `logReport` 输出 `岛屿=` / `特殊=` 便于运行期核对。

### 复验

- `gradlew build` 通过；
- `tools/terrain-wiring-check.ps1`：**130/130**；
- 接线核验（避免空转）：`RtgBiomeLayout.add()` 的 switch 覆盖全部 11 个 placement
  且 `default` 抛异常；`small[climate]` 在 `getLandBiomeAt` 被读；
  `selectIslandBiome` 在 `computeBiomeDataAt` 被调。

### Known issues

- **仍未在游戏内验证。** `1.0.10` 起累积的全部改动均未实机确认。
- `LARGE_ISLAND` 池**仍为空**：rtgc 没有 RWG 的 `fungiForest` / `hotPlainsCanyonIsland`
  对应物，补就是发明。`selectIslandBiome` 会退到核心池，故不会出现 null。
- **B1 的装饰缩放做不到**：RWG 会把 `strength × 平缓采样点比例` 传给备份群系，
  而 rtgc 的 `IRealisticBiome.rDecorate` **没有 `strength` 形参**，
  无法表达按比例缩放。补它需要给约 32 个装饰类加形参的跨接口重构。
- D5 深海不足（rtgc 1 个 vs RWG 4 个）：属"补群系=发明"，按目标要求**只记录不动手**。

## [1.0.14]

阶段 D1 / D2：清掉两套"平行实现"（RTG 时代的重复代码）。
**本轮没有行为改动**——只删死代码，删完的调用点改走与主生成路径同源的那一份。

### Removed

- **D1：`TerrainBase` 里整套与布局重复的河道族。**
  `RtgBiomeLayout` 已提供 RWG 逐行移植的 `getRiverStrength` /
  `getRiverTunnelStrength` / `getRiverJunctionStrength` / `calculateRiver`，
  而 `TerrainBase` 还留着一份用 rtgc 自造 `SpacedCellularNoise` 写的等价物：
  `getRiverStrength`（3 重载，含 `warpedRiverVoronoi`）、
  `getRiverTunnelStrength`、`getRiverJunctionStrength`、`fillRiverStrengths`、
  `rwgCalculateRiver`、`rwgRiverBed`、`bayesianAdjustment`，
  以及仅供它们的常量 `RWG_RIVER_BED_LITERAL` / `RWG_RIVER_CARVE_WIDTH_BLOCKS` /
  `BORDER_DISTANCE_CALIBRATION`。
  - ⚠ **关键点**：`BORDER_DISTANCE_CALIBRATION = 1.171f` 是为 **rtgc 自造的
    `SpacedCellularNoise`** 标定的；布局用的是**逐行移植的 RWG `CellNoise`**
    （`RwgCellNoise`），几何与 RWG 一致 ⇒ RWG 的原始宽度字面量才是对的，不需要标定。
    **两份实现不能混用常数。**
  - 改为：`/rtg probe` 与 `ChunkGeneratorRTG.getNewerNoiseSingleBiome` 都改走布局。
    顺带修掉单群系路径的一处约定不一致（原先写 `landscape.river[k] = riverValues[k]`，
    主路径写 `-riverValues[k]`）。
  - 同时删除因此变为无人引用的 `VoronoiBasinEffect`、`VoronoiPlateauEffect`。
  - **`TerrainBase` 1282 → 1027 行。**
- **D2：`BiomeAnalyzer` 里三阶段修复留下的整套死重量。**
  内部类 `SmoothingSearchStatus`（约 170 行）及其全部方法
  （`hunt` / `search` / `smoothBiomes` / `smoothQuadrant` / `addBiome` / `addWeight` /
  `preferredBiome` / `biomeIndex` / `clear` / `isAbsent` / `isNotHunted` / `setNotHunted`）、
  `filterForFlag` / `setSearches` / `setupBeachesForBiomes` / `riverAdjusted`，
  以及只喂给它们的字段 `preferredBeach` / `flagCache` / `beachSearch` / `landSearch` /
  `oceanSearch` 与常量 `NO_BIOME`。
  - 判定依据：`newRepair` 只读 `biomeIDs` 与 `landscape`，不触碰上述任何成员；
    `NO_BIOME` 的 6 处使用全在上述死簇内；`biomeNeighborhood` 形参的唯一消费者
    `hunt` 已删（保留形参只为不改调用点签名）。
  - **`BiomeAnalyzer` 355 → 139 行**，只剩 `initBiomes` / `newRepair` / `xyinverted`。

### Added

- `tools/delete-java-method.ps1` —— 按签名定位并删除一个 Java 方法（含其 javadoc），
  打印删除范围以便复核。本轮所有删除都用它，避免手写大段 `old_string` 出错。

### 复验

- `gradlew build` 通过；
- `tools/terrain-wiring-check.ps1`：**130/130** 群系走 RWG 地形函数；
- `tools/reachability.ps1`：`TerrainBase` 中不可达的只剩 `terrainDunes`
  （RWG 忠实移植、待接线，**保留**）。

### Known issues

- **仍未在游戏内验证。** `1.0.10` 起累积的地形反相修复、129 群系迁移、山地链、
  边界池、装饰高度图转置修复全部未实机确认。
- **C2 / C3 未做**：`SMALL`、`ISLAND`、`SMALL_ISLAND`、`LARGE_ISLAND` 池仍为空。
  RWG 的池成员含 `tropics` / `garden` / `fungiForest`，rtgc 的 BOP 1.12.2 **没有**
  这三个群系，按名字硬凑即为发明；而 `SMALL` 池会产生"每 75 格散布特殊群系"的可见效果，
  填错比留空更糟。
- D5 深海不足、D4 #3 邻域装饰分摊：见 `docs/rwg-port-gaps.md` §17 的待决清单。

## [1.0.13]

**找到了"没有地表装饰"的真正原因**：`ChunkInfo` 的高度图被**转置**了，
导致全部 32 个 RTG 装饰类都按错误的列高放东西。另修 `DecorateBiomeEvent` 从未发出、
气候归类偏斜，并修掉自己上轮引入的一处性能问题。

### ⚠ BREAKING — 需要新建世界

### Fixed

- **`ChunkInfo` 的高度图索引顺序不一致（"没有地表装饰"的直接原因）。**
  `noise[]` 由 `ChunkGeneratorRTG` 按 `k = x*16 + z` 写入，而 `heightCache` 按
  `(z & 15) * 16 + (x & 15)` 读取，原实现却直接 `heightCache[i] = (int) noise[i]`
  —— **整张高度图被转置**。
  影响面是全部 RTG 地表装饰：**32 个装饰类**都用 `chunkInfo.getHeight(...)` 定位地表
  （`DecoTree` `DecoGrass` `DecoFlowersRTG` `DecoCactus` `DecoShrub` `DecoBoulder`
  `DecoMushrooms` `DecoReed` `DecoDeadBush` `DecoPumpkin` `DecoFallenTree`
  `DecoVariable*` `DecoHelper*` `DecoWorldGen` …），而 `ChunkInfo` **只在带 noise 的
  那条构造路径上被创建**——也就是唯一被使用的路径。
  后果：`DecoTree.doGenerate` 用精确的 `pos.up(y)` 放树（不向下找地面），
  山地上约一半的树被埋进石头、另一半悬空，大量还被 `y > maxY || y < minY` 剔除。
- **`DecorateBiomeEvent.Pre` / `Post` 从未被发出。** rtgc 的 `populate` 取代了原版
  `ChunkProviderServer#populate`，而后者是这两个事件的**唯一**发出点 ⇒
  所有靠它们做装饰的 mod（含 BOP 的一部分植被）在本世界类型下**完全不生效**。
  RWG 也是自己发的（`ChunkGeneratorRealistic:977/1036`），已补。

### Changed

- **气候归类（D3）**：HOT 原先只有 `temp >= 0.9f` 一条纯温度判据，于是大量
  **暖而干**的群系（steppe / scrubland / outback / lushDesert / wasteland /
  xericShrubland …，温度多 0.7–0.85）全部落进 COLD 兜底（实测 COLD=53 / HOT=20）。
  改为按温湿二维补一条 `temp >= 0.7f && rain <= 0.35f`。阈值取得保守，
  以免把温带森林误判成热带。**新分布只能在运行期日志里确认**。

### Performance

- **修掉自己上轮引入的问题**：`nearbyMountainChainInfluence` 对每一列扫 441 个采样点，
  每点都调 `RtgRealisticIndex.biomeOf`，而 `mcBound()`/`usedBound()` 当时是
  `synchronized` —— 每区块约 **11 万次加锁调用**。
  - `RtgRealisticIndex` 读路径**全部无锁**（`mcBound` 改 volatile 惰性冻结，
    合成表改 volatile 数组；只有 `register` 加锁）。
  - 生成器新增 `sampleIsChain[441]`，采样网格建好后一次性解析 441 次，
    内层只剩布尔数组读取（**11 万次 → 441 次**编号解析）。

### Known issues

- **未在游戏内验证。**
- **D4 发现 #3（未修）**：RWG 的装饰是**按 9×9 区块邻域把总量 1.0 分摊**给各相邻群系
  （`borderNoise` 81 × 0.01234569 = 1.0），rtgc 只按**单点**群系装饰一次 ——
  总量相当，但**边界处不会混合**。照抄需要给 `IRealisticBiome.rDecorate` 补 `strength`
  形参并让约 32 个装饰类按它缩放，属跨接口重构，本轮未擅自动手。
- **D5（记录，不擅自发明）**：rtgc 只有 **1 个深海**群系 vs RWG 的 4 个按气候写死的深海，
  故四个气候共用同一个深海（`fillMissingOceanSlots` 兜底）。RWG 的 4 个是 RWG 自造群系，
  rtgc 无对应物；凭空造 3 个新群系属于**发明**而非移植，故只记录。
- C2 `SMALL` 池、C3 岛屿池仍为空；D1 旧河道族、D2 `BiomeAnalyzer` 死重量未清。

## [1.0.12]

阶段 B（山地链整条链）+ C1（边界池）。本轮还挖出**第三个大问题**：
**所有气候边界列此前根本没走 RWG 布局**，而是回落到了原版群系。

### ⚠ BREAKING — 需要新建世界

地形再次改变，与此前任何版本都不兼容。

### Fixed

- **气候边界列完全没走布局。** `RtgBiomeLayout.selectCombinedBiome` 在两个池都为空时
  返回 `null`，而 `getLandBiomeAt` 在气候边界处只从
  `border[climate]` ∪ (`coldBorder`|`hotBorder`)[climate] 里选。三池皆空 ⇒
  所有气候边界列拿到 null ⇒ 生成器整列回落到**原版 GenLayer 群系**。
  边界带并不窄：`CLIMATE_BORDER_DISTANCE_DIFFERENCE = 288` vs `climateWidth = 1400`，
  即气候 Voronoi 单元四周约 288 格宽的一圈都算边界。
  **这既是"地形没变好看"的一大块，也是山地链一直不显形的原因。**
- `mountainChainWeight` / `mountainChainRiverHost` 从生成器字段移进 `ChunkLandscape`。
  `landscape` 可能来自 `landscapeCache`，而 `carveRiverTunnels` 是稍后才跑的 ——
  放生成器字段会在缓存命中时读到**上一个区块**的值。

### Added

- **山地链（阶段 B 全部）**：
  - `RealisticBiomeMountainChain` —— RWG 同名类的移植。地形
    `terrainHilly(…, 230f, 120f, 0f, 260f, 120f)`（RWG `TerrainHilly(230,120,0,260,120)`），
    其余全部委托给备份群系。**不继承 `RealisticBiomeBase`**，因为那会在构造时新建
    第二份 `BiomeConfig`（同一配置文件读两遍，且链的 `initConfig()` 为空时有把
    备份群系配置刷掉的风险）。
  - `SurfaceMountainStoneMix1` —— RWG 同名类的逐行移植（山地链的岩石混合地表）。
  - `RtgRealisticIndex` —— **合成群系编号空间**。RWG 靠自己的现实主义编号空间让
    「链」与「备份群系」共存于同一个 MC 群系之下（`super(0, baseBiome, …)`）；
    rtgc 原先没有这层空间，`getNewerNoise` 用 `Biome.getIdForBiome(...)` 建采样数组，
    于是链与备份群系被折叠成同一个编号 —— 链根本无法表示。
    现在采样数组改走现实主义编号。
  - `mountainChainWeight` / `nearbyMountainChainInfluence` / `fade`
    （RWG `ChunkGeneratorRealistic:474-554`）：**山地链内部不做河道雕刻**，
    河道改以地下隧道＋洞厅形式出现。
  - `RtgBiomeLayout.mirrorCoreIntoBorders()`（C1）：把每个气候的核心池复制进
    `border` / `coldBorder` / `hotBorder`，使边界列不再拿到 null，
    并使四个极端镜像池能填成山地链。

### Changed

- **隧道/洞厅门控**从「去掉河流后的干高度 ≥ 76」换成 RWG 的
  `mountainChainRiverHost > 0.10`。地下河与洞厅因此成为**山地链专有**的地貌，
  而不是"任何够高的地形都会有"。`TUNNEL_MIN_SURFACE`(76) 降级为
  洞厅天窗的**权重**`overheadHost`（RWG 也是这么用的）。
- `RiverCaveVines` 的门控**未改**：B5 之后隧道只可能在链内产生，
  而 `riverCaveCeiling` 只在真正开凿成功时写入 ⇒ 与 RWG 的
  `hasMountainChainNearby` **等价**，再查一遍链邻域属重复计算。

### Performance

- 采样数组按 `RtgRealisticIndex.biomeIdBound()`（含 256 个预留槽位）**分配**，
  但每区块热循环按 `usedBound()`（动态：MC 上界 + 已分配槽位数）**遍历**。
  没有链时 `usedBound() == mcBound()`，热循环与引入本类之前完全一致；
  预留只多约 0.7 MB 内存。
- 预留是必需的：1.12.2 的 `WorldServer#createChunkProvider` 是
  `createChunkGenerator()` 先、`createBiomeProvider()` 后，
  **生成器先构造**，那一刻山地链还不存在。

### Known issues

- **未在游戏内验证。** 全部结论来自源码比对、静态检查与离线计算。
- **阶段 C1 的填法是有意的偏离**：RWG 的三个边界池是作者手工挑选的过渡群系
  （实测 `COLD_BORDER` 只有 chaparral/meadow/rainforest/tropicalRainforest），
  rtgc 的 MC 群系没有这层数据，故取"三池 = 本气候核心池"。代价是
  `borderDirection` 不再产生方向性差异。
- **山地链的装饰缩放暂缺**：RWG 的 `rDecorate` 按「平缓采样点比例」缩放备份群系的
  装饰强度；rtgc 的装饰走 MC 群系（区块中心）而非链对象，故未移植。
- C2 `SMALL` 池、C3 `ISLAND`/`SMALL_ISLAND`/`LARGE_ISLAND` 池仍为空。
- D1 旧河道族、D3 气候归类偏斜、D4「没有地表装饰」、D5 海洋群系不足均未做。

## [1.0.11]

**A3 收官：129 个群系文件全部走 RWG 移植过来的地形函数**（此前 117/129）。
本轮同时抓到并修掉了两处"迁移改到了死类"的空转 —— 它们此前让迁移**一行都没生效**。

### ⚠ BREAKING — 需要新建世界

地形再次改变，与此前任何版本都不兼容。

### Changed

- **12 个仍走 RTG `GroundEffect` 系统的群系全部迁到 RWG 地形函数**：

  | rtgc 群系 | 落点 | 依据 |
  |---|---|---|
  | `VanillaMesa` | `terrainMesa` | ✅ RWG `red\RealisticBiomeMesa` → `new TerrainMesa()` |
  | `VanillaMesaPlateau` `…F` `…FM` `…M` | `terrainMesa` | ✅ RWG `savanna\RealisticBiomeMesaPlains` → `new TerrainMesa()` |
  | `VanillaSavanna` | `terrainGrasslandFlats` | ✅ RWG `savanna\RealisticBiomeSavanna` → `new TerrainGrasslandFlats()` |
  | `BOPTropicalIsland` | `terrainIslandTropical` | ✅ RWG `ocean\RealisticBiomeIslandTropical.rNoise` |
  | `BOPLandOfLakes` | `terrainGrasslandHills(90,180,13,100,38,260,71)` | ⚠ 近亲（RWG 只有 `landOfLakesMarsh`） |
  | `VanillaPlains` `VanillaSunflowerPlains` | `terrainGrasslandFlats` | ⚠ 推断 |
  | `BOPXericShrubland` | `terrainGrasslandHills(90,180,13,100,38,260,71)` | ⚠ 推断（取同族 `shrubland` 参数） |
  | `BOPRedwoodForestEdge` | `terrainGrasslandHills(80,180,13,100,38,260,71)` | ⚠ 推断（同族 `BOPRedwoodForest`） |
  | `VanillaMesaBryce` | `terrainMesa` | ⚠ 推断（RWG 无 Bryce / hoodoo） |

- `terrainGrasslandFlats` 去掉 `mPitch` / `baseHeight` 两个形参。RWG 的
  `TerrainGrasslandFlats` **没有参数**，硬编码 `m *= m / 40f` 与 `68f + h + m - l`。
  留着形参就等于"允许用非 RWG 的值调用"，那正是偏离的入口。
- `RealisticBiomeVanillaMesaPlateau.TerrainRTGMesaPlateau` 由自造的
  "抖动 + `VoronoiPlateauEffect` + `bordercap`/`rivercap`"换成 `terrainMesa`。

### Fixed

- **`VanillaMesaBryce` 的迁移此前完全空转**：`initTerrain()` 返回的是
  `new TerrainRTGBrycePlateau(67f)`（RTG 自造 Bryce 高原 + `hoodooHeight` 石林），
  而迁移改的是**同一文件里从未被实例化的** `TerrainRTGMesaBryce`。
- **`BOPTropicalIsland` 同类问题**：其 `TerrainVanillaExtremeHillsPlus` 是死类
  （同名真身在 `RealisticBiomeVanillaExtremeHillsPlus.java`），
  而 `initTerrain()` 实际返回 `RealisticBiomeVanillaExtremeHills.GrandMountain`。
  两者都已拨正。

### Removed

- 已被替换的群系里 `GroundEffect` / `HeightEffect` / `HeightVariation` / `JitterEffect` /
  `RaiseEffect` / `BumpyHillsEffect` / `MountainsWithPassesEffect` / `VoronoiPlateauEffect`
  留下的**死字段、死构造代码与未用 import**
  （`VanillaBirchForest` `VanillaJungleEdge` `VanillaRoofedForest` `BOPBambooForest`
  `BOPDeadSwamp` `BOPLushDesert` `BOPOutback` `VanillaMesaBryce` `BOPXericShrubland`
  `BOPLandOfLakes`）。
- `RealisticBiomeVanillaMesaBryce.TerrainRTGBrycePlateau`（含 `hoodooHeight`）。

### Added

- `tools/terrain-wiring-check.ps1` —— 解析每个群系 `initTerrain()` 的**真实返回类型**
  （支持 `A.B` 限定名），定位该类类体并检查其中是否有 `terrainXxx(` 调用。
  这是抓"迁移改到了死类"这类空转的唯一可靠办法。
  当前结果：**129/129 通过**。
- `tools/reachability.ps1` —— Java 函数可达性分析（判断死代码）。

### Known issues

- **未在游戏内验证。** 全部断言来自源码比对、静态检查与离线数值计算。
- `surface` 路径仍用 rtgc 旧 river 约定（1 = 河心），与 RWG 的 `river + 1f` 不同 —— 独立欠账。
- `RTGWorld.riverAdjustedforDepthDifference` / `RIVER_FLATTENING_ADDEND` /
  `getLake{Frequency,ShoreLevel,DepressionLevel,BendSize*}` 已无调用者。
- `terrainDunes` 仍是无人调用的 RWG 忠实移植（保留待接线，不是死码）。

## [1.0.10]

修的是"把 RWG 的公式逐行抄完了、地形却没变好看"的**第二根因**。
1.0.9 修的是"喂给公式的噪声不是同一个函数"，本轮修的是"喂给公式的**参数**不是同一个值"。

### ⚠ BREAKING — 需要新建世界

地形再次改变，与此前任何版本都不兼容。请新建世界。

### Fixed

- **`rNoise` 不再对 `border` / `river` 做任何加工。**
  RWG 的 `RealisticBiomeBase.rNoise` 是原样透传：
  `return terrain.generateNoise(perlin, cell, x, y, ocean, border, river);`
  rtgc 却在同位置挂了一整套 RTG 时代的河道／湖泊变换
  （`newrNoise` = `lakePressure` → `lakeToRiverProportions` →
  `riverAdjustedforDepthDifference` → `riverFlattening`），它把输入**反相**：
  默认配置下实测（`riverDepth=57`、`waterFeatureWidthMultiplier=1.0`）

  | RWG 传入 `river`（0 = 河心，1 = 内陆） | 地形函数实收 |
  |---|---|
  | 0.00 | **1.0000** |
  | 0.05 | 0.9710 |
  | 0.50 | 0.4734 |
  | 1.00 | **0.0000** |

  右列**精确等于** `1 − 左列`（可用 `tools/river-convention.ps1` 复算，
  纯算术、无 MC 依赖），也就是说它是**为 rtgc 旧约定（1 = 最强河流）写的转换器**。
  把 117 个群系逐个改成 RWG 的原始公式（`m = noise * strength * river`，陆地 = 1）时，
  转换器忘了拆，于是地形整体反相：

  - **内陆** `river = 0` ⇒ `m` 项**被完全抹平** ⇒ 丘陵／山脉全部消失，世界被压成平板
    （有湖处最高也只到 ≈0.039，即不足 4%）；
  - **河心** `river = 1` ⇒ `m` 项满幅 ⇒ 沿每条河长出一圈山墙。

  这就是"照抄了 RWG 却不 RWG"的直接原因。现已与 RWG 逐字同构。

  **副产品（性能）**：每列少一次 `cellularInstance(0).eval2D`
  （`lakePressure` 的 Voronoi 求值），是纯收益。

- `ChunkGeneratorRTG.getNewerNoiseSingleBiome` 原先传 `riverValues[k]`（旧约定原始值，
  ∈[-1,0]），与主路径的 `riverValues[k] + 1f` 不一致，已对齐。

### Removed

- `RealisticBiomeBase`：`newrNoise`、`lakePressure`、`lakeToRiverProportions`。
- `IRealisticBiome.lakePressure`，以及 `RealisticBiomeBOPBayou` / `RealisticBiomeBOPShield`
  中对应的两处覆写。
- `TerrainBase` 中 13 个 RTG 自造地形函数
  （`terrainPlains` `terrainRollingHills` `terrainBeach` `terrainOcean` `terrainOceanCanyon`
  `terrainPlateau` `terrainBryce` `terrainLonelyMountain` `terrainHighlandLegacy`
  `terrainFlatLakesRTG` `terrainForest`）与 2 个死重载
  （`terrainGrasslandHills` 9 参、`terrainGrasslandMountains` 7 参）、孤儿助手 `above`。

### Known issues

- **`surface` 路径仍用 rtgc 旧约定**（`landscape.river[k] = -riverValues[k]`，1 = 河心），
  而 RWG 的 surface 收到的是 `river + 1f`。这是独立欠账（surface 审计），
  当前 surface 代码与其输入自洽，故未动。
- `RTGWorld.riverAdjustedforDepthDifference` / `RIVER_FLATTENING_ADDEND` /
  `getLake{Frequency,ShoreLevel,DepressionLevel,BendSize*}` 已无调用者。
- **12 个群系仍走 RTG `GroundEffect` 系统**（尚未迁到 RWG 地形函数）：
  `VanillaPlains` `VanillaSunflowerPlains` `VanillaSavanna` `VanillaMesa`
  `BOPXericShrubland` `BOPTropicalIsland` `BOPLandOfLakes` `BOPRedwoodForestEdge`
  `VanillaMesaPlateau` `VanillaMesaPlateauF` `VanillaMesaPlateauFM` `VanillaMesaPlateauM`。
  最后 4 个是唯一仍**依赖旧 river 约定**（`rivercap = 3f * river`）的代码。
- **未在游戏内验证。** 本轮的断言全部来自源码比对与离线数值计算，实机效果待你确认。

## [1.0.9]

本轮修的是"地形不像 RWG"的**根因**。此前的问题是：地形常数确实**逐行照抄**了 RWG，
但喂给这些公式的**噪声后端不是同一个函数** —— 同样的公式配不同的噪声场，出来的就是不同的地貌。

### ⚠ BREAKING — 需要新建世界

世界生成算法再次改变，与此前任何版本都不兼容。请新建世界。

### Fixed

- **噪声后端改回 RWG 的经典 Perlin**。
  源码依据：`RwgWorldSavedData.noiseImplementation` 的**字段初值就是 `UNKNOWN`**，
  `NoiseSelector` 把 `UNKNOWN` / `DYNAMICPERLIN` 都映射到 `useOpenSimplex = false`，
  于是 `NoiseGeneratorWrapper.noise2()` 转发给 `PerlinNoise.noise2()`；
  只有执行过 `/rwgnoise OPENSIMPLEX` 的世界才走 OpenSimplex。
  - 离线实测（`gradlew calibrateNoiseBackend`，零 MC 依赖）：`noise2` 标准差
    Perlin **0.211** vs OpenSimplex **0.423**，恰好 2 倍。而地形函数普遍对噪声**平方**
    （如 `terrainHilly` 的 `m *= m / 35f`），故起伏实际差 **4 倍**。
  - 后果：换之前，同一套 RWG 常数在 OpenSimplex 上产出的是"处处巨山、被软顶压成高原"；
    Perlin 下才是 RWG 的"缓丘之间偶尔冒出高峰"。这也解释了 rtgc 自加的 `mountainCap`
    为什么存在 —— 它是在给错误的噪声后端打补丁（换回 Perlin 后峰值 ≈188，cap 自然失效）。
  - 特征块头（自相关半衰长度）两者一致（比值 0.67–1.25）⇒ **既有 pitch 常数无需重调**。
  - **尚未在游戏内目视验证。**
- **河道架构对齐 RWG（WP-5）**。RWG 是"**对混合后的高度执行一次**、线性混合到
  `59 ± 3.5` 的噪声河床"（`ChunkManagerRealistic.calculateRiver:863-884`，
  由 `ChunkGeneratorRealistic:551` 每列调一次）；rtgc 原来是"按**每个贡献群系各跑**一遍
  `riverized`（目标 61.5 水平面，且 `if (h < 61.5) return h` 在 61.5 处造成硬断层 = **台地化**）
  + `erodedNoise`（目标硬编码 53）"。
  现已按 RWG 结构重写，新截面是**从河床 59 到岸顶的线性斜坡**：

  | 距河心 c（格） | 0 | 6 | 12 | 24 | 36 | 48.1 |
  |---|---|---|---|---|---|---|
  | 雕刻后高度（岸顶 80） | **59** | 61.6 | 64.2 | 69.5 | 74.7 | 80 |

  水面在 62 ⇒ **河深约 3 格、全宽 ≈48 格（半宽 ≈24 格）**，无台面、无碟形。
  - `borderDistance` 与河流强度**复用同一次 Voronoi 求值**，不增加开销。
  - 雕刻宽度按 `gradlew calibrateCellularNoise` 实测的均值比 **1.171** 校正
    （RWG `(d2−d1)×pitch` 均值 6.103 vs rtgc `border×2×COORD_SCALE×pitch` 均值 7.146）。
  - **尚未在游戏内目视验证。**
- **`terrainMarsh` 拨回 RWG 忠实版**：主噪声 `20f`→`30f`、死区阈值 `8f`→`4f`、
  删除多余的 `h *= 2f`（对齐 `rwg/terrain/TerrainMarsh.java:13/18/21-22`）。
  此前沼泽起伏只剩 RWG 的约 1/3。
- **`terrainHighland` 重写为忠实版**：原实现是 Team-RTG 的改动版（多一层 `* river`、
  返回时又乘一次 `river`、基高改用 `getTerrainBase(river)`、且**参数顺序不同**）。
  已把旧实现改名 `terrainHighlandLegacy`（8 个调用点行为不变），
  并新增与 `rwg/terrain/TerrainHighland.java` 逐行一致的
  `terrainHighland(...)` 四参 / 五参（`flatDetailStrength`）重载。

### Changed

- **群系 → 地形的分配对齐 RWG `SupportBOP.java`**：共 **19 个** BOP 群系改到 RWG 指定的函数上，
  参数逐字照搬。其中 12 个对应 RWG 的**活跃**条目（真正照抄），
  另 7 个按 RWG **注释行**原文采用，并已在代码注释中逐条标明**"推断，非照抄"**。
- 修正 4 处此前标为"照抄"、实际引自 RWG 被 `/* */` **整块注释掉**条目的注释
  （`BOPWasteland` / `BOPSteppe` / `BOPGrassland` / `BOPCrag`），
  改为"推断"并注明原注释行的真实参数。
- **明确记录：vanilla 群系没有可照抄的对应物**。RWG 的 `Support.java` 只用
  `baseHotDesert` / `baseTemperateForest` 等**它自己的** `RWGBiomes.base*` 分类，
  vanilla `Biomes.*` 根本不参与地形选择 —— RWG 是**替换**群系表，不是映射 MC 群系。
  故 vanilla 的地形选择是 rtgc 原创，不应按推测改动。可照抄的只有 BOP（`SupportBOP.java` 按名字映射）。

### Added

- **`PerlinNoise`**：`rwg/util/PerlinNoise.java` 的移植。`noise1` / `noise2` / `noise3` /
  `improvedNoise` / `initPerlin1` / `sCurve` 六段本体**逐字未改**（已按字符长度逐一校验相同），
  只改包名与接口名，并补上 rtgc `SimplexNoise` 接口的 4 个转发 + `multiEval2D`。
- **离线噪声标定工具**：`gradlew calibrateNoiseBackend`（零 MC 依赖，可直接 javac 运行）。
- `docs/refactor-plan.md` 新增附录 E（本会话核实的事实，含**被推翻的假设**）
  与附录 F（地形函数保真度与河道架构审计）。

### Removed

**全部逃生开关已删除**（用户明确表示不需要）。忠实行为现为唯一路径，不再可切换。
被删掉的开关与随之删除的旧代码：

| 删除的开关 | 随之删除的旧代码 |
|---|---|
| `surface.riverTunnels` | 门控（地下河隧道改为无条件生成） |
| `surface.biomeSearchRepair` | 门控（群系修复三阶段改为无条件执行） |
| `surface.cellDistanceFix` | `CELL_DISTANCE_FIX` 静态字段与 `setCellDistanceFix()`；`cellDistance()` 固定走 `rwgCellDistance()` |
| `surface.rwgPerlinNoise` | `OpenSimplexNoise` 作为地形后端的整条分支；`RTGWorld` 无条件构造 `PerlinNoise` |
| `surface.rwgRiverPipeline` | `riverized()` 函数**及其 23 个调用点**、`erodedNoise()`（含 `BOPBayou`/`BOPShield` 两个 override）、单群系路径的 8 格侵蚀 |

配置项因此从 30 降到 22。回退现在需要改代码（git），不再是改配置。

### Known issues（已定位，尚未修）

- 6 个地形函数多套了一层 RWG 没有的 `mountainCap`。
- `terrainGrasslandFlats` 幅度只有 RWG 的 1/4；`terrainHighlandLegacy` 用 3D simplex
  顶替了 RWG 的 Voronoi cell 项。
- RWG 有、rtgc **未移植**的函数：`TerrainMesa` / `TerrainDunes` / `TerrainSmallIsland` /
  `TerrainSmallSupport`。
- `terrainGrasslandHills`(9 参) 与 `terrainGrasslandMountains`(8 参) 两段最偏离的函数
  **在 `src/` 内零调用者** —— 维护陷阱，建议删除。

## [1.0.8]

参照 [Realistic World Gen](https://github.com/ted80/Realistic-World-Gen)（RWG，1.7.10 / `rwg2` 分支）
对地形系统做了一次完整移植与修复。全过程记录见 `docs/refactor-plan.md`。

### ⚠ BREAKING — 需要新建世界

**本版本改变了世界生成算法，与旧存档不兼容。** 请使用旧世界以外的**新建世界**，
否则已生成区块与新生成区块之间会出现明显接缝。

具体地：本版本**默认**已不再逐位等于移植前地形。以下四项都是**全局**改动
（现已提供逃生开关，见 Added：三个开关全部关掉即可回到移植前）：

- `BiomeAnalyzer` 的海滩 / 陆地 / 海洋三个阶段此前从未真正执行（见 Fixed 第一条），现已修复；
- 地形混合在区块边界处的 `border` 短路（高度台阶）；
- RWG-GRAND 地形函数的 cell 项距离量纲（幅值此前偏弱约 38.7 倍）；
- 地下河隧道的门控与高度带。

### Added

- **两个世界生成逃生开关**（都在 `common.surface` 下，默认 `true`）：
  - `biomeSearchRepair` —— 关掉即回到移植前的群系分布（群系只由原版 GenLayer 决定）；
  - `cellDistanceFix` —— 关掉即回到移植前的山体细节（更圆滑）。

  加上 `surface.riverTunnels`，三个开关**全部关掉 = 移植前的地形**。
- **高度基准统一**。新增 `rtg.api.world.WaterLevel` 作为水位**单一真相源**，
  散落在框架层与 **38 处**群系层的 `61.5 / 62 / 63 / 64.5 / 69` 等硬编码水位字面量
  统一派生自 `seaLevel`。此前调整海平面只改变水面高度，沙滩线、悬崖带、河流水面、
  表层门槛全部不动。
- **地下河与洞厅**。沿河网（Voronoi 单元边界）生成地下河隧道，
  在河网交汇处生成洞厅，并有天窗与地表连通。
  配置项 `surface.riverTunnels`（默认 `true`）可关闭。
- **区块生成分阶段性能分析器**。此前 `enableProfiling()` 等三个开关是空壳实现、
  从不生效；现改为真正读取系统属性：
  `-Drtg.enableProfiling=true -Drtg.profilerLogInterval=50 -Drtg.profilerSlowThresholdMs=50`。
- **离线校验工具**（不需要游戏进程）：
  - `gradlew calibrateCellularNoise` —— `VoronoiResult.rwgCellDistance()` 与 RWG
    `CellNoise` 的逐点比对（幅值 / 特征尺度）。
- **游戏内诊断命令**：`/rtg probe [x z]`、`/rtg verify [radius] [step]`。

### Fixed

- 🔴 **`BiomeAnalyzer.newRepair` 的海滩 / 陆地 / 海洋三个阶段是死代码。**
  `SmoothingSearchStatus.absent` 是长期存活的实例字段，只被置 `true`、全仓无处复位，
  而三个阶段都在进入循环**之前**调用了 `setAbsent()`，于是循环在第 0 列就 `break`，
  三阶段**从第一个区块起**全部空转。后果是 RTG 的专属滨海带（`preferredBeach`）
  与陆海群系修复从未生效，群系过渡只体现在高度上。
  连带修复：`hunt()` 现在自行计算 `absent` 并清除 `notHunted`，
  每个区块的 `hunt()` 调用从最多 3×256 次降到 3 次。
- 🔴 **`preferredBeach.get()` 拆箱 NPE（会崩溃世界生成）。**
  `preferredBeach` 只为有 RTG 包装的群系赋值，而邻域搜索可能命中没有包装的模组群系，
  此时 `SparseList.get()` 返回 `null`，直接拆箱给 `int` 会抛 NPE。
- 🔴 **地下河的入口条件自相矛盾，从未开凿过一格。**
  门控用的是**已被河流压平后**的实际地表高度（约 63）却要求 `≥ 76`，
  而隧道带完全落在河网带内部（实测带内河流强度 min 0.637、均值 0.943）。
  现改为用**去掉河流后**的地形高度门控（语义等价于 RWG 的"山地河道"），
  并让隧道在必要时**整体下移**以保证洞顶留在地表以下 10 格
  （地表足够高时自动回到 RWG 的原始高度 58–73）。
  同时修正了洞厅顶部钳制的顺序错误（原实现先 `min` 后 `max`，钳制被顶回去）。
  ⚠ **该"修正"已在 1.0.33 同版本追加里撤回**（见本文件顶部的「撤回"隧道按地表下移"的适配」）：
  那个顺序正是 RWG 让地下河在地表低处与外界河**联通**的机制，钳制被顶回去是有意的。
- **区块边界上的直线高度断差。** 地形混合在"中心群系权重 > 0.95"时会切换公式并跳过
  带权求和，而该判据取自整个 16×16 区块，于是台阶恰好落在区块边界上。
  现改为**精确独占**判据（`center[]` 中只有一个非零项），使短路路径与完整求和在
  数学上逐位相同。
- **RWG-GRAND 地形函数的 cell 项幅值偏弱约 38.7 倍。**
  7 处函数是 RWG 原版的逐行对译，唯独 cell 项把 RWG 的
  `cell.noise(x/25, y/25, 1)`（线性距离）写成了 `getShortestDistance()`（**平方**距离，
  且位于内部缩放空间）。新增 `VoronoiResult.rwgCellDistance()` 完成换算，
  离线实测均值比由 **0.0259** 提升到 **0.9004**，特征尺度（自相关半衰长度）
  与 RWG 一致（11.0 格，比值 1.000）。
- **`RTGConfig.sync()` 漏读 19 项配置。** 原实现是一个只列出少数枚举常量的 `switch`
  且没有 `default` 分支，导致所有 `INTEGER` 配置（`landScheme` / `islandScheme` /
  `tempScheme` / `rainScheme` / `riverSize` / `biomeSize` / `surfaceBlendRadius`）
  以及未列出的 `DOUBLE` / `BOOLEAN` / `STRING` 数组**永远**停留在硬编码默认值。
  表现是配置文件里明明写着用户设的值、代码读到的却是默认值。
  现改为按类型回读全部设置。
- **BOP 的陆海层分支中存在恒为真的判定**（原 `effectiveLandScheme()` 化简后恒等于 1），
  使 BOP 的 2/3 号陆海层与其"中岛"分支永久不可达。相关分支与整个大陆场现已一并删除。
- **`RTGAPI.getMaxBiomeIDs()` 被误用为数组上界（性能退化约 10 倍）。**
  装了 RoughlyEnoughIDs 时它返回理论上限 65536，使地形混合数组膨胀 256 倍。
  现改为扫描 `ForgeRegistries.BIOMES` 取**真实**最大 ID。
- **`LandmassProvider.oceanFloor()` 对陆地入参返回 NaN**（`Math.sqrt` 负数），
  已加下界钳制。
- **JEID 环境下生物群系 ID ≥ 256 越界**（`hugeRender` / `smallRender` 第二维）。
- **BOP 陆海层与 RTG 大陆场曾同时驱动两套不同尺度的大陆系统**（曾表现为世界几乎全是水）。
  大陆场已删除，BOP 侧固定使用原版陆海层。
- **`terrainVolcano` 的高度被混合权重缩放两次**（曾是唯一使用 `border` 的地形函数）。
  该函数已删除，见下。

### Changed

- **`RTGConfig` 配置项数量 33 → 25**：移除大陆场（`landScheme` + `common.landmass` 全部 8 项）、
  地标（`landmarksPerTypePerContinent`）、火山（`largeIslandVolcanoChance`），
  新增两个逃生开关（`biomeSearchRepair` / `cellDistanceFix`）。
- **`terrainCanyon()` 由"有偏差的死副本"改为与 RWG 逐行一致**，并由
  `RealisticBiomeBOPCrag` 复用（此前该群系内联了整整一份拷贝，两份实现已漂移）。
  重构后 BOP crag 的世界生成结果不变。
- 配置项 `islandScheme` / `tempScheme` / `rainScheme` / `biomeSize` 的说明中
  明确标注**仅在安装 Biomes O' Plenty 时生效**（它们驱动的是 BOP 自己的 GenLayer 栈；
  本仓库的基础群系来自原版 GenLayer，没有对应机制可接）。

### Removed

- 🔴 **大陆 / 岛屿噪声场（原 `landScheme = 2` / `3`）—— 按用户要求整体删除。**
  原因：它的海洋高度是**解析剖面**，会**整列覆盖**群系自己算出的高度，
  使海洋变成一片平坦的"浴缸底"，并在大陆边界留下明显高度台阶；
  根因是当初为避免改约 300 个群系的 `rNoise` 签名而选择了"在生成器里统一覆写高度"
  （而 RWG 是把 ocean 值**传进**每个群系的地形公式）。
  **实测在游戏中不可接受**，故整块移除：
  - 删除包 `rtg/api/world/landmass/`（`ContinentalNoise` / `PoissonPointNoise` /
    `IslandPointNoise` / `PerlinNoise` / `NoiseGenerator` / `LandmassProvider` / `LandmassSettings`）；
  - 删除 `ChunkGeneratorRTG.applyLandmassOverride()`、`ChunkLandscape.continent` /
    `landmassEnabled`、`BiomeAnalyzer` 的大陆场最终裁决与 `landmassOceanBiome()`；
  - 删除 `ContinentalNoise` 的火山 / 熔岩洞地标采样（连同 `ContinentLandmarkNoise`）；
  - 删除配置项 `common.geography.landScheme`，以及 `common.landmass` 分类下的全部 8 项数值参数；
  - 删除 `/rtg verify` 命令（它整个建立在大陆场之上）与离线任务 `calibrateOceanCoverage`。
  
  **影响**：`RTGConfig` 配置项 **32 → 25**。地形管线回到"原版 GenLayer 决定群系 +
  RTG 高度混合"这一纯地形路径，不再有任何全局高度覆写。
- **火山地貌**（按用户要求）：`TerrainBase.terrainVolcano()`、
  `RTGConfig.Setting.largeIslandVolcanoChance`、`LandmassSettings.largeIslandVolcanoChance`。
  BOP 的 `volcanic_island` 群系包装保留（否则该群系拿不到本仓库的配置与地表规则），
  但其地形改为普通低缓丘陵。
- **大陆地标与火山地标机制**（按用户要求）：`ContinentLandmarkNoise`、
  `ContinentalNoise` 的 `continentVolcanoes` / `continentLavaCaves` 两套采样及
  相应的 `getVolcano*` / `getLavaCave*` 访问口、`volcano*` / `lavaCave*` 字段。
  `ContinentalNoise` 现在只负责大陆 / 岛屿的海陆距离场。
- **死代码**：`MesaBiomeCombiner`（0 引用）、`NoiseArrayPool`（0 引用，
  2.9 MB 静态分配）、`RealisticBiomeBase.oldErodedNoise()` 及其孤立的 `INV_12` / `INV_8`。
- **29 个模组的群系子包**（按用户要求只保留 BiomesOPlenty / Thaumcraft / Vanilla）：
  `RealisticBiome*` 类 523 → **129**，`src` java 文件 706 → **319**，
  `BiomeInit.java` 2061 → 466 行，`ModCompat.Mods` 枚举 33 → 4。
  全量 diff：**539 个文件变更，+1628 / −38576 行**。

### 尚未在游戏内验证

以下各项已通过离线标定或静态分析，但**雕刻类行为无法在无游戏进程下验证**：

- 地下河隧道的连通性、天窗是否按预期开口、深埋段的观感；
- 修复后隧道与地表河的关系；
- 滨海带与陆海群系修复复活后的实际观感；


详细方法、实测数字与已知限制见 `docs/terrain-port-roadmap.md`
（尤其是附录 D 的发现记录与附录 E 的自检记录），
以及 `docs/terrain-port-report.md`。

## [1.0.0] - 2023-09-15

### Added
- This is a default template changelog that follows the [KeepAChangelog Convention](https://keepachangelog.com/en/1.1.0/)
