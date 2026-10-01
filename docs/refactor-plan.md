w# RTGC 重构与优化方案

**三条要求，也是本方案的唯一判据：**

1. **对着抄** —— 一切以 RWG（`E:\模组开发\资料库\Realistic-World-Gen`，`rwg2` 分支）源码为准，不做发明。
2. **性能不退化** —— 每项改动都要有实测的 ms/区块 前后对比。
3. **地形好看** —— 判据是"能不能一眼看出这是山/这是峡谷/这是台地"，不是"代码抄得对不对"。

---

## 1. 现状诊断

### 1.0 根因（本会话新增）：噪声后端根本不是同一个

**这一条推翻了此前"逐群系调参"的全部思路。**

rtgc 的地形常数确实是**逐行照抄** RWG 的（本会话已逐个与 `rwg/terrain/*.java` 比对，见附录 B），
但两边喂给这些公式的**噪声场不是同一个函数**：

| | RWG（默认） | rtgc（改之前） |
|---|---|---|
| 后端 | **经典 Perlin**（`rwg/util/PerlinNoise.java`） | `OpenSimplexNoise` |
| 晶格 | 方格点阵，`sCurve = 3t² − 2t³` | 三角（A2）晶格 |
| `noise2` 值域 | ≈ ±0.69 | ≈ ±0.996 |
| **标准差** | **0.211** | **0.423**（恰好 2 倍） |

源码依据（不是推测）：`RwgWorldSavedData.noiseImplementation` 的**字段初值就是 `UNKNOWN`**；
`NoiseSelector.createNoiseGenerator` 把 `UNKNOWN` 与 `DYNAMICPERLIN` 都映射到
`NoiseGeneratorWrapper.useOpenSimplex = false`；于是 `NoiseGeneratorWrapper.noise2()`
转发给 `PerlinNoise.noise2()`。只有执行过 `/rwgnoise OPENSIMPLEX` 的世界才走 OpenSimplex。

**为什么 2 倍会变成灾难**：地形函数普遍对噪声**平方**，例如
`terrainHilly` 的 `m = noise * strength * river; m *= m / 35f;`。
噪声标准差差 2 倍 ⇒ 平方后差 **4 倍**。以 `strength = 120`、`terrainHeight = 68` 为例：

| | 1σ 处 | 峰值处 |
|---|---|---|
| Perlin | `m ≈ 18` | `m ≈ 120` → 山顶 ≈ 188 |
| OpenSimplex | `m ≈ 71.5` | `m ≈ 206` → 山顶 ≈ 274（撞 255 上限）|

即：**OpenSimplex 下几乎处处都是巨山**，被 `m > 70 → 70 + (m−70)/2.5` 的软顶压成一片高原，
失去了 RWG「缓丘之间偶尔冒出高峰」的层次感。

这同时解释了 `mountainCap`（rtgc 自己加的 `> 200 → 200 + (m−200)×0.75`）为什么存在——
它是在给**错误的噪声后端**打补丁。换回 Perlin 后峰值 ≈ 188，远在 200 以下，该 cap 自然失效。

**已落地的修法**：把 `rwg/util/PerlinNoise.java` **原样搬进**
`src/main/java/rtg/api/util/noise/PerlinNoise.java`——`noise1` / `noise2` / `noise3` /
`improvedNoise` / `initPerlin1` / `sCurve` 六段本体**逐字未改**（已按字符长度逐一校验相同），
只改包名与接口名，并补上 rtgc `SimplexNoise` 接口的 4 个转发 + `multiEval2D`。
`RTGWorld` 构造时**无条件**创建 `PerlinNoise`（开关已按要求删除，见 §2 Removed）。

**离线实测数字**（`gradlew calibrateNoiseBackend`，零 MC 依赖）：

| pitch | Perlin std | OpenSimplex std | std 比 | 自相关半衰比 | 平坦占比 `\|v\|<0.1` |
|---|---|---|---|---|---|
| /230 | 0.2148 | 0.4216 | 0.5095 | 1.25 | 36.3% vs 14.6% |
| /70 | 0.2117 | 0.4226 | 0.5010 | 1.13 | 36.9% vs 14.4% |
| /20 | 0.2110 | 0.4237 | 0.4979 | 1.00 | 37.1% vs 14.3% |

- **特征块头（自相关半衰长度）两者一致**（比值 0.67–1.25）⇒ 既有 pitch 常数仍然有效，**不需要重调**；
- **平坦占比 Perlin 高得多**（36% vs 15%）⇒ 这正是 RWG「山与山之间有大片缓地」的来源。

✅ **已硬编码为忠实行为**：全部逃生开关已按要求删除（用户明确表示不需要），
本改动现为唯一路径。**但尚未在游戏内目视验证。**

### 1.1 已经抄到的（不用再动）

| 内容 | 状态 |
|---|---|
| 地形函数本体 | ⚠️ 逐行对译**但非全部忠实**：本会话审计（附录 B）发现 6 个函数多套了一层 RWG 没有的 `mountainCap`，另有多处 `riverized` 包裹。已核对的忠实函数见附录 B |
| `terrainHilly` / `terrainGrasslandHills` / `terrainCanyon` / `terrainPolar` | ✅ 存在；本会话已与 RWG 逐行核对（`terrainHilly`/`terrainGrasslandHills` 仅多 `mountainCap`，`terrainCanyon` 忠实） |
| `terrainPlateau` / `terrainBryce` / `terrainPlains` / `terrainRollingHills` / `terrainBeach` / `terrainOcean` / `terrainOceanCanyon` / `terrainLonelyMountain` | ⚠️ **RWG `rwg/terrain/` 里没有这些类**（已用 `git ls-tree` 在 master + 5 个 tag 上确认）——它们是 RTG 时代发明，**没有"照抄"基准**，不能拿它们当忠实性判据 |
| RWG 有而 rtgc **没有**的函数：`TerrainMesa` / `TerrainDunes` / `TerrainSmallIsland` / `TerrainSmallSupport` | ⚠️ 未移植（mesa / 沙丘系群系因此无忠实函数可用） |
| cell 项距离量纲 | ✅ 已修正（离线逐点比对 RWG `CellNoise`：均值比 0.0259 → 0.9004） |
| 地形混合管线 | ✅ 与 RWG 同为加权平均（`ChunkGeneratorRealistic.java:505` `testHeight[...] += biomeHeight * weight`），管线一致 |

**顺带排除两个我原以为的差异**（都查过源码，不成立）：

- RWG 并没有"每列只取一个群系"——它也在加权平均；
- RWG 的 `ocean` 参数**从未在地形函数体里被使用**，20 个 `Terrain*` 里全部只是签名占位。

### 1.2 没抄到的（这就是"不好看"的原因）

| # | 缺口 | 实测证据 |
|---|---|---|
| **①** | **群系 → 地形的分配** | rtgc 129 个群系里：**25 个（19%）**用了有立体感的地形；**50 个（39%）**只用 `Plains`/`FlatLakes`/`RollingHills`；**20 个（16%）**连命名地形都没有，`return` 一段本地噪声。合计 **70/129 = 54% 是平的或平凡的** |
| **②** | **群系区域尺度** | RWG 用自己的群系场：`biomeWidth = 500`、`climateWidth = 1400`（`ChunkManagerRealistic:81-82`）；rtgc 用**原版 GenLayer**（约 200–300 格）。群系比一座山还小 → 出不了"山系"，只能出"一片小丘" |
| **③** | **山地链** | RWG 有 `RealisticBiomeMountainChain` + `mountainChainWeight`（`ChunkGeneratorRealistic:97,512`），让山脉横跨气候带；rtgc **完全没有** |

**①是主体工作量，也是最快见效的**：rtgc 没有 RWG 的 `Plains`/`Hills`/`Mountains` 三档拆分（每个原版群系只有一个包装），所以"把基础群系配成平地"等于整个世界是平的。这一点是 Team-RTG 当年移植包装时随手写的，**不是 RWG 的选择**。

### 1.3 性能（本轮自检发现）

| 项 | 状态 |
|---|---|
| 🔴 `carveRiverTunnels` 把 Voronoi 计算放在所有门控之前 ⇒ 每区块 256 列全算一遍 | **已修**：加 `landscape.river[k] < 0.25f` 前置过滤（离线实测隧道带内 river 最小 0.637，留 2.5 倍余量） |
| 🔴 大陆场 `landScheme=2` 的 `applyLandmassOverride` 逐列调 `landmass.value()`（实测 413.7 ns/列 ≈ 0.106 ms/区块） | **已删**（整个大陆场） |
| ⚠ C-4（区块边界台阶修复）让"中心群系主导"的区块改走完整加权和 | **未实测**。我此前写"开销可忽略"是**估计不是测量**——违反了自己定的性能门禁 |
| ⚠ `warpedRiverVoronoi` 每区块被算两遍（`getNewerNoise` 的河网 + `fillRiverStrengths` 的隧道），后者已被前置过滤压到约 18% 的列 | 待优化 |

---

## 2. 工作包

### WP-0 · 零代码基线（先做，当天可看）

**目的**：在动代码之前，先确认"尺度"这一项能带来多少改善，并建立性能基线。

| 步骤 | 操作 | 看什么 |
|---|---|---|
| 0.1 | 新建世界，记下出生点地形与 `/rtg probe` 输出 | 基线截图 |
| 0.2 | `common.geography.biomeSize` 从 5 → **9**（仅 BOP 路径生效，你装了 BOP），重建世界 | 群系区域是否明显变大、地形是否更像"山系"而不是"碎片" |
| 0.3 | 开启 profiler（`gradle.properties` 的 `extra_jvm_args` 填入 `-Drtg.enableProfiling=true`），跑 5 分钟，记下 `Landscape get/gen` 与 `Populate total` 的 ms/区块 | **这是后面所有改动的性能基线** |
| 0.4 | ~~三个逃生开关逐个关掉对比~~ **已作废**：按用户要求删除了全部逃生开关 | — |

**验收**：产出三张截图 + 一份 ms/区块表。**在这之前不写任何代码。**

---

### WP-1 · 群系 → 地形 重分配（对着抄，主体）

**目标**：把 54% 的平地形消掉。判据：除海滩 / 海洋 / 沼泽 / 河湖之外，**每个陆地群系都应有可辨识的起伏**。

**依据**：RWG 的地形分配规律是**每个气候带分 `Plains` / `Hills` / `Mountains` 三档**，且 `TerrainHilly` 是主力（15/54）。rtgc 没有这个拆分，所以基础群系必须按 RWG 的 **Hills 档**配。

**可直接照抄的参数**（已从 RWG 源码抽出，与 rtgc 签名 1:1）：

| RWG 群系 | RWG 写法 | rtgc 写法 |
|---|---|---|
| `WoodHills` / `DarkRedwood` / `DesertMountains` / `RedDesertMountains` / `StoneMountains` / `TestRiver` | `new TerrainHilly(230f, 120f, 0f)` | `terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f)` |
| `JungleHills` | `new TerrainHilly(230f, 120f, 50f)` | `terrainHilly(..., 230f, 120f, 50f, 260f, 68f)` |
| `Redwood` / `RedwoodSnow` | `new TerrainHilly(230f, 120f, 90f)` | `terrainHilly(..., 230f, 120f, 90f, 260f, 68f)` |
| `Desert` | `new TerrainHilly(150f, 50f, 0f)` | `terrainHilly(..., 150f, 50f, 0f, 260f, 68f)` |
| `RedwoodJungle` | `new TerrainHilly(230f, 100f, 0f)` | `terrainHilly(..., 230f, 100f, 0f, 260f, 68f)` |
| `MountainChain` | `new TerrainHilly(230f, 120f, 0f, 260f, 120f)` | `terrainHilly(..., 230f, 120f, 0f, 260f, 120f)` |
| `Oasis` / `RedOasis` | `new TerrainHilly(230f, 120f, 20f, 60f, 63f)` | `terrainHilly(..., 230f, 120f, 20f, 60f, 63f)` |
| `HighRainforest` | `new TerrainHighland(0f, 140f, 68f, 200f)` | `terrainHighland(..., 0f, 140f, 68f, 200f)` |
| `TaigaHills` / `WoodMountains` / `SnowRivers` | `new TerrainMountainRiver()` | `terrainMountainRiver(x, y, rtgWorld, river)` |
| `TundraHills` | `new TerrainMountain()` | `terrainMountain(x, y, rtgWorld, river)` |

> 注：RWG `TerrainHilly` 的 3 参数重载默认 `lakeWidth = 260f, terrainHeight = 68f`（`TerrainHilly.java:18-20`）。

**执行步骤**：

1. **出一张对照表**（先不动代码）：129 行，每行 = `rtgc 群系` → `RWG 气候带家族` → `RWG 对应群系` → `建议地形 + 参数` → `当前地形`。
   - 家族划分（从 RWG 实测）：Tundra / Taiga-Wood / Redwood / Jungle / Desert / Savanna / Oasis / Stone / Red-Mesa / Polar / Coast-Ocean-Island。
   - **先给用户过目**，认可后再改代码。
2. **按家族分批改**，每批 10–20 个群系，改完立刻新建世界看一批，确认"这批是不是出山了"。
3. 只动 `initTerrain()`；不动 `initSurface()`（地表方块是另一件事，别混在一起）。

**验收**：
- 129 个群系里"平/平凡"的数量从 **70 降到 ≤ 25**（剩下的必须是海滩/海洋/沼泽/河湖/冰原）；
- 每个家族至少一张截图，肉眼能看出起伏。

---

### WP-2 · 山地链（对着抄）

**目标**：补上 RWG 最有辨识度的特征——**山脉横跨气候带**，而不是每个群系各自为政。

**RWG 依据**：
- `RealisticBiomeMountainChain.forBiome(biome)`（`Support.java:203-210`）为每类边界群系生成山地链包装；
- `ChunkGeneratorRealistic:97,512` 的 `mountainChainWeight[]`，与 48 格邻域取最大后作为门控；
- 地形参数：`new TerrainHilly(230f, 120f, 0f, 260f, 120f)`。

**执行步骤**：
1. 移植 `RealisticBiomeMountainChain` → `rtg/world/biome/realistic/MountainChainWrapper.java`（对着抄）。
2. 在 `ChunkGenerationRTG` 加 `mountainChainWeight[256]`（对着抄 `:512` 的算法）。
3. 用该权重重选地形：权重高时把该列的地形换成山地链地形。
4. 默认**关闭**，配置项 `surface.mountainChains`，因为它是新增地物，需要先看过再开。

**验收**：一条山脉能跨 2 个以上群系边界；关掉开关后地形与 WP-1 完成时一致。

---

### WP-3 · 群系区域尺度（结构性，最后做）

**目标**：让群系区域接近 RWG 的 `biomeWidth = 500` / `climateWidth = 1400`，而不是原版的 200–300 格。

**这是唯一需要动架构的一项，也是我上次失败的地方。** 上次（已删除的大陆场）我错在**去改高度**而不是**改群系来源**——RWG 的做法是**替换群系选择本身**（`ChunkManagerRealistic.getLandBiomeAt`：气候场 → 群系场 → 按 Voronoi 单元取群系）。

**前置条件（不满足就不动工）**：
1. WP-0 的 `biomeSize=9` 实验已给出"尺度能带来多少改善"的量化答案；
2. WP-1 已完成（否则群系换了、地形还是平的，白搭）；
3. 有办法**只看不玩**地验证群系分布——即能从 `level.dat` 或离线工具读出某个区域的群系图。**没有这个就不做**（上次就是没有它才失控）。

**执行步骤**：对着抄 `ChunkManagerRealistic` 的 `getClimateValue` / `getLandBiomeAt` / `selectBiome` / `warpClimateCoordinates`，作为 `BiomeProviderRTG` 的**替换实现**，配置项 `geography.customBiomeField`（默认关闭）。

**验收**：同一片区域里群系斑块的中位尺寸从 ~250 格升到 ~500 格以上；开关关闭时与原版 GenLayer 逐块一致。

---

### WP-4 · 性能

| 项 | 动作 |
|---|---|
| **C-4 的真实开销** | 加一次性计数器统计"旧短路命中率"与"每列 `rNoise` 调用次数"，跑一个区块报告一次；据此决定是否加开关 |
| **河网 Voronoi 重复计算** | `getNewerNoise` 已经逐列算过 `getRiverStrength`（内含 `warpedRiverVoronoi`）。让 `carveRiverTunnels` **复用**结果，而不是重算 |
| **`rNoise` 内部** | `lakePressure` 每次调用 `SimplexData2D.newDisk()` 分配对象；改为复用实例（仅在有风景湖的群系生效） |
| **性能门禁** | 每项改动前后各跑一次 WP-0.3 的 profiler 流程，把 ms/区块写进本文件末尾的表 |

**验收**：所有改动完成后，`Landscape get/gen` 与 `Populate total` 不高于 WP-0 基线。

---

### WP-5 · 河道架构对齐（**本会话已完成**）

**问题**：RWG 把河道当作**混合后高度**的一次后处理（`ChunkManagerRealistic.calculateRiver:863-884`），
rtgc 却把它当成**每个群系各自在公式里做的事**，而且做了两遍。详见附录 F.4。

**已做的事**：

1. `TerrainBase.rwgCalculateRiver(x, y, riverStrength, biomeHeight, borderDistance, rtgWorld)`
   —— RWG `calculateRiver` 的逐行移植：`k = (c/W − 1)`、
   `bed = 59 + noise2(x/12)*2 + noise2(x/8)*1.5`、`h' = h*(k+1) + bed*(−k)`，
   门控与 RWG 相同（`riverStrength > 0 && height > 59`）。
2. **调用点**：`ChunkGeneratorRTG.getNewerNoise` 在混合完成、写入 `landscape.noise[]` 之后
   逐列调用一次 —— 对应 RWG 的 `ChunkGeneratorRealistic:551`。单群系路径
   （`getNewerNoiseSingleBiome`）同样处理，其 RTG 式 8 格侵蚀在开关打开时停用。
3. **`borderDistance` 复用同一次 Voronoi**：`getRiverStrength` 新增带回 `borderDistance` 的重载，
   故雕刻**不增加 Voronoi 求值**（`riverValues` 与 `riverBorders` 出自同一次 `eval2D`）。
4. **`riverized` 在开关打开时变成恒等** —— RWG 全树没有这个函数，它在 61.5 处的
   `if (height < riverSurface) return height` 硬断层就是台地化的来源。
   （25 个调用点未删，保留函数体只为逃生路径；游戏内确认无误后可整段清理。）
5. **`erodedNoise` 在开关打开时不再执行** —— 它是"按每个贡献群系各跑一遍、拉向硬编码 53"的旧结构。
6. **带宽标定**：`gradlew calibrateCellularNoise` 实测 RWG `(d2−d1)×pitch` 均值 **6.103**、
   rtgc `border×2×COORD_SCALE×pitch` 均值 **7.146** ⇒ **均值比 1.171**，
   故设 `BORDER_DISTANCE_CALIBRATION = 1.171f`。不校正的话雕刻带会比 RWG 窄 17%。

**新截面**（岸顶 `h = 80`，`bed = 59`）：

| 距河心 c（格） | 0 | 6 | 12 | 24 | 36 | 48.1 |
|---|---|---|---|---|---|---|
| `k` | −1 | −0.875 | −0.750 | −0.501 | −0.251 | 0 |
| 雕刻后高度 | **59** | 61.6 | 64.2 | 69.5 | 74.7 | 80 |

即 `h' = bed + (1+k)(h − bed)`：**从河床 59 到岸顶的线性斜坡**。
水面在 62 ⇒ 河深约 3 格，**全宽 ≈48 格（半宽 ≈24 格）**，无台面、无碟形。

✅ 已硬编码为唯一路径：`riverized` 与其 23 个调用点、`erodedNoise`（含 2 个群系 override）
以及 `mountainCap` 已全部删除。**尚未在游戏内目视验证。**

---

## 3. 铁律（每一条都是踩过的坑）

1. **没在游戏里看过的东西，不许标"完成"。**
   已删除的大陆场、以及熔岩洞地标，都是"离线全绿、游戏里不能用/看不见"。离线工具只能证明**可达性与量级**，证明不了好看。
2. **工程取舍要按"牺牲了什么观感"来算，不是按"要改几个文件"。**
   大陆场当初选"整列覆写高度"就是为了少改几个签名，代价是海洋变成平坦浴缸底。
3. **否定一个概念时，要连同承载它的整个机制确认范围。**
   "不要火山"我理解成只删一个函数，结果又去做了另一种地标。
4. **性能必须实测。"可忽略"三个字不许出现在没有数字的地方。**
   C-4 和 F-39 上我都违反了这条。
5. **每个改动都要能单独关掉，且要保留"回到改动前"的路径。**
6. **动笔前用一句话把"这一步要做什么、做完看到什么"复述给用户确认**，尤其是工作量超过 100 行时。

---

## 4. 顺序

```
WP-0 基线（零代码）
  └─▶ WP-1 地形重分配（主体，对着抄）  ──▶ WP-2 山地链（对着抄）
                                              │
        WP-4 性能（与上面并行，每步都测）      │
                                              ▼
                                   WP-3 群系区域尺度（前置条件全满足才动工）
```

**里程碑**：

| 里程碑 | 内容 | 判据 |
|---|---|---|
| ~~M1~~ | ~~WP-0 基线~~ | **用户已自行研究，跳过**；`biomeSize` 维持当前设置 |
| M2 | WP-1 对照表产出 | ✅ 已产出（见附录 A） |
| **M3** | WP-1 进行中 | 平地形从 70 降到 ≤25；**当前已完成 22 个群系** |
| M4 | WP-2 山地链 | 山脉能跨群系边界，开关可关 |
| M5 | WP-4 完成 | 性能不高于基线 |
| M6 | WP-3（可选） | 群系斑块中位尺寸 ≥500 格 |

---

## 附录 A — WP-1 已完成的地形重分配（22 个群系，全部照抄 RWG `SupportBOP.java`）

判定口径：只改**当前是平 / 平凡地形**的群系，且只落到**已验证忠实**的 rtgc 函数上。

| # | 群系 | 原地形 | 新地形（照抄 RWG） |
|---|---|---|---|
| 1 | `BOPRedwoodForest` | `riverized(floNoise, river)`（本地噪声） | `terrainGrasslandHills(80,180,13,100,38,260,71)` |
| 2 | `BOPBrushland` | `riverized(base+groundNoise+m, river)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 3 | `BOPChaparral` | `groundNoise * river + h` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 4 | `BOPLushDesert` | `riverized(minHeight + heightEffect, river)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 5 | `BOPPrairie` | `terrainPlains(200,1,30,1,maxHeight)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 6 | `BOPShield` | `terrainPlains(160,10,60,200,64)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 7 | `BOPShrubland` | `terrainPlains(160,10,60,100,65)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 8 | `BOPTundra` | `terrainPlains(160,10,60,100,66)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 9 | `BOPGrassland` | `terrainPlains(160,10,60,200,66)` | `terrainGrasslandHills(90,180,13,100,38,260,71)` |
| 10 | `BOPSteppe` | `terrainRollingHills(...)` | `terrainGrasslandHills(70,180,13,100,38,260,71)` |
| 11 | `BOPOminousWoods` | `terrainRollingHills(...)` | `terrainHilly(230,120,0,260,68)` |
| 12 | `BOPBorealForest` | `riverized(base) + (groundNoise+m)*river` | `terrainMountainSpikes()` |
| 13 | `BOPLavenderFields` | `terrainPlains(160,10,60,80,66)` | `terrainMountainSpikes()` |
| 14 | `BOPMeadow` | `terrainPlains(160,10,60,200,66)` | `terrainMountainSpikes()` |
| 15 | `BOPConiferousForest` | `terrainRollingHills(...)` | `terrainMountainRiver()` |
| 16 | `BOPSnowyConiferousForest` | `terrainRollingHills(...)` | `terrainMountainRiver()` |
| 17 | `BOPTemperateRainforest` | `terrainPlains(160,10,60,100,65)` | `terrainMountainRiver()` |
| 18 | `BOPBayou` | `terrainPlains(80,1,40,20,62)` | `terrainSwampRiver()` |
| 19 | `BOPLushSwamp` | `terrainMarsh(61.5)` | `terrainSwampRiver()` |
| 20 | `BOPMarsh` | `baseHeight + heightEffect×2` | `terrainMarsh()`（**推断**：RWG 该条 `marsh` 被注释，注释行原文是 `TerrainMarsh()`） |
| 21 | `BOPOutback` | `riverized(minHeight + heightEffect, river)` | `terrainDuneValley(300f)` |
| 22 | `BOPEucalyptusForest` | `riverized(groundNoise + h, river)` | `terrainSwampMountain(135,300)` |

新增移植（原本 rtgc **没有**这 3 个函数，这是那 5 个群系此前退化成平地/本地噪声的根因）：

| 函数 | RWG 源 | 用途 |
|---|---|---|
| `terrainSwampRiver()` | `TerrainSwampRiver.java` | bayou / lushSwamp / marsh |
| `terrainSwampMountain(h, w)` | `TerrainSwampMountain.java` | eucalyptusForest / rainforest（⚠ `kelpForest` **不属于**这一族：RWG 里它走 `RealisticBiomeBOPOcean` → 海洋地形，见 `docs/rwg-port-gaps.md` §10） |
| `terrainDuneValley(size)` | `TerrainDuneValley.java` | heathland / outback |

---

## 附录 B — 地形函数保真度审计（照抄的前提）

**往漂移的函数里抄参数是无效的**，所以每个要用到的函数都先与 RWG 逐行对过。

| rtgc 函数 | 与 RWG 的关系 | 结论 |
|---|---|---|
| `terrainHilly` | 逐行一致；3 参版默认 `lakeWidth=260f, terrainHeight=68f` | ✅ 可精确照抄 |
| `terrainGrasslandHills`(7 参) | 逐行一致（`m²/40`、`min(m/20,3.75)`、`cell×12`、`l²/25`、`max(l,8f)`） | ✅ 可精确照抄 |
| `terrainMountain` / `terrainMountainRiver` / `terrainMountainSpikes` | 逐行一致（仅多了 `mountainCap`，200 以下为恒等） | ✅ 可精确照抄 |
| `terrainCanyon` / `terrainPolar` | RWG 有同名源文件；`terrainCanyon` 本会话已修正为忠实版 | 待审计复核 |
| `terrainPlateau` / `terrainBryce` / `terrainPlains` / `terrainRollingHills` / `terrainBeach` / `terrainOcean` / `terrainOceanCanyon` / `terrainLonelyMountain` | **RWG `rwg/terrain/` 里没有这些类** —— 它们是 RTG 时代的发明，**没有"照抄"基准**，只能按效果判断 | ⚠️ 无基准 |
| rtgc **缺失**的 RWG 函数：`TerrainMesa` / `TerrainDunes` / `TerrainSmallIsland` / `TerrainSmallSupport` | RWG 有、rtgc 没有 | ⚠️ 未移植 |
| **`terrainHighland`** | **本会话已修**：漂移版改名 `terrainHighlandLegacy`（8 个调用点行为不变），新增逐行忠实的 `terrainHighland(...)` 4 参 / 5 参（`flatDetailStrength`）重载；已与 `rwg/terrain/TerrainHighland.java` 逐行对齐 | ✅ 已修 |
| **`terrainMarsh`** | **本会话已修**：主噪声 `20f`→`30f`、阈值 `8f`→`4f`、删除多余的 `h *= 2f`（对齐 `TerrainMarsh.java:13/18/21-22`） | ✅ 已修（`riverized` 包裹待定） |
| `terrainSwampRiver` / `terrainSwampMountain` / `terrainDuneValley` | 原缺失 | ✅ 本次已按 RWG 逐行移植 |

**（已作废）** 原先此处记的是"用漂移版 `terrainHighland` 时的参数换算"。忠实版已就位，直接用

```
terrainHighland(x, y, rtgWorld, river, hillStart, landHeight, baseHeight, hillWidth[, flatDetailStrength])
```

参数顺序与 RWG 构造参数**完全相同**，不再需要换算。

### 系统性发现：`riverized(...)` 在 RWG 里根本不存在

本会话核实：`riverized` 在 RWG 全树**零命中**；`rwg/terrain/TerrainBase.java` 全文只有 14 行，
内容就是 `return 70f;`，没有任何辅助函数。RWG 的河道是**两段式**：

1. 地形函数收到的是 `river + 1f`，公式里只把**山体项**乘 `river`（压山是内置的）；
2. 真正的河道由 `ChunkManagerRealistic.calculateRiver(...)` **在外面单独挖**，再用 `fade` 混合
   （`ChunkGeneratorRealistic.java:503 / 505 / 550-554`）。

而 rtgc 在 **14 个地形函数内部**还包了一层 `riverized(...)`（另有 13 处调用点分散在群系文件里）。
存在**双重压平**的嫌疑。是否删除须等河道管线逐一比对后决定，不凭推测动手。

---

## 附录 C — WP-1 剩余目标（下一步）

| 组 | 数量 | 阻塞原因 |
|---|---|---|
| 需要 `terrainHighland` 的（mapleWoods / seasonalForest / cherryBlossomGrove / fen / tropicalRainforest / woodland / sacredSprings / wasteland…） | 约 10 | 先修 `terrainHighland` 的漂移 |
| 需要 `terrainMarsh` 的（bog / deadSwamp / flowerField / quagmire / wetland / oasis…） | 约 6 | 先修 `terrainMarsh` 的漂移（注意：沼泽**本来就该平**，需逐个人工判断是否真要改） |
| 需要 `terrainSwampMountain` 的其余（rainforest；`kelpForest` 已改走海洋地形） | 1 | 已具备函数；`rainforest` **当前不是平地形**，需单独判断该不该动 |
| BOP 的 rollingHills / plains 家族（moor / mysticGrove / mapleWoods / orchard / pasture / landOfLakes / snowyForest / snowyTundra / wasteland / coldDesert / bambooForest / redwoodForestEdge / xericShrubland / brushland…） | 约 20 | 需在 RWG `SupportBOP` 里找到对应条目 |
| 原版（Vanilla）的平地形群系（forest / birchForest / flowerForest / roofedForest / jungle / taiga / coldTaiga / megataiga / icePlains…） | 约 20 | 用 RWG 通用家族：`WoodHills`=`terrainHilly(230,120,0,260,68)`、`TaigaHills`=`terrainMountainRiver()`、`JungleHills`=`terrainHilly(230,120,50,260,68)`、`SnowHills`=`terrainMountainSpikes()`<br>⚠ **例外**：`minecraft:jungle` **不能**用 `JungleHills` 家族 —— RWG `Support.java` 对 `BiomeGenBase.jungle` 有一条 **active 显式条目**（`TerrainHighland(0f,140f,68f,200f)` + `SurfaceGrassland(jungle.topBlock, jungle.fillerBlock, stone, cobble)`），已按它改回；见 `docs/rwg-port-gaps.md` §0.5.4 C6 |

---

## 附录 D — 性能记录表

| 日期 | 改动 | Landscape ms/区块 | Populate ms/区块 | 备注 |
|---|---|---|---|---|
| — | 基线 | 待填 | 待填 | 用户已跳过 WP-0，**目前没有任何实测数字** |
| 本会话 | WP-1 批 3：7 群系 → 忠实 `terrainHighland` | **未实测** | **未实测** | ⚠️ **成本上升项**：忠实版每列多一次 `cellDistance(cellularInstance(0).eval2D(...))`（Voronoi），漂移版用的是 `simplexInstance(4).noise3f`。加上随后批 4 新增的，共 **10 个群系**落在这个函数上 |
| 本会话 | WP-1 批 4：12 群系 → 照抄 RWG | **未实测** | **未实测** | 其中 5 个转 `terrainMarsh`（纯 simplex，比原 `terrainRollingHills` 便宜）、2 个 `terrainSwampMountain`、1 个 `terrainSwampRiver`、1 个 `terrainDuneValley`、3 个 `terrainHighland`（见上行） |
| 本会话 | `terrainMarsh` 拨回忠实版 | **未实测** | **未实测** | 只改常量与删除一个乘子/一层 `riverized`，反而**少**一次 `bayesianAdjustment` |

**`eval2D` 成本的量级估计（非实测，仅供参考）**：`SpacedCellularNoise.eval2D` 每次调用会
`new Point` + `new VoronoiResult`，并查 8 个邻接方格的缓存；缓存未命中时 `generatedAreaPoints`
要 `new Random` + `boolean[100]` + `Point2D.Double[25]` + 25 次最小距离插入。
缓存按 `Point` 键、容量 256，而在 `x/70` 这类尺度下 16×16 的区块只覆盖 1~2 个方格，
**故命中率高、不会抖动**；每列净成本主要是 8 次 map 查询 + 约 200 次距离运算。
相对 simplex 单次调用仍是十几倍量级。**必须实测后再决定是否保留。**

## 5. 性能记录表（每项改动后填写）

| 日期 | 改动 | Landscape ms/区块 | Populate ms/区块 | 备注 |
|---|---|---|---|---|
| — | 基线 | — | — | 未测；WP-0 被跳过 |

---

## 附录 F — 地形函数保真度与河道架构审计（本会话，逐行对 RWG 源码）

判定口径：**RWG 源文件为唯一基准**。RWG 的 `rwg/terrain/` 在 `rwg2` / `master` + 5 个 tag 上
都只有 19–20 个文件，数值本体跨版本一致。

### F.1 忠实（可直接照抄参数）

| 函数 | RWG 源 |
|---|---|
| `terrainCanyon` | `TerrainCanyon.java`（本会话已修正为逐行一致）|
| `terrainSwampRiver` | `TerrainSwampRiver.java` |
| `terrainSwampMountain` | `TerrainSwampMountain.java` |
| `terrainDuneValley` | `TerrainDuneValley.java` |
| `terrainHighland` | `TerrainHighland.java`（含 `flatDetailStrength`；cell 频率 `x*INV_70 ↔ x/70D` 正确）|

### F.2 仅多一层 `mountainCap`（RWG 无此压缩）

`terrainMountain` / `terrainMountainRiver` / `terrainMountainSpikes` / `terrainHilly` /
`terrainGrasslandHills`(10 参) / `terrainGrasslandMountains`(5 参)。
影响：>200 的峰被 ×0.75；`terrainHilly` 最坏情况 −32 格。
**注**：换到 Perlin 后峰值降到 ≈188，这条的实际影响大幅下降（见 §1.0）——
`mountainCap` 本来就是给错误噪声后端打的补丁。

### F.3 其它偏离

| 函数 | 偏离 |
|---|---|
| `terrainGrasslandFlats` | `*35f` vs RWG `*70f`（平方后差 4 倍）；`sm` 被 `blendedHillHeight` 变成恒正；缺 RWG 的湖底项 `l`；多 `riverized`。仅 MushroomIsland 用 |
| `terrainHighlandLegacy` | `noise3f`(实例 4) 顶替 RWG 的 `cell.noise`；多 `h *= river`；细节幅度 4/2/1 vs RWG 5/3/1.5；返回 `getTerrainBase(river) + (h+baseAdjust)*river` vs RWG `base + h`。ExtremeHills / ExtremeHillsM / ColdTaigaHills 用 |
| `terrainPolar` | `Math.max(st, 0.1f)` vs RWG `0.2f` → 沙丘幅度减半；多 `riverized` |
| `terrainForest` | `+20f` 项漏乘 `river`（RWG `TerrainSmallSupport.java:18`）|
| `terrainOcean` / `terrainOceanCanyon` | 基高 40/50 与 `Math.max(flo, 20.01f)` 钳制；RWG 的 `TerrainFlatLakes` 是 `62f + h` 且无钳制 |
| `terrainMarsh` | 数值本会话已拨回 RWG（`30f` / `4f` / 去掉 `×2f`），仅剩 `riverized` 包裹待定 |
| `terrainGrasslandHills`(9 参) / `terrainGrasslandMountains`(8 参) | 两段最偏离的函数，且 **`src/` 内零调用者** —— 维护陷阱，建议删除 |

### F.4 河道架构（独立审计）

RWG 是**两段式**：函数收到 `river + 1f ∈ [0,1]`（**0 = 河心**）只压山体项；
真正的河道由 `ChunkManagerRealistic.calculateRiver`（`:863-884`）**对混合后的高度执行一次**：

```
k   = cell.border(pX/1250, pY/1250, 50/1300, 1)   // [-1,0]，-1 在河心
bed = 59 + noise2(x/12)*2 + noise2(x/8)*1.5       // 59 ± 3.5
h'  = h*(k+1) + bed*(-k)                          // 线性混合到河床
```

水面在 y=62 ⇒ 河深约 3 格；`fade`（`:552-553`）是**山地链**混合，不是河道淡出。
（同时纠正了此前"`riverSample` 是每区块 4 样本缓存"的假设：它只是**一个** `float[4]`
临时缓冲，逐列失效，不跨列复用。）

rtgc 则是**按每个贡献群系各跑**两段，之后再加权混合：

1. `riverized`（公式内）：目标 **61.5 水平面**，且 `if (height < 61.5) return height`
   在 61.5 处制造**硬断层 → 台地化**；乘子还依赖该列自身高度 `d = h−61.5`。
2. `erodedNoise`（公式后）：目标 **53 硬编码 `LAKE_BOTTOM`**。

两者叠加区在 `riverStrength ≳ 0.7`。带宽：rtgc 乘子带 **±47 格** vs RWG **±104 格**；
侵蚀带 **±13.5 格** vs RWG **±24 格**。

**结论**：rtgc 的截面是「宽缓河漫滩被压掉 30–50% 起伏 → 停在水平台面 → 中间地带缺失 →
一条窄深槽」，即**双峰/碟形**；RWG 是单一渐变的 V 形谷、河床 59。
这条尚未动手（列为 WP-5），因为它需要新增一个"对混合后高度执行一次"的后处理阶段。

### F.5 helpers 全部没有 RWG 对应

`rwg/terrain/TerrainBase.java` 全文 14 行，内容就是 `return 70f;`。RWG 全树 grep
`blendedHillHeight|groundNoise|riverized|hills(|minimumOceanFloor` **零命中**。
故 `blendedHillHeight` / `hills` / `groundNoise` / `riverized` / `mountainCap` /
`bayesianAdjustment` 都是 RTG 时代产物，**不是 RWG 的东西**。

### F.6 `ocean` 参数确认未使用

逐个检查 20 个 RWG `Terrain*` 文件：`ocean` 只在签名里出现，**函数体中从未被使用**。
此前"RWG 用 `ocean` 塑形海床"的假设被彻底否定。

---

## 附录 E — 本会话核实的事实（含被推翻的旧假设）

| # | 事实 | 证据（文件:行） | 影响 |
|---|---|---|---|
| E1 | `riverized` 不存在于 RWG | RWG 全树 grep `riverized` 零命中；`rwg/terrain/TerrainBase.java` 全文 14 行、仅 `return 70f` | 14 个地形函数多了 RWG 没有的河道后处理，疑双重压平 |
| E2 | RWG 真实路径 `useDistance = true` | `rwg/world/ChunkGeneratorRealistic.java:149-150`、`rwg/world/ChunkManagerRealistic.java:139-140` 均显式 `cell.setUseDistance(true)`，且 `distanceMethod = 0` ⇒ `√(dx²+dz²)/√2` | **C-5 的 `cellDistance` 换算方向正确**，首次拿到源码级依据（此前只有统计比值） |
| E3 | RWG `SupportBOP.java` 有 **8 条整块被 `/* */` 注释**的条目 | `alps` `:55-59`、`arctic` `:61-65`、`crag` `:231-237`、`grassland` `:378-383`、`marsh` `:525-530`、`steppe` `:745-750`、`thicket` `:766-771`、`wasteland` `:864-869` | 引用这些条目**不是照抄**，属"作者意图推断"；相关代码注释已逐条改标 |
| E4 | RWG 的 **vanilla 群系映射不可照抄** | `rwg/support/Support.java` 只用 `baseHotDesert` / `baseTemperateForest` / `baseJungle` … 等 **RWG 自己的** `RWGBiomes.base*` 分类；vanilla `Biomes.*` 不参与地形选择 | 附录 C 原"原版群系用 RWG 通用家族"一行**不成立**；vanilla 的地形选择是 rtgc 原创，**没有照抄基准**，不应按推测改动 |
| E5 | RWG 有、rtgc 没有的地形函数 | `rwg/terrain/` 下有 `TerrainMesa` / `TerrainDunes` / `TerrainSmallIsland` / `TerrainSmallSupport`；rtgc `TerrainBase` 无对应 | mesa 系群系无忠实函数可用 |
| E6 | `TerrainHilly` 3 参构造的默认值 | `rwg/terrain/TerrainHilly.java:18-20` ⇒ `lakeWidth = 260f, terrainHeight = 68f` | 照抄 3 参写法时 rtgc 需显式补齐这两个值 |

### 本会话被推翻的假设

| 假设 | 为何错 |
|---|---|
| "`VanillaDesert` 跑 `terrainPolar` 是误用，应改成 RWG `desert\Desert` 的 `TerrainHilly(150f,50f,0f)`" | 见 E4：RWG 没有"MC 沙漠"这个对应物，`baseHotDesert` 同时供 `desert\Desert`、`red\Mesa`、`red\RedDesertMountains`、`savanna\MesaPlains` 使用。改了只是换一个猜测 |
| "`VanillaForestHills` 应照抄 RWG 的 `WoodHills`" | RWG 的 `WoodHills` 是**主**森林群系（对应 rtgc 的 `VanillaForest`，已经是对的）；MC 的 `ForestHills` 在 RWG 里没有对应物 |

---

## 附录 G — 🔴 第二根因：`rNoise` 把 RWG 的 `river` 反相

> 与 §1.0（噪声后端）并列的两条根因之一。完整数据表见 `docs/rwg-port-gaps.md` §11。

RWG：`return terrain.generateNoise(perlin, cell, x, y, ocean, border, river);` —— 原样透传。

rtgc：`rNoise` → `newrNoise` 挂了一整套 RTG 河道／湖泊变换
（`lakePressure` → `lakeToRiverProportions` → `riverAdjustedforDepthDifference` → `riverFlattening`）。
默认配置下实测右列**精确等于 `1 − 左列`**（可复算：`tools/river-convention.ps1`）：

| RWG 输入（0=河心，1=内陆） | 地形实收 |
|---|---|
| 0.00 | 1.0000 |
| 0.50 | 0.4734 |
| 1.00 | 0.0000 |

而这套变换的输出**恰好等于 `landscape.river[k] = -riverValues[k]`**，即 rtgc 的旧约定
（1 = 最强河流）。所以它是**为旧约定写的转换器**；把 117 个群系改成 RWG 原始公式
（`m = noise * strength * river`，陆地 = 1）时忘了拆掉它 ⇒ 地形被整体反相：

- 内陆 `river = 0` ⇒ `m` 项**被完全抹平** ⇒ 丘陵山脉消失、世界成平板；
- 河心拿到满幅 ⇒ 沿河长出山墙。

**这就是"公式照抄了却不像 RWG"的直接原因。** 已修复（改为透传 + 删除整套机制）。
副产品：每列少一次 `cellularInstance(0).eval2D`。

**遗留**：`surface` 路径仍用旧约定（1 = 河心），与 RWG 的 `river + 1f` 不同 ——
属 surface 审计的独立欠账，见 `rwg-port-gaps.md` §11 末尾。

### 关于"可删的遗产辅助"

`blendedHillHeight` / `hills` / `groundNoise` / `getTerrainBase` / `bayesianAdjustment`
**不是死代码**：可达性分析（`tools/reachability.ps1`）测出它们分别有 8 / 1 / 5 / 1 / 10
处**文件外**调用。此前"已无用可删"的判断建立在名字计数上，是错的。
