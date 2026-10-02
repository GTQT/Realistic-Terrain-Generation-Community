# rtgc 相对于 RWG 的**照抄缺口清单**

基准：`E:\模组开发\资料库\Realistic-World-Gen`（`rwg2` 分支）。
口径：**「照抄」= 与 RWG 源码逐字/逐行一致**；凡标注 ⚠ 的是**有意偏离**（原因已写明），
凡标注 ❌ 的是**尚未抄**。

> ## ⚠ 先读这一段：本文档是**按轮次追加**的，越靠前越旧
>
> - **§1–§9 的 ✅/❌ 表是最早一轮的缺口快照**，里面绝大多数 ❌ **已经做完**
>   （边界池 / SMALL / 岛屿池、山地链、`areBiomesViable`、`findBiomePosition`、
>   极端边界镜像、`mountainChainRiverHost` … 都已实现）；
> - §10–§29 里散布的 `### 仍未做` 同理，**写完就当轮或下一轮做掉了**；
> - 历史章节保留原样，是为了留下**判断过程与踩过的坑**（那才是这份文档的价值）。
>
> **判断"还剩什么"只看 §0.5。** 本会话已经因为照旧章节误报过两次欠账。

---

## 0. 火山与地标：**已按用户要求写回**（此前的"唯一豁免"作废）

> ⚠ **状态变更（1.0.33 同版本追记）**：本节原先写的是"用户明确要求删除，故不作为缺口"
> （`Support.volcanoIsland`、`canGenerateVolcanoAt`、`hasRiverNearVolcano`、
> `getVolcanoBaseHeight/UnderlyingHeight/UnderlyingBiome`、`getVolcanoCoordinates/VicinityCoordinates`、
> `getLavaCave*`、`LavaCaveLandmark`、`LandmarkDecorations`、`ContinentLandmarkNoise` 的使用，
> 以及用 `RwgLayoutConfig.averageLandmarksPerTypeAndContinent = 0` 把整支关掉）。
> **用户随后要求「写回火山的全部内容」**，所以上表那些符号现在**全部已实现**：

| 组成 | rtgc 落地位置 | RWG 出处 |
|---|---|---|
| 火山锥 / 岩缘 / 熔岩口 / 山顶门控 | `rtg/world/biome/realistic/land/RealisticBiomeIslandVolcano.java` | `rwg/biomes/realistic/ocean/RealisticBiomeIslandVolcano.java` |
| 火山渣地表 | `rtg/api/world/surface/SurfaceVolcanoAsh.java` | `rwg/surface/SurfaceVolcanoAsh.java` |
| 热带岛小火山（地图生成钩子） | `rtg/world/gen/MapVolcano.java` ＋ `IRealisticBiome.rMapGen` / `RealisticBiomeBase.generateMapGen` ＋ `RealisticBiomeBOPTropicalIsland.rMapGen` | `rwg/map/MapVolcano.java` ＋ `RealisticBiomeBase:186-201` ＋ `RealisticBiomeIslandTropical:56-74` |
| 岩浆房 / 火山通道 | `RealisticBiomeIslandVolcano.generateMagmaChamber`（生成器在**结构之后**调） | `ChunkGeneratorRealistic:247-250` |
| 熔岩洞地标 | `rtg/world/gen/LavaCaveLandmark.java` | `rwg/map/LavaCaveLandmark.java` |
| 地标装饰（深板岩柱/拼图大师/暮色门/发光蘑菇） | **不移植**（用户裁定"彻底删掉，什么都没有，别判断模组行不行"） | `rwg/support/LandmarkDecorations.java`（四类装饰全部依赖 1.12.2 不存在的模组） |
| 火山/熔岩洞的选址与门控 | `RtgBiomeLayout.getVolcanoXxx/getLavaCaveXxx/canGenerateVolcanoAt/hasRiverNearVolcano/isBorderlessAt/getNoiseWithRiverOceanAt` | `ChunkManagerRealistic:402-476/607-655/859-861/945-963` |
| 生成器接线（高度叠加 / 地表分支 / 岩浆房 / 熔岩洞 / 地标装饰 / mapgen 调度） | `ChunkGeneratorRTG` | `ChunkGeneratorRealistic:216-233/247-257/513-549/697-743/1022/1056-1058` |
| 开关 | `RwgLayoutConfig`：`largeIslandVolcanoChance = 0.15f`、`averageLandmarksPerTypeAndContinent = 0.25f`（RWG 原值） | `ConfigRWG:36-37` |

**四处已记录的偏离**（都不是"省略"，是 1.12.2 没有对应物 / 用户裁定）：
1. **`LandmarkDecorations` 整类不移植**（用户裁定，原话："**就是彻底删掉，什么都没有，别判断模组行不行**"）：
   RWG 的这个类产生四类可选装饰（深板岩柱 / LootGames 拼图大师 / 暮色门 / Natura 发光蘑菇），
   全部依赖 1.12.2 不存在的模组。用户先裁定"深板岩 → 黑曜石"，继而要"凑不齐就什么都别生成"，
   最后明确"彻底删掉、别判断模组行不行" ⇒ **类已删除**，`ChunkGeneratorRTG` 里连
   `landmarkDecorations` 字段与调用点一并移除，**不做任何 `Loader.isModLoaded` 判断**（也不留半成品）。
   `LavaCaveLandmark`（熔岩洞本体 + 通风口锥体 + 冒烟草）**不受影响**：它零可选模组依赖，
   冒烟草用 BOP 的 `biomesoplenty:grass`（BOP 在场时才有值，与 RWG 同义）。
2. **熔岩洞的"标记群系"未落地**：RWG 用 BOP 的 `phantasmagoric_inferno` 标开口列
   （`markLavaCaveOpeningBiome`），而 BOP 7.0.1.2445 **既无该群系类也无该 lang 名**（已核对 jar）⇒ 跳过，不发明群系。
3. **`isBorderlessAt` 的桶宽**：RWG 写死 `float[256]`（它自己的现实主义编号空间）；rtgc 改用
   `RtgRealisticIndex.idFor(...)` 与 `biomeIdBound()`（REID 下 MC 编号可 >256）。
   ⚠ 不能用 `baseBiomeId()`：山地链与其备份群系共用同一 MC 编号，会被折叠成一个桶。
4. **mapgen 去重**：RWG 用独立的 `mapGenBiomes[256]` 标记数组（用完清 0）；rtgc 照同一判据
   （中心列 `smallRender[312]`）读，再用本区块的 `activeBiomeIds`（天然无重复）去重 ——
   不复用/清零 `smallRender`（清掉会破坏金字塔）。

**实测**：`gradlew calibrateVolcanoPlacement`（`src/preview`）给出开关打开后的实际密度 ——
种子 123456789、20000² 窗口内 **4 座火山中心**（最近邻间距 p50 ≈ 7925 格）、火山锥列占 0.048%、
熔岩洞中心 6 个（间距 ≈ 8165 格）⇒ 开关确实生效，且与 RWG 的"每大陆每类 0.25 个"稀疏度同量级。
火山群系**不进任何布局池**（`RtgBiomeCategorizer` 已加排除：RWG 的 `Support.volcanoIsland` 也从不 addBiome）。

---

## 0.5 ⚠ 剩余欠账（**唯一权威 —— 判断当前状态只看本节**）

> 这份文档是**按轮次追加**的勘查记录：同一件事在 §10–§29 里会被反复提起，
> 而写下的 `### 仍未做` 小节**不会回头改写** —— 其中好几条在当轮或下一轮就做掉了。
> 后果是**照着旧小节标题判断"还剩什么"必然会误报**：本会话已经因此误报过两次
> （`RealisticBiomeMountainChain.gentleFraction` 的装饰缩放、`rDecorateAfterIce`
> 都**已经实现**，却仍被当成欠账报给用户）。
>
> 因此：**下文所有 `### 仍未做` 一律视为"写那一刻的快照"，不是待办清单。**

### 0.5.1 真正的剩余欠账

| # | 欠账 | 性质 | 现状 | 依据 |
|---|---|---|---|---|
| 1 | 装饰的**逐装饰器强度缩放**：RWG 把邻域权重当 `strength` 传给 `rDecorate`，约 32 个装饰类按它缩放循环次数 | 需给 `IRealisticBiome.rDecorate` 补 `strength` 形参（跨接口，改动面大） | ⚠ **邻域分摊本身已经做了**（别再把整件事当欠账）：`ChunkGeneratorRTG.populate` 按 RWG 原式累加权重（9×9、`+24` 偏移、`RWG_DECO_NEIGHBOUR_WEIGHT = 0.01234569f`）再**按权重概率性**调用相邻群系的 `rDecorate` —— 即 §17③ 的**选项 (c)**。剩下的差异只是"单区块确定性会变"与"被调到的那次是满强度、不是分数强度" | §16 §17③(c) §19 |
| 2 | `terrainDunes` 与 `terrainPolar(int,int,RTGWorld,float)` **零调用者** | RWG 忠实移植、**待接线**（不是死码，`reachability.ps1` 会一直报） | 保留 | §12 §20 §21 |
| 3 | 约 13 个群系的地形落点是**推断**（RWG 对它们没有显式地形行） | 能力缺口 | 已逐个写出依据，集中在 §13/§25 的落点表 | §13 §25 |
| 4 | `LARGE_ISLAND` 池为空（RWG 成员 `fungiForest` / `hotPlainsCanyonIsland` 在 1.12 无对应物） | 能力缺口；`selectIslandBiome` 逐级退化到核心池，不会返回 null | 保持 | §18 |
| 5 | RWG `RealisticBiomeBOPOcean.sanitizeKelp` 的**列 metadata 重写**（8/9/10/11 四段） | 1.12 的 BOP 没有对应的方块状态/API | 只做"站不住就换回水"（`OceanDecorationSanitizer`） | CHANGELOG |
| 6 | 火山 + 地标 | ~~唯一豁免（用户裁定删除）~~ → **已按用户要求写回** | **已实现**（锥体/地表/岩浆房/熔岩洞/地标装饰/开关 + 生成器接线），四处偏离见 §0 | §0 |
| 7 | RWG `RealisticBiomeCoastDunes.rDecorate` 的 `DecoWaterGrass`（海草 / 树叶 / 高草，`11 × strength` 次）未移植 | 需要新写一个 `DecoWaterGrass`（1.7.10 的 `Blocks.double_plant`＋metadata、`canBlockStay` 都要映射到 1.12 的 `IBlockState` / `canPlaceBlockAt`） | **未做**：rtgc 全仓 grep `DecoWaterGrass` = 0 命中；4 个海滩只有各自原有的植被装饰 | 地表审计（`RealisticBiomeCoastDunes:24-40`） |
| 8 | 这 10 个群系不吃 `SURFACE_TOP_BLOCK` / `SURFACE_FILLER_BLOCK` / `SURFACE_CLIFF_*` 配置 | RWG 的这几个 `rReplace` **把方块写死**（沙/砂岩/圆石/石头），移植时照抄 | 有意：改 cfg 无效，且这是唯一忠实做法（`SurfaceCoastDunes` / `SurfaceOcean` 的 javadoc 已声明） | 地表审计 |
| 9 | **约 88 个群系的"地形家族"是最近亲推断，且其中几条只对齐了一半**（地形照 RWG 某类，地表却留在别的类上） | RWG 对这些 MC/BOP 群系**没有**显式条目，只能取"最近亲"；工具**判不出**该取哪个家族 | **部分未做**：二次自检已修 5 处（§0.5.4 的 C5、C8–C11）；仍待复核的见下方清单 | 独立审计（第二遍） |
| 10 | **死类 + RWG 有而 rtgc 零调用的地表**：~~`RealisticBiomeBOPVolcanicIsland.SurfaceBOPVolcanicIsland`（零调用）~~、`RealisticBiomeVanillaIcePlains.SurfacePolar`（零调用）、`BOPKelpForest.TerrainBOPKelpForest` 的死构造参数；~~RWG 的 `SurfaceVolcanoAsh` **未移植**~~ | 历史遗留 / ~~火山豁免~~ | **火山那两项已消**：`SurfaceVolcanoAsh` 已移植并接线，`RealisticBiomeBOPVolcanicIsland` 整个类已删除（RWG 对 BOP 火山岛只有一个包装 `RealisticBiomeIslandVolcano`）。剩下两项仍不影响生成，但**会误导审计**（本次就被 `SurfaceBOPVolcanicIsland` 那条自相矛盾的注释带偏过一次） | 独立审计 C12 / §0 |

**第 9 条里"仍待复核"的清单**（都不是"冰雪/沙漠配错气候"那类实害，属**依据不足**）：

| 群系 | 现状 | 问题 |
|---|---|---|
| `VanillaColdTaigaM` / `JungleEdgeM` / `JungleM` / `RoofedForestM` | `terrainGrasslandMountains` + `SurfaceMountainStone(0.6f)` | 该函数在 RWG 只属 `savanna/RealisticBiomeSavannaForest`（草原森林族），给这 4 个"变体"用属跨族借用 |
| `VanillaExtremeHillsPlus(+M)` / `JungleEdge` / `JungleHills` | `terrainHilly(230,120,**50**,260,68)` | `50` 这个 lakeDepth 在 RWG 只属（**未被任何池引用**的）`land/RealisticBiomeJungleHills` |
| `VanillaMushroomIslandShore` | `terrainMarsh` + `SurfaceGrassland` | RWG 没有这个现实主义群系（只包了 `mushroomIsland → TerrainSmallIsland`）；岸边用沼泽地形无依据 |
| `VanillaSavannaM` / `SavannaPlateau` / `SavannaPlateauM` | `terrainMesa` + `SurfaceMesa` | 注释已写"最近亲是 Mesa"，但 RWG 的 savanna 族是 `TerrainGrasslandFlats` / `TerrainDuneValley` |
| `BOPSnowyForest` | `terrainHighland` + `SurfaceGrassland` | RWG 雪族一律配 `SurfaceMountainSnow`（`k>110` 落雪）⇒ 可能少了山顶积雪 |
| `BOPFlowerIsland` | `terrainIslandTropical` | 归类 COLD、地形取自 RWG 的 WET 岛（不与地形族冲突，但值得复核） |
| `BOPCrag` | 地形取 RWG **注释掉的** crag 条目、地表取 canyon 的 active 条目 | 已在下游注释标明"有意混用两条条目"（§13） |

### 0.5.2 与 RWG 的**有意口径差异**（不是欠账，别再当待办翻出来）

| 项 | RWG | rtgc | 为什么 |
|---|---|---|---|
| 雪带浅海 | `baseOceanCold`（温度 0.5，**不结冰**） | 原版 `frozen_ocean`（结冰） | rtgc 的槽位**按群系温度自动归类**，温度 0.5 的群系占不住 SNOW 槽；且雪带海面结冰更符合原版观感 |
| 雪带深海 | `baseOceanCold` | `rtgc:deep_ice_ocean`（`Kind.ICE`，温度 0.0） | 同上 |
| 冷带深海 | 原版 `deepOcean` | 原版 `deep_ocean`（**照抄**） | 海底神殿 `StructureOceanMonument.SPAWN_BIOMES` **只认 `deep_ocean`**，换掉会让它彻底消失 |
| 海洋装饰 | `rDecorate` 空 + `decorateBaseBiome=false`（完全不装饰） | `DecoCollectionOcean` + 默认原版装饰 | 原版 `Biome.decorate` 里**带矿物生成**，关掉会连矿一起没 |
| 5 个海滩群系 | RWG 没有"以 MC 海滩为原型"的现实主义群系 | 进 `CORE`（像普通陆地群系一样出现） | 否则它们是**永远不出现的死群系**；RWG 的海岸只有 `coastIce` / `coastDunes`（已接好） |
| 海滩的**方块与名字不符** | 无对应（RWG 只有一个不区分材质的 `CoastDunes`） | 5 个海滩（含 `BOPWhiteBeach`）都用 `SurfaceCoastDunes` ⇒ `Stone Beach` 脚下是沙/砂岩/圆石、`Gravel Beach` 脚下是沙 | 这是"给 MC 海滩群系配 RWG 地形"的必然结果，**不是 bug**：RWG 的暖海岸地表本来就只有一种配方（沙），`topBlock`/`fillerBlock` 在 rtgc 管线里根本不被读取 |
| 海洋群系数量 | RWG 自己注册 `baseOceanIce/Cold/Temperate/Hot/Wet/Oasis` 6 个 | rtgc 注册 6 个 `RtgOceanBiome`（ICE/COLD/HOT/WET 各就位 + 深/浅两份包装） | RWG 的 `baseOcean*` 是**它自己的源码定义**，可照抄；此处是照抄而非发明 |
| 崖壁判定 | `CliffCalculator.calc(x, y, noise)`（3 参，纯四邻高差取最大） | `TerrainBase.calcCliff(x, z, noise, river)`（4 参，**多一条**：高度落在海平面带内且 `river > 0.85` 时取四邻最小 ⇒ 河口岸边不出崖壁） | 这是 RTG 上游为解决"河口一圈全是崖"加的，rtgc 全线沿用（24+ 处调用点）；已在 `SurfaceGrassland` / `SurfaceMountainStoneMix1` 的 javadoc 记录 |
| **暗河的适用范围** | **只有山地链**（`mountainChainRiverHost > 0.10`）—— 链只出现在气候交界，实测 7.7% 的列、8 个「气候×方向」里只有 3 个填得满 ⇒ 普通的山一滴暗河都没有 | 门控取大：`max(链宿主, 山体门控)`，山体门控 = `smoothstep((附近最大干高度 − 68)/24)` ⇒ 链内行为不变，链外的山也有暗河（有效阈值约 **73** 格；`UndergroundRiver.MIN_SURFACE=76` 只留给洞厅/天窗的"上方够不够厚"） | **用户明确要求**的增强，不是照抄（RWG 没有这条）。实现必须用**干高度**（河道雕刻前），否则重演 F-39：隧道带 ⊂ 河网带、地表被压到 ≈59，用已雕刻地表做高度门控永不成立 |
| **暗河密度** | 隧道带宽 `9/1250`（9 格）⇒ 只有 2.93% 的列落在带内；山体阈值 76（有效 ≈81） | 带宽 **`25/1250`（25 格）** ⇒ 8.06%；山体阈值 **68**（有效 ≈73）+ 同区块 3×3 ±8 格"最大干高度"（防侧翼断开） | 用户反馈"很多山里都没暗河"。联合命中（链∧河网∧隧道\|交汇）**0.230% → 0.611%**；复跑 `gradlew calibrateRiverTunnels` 可验 |
| 暗河与地表水的联通 | **水面恒在水面高度 62**（`blockY <= 62 ? water : air`），洞体也以 62 为中心 ⇒ 地表低于洞顶（≈73）的列**洞顶天然冒出地表** = 河口/峡谷，从河面直接划船进山 | **已回到 RWG 原样**（1.0.33 追记）：中心写死 62、洞厅基准 63、水面恒 62 | 中途我加过"按地表整体下移"，造成三件事：水面接不上、河床被掏成深沟、永远只能潜水进 ⇒ 已撤销；自造的"显式联通口"也一并删掉（RWG 的机制本来就覆盖它） |
| 洞顶藤蔓的长度 | RWG 只算到"洞顶那一格空气"，**摆放交给 EFR 的 `WorldGenCaveVines`**（`Et-Futurum-Requiem`，1.7.10 专属，本仓库**无从照抄**它的长度规则） | `RiverCaveVines.placeVine` 自己定：长度 = `1..可用空间` 随机，上限 = **水面之上那一格（y=63）** ⇒ 最长的一株正好垂到水面；干洞里则以洞底为上界 | 用户要求"让藤蔓长一点，最好最长能到水面，当然是随机长度"。隧道段可用 1–8 格（洞顶 = `62 + round(√tunnel × 8)` ≤ 70）⇒ 均值 ≈4.5 格、约 1/8 的藤触水；洞厅段洞顶可到 80–100 ⇒ 长藤帘。已核对原版 `BlockVine`：纯洞顶藤（只有 `UP`）既不会掉也不会自己变长 ⇒ **生成多长就是多长** |

### 0.5.3 已被**推翻 / 关闭**的旧结论（下文还在，但已作废）

| 旧结论 | 位置 | 现状 |
|---|---|---|
| "**深海缺口在 1.12.2 里补不上**，跨气候借用就是正确适配" | §14 末尾（`### D5 的**否定结论**`） | **已推翻**。缺的不是"MC 有没有更多海洋群系"，而是"RWG 的 `baseOcean*` 没有对应物"；`RtgOceanBiome` 就是 `rwg/biomes/base/BaseBiomeOcean.java` 的逐行移植（RWG 自己的源码定义）。3 个深海 + 3 个浅海已注册 |
| "D5 已由用户裁定结案，不再追" | §15 `### D5：海洋群系不足` | 用户后来**重新打开**这一项（"三个都做"），故已实施；该小节是快照 |
| "RWG 里 kelp / coral **只是 COLD 浅海的别名**；把 BOP 的海带森林/珊瑚礁接上去是**偏离** RWG" | §15 与 `RtgBiomeCategorizer` 旧注释 | **两句都错**。`SupportBOP.java:39-47` 就在同一次 `Support.init()` 里（`Support.java:177-179`）把这两个钩子覆盖成 BOP 的海带森林 / 珊瑚礁 —— 那才是 RWG 装 BOP 时的真实行为 |
| "kelpForest / coralReef 是海洋群系，进浅海槽位" | 旧日志 `COLD/HOT/WET 浅海=biomesoplenty:kelp_forest` | **已修**：它们**不占槽位**，只做 patch（`SupportBOP` 的语义） |
| "镜像核心池对群系选择是**恒等变换 / 逐点相同**" | §14.1 第 2 条 | 已由 `calibrateBorderPools` **实测推翻**：分布相同、**逐列 19.73% 不同**（§14.4 已改写） |
| §8「有意的偏离汇总」第 1/2/5/8/9 行 | §8 | 全部已消或已改写，见 §8 的新表 |
| "`BOPWhiteBeach` 属于冷海岸（用 RWG `coastIce` 的地形/地表）" | §13 落点表（本节上方） | **已推翻**：BOP `white_beach` 实测温度 **1.00**/雨量 0.95（热带白沙），按 RWG 自己的规则（`temperature < 0.15f` 才用 coastIce）它属于 **coastDunes**。此前接成 `terrainCoastIce + SurfaceGrassland(packed_ice×3, ice)` ⇒ **热带地表出现浮冰**；已改回 `terrainCoastDunes + SurfaceCoastDunes` |
| "洞顶的 `surface-10` 钳制被 `max()` 顶回去 —— 这是**顺序 bug**，要修正" | 旧 CHANGELOG 条目与本类旧注释 | **已推翻**：那是有意的，正是 RWG 让地下河在地表低处与外界河**联通**的机制。连同"整条隧道按地表下移"一起撤回（§0.5.4 C13） |

### 0.5.4 本轮自检：接错排查（两遍：我的机械检查 + 一次独立审计）

起因是用户问了一句"White Beach 是你加的？"，顺着查出**一整个类别的接错**：某个群系用的地形/地表
**是 RWG 的，但不是 RWG 给它的那一份**。这类错误骗过了此前所有检查器 ——
`terrain-wiring-check` 只问"有没有走 RWG 地形函数"，`surface-wiring-check` 只问"是不是共享地表类"，
两者对"配对错了"都无感。于是补工具 + 做两遍（第二遍是一个子代理**独立**从 RWG 源码重推配对）：

| 工具 | 做什么 |
|---|---|
| `tools/terrain-surface-audit.ps1`（新） | 逐群系把「代码里的地形函数 + 地表类」与 ① 该群系在 `run/logs/latest.log` 里的实测温度/降雨/标签 ② `docs/_rwg_support_map.csv` 里 RWG 的显式条目**同时**对照；输出 `docs/_terrain_surface_audit.csv` |
| `tools/rwg-support-map.ps1`（改） | 重新抽取时**多抽一列 `Terrain`** —— 此前只有 `Surface`，所以"地形配对"根本没被比对过 |
| `tools/surface-wiring-check.ps1`（改） | RULE2：12 个海岸/海洋群系**必须**用对应的 `SurfaceCoastIce` / `SurfaceCoastDunes` / `SurfaceOcean`（含浅海标志），并在匹配前剥注释 |
| `tools/rwg-placement-check.ps1`（改） | 支持 `SupportTC` 的**按显示名**解析（`"Magical Forest".equals(...)`）；并修掉一个自己的 bug：把 `RWGBiomes.baseRiverXxx`（河道参数）误当成群系名，导致两条假"缺口" |

**结果**：43 个在 RWG `Support*.java` 里有显式条目的群系（含 TC 的 2 条），**配对不一致 = 0**；
气候 × 地表的矛盾标记只剩 `RealisticBiomeVanillaDesert` 那条**已记录的** `terrainPolar` 当沙丘配方（§20）。
两遍一共修掉 **11 处**（详情见 CHANGELOG「地表审计 / 自检接错」两节）：

| # | 群系 | 原来 | 现在 | RWG 依据 |
|---|---|---|---|---|
| C1 | `VanillaDeepOcean`（冷带深海） | `SurfaceMountainSnow(…, SAND, 0.2f)` | `SurfaceOcean(config, false)` 砾石 | `Support.java:172` `oceanDeepCold = RealisticBiomeOcean(deepOcean, false, …)` |
| C2 | `VanillaFrozenOcean` | 同上（只有顶层 1 格沙） | `SurfaceOcean(config, true)` | `Support.java:156-160` `oceanShallowSnow` |
| C3 | `VanillaOcean` | 同上（现不占槽位，只在回落后用） | `SurfaceOcean(config, true)` | 同上，与其余海洋群系统一 |
| C4 | `BOPWhiteBeach`（`white_beach`，温度 **1.00**） | `terrainCoastIce` + 浮冰地表 | `terrainCoastDunes` + `SurfaceCoastDunes` | `ChunkManagerRealistic:590-593`：`temperature < 0.15f ? coastIce : coastDunes` |
| C5 | `VanillaColdBeach`（`cold_beach`） | `SurfaceGrassland(packed_ice×3, ice)` | `SurfaceCoastIce`（新，逐行移植） | `RealisticBiomeCoastIce` **覆写了 rReplace**（L58-92）：雪 + 砾石，只有崖壁是浮冰/冰；被抄的那个 `surface` 字段在 RWG 里**没人读** |
| C6 | `VanillaJungle`（`minecraft:jungle`） | `terrainHilly(230,120,50,260,68)` | `terrainHighland(0,140,68,200)` | `Support.java:116-126` 给 `BiomeGenBase.jungle` 的条目；原注释引的是 RWG **另一个**群系 `JungleHills` |
| C7 | `BOPMountainPeaks`（`biomesoplenty:mountain`） | `terrainMountain` + `SurfaceTundra` | `terrainMountainRiver` + `SurfaceMountainStone(top,filler,true,sand,0.75f)` | `SupportBOP.java:576-588`（`// MOUNTAIN`，active 条目） |
| C8 | `VanillaIcePlainsSpikes`（`mutated_ice_flats`） | `terrainMountainSpikes` | `terrainHighland(0,140,68,200)`（地表**不动**） | `Support.java:127-139`（`icePlainsSpikes = icePlains.biomeID + 128`）；原注释引的是 `SnowHills` |
| C9 | `VanillaSunflowerPlains`（`mutated_plains`） | `terrainGrasslandFlats` | `terrainMarsh(waterSurfaceTop, river)` | `Support.java:140-152`（`sunflowerPlains = plains.biomeID + 128`）；原注释写"RWG 无 MC 平原对应物"——**前提不成立** |
| C10 | `VanillaSavanna` | 地形已接 RWG Savanna、地表留在 `SurfaceGrassland` | `SurfaceGrasslandMix1(grass, dirt, sand, stone, cobble, 13f, 0.27f)` | `savanna/RealisticBiomeSavanna.java:35-42` —— 同一份配对**只接了地形那一半** |
| C11 | `TCMagicalForest`（`magical_forest`） | `terrainSmallSupport`（那是 RWG 给 **Tainted Land** 的）＋ 表里没有它 ⇒ 落 CORE | `terrainSwampMountain(135f,300f)` ＋ `RWG_PLACEMENTS` 补 `magicalforest`/SNOW/SMALL_ISLAND | `SupportTC.java:38-50` |
| C12 | 注释与实际不符（不算生成改动，但会误导审计） | `BOPAlps` 无标注、`BOPCrag` 说"改用 canyon 配方"、`BOPVolcanicIsland` 说"火山渣地表保持不变"、`TCEerie` 无标注 | 逐条改成与代码一致，并写明"推断/未移植/死类" | 独立审计 B-3/B-4/C3/C11 |
| C13 | **地下河"变少 + 一格水线"**（用户实机反馈）：① RWG 把隧道中心**写死 62**，前提是"链一定很高（≥83）"，而 rtgc 的链是中低山地（地表 60–80）⇒ 洞体整段跑到地面之上、地下挖不到东西；② 我顺手删掉的 `ceiling <= floor` 守卫让"一格水在石头里"的退化列被记录，而**离玩家最近的往往正是这个最外侧薄边** | 隧道**按地表下移**（地下一定有）＋**显式联通口**（见 C14）＋两条守卫（退化列跳过**且不记录**） | 与 RWG 的关系：门控/走线/宽度/层高/洞厅/天窗**逐位一致**，只有"摆放方式"是适配。实测几何见 `gradlew calibrateRiverTunnels`；退化列占比 0.175% | 用户实机反馈（"逗我呢，暗河变少、水面只有一层"）→ 三条原因逐条定位 |
| C14 | **"我要的是水面接通"**（用户实机反馈）：联通口此前把水灌到**地表**（河床 ≈59），而外面的水停在 **62** ⇒ 洞里水面比外面低 3 格，看着不连通。另有：`/rtg tunnels` 指令按用户要求**移除** | 联通口改为灌到 **`WaterLevel.waterSurfaceTop()`（默认 62）** 并把洞体凿到水面那一层 ⇒ **洞里与外面的水同一个水面平面**（顺河游进去即可）；触发条件仍是"地表已降到水面及以下"（= 本来就是水：河床/湖底/海底）。指令：删掉 `CommandTunnels` 与它专用的 `cachedLandscape()`（不留死码），`/rtg` 只剩 `whereami` / `probe` | 前者是**用户明确要求**的"水面"语义（RWG 靠"水面固定在 62"顺带做到）；后者是用户要求删命令 | 用户实机反馈 + 明确指令 |
| C15 | **刷日志 + 河床变悬崖**（用户实机反馈）：① `[RTG-DECOPROF]` 用 `nanoTime() - start()` 算耗时，而 `start()` 在**计时关闭**时返回 0 ⇒ 算出绝对值（日志里 `28420861ms`）永远超阈值 ⇒ **每区块刷一行**（实测 3560 行里 3287 行）；② 联通口对整条 25 格宽的隧道带生效 ⇒ 河床被挖成两边垂直岩壁的深沟 | ① 加 `ChunkGenerationProfiler.isEnabled()` 守卫（注释写清为什么必须有）；② 新增 `CONNECT_MIN_TUNNEL_STRENGTH = 0.7f`，只有带中心线附近开口 ⇒ 变成几格宽的**落水洞** | 都是 rtgc 自己引入的缺陷（RWG 无 DECOPROF、也无联通口）。"水下接通"是几何本身：进水口在水面以下，气道仍在岩下 ⇒ 想在水面之上也有洞口需要"隧道抬升"，属发明，待定 | 用户实机反馈（"为什么我的游戏在刷日志 / 河床两边变成悬崖"）|
| C16 | **"在河里划船，不就该直接划进山吗"**（用户实机反馈）：我把洞体"按地表整体下移"（`center = min(62, surface-21)`）—— 这是**三件坏事的共同根因**：① 水面跟着洞心掉（洞里 29–60，接不上外面 62）② 洞底被挖到 y≈34（**河床被掏成两边深沟**）③ 洞顶恒在地表下 10 格（**永远只能潜水进**） | **撤销下移、回到 RWG 原样**：`center` 写死 **62**、洞厅基准 **63**、水面恒 `y <= 62 ? water : air`；**删掉我自造的"显式联通口"与 `CONNECT_MIN_TUNNEL_STRENGTH`**（RWG 机制本来就覆盖它）。于是地表低于洞顶（≈73）的列**天然开口成河口/峡谷** ⇒ 从河面直接划船进山。两条守卫保留（只挡"一格水缝/悬空水"） | 与 RWG 的关系回到"几何逐行照抄"，只剩三处用户要求的差异：门控扩展（普通山）、隧道带 9→25、守卫 | 用户实机反馈（"难不成还得潜水进去"）+ `gradlew calibrateRiverTunnels` 的三列对照表 |
| C17 | **"山外河与普通河接壤不自然：宽度不同、对河床的处理也不同"**（用户实机反馈）：隧道是 25 格宽、直壁、平底的**方块开挖**，地表河是 `50/1300` 的**平滑高度混合** + 噪声河床 59±3.5 ⇒ 门控一刀切处是一个 25 格宽、十几格深的**钝头** | 断面按门控权重**渐隐**：`taper = mountainHost`（边缘 0、内部 1），`tunnelCurve = √tunnel × taper` ⇒ 尾部逐格变浅、不足一格被守卫跳过，硬开挖平滑交回给地表河；水面仍恒 62 | **链内部 taper=1，与 RWG 逐位一致**，只有最外那圈过渡带改变。不动地表河（`calculateRiver` 一行未改） | 用户实机反馈（"其余都满意，就是接壤不太自然"）|
| C18 | **"暗河会侧切山体（水面齐平处被侵蚀进去）…收一点点"**（用户实机反馈）：那个凹槽的高度就是**洞顶起伏**（`62 + round(√tunnel × 11)` = 水面以上最多 11 格）。它是 RWG 的规则（洞顶冒出地表 ⇒ 河口），也正是"能划船进山"的原因 | `UndergroundRiver.ROOF_RISE` **11 → 8**（咬痕 −27%，划船头顶仍 ≥6 格；河口上限 73 → 70）。`UndergroundRiver.FLOOR_DROP` 与**洞厅段的 `surface-10` 未动**（保留"暗河出口盆地"的观感） | 属 rtgc 微调（RWG 是 11）；已与断面渐隐（C17）一起，使侧切既保留现实感、又不过分 | 用户实机反馈（"虽然挺符合现实…收一点点"）|

**这个工具的边界（别过度信任）**：只有 43 个群系在 RWG `Support*.java` 里有显式条目；
其余约 88 个是"rtgc 用 RWG 的通用家族近似"（§12/§13 的落点表），
工具只能对它们做**气候/家族一致性**检查（冰配方配暖群系、沙配方配雪带、海岸族配内陆族……），
**判不出**"该用哪个通用家族"。那部分已连同独立审计的具体条目写进 §0.5.1 第 9 条。

**两遍自检实际跑过的 6 类检查（都可复跑）**：

| # | 检查 | 命令 | 结果 |
|---|---|---|---|
| 1 | 每个群系是否接共享地表 + 12 个海岸/海洋是否用对了**哪一个** | `tools/surface-wiring-check.ps1` | `shared=130 still-inner=0 no-new=1` + `RULE2 PASS(12)` |
| 2 | 每个群系是否路由进 RWG 地形函数 | `tools/terrain-wiring-check.ps1` | **131/131** |
| 3 | ① 气候×地表矛盾 ② RWG 显式条目的**地形+地表**配对 | `tools/terrain-surface-audit.ps1` | 43 个显式条目 **0 mismatch**；气候矛盾只剩 §20 那条已知偏离 |
| 4 | 有没有群系被**静默顶掉**（`RTGAPI.RTG_BIOMES` 是 `Map<Biome,…>`，同一 MC 群系两个包装 ⇒ 后者顶掉前者） | `_terrain_surface_audit.csv` 按 `Key` 分组 | **无重复 Key**（131 个包装 ↔ 131 个不同 MC 群系） |
| 5 | 有没有"写了但从未注册"的包装类 | `BiomeInit` 里的 `new RealisticBiome*` ↔ 磁盘类名互查 | 无缺；`MountainChain`（运行时合成）与 `RtgOcean`（`init_rtgc_oceans` 注册）属预期不在那份清单里 |
| 6 | `RWG_PLACEMENTS` 每一行是否都有 RWG 出处 | `tools/rwg-placement-check.ps1 -RwgSupportDir … -TableFile … -RtgcSpecific …` | **PASS**：copied=22、rtgc-only=1、缺口=1（`fungiforest`/LARGE_ISLAND） |
| 6 | `TerrainBase` 里还有没有不可达函数 | `tools/reachability.ps1` | 只剩 `terrainDunes`（§0.5.1 第 2 条，RWG 忠实移植待接线） |

### 0.5.5 地下暗河：逻辑封装（1.0.33 追记，**不是行为变更**）

起因是用户一句"封装一下地下暗河相关吧，别到时候写乱了"。暗河在 C13–C18 六轮修补里前后改了
门控、断面、守卫、天窗，代码却一直摊在 `ChunkGeneratorRTG` 里（常量 + `carveRiverTunnels`
+ `dryHeightHost` + `smoothstep` + 探针读数），确实到了"再改一处要看五个地方"的程度。

| 项目 | 现在在哪 |
|---|---|
| 常量（洞心 62 / 洞顶起伏 8 / 洞底下探 4 / 洞厅留厚 10 / 地表基准 76 / 门控下限 0.10 / 干高度 68 / 3×3 步长 8） | `UndergroundRiver`（`public static final`，带"RWG 原值是多少、哪几处是 rtgc 微调"的注释） |
| 四级判定链 + 断面几何 + 两条守卫 + 天窗 + 灌水 | `UndergroundRiver.carve(ChunkPrimer, int, int, ChunkLandscape)` |
| `dryHeightHost`（干高度门控）与 `smoothstep` | 同上（`private static`）；`smoothstep` 在全工程**只有暗河用**，故随之下移 |
| 山体门控读数（`max(链宿主, 干高度门控)`） | `UndergroundRiver.mountainHostAt(...)`，**开凿与 `/rtg probe` 共用同一函数**（此前 `probeTunnelColumn` 抄了一份 `dryHeightHost` 调用，是"读数与实现漂移"的隐患） |
| 调用点 | `ChunkGeneratorRTG.generate` 内 `UndergroundRiver.carve(...)`，仍**在地表替换之后**（顺序理由见该类 javadoc：地表替换按 `depth` 计数涂刷，先开凿会把隧道底刷成草/沙） |
| 顺带清掉的死重量 | 生成器字段 `riverStrengths`（每列复用的小数组改为开凿内的局部变量）、`carveRiverTunnels` 里从未被读的 `mpos.setPos(...)` |

**"没改行为"是怎么验的**（而不是嘴上说）：迁移是逐行搬移，只做了 4 处等价改写
（`air` 局部变量 → 静态常量、`center` 局部变量 → `CENTER_Y`、`63` → `CHAMBER_BASE_Y`、
`0.70f`/`24f`/`0.40f`/`0.15f` → 具名常量）；搬移后复跑全套：

| 检查 | 结果 |
|---|---|
| `gradlew build` | BUILD SUCCESSFUL |
| `gradlew calibrateRiverTunnels` | 河网带 56.373%、隧道带 8.0586%、交汇盘 2.4162%、合并 8.9170%、链列 7.686%、联合命中 **0.61118%**、天窗 0.00040%、退化 0.376%/1.547% —— **与本轮封装前逐个数字相同** |
| `tools/surface-wiring-check.ps1` | `RULE2 PASS (12)` |
| `tools/terrain-wiring-check.ps1` | 131/131 |
| `tools/terrain-surface-audit.ps1` | `matched=43, mismatched=0` |
| `tools/rwg-placement-check.ps1` | PASS（copied=22、rtgc-only=1、缺口=1 `fungiforest`） |
| `tools/reachability.ps1` | 仍只有 `terrainDunes` DEAD |

**给后来人的规矩**：要改暗河的密度/几何/守卫，**只进 `UndergroundRiver.java`**；
`ChunkGeneratorRTG` 那边只有一行调用 + 一行"必须在地表替换之后"的注释。
若新增"暗河专用的"读数或工具，请像 `mountainHostAt` 一样**复用**该类函数，不要另抄一份。

---

## 1. 群系布局 `ChunkManagerRealistic`

> ⚠ 本节的 ❌ 表是最早一轮的快照，其中绝大多数已实现 —— 见文档顶部的横幅与 §0.5。

| 项 | 状态 |
|---|---|
| 气候场（`getClimateValue` / `warpClimateCoordinates` / `getClimateFromValue`） | ✅ 逐行 |
| 选择器（`sampleBiomeSelector` / `selectBiome` / `selectCombinedBiome`） | ✅ 逐行 |
| 气候边界 + 极端边界判定 | ✅ 逐行 |
| `getLandBiomeAt` 三段（small → border → core） | ✅ 逐行，但 small/border 池为空 |
| 大陆场 / 海洋值（`getContinentValue` / `getTerrainOceanValue`） | ✅ |
| 海洋 / 岛屿 / 滨海 / 海岸决策树 | ✅ 逐行 |
| 河道族（`getRiverStrength` ×2 / `calculateRiver` ×2 / 隧道 / 交汇） | ✅ 逐行 |
| 5 个噪声基础类（`CellNoise` / `NoiseGenerator` / `PoissonPointNoise` / `IslandPointNoise` / `ContinentalNoise` / `ContinentLandmarkNoise`） | ✅ **逐字符一致**（归一化掉有意改名后） |
| **`rebuildExtremeBorderMountains` 未被调用** | ❌ 极端边界池为空 → 山地链完全不存在（fall through 到普通边界） |
| **`RealisticBiomeMountainChain`** | ❌ 未移植 |
| **合成群系槽位** | ❌ rtgc `RTGAPI.RTG_BIOMES` 按 MC 群系 ID 索引，一个群系只能挂一个现实主义群系；RWG 用 `super(0, baseBiome, …)` 合成槽位 |
| **`mountainChainWeight` / `nearbyMountainChainInfluence` / `fade`** | ❌ 未移植（山地链列**不被河道雕刻**的豁免逻辑） |
| **`mountainChainRiverHost` 作隧道门控** | ❌ rtgc 用「干高度 ≥ 76」替代（⚠ 有意替代，Stage 2 应换回） |
| **BORDER / COLD_BORDER / HOT_BORDER 池** | ❌ 全空 → 没有边界群系 |
| **SMALL / ISLAND / SMALL_ISLAND / LARGE_ISLAND 池** | ❌ 全空 → 无小型群系、无岛屿群系 |
| `isBorderlessAt` / `getNoiseAt` / `getBiomesGens` / `getBiomesGensData` / `getMetaBiomeAt` / `getPlacementAt` / `getBiomesFor` / `getConfiguredBiomes(Categories)` | ❌ 未移植（RWG 的 `WorldChunkManager` 覆写层） |
| `areBiomesViable` / `findBiomePosition`（RWG：后者恒 null） | ❌ 未覆写（仍走原版 GenLayer） |

---

## 2. 地形函数 `rwg/terrain/*`

**RWG 有、rtgc 没有：**

- ✅ `TerrainMesa`、`TerrainDunes`、`TerrainSmallIsland`、`TerrainSmallSupport` —— **已移植（A1）**。
  常量序列与 RWG 源比对通过；差异仅三类且可解释：`simplexInstance(0)` 的实例下标、
  C-5 的频率约定（`cell.noise(x/P,y/P,1)` ↔ `eval2D(x/P,y/P)`）、以及 RWG 用命名字段而我内联常量
  （值完全一致，含 `TerrainSmallIsland` 的 `58f/58f` 下限钳制）。
- ✅ RWG 原样的 `terrainFlatLakes`（`return 62f + h`、**无** `minimumOceanFloor` 钳制）
  与 `terrainPolar`（常量 160/35/60/50、下限 **0.2f**、返回 `70f + h`）—— **已移植（A2）**。
  **名字冲突已解决**：rtgc 原有那个 RTG 版 `terrainFlatLakes` 改名 `terrainFlatLakesRTG`
  （它的两个调用方 VanillaRiver / VanillaFrozenRiver 同步改名）。

**rtgc 有、RWG 没有（照抄口径下应删或替换）：**

- ❌ `terrainPlateau`、`terrainBryce`、`terrainPlains`、`terrainRollingHills`、`terrainBeach`、
  `terrainOcean`、`terrainOceanCanyon`、`terrainLonelyMountain`、`terrainHighlandLegacy`
- ❌ `terrainFlatLakes`（rtgc 这份是 RTG 实现；RWG 的 `TerrainFlatLakes` 本体其实是 rtgc 的 `terrainOcean`）
- ❌ `terrainGrasslandHills`(9 参) 与 `terrainGrasslandMountains`(8 参)：**最偏离且零调用者**

**已知未修正的偏离：**

- ✅ `terrainGrasslandFlats`：**已按 RWG 拨回（A2）**——`*35f`→`*70f`、去掉 `sm` 上的
  `blendedHillHeight`（它把 [-1,1] 映射成约 [0.29,1.32]，使 `sm` 恒正且带偏置）、
  补上原先**整个缺失**的湖底项 `l` 并改为 `return baseHeight + h + m - l`。
- ✅ `terrainForest`：**已补 `* river`（A2）**；两个调用方（TCEerie / TCMagicalForest）
  已迁到 RWG 原样的 `terrainSmallSupport`。函数本身待 A3 删除。
- ✅ `terrainOcean`（3 个海洋群系）**已迁到 RWG 原样的 `terrainFlatLakes`（A2）**，不再用调用方传入的
  `averageFloor`（原先 40/50）与 `minimumOceanFloor` 钳制。函数本身待 A3 删除。
- ✅ `terrainPolar` 的 `0.1f` 下限：RWG 原样版（`0.2f`）已作为 4 参重载加入（A2）；
  rtgc 那个 9 参泛化版仍是 `0.1f`，其唯一调用方 VanillaDesert 属 A3 的迁移范围。
- ❌ `terrainHighlandLegacy`：3D simplex 顶替 RWG 的 `cell.noise`、多 `h *= river`、细节幅度 4/2/1 vs 5/3/1.5
- ❌ `terrainOceanCanyon`：基高 30f 硬编码（RWG 是 `base + b`）+ 多余的 `minimumOceanFloor` 钳制；
  两个调用方（BOPCoralReef / BOPKelpForest）按 RWG `SupportBOP` 应分别迁走
- ❌ `terrainMarsh`：数值已拨回忠实版，但**现在没有群系在用**（marsh 系被分类到各气候 core）

**RTG 遗产（RWG 的 `TerrainBase` 只有 `return 70f`，无任何辅助函数）：**

- ❌ `blendedHillHeight`、`hills`、`groundNoise`、`bayesianAdjustment`、`getTerrainBase()` / `getTerrainBase(river)`
- ❌ `terrainRiverized` 系列已删；但 `TerrainBase` 旧河道族（`getRiverStrength` / `fillRiverStrengths` /
  `rwgCalculateRiver` / `rwgRiverBed`）**世界生成已不再调用**，仍是死重量

---

## 3. 群系分类 `Support*.java`

- ❌ 边界池 / small 池 / island 池未填（同 §1）
- ⚠ 气候归类是**规则推导**（RWG 逐群系手写）：实测 SNOW=13 / COLD=53 / HOT=20 / WET=27，
  而带面积是 14% / 30% / 28% / 28% ⇒ **COLD 偏多、HOT 偏少**，规则需要修
- ⚠ **海洋群系数量不足**：rtgc 只有 1 个深海（`VanillaDeepOcean`）+ 4 个浅海，
  RWG 期望 4 深 + 4 浅 + 海藻 + 珊瑚 ⇒ 四气候的深海被兜底成同一个 `deep_ocean`（占世界 52% 的海洋全是同一种）
- ❌ `Support.addBiome` 的 `SMALL` 语义（同时进 4 个气候的 small 池）与 `BiomePlacement` 11 种用法的完整对应
- ❌ `SupportEBXL` / `SupportTC` / `SupportCC`（rtgc 只支持 BOP）

---

## 4. 生成器 `ChunkGeneratorRealistic`

- ❌ 整体结构未照抄：rtgc 是在**自己的**生成器上改接线，不是移植 RWG 的生成器
- ❌ `fade` / 山地链河道豁免（同 §1）
- ❌ `getNoiseAt`（山地链装饰用）
- ⚠ `getNewerNoiseSingleBiome` 仍走旧河道（`useSingleBiome` 默认 false，惰性）

---

## 5. 装饰

- ❌ `EtFuturumCaveVines` 的**原版门控** `hasMountainChainNearby`：rtgc 替换成「本列被开凿过山间河洞」
  （⚠ 有意替换，Stage 2 应补回原版邻域判定）
- ❌ `LargeTreeWorldgenCompat`、`WitcheryWorldgenCompat`（后者依赖山地链邻近判定）
- ⚠ 方块替换：RWG 用 Et Futurum Requiem 的 `ModBlocks.CAVE_VINE`（1.7.10 专属）→
  rtgc 用原版 `Blocks.VINE`（**用户确认**）

---

## 6. 噪声实现的可切换性

- ❌ `NoiseSelector` / `NoiseGeneratorWrapper` / `RwgWorldSavedData` 的「Perlin ↔ OpenSimplex 可切换」机制
  （⚠ 按用户要求硬编码为经典 Perlin；RWG 允许 `/rwgnoise` 切换）

---

## 7. 其它 RWG 内容

- ❌ `MapAncientRuins`、`MapGenAncientVillage`、`DecoBlob` 等 RWG 自定义结构/装饰
- ❌ `SnowheightCalculator`、`CliffCalculator`、`CanyonColor`、`TerrainMath` 等 util
- ❌ `RWGPreviewTool`（预览/诊断工具）
- ⚠ `ConfigRWG` 的**可配置性**：rtgc 硬编码为 `RwgLayoutConfig` 常量（按用户要求不再加配置开关）

---

## 8. 有意的偏离汇总（照抄口径下这些是"欠账"）

| # | 偏离 | 原因 | 现状（截至 `1.0.33` 同版本追加；欠账以 §0.5 为准） |
|---|---|---|---|
| 1 | 河道仍有两套：布局的（生效）与 `TerrainBase` 旧族（死） | 分期切换 | **已消**：旧族随 D1 整族删除（§16） |
| 2 | `rwgRiverBed()` 派生自 `WaterLevel`，布局里用字面 `59f` | F-41 水位单一真相源 | **已消**：旧族删除后只剩布局的 `59f` |
| 3 | 火山 / 地标整体缺席（**当时**为**用户要求**） | 用户后来要求写回 | **已消**（1.0.33 同版本追记）：全部按 RWG 移植接线，开关恢复 0.15 / 0.25，实测密度见 §0 |
| 4 | `RwgLayoutConfig` 硬编码（不做逃生开关） | **用户要求** | 不消 |
| 5 | 海洋槽位兜底（跨气候借用同一个海洋） | rtgc 曾只有 1 个深海 | **已消**：6 个 `RtgOceanBiome` + `frozen_ocean` / `deep_ocean` 填满 8 个槽位，`fillMissingOceanSlots` 只在真缺群系时才生效 |
| 6 | 气候归类用规则而非手写 | rtgc 有约 130 群系、无对照表 | 保留；偏斜已按 **Forge 自己的标签**修正（§15 D3） |
| 7 | 缓存键按**逐列**（`pack(worldX, worldZ)`） | RWG 字面即是逐列且无 `>>4`；逐列在语义上永远安全 | 不消 |
| 8 | 装饰期"区块原点群系"决定整块装饰 | rtgc 既有结构 | **已消（近似）**：装饰群系改从**布局**取，并按 RWG 的 9×9 / `0.01234569` 权重**概率式分摊**给相邻群系（§19/§26 与 `ChunkGeneratorRTG.populate`）；只有"逐装饰器按 `strength` 缩放"没做 ⇒ §0.5.1 第 1 条 |
| 9 | 群系边界失去噪声混合 | RWG 的 Voronoi 单元(500) ≫ RTG 的混合窗口(±84) | **已消**：按用户"恢复 RTG 噪声混合"的要求补回 `randBiome` 抖动（§28/§29.2） |

---

## 9. 当前已验证生效的部分

> 数字是**当时**的日志快照。最新一次运行（`1.0.33`，`-Drtg.debugLayout`）的核心池大小是
> **SNOW 13 / COLD 39 / HOT 22 / WET 25**，山地链 4 个变体，`无法分类=0`。

- 群系选择：`RtgBiomeLayout`（`BiomeProviderRTG` / `BiomeProviderBOP` 的 `getBiome`）
- 河道全族：`RtgBiomeLayout`
- 布局后改写：**无**（`BiomeAnalyzer` 三阶段 + 风景湖阶段均已删除）
- 实测（`latest.log:188-194`）：4 个气候核心池 13/53/20/27，无空池 ERROR，`无法分类=0`

---

## 10. A3 迁移进度（群系 → RWG 地形函数）

### ⚠ 一条必须记住的教训：清单必须排除注释

先前那份「32 个群系在用 RTG 自造函数」是**错的**——我的正则匹配到了 `//return terrainPlains(...)` 这类
**被注释掉的旧调用**。`VanillaSavanna` / `VanillaPlains` / `VanillaSunflowerPlains` 三者的真实地形是
`65f + groundEffect.added(...)`，与 `terrainPlains` 无关。**排除注释后真实目标是 22 个。**

同理，任何 `return` 抽取都必须先跳过 `//` 开头的行。

### 已迁移（7 个，全部带依据注释）

| 群系 | 原（RTG 自造） | 新（RWG） | 依据 |
|---|---|---|---|
| `BOPKelpForest` | `terrainOceanCanyon` | `terrainSwampMountain(135f,300f)` → **随后改回 `terrainOcean(shallow=true)`** | ⚠ **本行的依据是错的、已更正**：`SupportBOP` 里 `TerrainSwampMountain(135f,300f)` 属于 **bambooForest / eucalyptusForest / fungiForest**，而 `kelpForest` 走的是 `RealisticBiomeBOPOcean`（`SupportBOP.java:39-43`），它继承 `RealisticBiomeOcean.rNoise` = 海洋高度（浅海 52 / 深海 34）。故现在接的是 `terrainOcean(shallow=true)` |
| `BOPSnowyForest` | `terrainRollingHills` | `terrainHighland(0f,140f,68f,200f)` | 照抄 `SupportBOP` `frostForest`（BOP 1.12 的 snowyForest） |
| `BOPMoor` | `terrainRollingHills` | `terrainMountainRiver()` | 照抄 `SupportBOP:561-566` `garden`（注释「GARDEN (MOOR TERRAIN)」） |
| `BOPSnowyTundra` | `terrainPlains` | `terrainGrasslandHills(90,…,71f)` | 照抄 `SupportBOP` `tundra` |
| `VanillaIcePlains` | `terrainPlains` | `terrainFlatLakes` | 照抄 RWG `land\RealisticBiomeTundraPlains` |
| `VanillaRiver` | `terrainFlatLakesRTG` | `terrainMountainRiver()` | 照抄 RWG `land\RealisticBiomeSnowRivers` |
| `VanillaFrozenRiver` | `terrainFlatLakesRTG` | `terrainMountainRiver()` | 同上 |

### 已迁移（17 个，全部带依据注释）

第二批（海岸 / 岛屿）：RWG 的这四类地形**不在 `rwg/terrain/` 里**，而是作为 `rNoise` 覆写
**手写在群系类中**。已逐行提成 `TerrainBase.terrainCoastDunes / terrainCoastIce /
terrainIslandTundra / terrainIslandTropical`（打印核对：与源逐行一致，连 RWG 那个
**算了却从未使用**的 `st` 都原样保留）。

| 群系 | 新（RWG） | 依据 |
|---|---|---|
| `VanillaBeach` `VanillaStoneBeach` `BOPGravelBeach` `BOPOriginBeach` **`BOPWhiteBeach`** | `terrainCoastDunes` | 照抄 `coast\RealisticBiomeCoastDunes.java:43-63`。⚠ `BOPWhiteBeach` 是**后来更正**加入的（原文把它和 `VanillaColdBeach` 一起放在 coastIce 那一行，与本行自己写的规则"RWG 在 `baseBiome` 温度 < 0.15f 时用 coastIce"矛盾 —— BOP 白沙滩实测温度 **1.00**） |
| `VanillaColdBeach` | `terrainCoastIce` | 照抄 `coast\RealisticBiomeCoastIce.java:34-56`（RWG 在 `baseBiome` 温度 < 0.15f 时用 coastIce；`minecraft:cold_beach` 温度 0.05、SNOWY 标签，正是这一支） |
| `BOPFlowerIsland` | `terrainIslandTropical` | 照抄 `ocean\RealisticBiomeIslandTropical.java:77-95` |
| `BOPOriginIsland` | `terrainIslandTundra` | 照抄 `ocean\RealisticBiomeIslandTundra.java:32-40`；**选 tundra 版属推断**（RWG 按岛屿种子气候在 tropical/tundra 间选） |
| `BOPVolcanicIsland` | `terrainIslandTropical` | **推断**：RWG 的 `IslandVolcano` 已按用户要求删除，热带岛地形是最近亲 |
| `VanillaMushroomIsland` | `terrainSmallIsland` | 照抄 `Support.java:104-115` —— `mushroomIsland` 用 `TerrainSmallIsland` + `BiomePlacement.SMALL_ISLAND` |

### ⚠ ocean 参数的管线适配

RWG 的 `rNoise` 签名带 `ocean`（海岸地形全靠它），而 **rtgc 的 `rNoise` 签名没有该参数**。
故 `terrainCoastDunes` / `terrainCoastIce` **自行向布局查询**
（`getTerrainOceanValue(getContinentValue(x, z))`，封装为 `TerrainBase.oceanAt`）——
取到的是**同一个值**。这是管线适配，不是公式改动；代价是这几个群系每列多一次大陆场求值。

若日后要消掉这个代价，正解是给 `IRealisticBiome.rNoise` 补上 `ocean` 形参
（RWG 原样），但那要动约 130 个群系文件。

### ⚠ 校验方法的教训（第二次踩）

我写的「常量序列比对」脚本对这些函数**反复误报**（剥注释的方式不当、过滤 `1` 太粗暴）。
两次都是**打印函数体、与源逐行比对**才确认代码其实是对的。
**结论：公式移植的验收只能靠打印核对，不能靠序列比对。**

### 剩余 13 个

| 组 | 数量 | 群系 | 预判依据 |
|---|---|---|---|
| 高地遗毒 | 3 | `VanillaExtremeHills` `VanillaExtremeHillsM` `VanillaColdTaigaHills` | 用已就位的忠实 `terrainHighland`，或 RWG `forest\WoodMountains → TerrainMountainRiver()` |
| 台地 | 3 | `VanillaSavannaPlateau` `VanillaSavannaPlateauM` `VanillaSavannaM` | RWG `red\Mesa` / `savanna\MesaPlains → TerrainMesa()`（**需要 `border`**） |
| 孤峰 | 2 | `VanillaExtremeHillsPlusM` `VanillaSwamplandM` | RWG 无 `TerrainLonelyMountain`；最近亲 `TerrainHilly` / `TerrainMountain` |
| 平地形 | 2 | `BOPMysticGrove` `BOPPasture` | RWG 无对应群系，推断 |
| 恶地 | 1 | `VanillaMesaBryce` | RWG `red\Mesa → TerrainMesa()`（推断） |
| 海洋峡谷 | 1 | `BOPCoralReef` | RWG 把 coralReef 当海洋群系（`Support.oceanShallowCoral`），无地形函数 |
| 果园 | 1 | `BOPOrchard` | RWG 无 orchard，推断 |

**迁移完这 13 个之后**才能删除 RTG 自造函数与遗产辅助
（`blendedHillHeight` / `hills` / `groundNoise` / `bayesianAdjustment` / `getTerrainBase`）。

> **⚠ 上面这句已被推翻，见 §12。**

---

## 11. 🔴 第二根因（本会话新增）：`rNoise` 的河道变换把 RWG 的 `river` **反相**

### 症状

把 117 个群系逐个改成 RWG 的原始地形公式之后，地形**没有变好看**——依然是大片平板。

### 根因

RWG 的 `RealisticBiomeBase.rNoise` 是**原样透传**，对 `border`／`river` 不做任何加工：

```java
return terrain.generateNoise(perlin, cell, x, y, ocean, border, river);
```

rtgc 却在此挂了一整套 RTG 时代的河道／湖泊变换（`RealisticBiomeBase.newrNoise`）：

```
lakePressure → lakeToRiverProportions → riverAdjustedforDepthDifference → riverFlattening
```

### 实测反相（默认配置；`riverDepth=57`、`waterFeatureWidthMultiplier=1.0`
⇒ `P=0.09375`、`A=0.103448`、`K=0.6`、`shore=0.035`、`depression=0.15`）

**可复算**：`powershell -NoProfile -File tools/river-convention.ps1`（纯算术，零 MC 依赖）。

| RWG 传入 `river` | 含义 | 地形函数**实收** |
|---|---|---|
| 0.00 | 河心 | **1.0000** |
| 0.05 | 河岸 | 0.9710 |
| 0.50 | 半程 | 0.4734 |
| 1.00 | 内陆 | **0.0000** |

右列**精确等于 `1 − 左列`**，也就是 rtgc 的旧约定 `landscape.river[k] = -riverValues[k]`
（1 = 最强河流）。所以：

- 这套变换原本是**为旧约定设计的转换器**；
- 把群系逐个改成 RWG 原始约定（`m = noise * strength * river`，陆地 = 1）时，
  **忘了同时拆掉转换器** ⇒ 地形被整体反相。

### 后果（与你描述的完全吻合）

- 内陆 `river = 0` ⇒ `m` 项**被完全抹平** ⇒ 山丘／山脉全部消失，世界压成平板
  （有湖处最高也只到 ≈0.039，不足 4%）；
- 河心 `river = 1` ⇒ `m` 项满幅 ⇒ **沿每条河长出一圈山墙**。

### 修复

`RealisticBiomeBase.rNoise` 改为与 RWG 逐字同构的透传（唯一保留的 rtgc 配置是
`ALLOW_RIVERS=false` ⇒ `river=1f`，其默认值为 `true`，故默认行为与 RWG 一致）；
并删除已死的 `newrNoise` / `lakePressure` / `lakeToRiverProportions`
以及 `IRealisticBiome.lakePressure`、`BiomeBOPBayou`/`BiomeBOPShield` 两处覆写。

**副作用是正向的**：每列少一次 `cellularInstance(0).eval2D`（`lakePressure` 的 Voronoi 求值）。

### 仍待处理（同一根因的残余）

- `ChunkGeneratorRTG.getNewerNoiseSingleBiome` 原先传 `riverValues[k]`（旧约定原始值），
  已改为 `riverValues[k] + 1f` 与主路径一致。
- **`surface` 路径仍用旧约定**（`landscape.river[k] = -riverValues[k]`，1 = 河心），
  而 RWG 的 surface 收的是 `river + 1f`。这是**独立的一笔欠账**（surface 审计，附录 F），
  不在地形根因里；当前 surface 代码与其输入自洽，故不改。
- `RTGWorld` 的 `riverAdjustedforDepthDifference` / `RIVER_FLATTENING_ADDEND` /
  `getLakeFrequency` / `getLakeShoreLevel` / `getLakeDepressionLevel` / `getLakeBendSize*`
  现已无调用者（`ACTUAL_RIVER_PROPORTION` 亦然）。删除前需确认 `src/preview` 未引用。

---

## 12. A3 收尾 + 可达性分析（**纠正 §10 尾部的结论**）

### 已删除

| 项 | 内容 |
|---|---|
| RTG 自造地形函数 | 13 个：`terrainPlains` `terrainRollingHills`(×2) `terrainBeach`(×2) `terrainOcean` `terrainOceanCanyon` `terrainPlateau` `terrainBryce` `terrainLonelyMountain` `terrainHighlandLegacy` `terrainFlatLakesRTG` `terrainForest` |
| 死重载 | `terrainGrasslandHills`(9 参) `terrainGrasslandMountains`(7 参) |
| 孤儿助手 | `above` |
| 悬空 `@link` | 2 处（`terrainHighlandLegacy`、`terrainFlatLakesRTG`） |
| 河道变换机制 | 见 §11 |

### ⚠ 教训（第三次踩同一类坑）：**别靠"数名字"判断死代码**

`TerrainBase.java` 里对 `blendedHillHeight` / `groundNoise` / `getTerrainBase` / `hills`
做过一次"字数统计"，得出"已无用可删"。**这是错的**——
`tools/reachability.ps1` 做了真正的可达性分析（切函数块 → 建文件内调用图 →
以"被本文件外调用"为根做 BFS）后，结论是：

| 名字 | 外部调用 | 判定 |
|---|---|---|
| `blendedHillHeight` | 8 | **活的** |
| `groundNoise` | 5 | **活的** |
| `getTerrainBase` | 1 | **活的** |
| `hills` | 1 | **活的** |
| `bayesianAdjustment` | 10 | **活的** |
| `above` | 0 | 死（已删） |

差点误删 4 个仍在使用的函数。**名字出现次数 ≠ 可达性。**

### 仍无人调用的 RWG 忠实移植（**保留**，属待接线，不是死码）

`terrainDunes`、`terrainGrasslandFlats` —— 它们是 RWG 的逐行移植，删掉等于以后还得重抄。

### ✅ A3 已完成（12 个群系全部迁移）

原表如下，**现已全部完成**，见 §13：

| 组 | 群系 | 原现状 | 落点 |
|---|---|---|---|
| α | `VanillaPlains` `VanillaSunflowerPlains` `VanillaSavanna` `VanillaMesa` `BOPXericShrubland` `BOPTropicalIsland` `BOPLandOfLakes` `BOPRedwoodForestEdge` | RTG `GroundEffect` | 见 §13 表 |
| β | `VanillaMesaPlateau` `VanillaMesaPlateauF` `VanillaMesaPlateauFM` `VanillaMesaPlateauM` | RTG 自造 `plateau.added(...)`，其中 `rivercap = 3f * river` **依赖旧约定** | `terrainMesa` |

---

## 13. A3 完成：129/129 群系全部走 RWG 地形函数

### 落点表（依据 / 推断分开标注）

| rtgc 群系 | 落点 | 依据 |
|---|---|---|
| `VanillaMesa` | `terrainMesa` | ✅ RWG `red\RealisticBiomeMesa` → `new TerrainMesa()` |
| `VanillaMesaPlateau` `…F` `…FM` `…M` | `terrainMesa` | ✅ RWG `savanna\RealisticBiomeMesaPlains` → `new TerrainMesa()`（4 个共用 `TerrainRTGMesaPlateau`） |
| `VanillaSavanna` | `terrainGrasslandFlats` + **`SurfaceGrasslandMix1(grass,dirt,sand,stone,cobble,13f,0.27f)`** | ✅ RWG `savanna\RealisticBiomeSavanna:35-42` → 同一份配对（地形 + 地表）；⚠ 地表那一半是**本轮二次自检才补齐的**（此前只接了地形） |
| `BOPTropicalIsland` | `terrainIslandTropical` | ✅ RWG `ocean\RealisticBiomeIslandTropical.rNoise`（rtgc 的同名函数就是它的逐行移植） |
| `BOPLandOfLakes` | `terrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)` | ⚠ **近亲**：RWG 只收了 `landOfLakesMarsh`（`SupportBOP.java:461-470`，`HOT_BORDER`） |
| `VanillaPlains` | `terrainGrasslandFlats` | ⚠ **推断**：RWG 无"MC 平原"对应物；取其承担"平坦陆地"角色的 `TerrainGrasslandFlats` |
| `VanillaSunflowerPlains` | `terrainMarsh(waterSurfaceTop, river)` | ✅ RWG `Support.java:140-152`（`sunflowerPlains = plains.biomeID + 128` → `TerrainMarsh()`）；⚠ 此前写成"推断"是**错的**，本轮二次自检改回（§0.5.4 C9） |
| `VanillaJungle` | `terrainHighland(0,140,68,200)` | ✅ RWG `Support.java:116-126`（`BiomeGenBase.jungle` 的 active 条目）；⚠ 此前引的是 `JungleHills` 家族（§0.5.4 C6） |
| `VanillaIcePlainsSpikes` | `terrainHighland(0,140,68,200)` | ✅ RWG `Support.java:127-139`（`icePlainsSpikes = icePlains.biomeID + 128`）；⚠ 此前引的是 `SnowHills`（§0.5.4 C8） |
| `BOPXericShrubland` | `terrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)` | ⚠ **推断**：RWG 无 `xericShrubland`；取 `SupportBOP.java:718-723` 的 `shrubland` 参数（同族 `BOPShrubland` 已用同一组） |
| `BOPRedwoodForestEdge` | `terrainGrasslandHills(80f,180f,13f,100f,38f,260f,71f)` | ⚠ **推断（同族）**：同族 `BOPRedwoodForest` 用该参数 |
| `VanillaMesaBryce` | `terrainMesa` | ⚠ **推断**：RWG 无 `TerrainBryce`，也无 hoodoo |

### 顺带修掉的两处「形参偏离」

| 函数 | 偏离 | 修法 |
|---|---|---|
| `terrainGrasslandFlats` | RWG **无参数**，硬编码 `m *= m / 40f` 与 `68f + h + m - l`；rtgc 却留了 `mPitch` / `baseHeight` 两个形参 | 去掉形参，与 RWG 逐字一致 |
| `RealisticBiomeVanillaMesaPlateau.TerrainRTGMesaPlateau` | 自造抖动 + `VoronoiPlateauEffect` + `bordercap`/`rivercap` | 换成 `terrainMesa`，构造参数一并删除 |

### 🔴 新踩的坑（第四次）：**迁移改到了死类**

`RealisticBiomeVanillaMesaBryce.initTerrain()` 返回的是 **`new TerrainRTGBrycePlateau(67f)`**
（RTG 自造的 Bryce 高原 + `hoodooHeight` 石林），
而上一轮的迁移改的是**同一文件里另一个从未被实例化的类** `TerrainRTGMesaBryce`。
也就是说那次迁移**完全空转**，一行都没生效。

同类风险还有一处：`RealisticBiomeBOPTropicalIsland` 里那个 `TerrainVanillaExtremeHillsPlus`
也是死类（同名真身在 `RealisticBiomeVanillaExtremeHillsPlus.java`），
而 `initTerrain()` 实际返回的是 `RealisticBiomeVanillaExtremeHills.GrandMountain`。

**结论：不能只看"这个文件里出现了 `terrainXxx(`"，必须顺着 `initTerrain()` 的真实返回类型去查。**

为此写了 `tools/terrain-wiring-check.ps1`：解析每个群系的 `initTerrain()` 返回类型
（支持 `A.B` 限定名）→ 定位该类的类体 → 检查其中是否有 `terrainXxx(` 调用。
当前结果：**129 个群系文件，129 个通过**。

### 同时清掉的死重量

`GroundEffect` / `HeightEffect` / `HeightVariation` / `JitterEffect` / `RaiseEffect` /
`BumpyHillsEffect` / `MountainsWithPassesEffect` / `VoronoiPlateauEffect` 等
在已被替换的群系里留下的字段、构造代码与 import
（`VanillaBirchForest` `VanillaJungleEdge` `VanillaRoofedForest` `BOPBambooForest`
`BOPDeadSwamp` `BOPLushDesert` `BOPOutback` `VanillaMesaBryce` `BOPXericShrubland` `BOPLandOfLakes`）。

### D2 准数：`rtg.api.world.terrain.heighteffect` 包的存活情况（下一轮清理用）

该包共 **14** 个类。按"除自身文件外是否仍被引用"统计：

| 状态 | 数量 | 类名 |
|---|---|---|
| **完全无外部引用** | 7 | `GroundEffect` `RaiseEffect` `SpikeEverywhereEffect` `VariableRuggednessEffect` `VoronoiBasinEffect` `VoronoiBorderEffect` `VoronoiPlateauEffect` |
| 仍被引用 | 7 | `HeightEffect`(50) `HeightVariation`(8) `HillockEffect`(5) `BumpyHillsEffect`(4) `JitterEffect`(3) `MountainsWithPassesEffect`(2) `SummedHeightEffect`(1) |

合计约 **2000+ 行**的 RTG 时代高度效应体系，其中一半已是纯死码。
删除前需确认 `src/preview` 未引用（`makeCalibration` 之类）。

> 注意：`HeightEffect` 的 50 处引用里，相当一部分只是**未被清理的字段声明**
> （形如 `private HeightEffect height;`）—— 即"地形已迁走、字段留着"的残留。
> 下一步可按"该字段是否在其类的 `generateNoise` 内出现"逐个判定。

---

## 14. 阶段 B（山地链）+ C1（边界池）—— 本轮完成

### 🔴 顺带发现的第三个大问题：**气候边界列根本没走布局**

`RtgBiomeLayout.selectCombinedBiome` 在两个池都为空时返回 **`null`**；而
`getLandBiomeAt` 在气候边界处只从 `border[climate]` ∪ (`coldBorder`|`hotBorder`)[climate] 里选。
**三池皆空** ⇒ 所有气候边界列拿到 null ⇒ `RtgLayoutAccess.biomeAt` 返回 null ⇒
生成器整列**回落到原版 GenLayer 群系**。

而边界带并不窄：`CLIMATE_BORDER_DISTANCE_DIFFERENCE = 288` vs `climateWidth = 1400`，
即气候 Voronoi 单元四周约 288 格宽的一圈都算边界。

**这既是"地形没变好看"的一大块，也是山地链（B4）不显形的原因。**

### 阶段 B 实现明细

| 步 | 内容 | 位置 |
|---|---|---|
| B1 | `RealisticBiomeMountainChain`：地形 `terrainHilly(…,230,120,0,260,120)`（RWG `TerrainHilly(230,120,0,260,120)`） | `rtg/world/biome/realistic/land/RealisticBiomeMountainChain.java` |
| B1 | `SurfaceMountainStoneMix1` 逐行移植 | `rtg/api/world/surface/SurfaceMountainStoneMix1.java` |
| B2 | 合成群系编号空间 | `rtg/world/biome/RtgRealisticIndex.java` |
| B3 | `mountainChainWeight` / `nearbyMountainChainInfluence` / `fade` | `ChunkGeneratorRTG.getNewerNoise` |
| B4 | `rebuildExtremeBorderMountains(RealisticBiomeMountainChain::forBiome)` | `RtgLayoutAccess.forSeed` |
| B5 | 隧道门控换成 `mountainChainRiverHost > 0.10` | `rtg/world/gen/UndergroundRiver.java`（1.0.33 追记：从 `ChunkGeneratorRTG` 抽出） |

#### B6 说明（未单独改动，已由构造满足）

RWG 的 `EtFuturumCaveVines` 门控是 `hasMountainChainNearby(manager, x, z)`。
rtgc 的 `RiverCaveVines` 门控是 `landscape.riverCaveCeiling[k] > 0`。因为 B5 之后
**隧道只可能在 `mountainChainRiverHost > 0.10` 的列产生**，而 `riverCaveCeiling`
只在真正开凿成功时写入 ⇒ **两者等价**。再单独查一遍链邻域属于重复计算，故不改。

### 与 RWG 的结构性差异（必须记录）

RWG 的链 `extends RealisticBiomeBase` 并 `super(0, backingBiome.baseBiome, …)`，
靠 RWG 自己的现实主义编号空间让「链」与「备份群系」共存于同一个 MC 群系之下。

rtgc 的 `RealisticBiomeMountainChain` 改为：
- **直接实现 `IRealisticBiome`**，除地形/地表/替换外全部委托给备份群系。
  这样可以避免 `RealisticBiomeBase` 构造时新建**第二份 `BiomeConfig`**
  （同一配置文件读两遍；且链的 `initConfig()` 为空时有把备份群系配置刷掉的风险）；
- 通过 `RtgRealisticIndex` 在合成编号空间里占独立槽位，达到与 `super(0, …)` 相同的效果。

**尚未移植**：RWG 的 `rDecorate` 会按「16 个采样点中高度变化 ≤ 4 的平缓点所占比例」
缩放备份群系的装饰强度。rtgc 的装饰走 MC 群系（区块中心）而非本对象，故这一步**暂缺**。

### 性能设计（关键，别改坏）

`hugeRender`/`smallRender` 按 **`RtgRealisticIndex.biomeIdBound()`（含 256 个预留槽位）分配**，
但每区块热循环按 **`usedBound()`（= MC 上界 + 已分配槽位数，动态）** 走。

- 为什么要预留：1.12.2 的 `WorldServer#createChunkProvider` 是
  `createChunkGenerator()` 先、`createBiomeProvider()` 后（Java 从左到右求值），
  **生成器先构造**，那一刻布局还没建、山地链还不存在；
- 代价只有约 0.7 MB 内存；没有链时 `usedBound() == mcBound()`，
  热循环与引入本类之前**完全一致**。
- ⚠ **绝不要**把 `MAX_SYNTHETIC` 调成 65536：历史教训是按
  `RTGAPI.getMaxBiomeIDs()` 分配（装了 REID 就直接 65536），两个数组放大 256 倍
  （约 176 MB），每区块多出约 4600 万次浮点写入，实测约 **10 倍**世界生成退化。

### 本轮修掉的一个自己造成的缺陷

`mountainChainWeight` / `mountainChainRiverHost` 一开始被我放成**生成器字段**。
但 `landscape` 可能是 `landscapeCache` 里的对象，而 `UndergroundRiver.carve` 是**稍后**才跑的 ——
缓存命中时读到的会是**上一个区块**的值。已改放进 `ChunkLandscape` 随区块保存
（`riverCaveCeiling` 当初放进去也是同一个原因）。

### 阶段 C1：边界三池的填法（**已被推翻并重做**，见下方 §14.1）

`RtgBiomeLayout.mirrorCoreIntoBorders()`：`border` / `coldBorder` / `hotBorder` ← 本气候的核心池。

**当时记下的依据与代价**：RWG 的三池是**作者手工挑选**的过渡群系
（`tools/rwg-placement-inventory.ps1` 可从 `Support*.java` 提取，实测
`COLD_BORDER` 只有 chaparral / meadow / rainforest / tropicalRainforest，
`HOT_BORDER` 只有 borealForest / jadeCliffs / landOfLakesMarsh）。
rtgc 的 MC 群系**没有这层标注数据**，任何"猜哪些群系算过渡带"都是发明。
故取机制等价、内容保守的填法：

- 边界列拿到**本气候自己的**群系 ⇒ 不会出现"雪原边长出丛林"这类错误组合；
  此前它们是 null（回落原版），所以是严格改善；
- `coldBorder`/`hotBorder` 有内容 ⇒ 四个极端镜像池被填成**山地链** ⇒ 山地链显形；
- **代价**：`borderDirection` 不再产生方向性差异（RWG 有）。这是一处**有意的、已记录的偏离**。

### 14.1 C1 的三处事实错误（本轮纠正）

上面那段有**两个事实错误**和一个**范围错误**：

1. **"三池皆空 ⇒ 边界列拿到 null ⇒ 回落原版 GenLayer" 不成立。**
   `RtgBiomeLayout.getLandBiomeAt` 在三个池都空时会落到
   `selectBiome(core[climate], …)` —— 这正是 RWG `ChunkManagerRealistic:785-798`
   的 fall-through，而 rtgc 那段本来就是逐行移植。边界列**从来不会**因为池空而拿到 null。
2. **镜像对群系选择不是恒等变换 —— 但也不是"严格改善"。**（**本条已被 §14.4 的数值标定修正**）
   当时写的是"`selectCombinedBiome(shared=core, directional=core)` 与 `selectBiome(core)`
   **逐点相同**"。**这是错的**：前者把选择器切成 8 段并让 `core[0..3]` 各出现两次，
   后者切成 4 段；两边的**分段边界不在同一位置**，所以同一列选到的 `core[k]` 不同。
   `gradlew calibrateBorderPools` 实测：气候边界列里有 **19.73%** 的列（≈ 全部边界列）
   逐点选到了不同的成员，而各成员的**宏观占比都仍在 1/4 附近**。
   正确说法是"**分布相同、逐点不同**"。
3. **范围错误：能抄的标注不止 SMALL / SMALL_ISLAND。**
   `SupportBOP.java` 里还有 `COLD_BORDER`（chaparral / meadow / rainforest /
   tropicalRainforest）、`HOT_BORDER`（borealForest / jadeCliffs / landOfLakesMarsh）、
   `LITTORAL`（bayou / deadSwamp / lushSwamp / mangrove / sludgepit / tropics）共 12 条
   **同样明确、同样可抄**的标注。当时只抄了 5 条 SMALL/SMALL_ISLAND，把另外 12 条当成了
   "没有数据"。

**镜像是唯一真实的作用**是喂给 `rebuildExtremeBorderMountains`：该方法从
`coldBorder` / `hotBorder` 镜像出山地链池，于是"整个核心池"灌进去之后，
RWG 那边的 **6 个**山地链在 rtgc 变成**每个陆地群系各一个**（实测
`RtgRealisticIndex.syntheticCount()` = **104**）—— 即"每种地形都可能变成山地链"。

### 14.4 数值标定：`gradlew calibrateBorderPools`（本轮新增，**修正了 14.1 的第 2 条**）

`src/preview/java/rtg/world/biome/BorderPoolEquivalenceCalibration.java`。
它跑**真正的** `RtgBiomeLayout`（不是复制一份判定逻辑），用 `java.lang.reflect.Proxy`
造出身份可区分的 `IRealisticBiome` 桩 —— 因为 `getLandBiomeAt` 全程不碰 `baseBiome()`，
所以不需要 MC 运行时。

同一 seed 建四个布局，逐列比对（seed 1234567891011121、步长 31、±11000、50.4 万列）：

| 命题 | 做法 | 实测 |
| --- | --- | --- |
| A | 镜像核心池 vs 池留空（都不建极端池） | **差异 19.73%**；边界列的成员占比两边都≈25% ⇒ **分布相同、逐点不同** |
| B | 旧（镜像核心池）vs 新（RWG 方向池成员），都建极端池 | **差异 10.65%**，且**只落在边界族**：极端 28308 + 普通边界 25398、**核心列 0**、小型圆盘 0 |
| C | 极端边界池大小 | 旧 = 16（4 气候 × 4 成员）→ 新 = 4（RWG 能对上的那几条）；生产量级对应 104 → ~4 |

**命题 A 的修正**：14.1 第 2 条说"逐点相同"是错的。正确结论是
`selectBiome(core,4)` 把选择器切成 4 段，而 `selectCombinedBiome(core,core)` 切成 8 段
（`core[0..3]` 各出现两次）—— **边界位置不同** ⇒ 同一列会换一个成员，
但每个成员的宏观占比不变。所以旧镜像既不是恒等变换，也不是"严格改善"，
而是一次**等分布的重新洗牌**。

**这不影响"删除镜像"的结论**，因为那个结论靠的是另外三条（都不依赖命题 A）：
① 它从来不是防 null 所必需（fall-through 本来就在）；
② 它唯一的真实作用是喂极端边界池，把 RWG 的 6 条链放大成 104 条（命题 C 已量化）；
③ RWG 的方向池是**手挑的少数成员**，不是整个核心池（`RwgPlacementCheck` 已核）。

**这次标定的价值恰恰是它推翻了我自己上一轮的一句话。** 判据要能证伪才算判据。

### 14.2 重做后的口径（现在）

- 三个边界池的内容改由 `RtgBiomeCategorizer.RWG_PLACEMENTS` **照抄** RWG 的显式标注
  （含 13 条能对上 BOP 1.12 的、2 条 MC 的、1 条 rtgc 特有的 shore）。
  匹配是**精确**匹配（注册名小写去下划线），避免 `tropicalrainforest ⊃ rainforest`
  这类子串歧义。RWG 有而 rtgc 无的条目（`jadeCliffs` / `tropics` / `garden` /
  `fungiForest` / `sludgepit` / `landOfLakesMarsh` / `baseRiver*`）**不写进表**，
  免得"填"进不存在的东西；它们的缺口由启动日志的
  `[RTG] RWG placement 覆盖：n/m` 那两行报告。
- `mirrorCoreIntoBorders()` **已删除**；没被标到的气候/方向走 RWG 的 fall-through。
- 与 RWG 一致：`placement != CORE` 的群系**只进该池，不进 core**
  （`Support.addBiome` 在 `Support.java:218-221` 直接 `return`）。
- `LITTORAL` 现在只有 **WET** 的四条（bayou / deadSwamp / lushSwamp / mangrove）；
  海滩群系不再进 LITTORAL（见 §14.3）。

### 14.3 顺带发现的更大一处偏离：**滨海带（432 格）全被填成海滩**

RWG 的 `ChunkManagerRealistic:590-593` 里，海岸线只有 `continent < 24` 那一圈，
用的是两个**专用**群系 `coastIce` / `coastDunes`（rtgc 已接成 `COLD_BEACH` / `BEACH`）。
RWG 的 `LITTORAL` 池（`continent < 432` 的覆盖，占世界 13.05%）里装的是
**沼泽/红树**，而且**只有 WET 有成员**。

旧写法把"字典带 BEACH 或名字含 beach/shore"的群系一律塞进 LITTORAL，
于是**四个气候**的 LITTORAL 池都非空 ⇒ 滨海覆盖在**全世界 13% 的面积**上生效，
把那一圈填成海滩群系。这是"地形不好看"里面积最大的一块之一。

现在海滩群系走 core（RWG `Support.addBiome(b, cat)` 的默认分支），
只有 WET 保留沼泽/红树的滨海带 —— 与 RWG 一致。

### 检查器更新

`tools/terrain-wiring-check.ps1` 现在也能解析 `initTerrain()` **返回字段**的情况
（`RealisticBiomeMountainChain` 返回 `terrain` 字段），并顺着字段的赋值找到具体类。
当前结果：**130/130 通过**。

### 仍未做（阶段 C2/C3、D1、D3–D5）

> **⚠ 历史快照（写于该轮，勿当待办）**：本节列的 C2/C3 已在 §18 做完，
> D1/D2 已在 §16 做完，D3 已在 §15 修正，D5 已在 §0.5.3 推翻并做完。
> **当前还剩什么只看 §0.5.1。**

- C2 `SMALL` 池、C3 `ISLAND`/`SMALL_ISLAND`/`LARGE_ISLAND` 池**仍为空**。
  为空时 `small` 特征被安全跳过；岛屿列返回 null 并回落原版（既有缺口，未恶化）。
- D1 `TerrainBase` 旧河道族、D3 气候归类偏斜、D4「没有地表装饰」、D5 海洋群系不足。

### D2 准数：`rtg.api.world.terrain.heighteffect` 包 —— **已整包删除**（本轮）

> **补充（同一轮稍后）**：同类残留还有**内层**的 `Surface*` / `Terrain*` 类 —— 见下方"内层类的清理"。

#### 内层类的清理（本轮，`tools/sweep-dead-inner-surfaces.ps1`）

`1.0.30/1.0.31` 把共享 `Surface*` 移植进来后，**旧的内层实现没删**。本轮删掉 **116 个
内层 `Surface* extends SurfaceBase`（8372 行）+ 3 个内层 `Terrain* extends TerrainBase`（63 行）**。

判据：**在声明文件之外零引用，且在声明文件之内除自己那一块之外也零引用**。
保留的 14 个里有一部分是"同名类出现在多个文件"造成的保守误判（留着不碍事）；
另外 121 个"外部零引用"的非 Surface 嵌套类逐个查了本文件内引用：118 个活着
（`initTerrain()` 返回它们），只有 3 个是真死。

**有意保留的 4 个**：`BOPGravelBeach` / `BOPOriginBeach` / `VanillaBeach` / `VanillaStoneBeach`
仍用各自的内层 `Surface*` —— RWG 的 `CoastDunes` 没有可共用的 `SurfaceBase` 子类，
硬接会变成发明。

**验证**：`BUILD SUCCESSFUL`；`terrain-wiring-check` = `NOT routing into RWG terrain: (none)`；
`surface-wiring-check` = `shared=125 still-inner=4 no-new=1`（与清理前一致）。

上一轮记录的是"14 个类，7 个无外部引用、7 个仍被引用，合计 2000+ 行"。
本轮的实测把这个判断推翻了：**整包 12 个类在包外零真实调用者**，上一轮那个
"7 个仍被引用"是把**已死的字段声明与 import** 当成了调用。

判据（三条，都可复核）：

1. `.added(` 的调用**全部在包内**（`JitterEffect` / `SummedHeightEffect` /
   `SpikeEverywhereEffect` / `VariableRuggednessEffect` 之间互相调用）；
2. 包外的引用只剩两类：
   - **只赋值、从不读取的字段**：`RealisticBiomeBOPBog`(bottomVariation / smallHills /
     mediumHills / bottom)、`RealisticBiomeBOPMarsh`(variation / smallVariation / baseHeight)、
     `RealisticBiomeBOPBorealForest`(hillEffect / baseHeight / hillStrength / hillWidth /
     hillBumpyness / hillBumpynessWidth) —— 这三个群系的地形早已改成
     `terrainMarsh` / `terrainMountainSpikes`，字段是残留；
   - **无用的 import**：`BOPBambooForest`(通配) / `BOPHighland` / `BOPMountainFoothills` /
     `BOPMountainPeaks` / `VanillaExtremeHills`（字段早已删掉，import 留着）；
3. `src/preview` 无任何引用。

**已删除**：整个包（12 个类 / 291 行）+ 上述字段、构造代码与 import。
同批删掉的 RTG 自造死助手：`TerrainBase.unsignedPower`（RWG 里没有、全仓零调用者）。

**保留但标明零调用者的两处**（都是 RWG 的逐行移植，删了要重抄）：

- `TerrainBase.terrainDunes` —— ext=0 / intra=0。沙漠当前走
  `terrainPolar(参数化) + groundNoise(...)`（RTG 配方，为保住"沙丘高度"配置）；
- `TerrainBase.toWorldBlocks` —— 隧道实现已改走布局的 `RwgCellNoise`，
  但 `src/preview` 的 `CellularNoiseCalibration` 用它那套换算离线复核隧道/洞厅覆盖率，
  公式要留着当对照物。

RTG 自造地形函数（上一轮记的 13 个：`terrainPlains` / `terrainRollingHills` /
`terrainBeach` / `terrainOceanCanyon` / `terrainPlateau` / `terrainBryce` /
`terrainLonelyMountain` / `terrainHighlandLegacy` / `terrainFlatLakesRTG` /
`terrainForest` …）**在这一轮之前就已经删完了** —— 现在全仓只有注释里还提到这些名字。

`tools/reachability.ps1` 的 "LEGACY HELPERS" 名单也一起更新了：它此前列着
`hills` / `bayesianAdjustment` 两个**早就不存在**的方法，输出成 `extCalls=`（空）
看起来像"0 调用者"，本轮据实改掉并加了 `NOT-A-static-float-METHOD` 状态位。

### D5 的**否定结论**：深海"缺口"在 1.12.2 里补不上（本轮实测）

> **⚠ 已推翻（保留原文只为记录判断过程）**：这里的推理**在前提上就错了** ——
> 缺的**不是**"MC 有没有更多海洋群系"，而是"RWG 的 `baseOcean*` 在 rtgc 有没有对应物"。
> 答案是有：`rwg/biomes/base/BaseBiomeOcean.java` 是 RWG **自己的源码定义**，
> 逐行移植为 `rtgc` 的 `RtgOceanBiome`（温度/降雨/动物/禁雨全一致），
> 于是 3 个深海 + 3 个浅海得以注册，8 个槽位全部实位填满。
> 详见 §0.5.3 与该类 / `BiomeInit.init_rtgc_oceans()` 的注释。

D5 一直记作"深海深度群系不足"。本轮去补，结论是**补不了，而且不是遗漏**：

1. MC 1.12.2 的 `Biomes`（查 MCP stable 39 的 `fields.csv`）里**只有 3 个海洋群系**：
   `OCEAN` / `DEEP_OCEAN` / `FROZEN_OCEAN`。
   **没有 `FROZEN_DEEP_OCEAN`** —— 那是 1.13 才加的（我先按 1.13 的印象写了
   `RealisticBiomeVanillaFrozenDeepOcean`，**编译器当场报"找不到符号"**，已删除）。
   21 个 `MUTATED_*` 里也没有任何海洋突变体。
2. BOP 1.12.2 的 `BOPBiomes`（`javap` 实测）里只有 `kelp_forest` / `coral_reef`
   两个海洋群系，也**没有**深海变体。

所以 rtgc 全仓的海洋群系就是 **1 深 + 4 浅**（与启动日志 `深海=1 浅海=4` 完全吻合），
而 RWG 的四个深海来自 RWG **自己注册的** `baseOceanHot` / `baseOceanWet` /
`baseOceanCold` 群系 —— rtgc 没有对应物，也不可能靠"抄"得到。

⇒ `fillMissingOceanSlots` 的跨气候借用（SNOW/HOT/WET 的深海借用 COLD 的 `deep_ocean`）
**就是 1.12.2 下的正确适配**，不是待修的缺口。要做成 RWG 那样，必须**新增 MC 群系**
（带自己的 id / lang / 生成规则），那是"发明新群系"，超出"照抄"的范围，需要单独决定。

本轮的处置：**让借用看得见**。`-Drtg.debugLayout` 现在逐气候打印
`浅海=… 深海=…` 与两个 patch 钩子，借用不再静默。

---

## 15. D3 / D4 / D5（本轮）

### 🔴 D4 的答案：`ChunkInfo` 的**高度图被转置**了

这是"没有地表装饰（花花草草、树）"的直接原因，而且是**一处索引顺序不一致**：

```java
// setHeightsFromNoise：noise[] 的索引由 ChunkGeneratorRTG 按 k = x*16 + z 写入
heightCache[i] = (int) noise[i];                       // ← 原实现
// getHeight：按 z*16 + x 读取（与 buildHeightCache 一致）
return heightCache[(z & 15) * 16 + (x & 15)];
```

`heightCache` 是 `z*16+x`，`noise` 是 `x*16+z` —— **整张高度图被转置**。
影响面是全部 RTG 地表装饰：**32 个装饰类**都用 `chunkInfo.getHeight(...)` 定位地表
（`DecoTree` `DecoGrass` `DecoFlowersRTG` `DecoDoubleGrass` `DecoCactus` `DecoShrub`
`DecoBoulder` `DecoMushrooms` `DecoReed` `DecoDeadBush` `DecoPumpkin` `DecoFallenTree`
`DecoVariable*` `DecoHelper*` `DecoWorldGen` …），而 `ChunkInfo` **只在带 noise 的那条
构造路径上被创建**（`IRealisticBiome.rDecorate`），也就是**唯一被使用的路径**。

后果链：`DecoTree.generate` 取 `y = chunkInfo.getHeight(x, z)`（其实是 (z,x) 那一列）→
`DecoTree.doGenerate` 用**精确的 `pos.up(y)`**（不向下找地面）→ 山地上约一半的树被埋进石头、
另一半悬空，还有大量被 `y > maxY || y < minY` 剔除。

**已修**：`setHeightsFromNoise` 转置写入。修复后高度自洽 ——
`generateTerrain` 把石头填到 `y = (int) noise[x*16+z]`，所以地表方块就在 `y = height`，
`pos.up(y)` 正好在其上方。

### D4 发现 #2：`DecorateBiomeEvent.Pre/Post` 从未被发出

rtgc 的 `populate` 取代了原版 `ChunkProviderServer#populate`，而后者是
`DecorateBiomeEvent.Pre/Post` 的**唯一**发出点。这两个事件此前从未被发出 ⇒
所有靠它们做装饰的 mod（含 BOP 的一部分植被）在本世界类型下**完全不生效**。
RWG 也是自己发的（`ChunkGeneratorRealistic:977/1036`）。**已补**。

### D4 发现 #3（**未修，独立欠账**）：RWG 的装饰是**按邻域分摊**的

> **⚠ 后续已实现（本节是快照）**：这一项后来按 §17③ 的**选项 (c)** 落地了 ——
> `ChunkGeneratorRTG.populate` 用 RWG 的原式累加权重
> （9×9、`+24` 偏移、`RWG_DECO_NEIGHBOUR_WEIGHT = 0.01234569f`），
> 再按权重**概率性**调用相邻群系的 `rDecorate`。仍未做的是本节末尾说的那件事：
> 给 `rDecorate` 补 `strength` 形参、让各装饰器按分数强度缩放循环次数。

RWG（L971-1005）先对 9×9 个区块的群系采样累加权重：

```java
for (bx = -4..4) for (by = -4..4)
    borderNoise[getBiomeDataAt(x + 24 + bx*16, y + 24 + by*16).biomeID] += 0.01234569f;
for (bn = 0..255) if (borderNoise[bn] > 0f)
    RealisticBiomeBase.getBiome(bn).rDecorate(..., borderNoise[bn], river);
```

81 × 0.01234569 = 1.0，即**每区块的总装饰量恒为 1.0，但在相邻群系之间按面积分摊**。
rtgc 只按**单点**群系装饰一次：总量相当，但**边界处不会混合**（RWG 会让相邻群系各自
以较低 strength 出场，形成装饰过渡）。

要照抄需要给 `IRealisticBiome.rDecorate` 补 `strength` 形参，并让约 32 个装饰类按它缩放
循环次数 —— 这是一次跨接口的重构，且 rtgc 的装饰目前**没有** `strength` 概念。
**记录为欠账，不在本轮擅自动手。**

### D3：气候归类偏斜

原规则里 HOT 只有 `temp >= 0.9f` 一条纯温度判据，于是大量**暖而干**的群系
（steppe / scrubland / outback / lushDesert / wasteland / xericShrubland …，温度多 0.7–0.85）
全部落进 COLD 兜底 —— 实测 COLD=53 / HOT=20。

**已改**：按温湿二维补一条 `temp >= 0.7f && rain <= 0.35f`（"暖且干"）。
阈值取得保守（rain ≤ 0.35 是真正的干旱），以免把温带森林误判成热带。
**新的分布只能在运行期日志里确认**（`陆地群系按气候：SNOW=… COLD=… HOT=… WET=…`）——
离线拿不到注册表，不做无据的调参。

#### D3 第二轮：**把自造的温雨阈值换成 Forge 自己的标签**（本轮）

上面那条 `rain <= 0.35` 留了下来，但 WET 那条自造阈值
（`rain >= 0.9f && temp >= 0.8f`）已删除，改用 `BiomeDictionary.Type.WET`。

依据是本仓库里 Forge 的**源码**（`E:/.gradle/.../forge/.../unpacked/src/main/java/`
`net/minecraftforge/common/BiomeDictionary.java:263-367`，`makeBestGuess`）：

| 标签 | Forge 的判据（原文） |
| --- | --- |
| WET | `rainfall > 0.85f` |
| DRY | `rainfall < 0.15f` |
| HOT | `temperature > 0.85f` |
| COLD | `temperature < 0.15f` |
| SAVANNA | `topBlock != sand && temperature >= 1.0f && rainfall < 0.2f` |
| SWAMP | `isHighHumidity() && heightVariation < 0 && baseHeight ∈ [0, 0.3]` |
| SANDY / MESA | `topBlock == sand` / `fillerBlock == hardened_clay` |

也就是说**这套阈值本来就在手上**，我们却在 `climateFor` 里另写了一套。
两套不一致的地方就是"误判"的来源：自造的 WET 判据多了一个 `temp >= 0.8f` 条件，
于是**雨量很高但不算热**的沼泽族（bog / fen / moor / marsh / wetland / bayou …）
既不是 WET 也不是 HOT，全部落进 COLD 兜底 —— 这正是 COLD 倍率 ~1.5 的来源。

**保留的两处（保守，不是遗漏）**：HOT 的 `temp >= 0.7f && rain <= 0.35f` 留着
（比 Forge 的 DRY 宽，去掉会让 HOT 更少）；**不加** `SANDY → HOT`
（温带海滩的顶方块也是沙，把 SANDY 当热带会把海滩整体推到 HOT）。

> ⚠ 同时纠正一句**写错的旧注释**：本 Forge 版本的 `BiomeDictionary.Type` **有** `MESA`
> （也有 DRY / WET / SANDY / WASTELAND / LUSH / DENSE / SPARSE，实测
> `javap net.minecraftforge.common.BiomeDictionary$Type`），没有的只有 `DESERT`。
> 旧注释写的"没有 DESERT / MESA 常量"，MESA 那半句是错的 —— 而**结论被我当依据用了两轮**。

#### D3 还差什么：一次运行

偏斜的**最终数字**仍然只能在运行期拿到（`climateFor` 依赖 `BiomeDictionary` 的运行时标签，
离线复刻它需要把 BOP 的生物群系属性从字节码里抠出来、还要猜 BOP 有没有显式 `addTypes`——
那是"推断"，本项目的教训是**不要把推断当依据**）。

但这一步已经不必再"猜一轮改一轮"：`-Drtg.debugLayout` 现在会为**每个陆地群系**打一行

```
[RTG]   [类] COLD   bog       temp=0.50 rain=0.90 标签=WET,SWAMP
```

即"归到哪个气候 + 名字 + 温度 + 雨量 + 命中的标签"。哪个群系被哪条判据漏掉，一眼可指。


### D5：海洋群系不足（**已由用户裁定结案，不再追**）

> **⚠ 已重新打开并完成（本节是快照）**：用户后来要求把这一项做掉（"三个都做"）。
> 本轮按 RWG `Support.java:156-175` 的槽位表补齐了 6 个 `RtgOceanBiome`
> （3 深 + 3 浅），并把 kelp/coral 改为**只做 patch**（`SupportBOP.java:39-47`）。
> 详见 §0.5.3。下面的"用户裁定"与"不能靠照抄补齐"两段**均已作废**。

> **用户裁定（本轮）**：「少几种海就少呗，无所谓的事情」。
> ⇒ 这一项**关闭**，不再尝试补深海变体、不再为"四个气候共用同一个深海"做改动。
> 下面保留原始记录，只为避免以后有人再把它当"待办"重新翻出来。

rtgc 只有 **1 个深海**群系（`VanillaDeepOcean`），浅海有 `VanillaOcean` /
`VanillaFrozenOcean` / `BOPKelpForest` / `BOPCoralReef`；而 RWG 的
`oceanDeep[4]` / `oceanShallow[4]` 是**按气候写死的 8 个实例**。

因此 `RtgBiomeCategorizer.fillMissingOceanSlots` 的兜底会触发（实测 5 次），
结果是**四个气候共用同一个深海群系**。

**这一项不能靠"照抄"补齐**（本轮实测）：MC 1.12.2 只有
`OCEAN` / `DEEP_OCEAN` / `FROZEN_OCEAN` 三个海洋群系（`frozen_deep_ocean` 是 1.13 才有的，
我按 1.13 的印象新建 `RealisticBiomeVanillaFrozenDeepOcean` 时被**编译器直接挡下**），
21 个 `MUTATED_*` 里没有任何海洋突变体；BOP 1.12.2 也只有 `kelp_forest` / `coral_reef`。
RWG 的那 4 个深海是 RWG **自造的群系**，凭空造群系属于发明而非移植。

⇒ 保留 `fillMissingOceanSlots` 的借用（1.12.2 下的正确适配），
并让借用**可见**（`-Drtg.debugLayout` 逐气候打印 `浅海=… 深海=…` 与两个 patch 钩子）。


### 本轮自检发现的自己的性能问题（已修）

上轮引入的 `nearbyMountainChainInfluence` 对**每一列**扫 441 个采样点，
每点都调 `RtgRealisticIndex.biomeOf`，而 `mcBound()` / `usedBound()` 当时是
**`synchronized`** —— 每区块约 **11 万次加锁调用**。

已做两处修正：
1. `RtgRealisticIndex` 的读路径**全部无锁**：`mcBound` 改 volatile 惰性冻结
   （竞态下两线程算出同一个值，幂等），合成表改 volatile 数组；只有 `register` 加锁。
2. 生成器新增 `sampleIsChain[441]`，在采样网格建好后**一次性**解析 441 次；
   `nearbyMountainChainInfluence` 内层只剩一次布尔数组读取（11 万次 → 441 次解析）。

**教训**：把调用次数说明写进方法 javadoc（本文件已写），否则下一轮很容易再往热路径里塞东西。

---

## 16. D1 / D2：清掉两套"平行实现"（本轮）

### D1 —— `TerrainBase` 里有一整套**与布局重复的河道族**

`RtgBiomeLayout` 已经提供 `getRiverStrength` / `getRiverTunnelStrength` /
`getRiverJunctionStrength` / `calculateRiver`（全部是 RWG 逐行移植 + `RwgCellNoise`），
而 `TerrainBase` 里还留着一份**用 rtgc 自造 `SpacedCellularNoise` 写的等价物**：

| 已删 | 原用途 |
|---|---|
| `getRiverStrength`（3 个重载，含 `warpedRiverVoronoi`） | 只被 `/rtg probe` 与单群系调试路径用 |
| `getRiverTunnelStrength` / `getRiverJunctionStrength` | **只**被 `/rtg probe` 用 |
| `fillRiverStrengths` | 0 调用者 |
| `rwgCalculateRiver` / `rwgRiverBed` | 只被单群系调试路径用 |
| `RWG_RIVER_BED_LITERAL` / `RWG_RIVER_CARVE_WIDTH_BLOCKS` / `BORDER_DISTANCE_CALIBRATION` | 仅供上面两者 |
| `bayesianAdjustment` | 删完上面后无人调用 |

**关键点（下一轮别再犯）**：`BORDER_DISTANCE_CALIBRATION = 1.171f` 是
`gradlew calibrateCellularNoise` 为 **rtgc 自造的 `SpacedCellularNoise`** 标定的
（实测 `c` 系统性偏大 17%）。而布局用的是**逐行移植的 RWG `CellNoise`**（`RwgCellNoise`），
几何与 RWG 一致 ⇒ **RWG 的原始宽度字面量 `50/1300` 才是对的，不需要标定**。
两份实现不能混用常数。

**改为**：`/rtg probe` 与 `ChunkGeneratorRTG.getNewerNoiseSingleBiome` 都改走布局，
与主生成路径**完全同源**。副作用是单群系调试路径的河道约定也顺带对齐了
（原先它写 `landscape.river[k] = riverValues[k]`，而主路径写 `-riverValues[k]`）。

同时删除两个因之变为无人引用的类：`VoronoiBasinEffect`、`VoronoiPlateauEffect`
（`heighteffect` 包，此前已确认 0 外部引用）。

**`TerrainBase` 1282 → 1027 行。**

### D2 —— `BiomeAnalyzer` 里三阶段修复留下的整套死重量

三阶段群系修复删除后，它的一整套邻域搜索机制还在，但**没有任何入口**：

- 内部类 `SmoothingSearchStatus`（约 170 行）及其全部方法
  （`hunt` / `search` / `smoothBiomes` / `smoothQuadrant` / `addBiome` / `addWeight` /
  `preferredBiome` / `biomeIndex` / `clear` / `isAbsent` / `isNotHunted` / `setNotHunted`）；
- `filterForFlag` / `setSearches` / `setupBeachesForBiomes` / `riverAdjusted`；
- 只喂给它们的字段 `preferredBeach` / `flagCache` / `beachSearch` / `landSearch` /
  `oceanSearch`，常量 `NO_BIOME`（其全部 6 处使用都在上述死簇内）。

**判定依据**：`newRepair` 只读 `biomeIDs`（`initBiomes` 填）与 `landscape`，
不触碰上述任何成员；`biomeNeighborhood` 形参的唯一消费者 `hunt` 已删，
故它现在也无人使用（保留形参只为不改调用点签名）。

**`BiomeAnalyzer` 355 → 139 行**，只剩 `initBiomes` / `newRepair` / `xyinverted` 三个成员。

### 复验

- `gradlew build` 通过；
- `tools/terrain-wiring-check.ps1`：**130/130**；
- `tools/reachability.ps1`：`TerrainBase` 中不可达的只剩 `terrainDunes`
  （RWG 忠实移植、待接线，**保留**）；`hills` 已随 D1 一并删除。

### 仍未做（阶段 C2 / C3）

> **⚠ 历史快照（写于该轮，勿当待办）**：C2 `SMALL` 池与 C3 `ISLAND` / `SMALL_ISLAND` 已在
> §18 实现（`RWG_PLACEMENTS` 照抄 + `ISLAND` 池适配）；`LARGE_ISLAND` 仍是空的，
> 那是**能力缺口**，见 §0.5.1 第 4 条。**当前还剩什么只看 §0.5.1。**

C2 `SMALL` 池 / C3 `ISLAND`、`SMALL_ISLAND`、`LARGE_ISLAND` 池**仍为空**。

**为什么本轮没做**：RWG 的这几个池是**作者手工挑选**的"特殊小群系"
（`SMALL` = flowerField / quagmire / tropics / oasis + 河流群系；
`SMALL_ISLAND` = garden / ominousWoods / tropics / …；
`LARGE_ISLAND` = hotPlainsCanyonIsland / fungiForest）。
rtgc **没有** `tropics` / `garden` / `fungiForest` 这三个对应群系，
按名字硬凑就是发明；而且 `SMALL` 池会带来"每 75 格半径散布一个特殊群系"的可见效果，
填错比留空更糟（留空时该特征被安全跳过，岛屿则回落原版）。

**需要用户决策**：见 §17 待决清单。

---

## 17. 需要你决策的三件事（我不擅自发明）

> **⚠ 历史快照（勿当待办）**：① D5 用户已裁定"做"并**已完成**（§0.5.3）；
> ② C2/C3 已在 §18 实现；③ 装饰的邻域分摊**已按选项 (c) 实现**（概率式），
> 只剩"逐装饰器 `strength` 缩放"（§0.5.1 第 1 条）。
> 也就是说本节 4 项里只剩 ③ 还开着。

这四项按"照抄"的口径都**做不下去**了 —— 不是没找到 RWG 怎么做，而是 rtgc 缺少可抄的对象，
继续做只能靠发明。列在这里等你定。

### ① D5：深海群系不足（rtgc 1 个 vs RWG 4 个）

RWG 按气候写死 `oceanDeep[SNOW/COLD/HOT/WET]` 与 `oceanShallow[×4]`（8 个实例）；
rtgc 只有 `VanillaDeepOcean` 一个深海。`RtgBiomeCategorizer.fillMissingOceanSlots`
因此触发兜底（实测 5 次），四个气候**共用同一个深海群系**。

要"补齐"就得**新增 3 个生物群系**。选项：
- (a) 维持现状（四个气候共用 1 个深海）——不发明，但海洋很单调；
- (b) 新增 3 个深海群系（例如 Frozen/冷暖区分的深海）——**发明**，需要你点头；
- (c) 用已有的 BOP 海洋群系（`BOPKelpForest` / `BOPCoralReef`）充当浅海差异，
  深海仍只有 1 个。

### ② C2 / C3：SMALL 与岛屿池没有可抄的对象

RWG 的池成员里有 **`tropics` / `garden` / `fungiForest`**（BOP 1.7.10 的群系），
rtgc 的 BOP 1.12.2 **没有这三个**。而 `SMALL` 池的效果是
"每 75 格半径散布一个特殊群系"，填错的代价是可见的杂乱。

选项：
- (a) 保持为空（现状：SMALL 特征安全跳过，岛屿回落原版群系）；
- (b) 按名字取**能对上的子集**（`BOPFlowerField` / `BOPQuagmire` / `BOPOasis` →
  SMALL；`BOPOminousWoods` / `BOPMarsh` → SMALL_ISLAND），落空的槽位留空；
- (c) 三池都填本气候核心池（与 C1 同法）——机制最简单，但 SMALL 会变成
  "全世界随机散布各类群系小圆盘"，很可能很难看。

### ③ D4 发现 #3：装饰的邻域分摊需要跨接口重构

RWG 每区块先按 9×9 区块邻域把总装饰量 1.0 分摊给各相邻群系，再各自 `rDecorate(strength)`。
要照抄必须给 `IRealisticBiome.rDecorate` 补 `strength` 形参，
并让约 **32 个装饰类**按它缩放循环次数。

选项：
- (a) 维持现状（按单点群系装饰，总量相同但边界不混合）；
- (b) 做完整重构（跨接口，改动面大，但这是"装饰看起来像 RWG"的最后一环）；
- (c) 折中：在生成器里按权重**概率性地**调用各相邻群系的 `rDecorate`
  （不碰装饰类，统计上近似 RWG 的分摊，但每区块的确定性会变）。

### 另需你做的事：先测一次

`1.0.10` → `1.0.14` 累积了地形反相修复、129 群系迁移、山地链、边界池、
装饰高度图转置修复等**全部未经实机验证**的改动。
继续往上叠之前，建议先跑一次 `rtgc-1.0.14.jar`（**必须新建世界**），看三件事：
1. 地表有没有花草树（D4 的转置修复是否生效）；
2. 地表起伏是否"像 RWG"（1.0.10 的 river 反相修复）；
3. 日志里 `[RTG] 山地链池（极端边界）：…` 那行是否**非零**（B4+C1 是否生效）。

---

## 18. C2 / C3：SMALL 与岛屿池（本轮完成）

### 🔴 先纠正一处此前的语义误解（也影响 C1 的定位）

`Support.addBiome`（`Support.java:216-246`）才是池语义的真相：

```java
if (placement != BiomePlacement.CORE && cat != BiomeCategory.SMALL && cat != BiomeCategory.TEST) {
    listFor(cat, placement).add(b);   // 只进该池
    return;                           // ← **不进 core**
}
switch (cat) {
    case SMALL:                       // 注意：cat 是 SMALL，不是 placement
        biomes_small.add(b);
        snow.small.add(b); cold.small.add(b); hot.small.add(b); wet.small.add(b);
```

两条要点：

1. **带 placement（BORDER / LITTORAL / ISLAND / SMALL_ISLAND / …）的群系不进 core。**
   也就是说 RWG 的 `core[气候]` 与 `border[气候]` 是**互斥**的两组群系。
2. `BiomeCategory.SMALL` 的群系会进**全部四个气候**的 `small` 池（旧版遗留行为，
   源码里带注释说明）。全 RWG 只有 `baseRiverTemperate` 一个属于这一类。

> **对 C1 的影响（已知偏离，维持不改）**：`mirrorCoreIntoBorders()` 把 `border =
   coldBorder = hotBorder = core`，等于让 core 群系**也**出现在边界池里 —— 与上面第 1 条
> 相反。之所以仍这么做：rtgc 的 MC/BOP 群系**没有作者标注**，无法知道"哪些群系属于边界"；
> 用核心池是唯一不发明的填法，效果是边界列仍拿到本气候群系（此前是 null）。
> 代价是 `borderDirection` 不产生方向性差异。**这是一处有意的语义偏离，不是照抄。**

### C2 / C3 的精确清单（`tools/rwg-placement-of.ps1` 从 `Support*.java` 提取）

| placement | 气候 | RWG 成员 | rtgc 可对应 |
|---|---|---|---|
| `SMALL` | COLD | `flowerField` | **`BOPFlowerField`** ✅ |
| `SMALL` | WET | `quagmire` | **`BOPQuagmire`** ✅ |
| `SMALL` | HOT | `tropics`、`oasis` | **`BOPOasis`** ✅（`tropics` rtgc 无） |
| `SMALL` | SNOW / COLD | `baseRiverIce` / `baseRiverTemperate` | 无（RWG 自造群系） |
| `SMALL_ISLAND` | WET | `mushroomIsland`、`tropics` | **`VanillaMushroomIsland`** ✅ |
| `SMALL_ISLAND` | SNOW / COLD | `ominousWoods` / `garden`、`baseRiverCold` | **`BOPOminousWoods`** ✅（`garden` rtgc 无） |
| `LARGE_ISLAND` | HOT / WET | `hotPlainsCanyonIsland` / `fungiForest` | **无** ❌ |
| `ISLAND` | — | **实际为空**（`Support.java` L262-266 只是 `listFor` 的 switch） | — |

### 实现（`RtgBiomeCategorizer`）

1. **`SPECIALS` 表**：把上表能对上的 5 个按**名字**移入对应池，并**沿用 RWG 给的气候**
   （不重新分类 —— 例如 `BOPOminousWoods` 按温度会被分到 COLD，而 RWG 把它放在 SNOW 的
   `SMALL_ISLAND`，这里照 RWG）。忠实于"不进 core"的语义：命中的群系**只进该池**。
2. **`islandBiome(name)`**：名字含 `island`（`mushroomIsland` 除外，它已归 `SMALL_ISLAND`）
   → `ISLAND` 池。这是 C3 的主要落点：rtgc 自带 `BOPOriginIsland` / `BOPFlowerIsland` /
   `BOPVolcanicIsland` / `BOPTropicalIsland` 四个岛屿群系，此前被当普通陆地群系撒在大陆上。
3. **`LARGE_ISLAND` 保持为空** —— rtgc 没有 `fungiForest` / `hotPlainsCanyonIsland`
   对应物，**不发明**。

### `selectIslandBiome` 的适配（**有意的偏离**）

RWG 在 `general.isEmpty() && sized.isEmpty()` 时返回 `null`。rtgc 若也返回 null，
岛屿列会**回落到原版 GenLayer 群系**（与所在气候无关），比拿到一个气候正确的群系更糟。
故改为逐级退化：`smallIsland/largeIsland` → `island` → **本气候核心池**。
效果：岛屿像该气候的"微缩大陆"。这是适配，不是照抄，已在方法 javadoc 标明。

### 接线核验（避免又一次空转）

- `RtgBiomeLayout.add()` 的 switch **覆盖全部 11 个 placement**（含 `SMALL`/`ISLAND`/
  `SMALL_ISLAND`/`LARGE_ISLAND`），`default` 会抛异常 —— 不存在"静默丢弃"；
- `small[climate]` 在 `getLandBiomeAt`（Poisson 小群系分支）被读取；
- `selectIslandBiome` 在 `computeBiomeDataAt` 被调用；
- `logReport` 新增 `岛屿=` / `特殊=` 两个计数，运行期可核对。

### B1 的装饰缩放：**本轮已实现**（见 §19）

---

## 19. B1 收尾：山地链的装饰缩放过来了（本轮）

这是目标清单里**最后一条被显式要求、却一直欠着**的子项。

### RWG 的做法

`RealisticBiomeMountainChain.rDecorate`（`RealisticBiomeMountainChain.java:55-88`）：

```java
for (offsetX = 8; offsetX < 24; offsetX += 4)        // 4×4 = 16 个采样点
  for (offsetZ = 8; offsetZ < 24; offsetZ += 4) {
      float height = manager.getNoiseAt(x, z);
      float change = max(|h - getNoiseAt(x±4, z)|, |h - getNoiseAt(x, z±4)|);
      if (change <= 4f) gentleSampleCount++;
      sampleCount++;
  }
backingBiome.rDecorate(..., strength * gentleSampleCount / sampleCount, river);
```

**效果**：陡峭的山体上不长成片森林，平缓处照常。这是山地链视觉上"像山"而不是"长满树的石堆"的关键。

### 🔴 关键前提：装饰群系必须改从**布局**取，否则整件事是空转

原先 `ChunkGeneratorRTG.populate` 里是：

```java
biome = RTGAPI.getRTGBiome(biomeProvider.getBiome(blockPos.add(16, 0, 16)));
```

山地链与它的备份群系**共用同一个 MC 群系**，所以这条往返只会拿回**备份群系**
（MC 群系 → MC 编号 → `RTGAPI`），链的 `rDecorate` **永远不会被调用**。
即便把 `rDecorate` 写对，也仍然一行都不会执行 —— 又一个"改了死路径"的陷阱。

已改为先查布局（现实主义编号空间）、查不到再退回原路：

```java
final IRealisticBiome layoutBiome = RtgLayoutAccess.biomeAt(x + 16, z + 16);
biome = layoutBiome != null ? layoutBiome
      : RTGAPI.getRTGBiome(biomeProvider.getBiome(blockPos.add(16, 0, 16)));
```

对**非链**的列两种取法结果完全相同（`getBiomeDataAt` 返回的 `IRealisticBiome`
其 `baseBiome()` 正是 `biomeProvider.getBiome` 给的那个 MC 群系），所以这行改动
只对山地链生效。

### rtgc 侧的实现与两处适配

`RealisticBiomeMountainChain.rDecorate` + `gentleFraction(noise)`：

| 项 | RWG | rtgc | 为什么 |
|---|---|---|---|
| 缩放方式 | `strength × 比例` 传给备份群系 | **逐个 deco 以该比例为概率触发** | rtgc 的 `IRealisticBiome.rDecorate` **没有 `strength` 形参**，无法表达按比例缩放。概率法的**期望装饰量与 RWG 一致**；代价是单区块确定性不同（多消耗随机数）。 |
| 采样网格 | 偏移 `8,12,16,20`（跨到下一区块） | 偏移 `4,8,12`（3×3 = 9 点，**区块内**） | `rDecorate` 只拿得到本区块的 256 列高度数组，不能跨界取样。越界的 ±4 邻居按 RWG 的方式跳过。 |
| deco 收到的群系 | 备份群系（RWG 转给 `backingBiome.rDecorate`） | **备份群系**（同左） | 保持与 RWG 一致 |
| 原版装饰 | 由备份群系的 `rDecorate` 负责 | 复刻默认实现（`overridesHardcoded()` / `disableVanillaVegetation()` + `baseBiome().decorate`） | 保持与 `IRealisticBiome` 默认实现一致 |

### 仍未做（与 §17 ③ 的关系）

> **⚠ 历史快照（写于该轮，勿当待办）**：山地链的装饰缩放**已实现**
> （`RealisticBiomeMountainChain.gentleFraction()`，本轮已复核），
> 而本节末尾说"邻域分摊仍未做"也**已经过时** —— `ChunkGeneratorRTG.populate` 已按
> 9×9 / `0.01234569` 权重概率式分摊（§17③ 的选项 (c)）。真正还欠的只有
> "逐装饰器按 `strength` 缩放"⇒ §0.5.1 第 1 条。

§17 ③ 描述的是**更广的一件事**：RWG 对**每一个**区块都按 9×9 区块邻域把总装饰量 1.0
分摊给各相邻群系（`borderNoise` 81 × 0.01234569 = 1.0），而不只是山地链。
本轮**只**实现了山地链自己的按比例缩放 —— 因为那正是 B1 明确要求的。

"所有群系都按邻域分摊"仍未做：它需要把 `populate` 里那一次 `rDecorate` 换成
遍历 81 个采样点各自的群系，且同样缺 `strength` 形参（只能用同样的概率法近似）。
这条继续留在 §17 ③ 的待决清单里。

---

## 20. A1 / A2 逐行复核（本轮）—— 又抓到一处未完成项

目标要求"凡能离线验证的跑一遍"。本轮把 A1 的四个已移植函数与 A2 的偏离项
**逐个打印原文比对**，结果如下。

### ✅ 三个函数与 RWG 逐行一致

| 函数 | 比对结果 |
|---|---|
| `terrainDunes` | 与 RWG `TerrainDunes` 逐行一致（`160/35/0.38/0.2/60/2/50/10/4/25/30/14`，`return 70f + h * river`）。`cell.noise(x/25D, y/25D, 1D)` → `cellDistance(cell.eval2D(...))` 是全文统一的移植约定。 |
| `terrainSmallIsland` | 一致（`HILL_START=6` `HILL_HEIGHT=120` `BASE_HEIGHT=58` `HILL_WIDTH=200`，`/4.5f`、`1.5f` 上限 15、`70D`、`20/5`、`12/3`、`5/1.5`，`max(58, 58+h)`）。 |
| `terrainSmallSupport` | 一致（`100/8`、`30/4`、`15/2`、`7/1`，`return 70f + 20f * river + h`）。 |
| `terrainMesa` | 一致（含 `border > 0.95f + hn * 0.09f` 的 `c2` 支路 —— 这是全地形函数里唯一需要 `border` 的）。 |

### 🔴 `terrainPolar` 的两处问题（都已修）

1. **钳制值不一致（A2 未真正完成）**：忠实重载（`terrainPolar(int,int,RTGWorld,float)`）
   早已是 `0.2f`，但**参数化重载**（`RealisticBiomeVanillaDesert` 用的那个）仍写着 `0.1f`。
   同一个公式在两处不一致。**已统一为 `0.2f`**。
2. **命名歧义**：RWG 的 `TerrainPolar` **没有参数**，而 rtgc 的 9 参重载是把它参数化后的
   泛化版。这类"同名不同物"正是 `terrainFlatLakes` 那次踩过的坑，故已在两处 javadoc 里
   互相点名说明。

### `terrainDunes` 为什么 0 调用者（不是遗漏）

RWG 里 `TerrainDunes` 的**唯一**使用者是 `desert\RealisticBiomeDuneValley`。
rtgc 的群系集合里**没有**对应物（BOP 1.12.2 没有 dunes / dune valley 群系），
故它和 `terrainPolar` 的忠实重载一样属于"**已移植、待接线**"，与 `terrainDunes` 并列。
（另：RWG 的 `RealisticBiomePolar` **不在** `Support.java` 里 ——
它在 RWG 的大陆模式下也从不被使用，所以忠实重载无人调用是**忠实**的。）

### `RealisticBiomeVanillaDesert` 用沙丘地形：**一处已知偏离**（未改）

现状：`terrainPolar(…, stPitch=200, stFactor=duneHeight, hPitch=70, hDivisor=40, base=64)`
`+ groundNoise(x, y, 1f, rtgWorld)` —— 即**把参数化的 TerrainPolar 当沙丘地形用**。

RWG 的沙漠族是 `desert\RealisticBiomeDesert → TerrainHilly(150f, 50f, 0f)`，**没有沙丘**；
RWG 的沙丘在 `RealisticBiomeDuneValley → TerrainDunes`。

**为什么没改**：
- 它是 GPU 之外的 **`sandDuneHeight` GUI 滑条（1–10）的唯一消费者**。
  换成 `terrainHilly` 会让该滑条变成**死配置** —— 对用户可见的功能退化。
- 视觉上它产出的是沙丘脊线，与"MC 沙漠"的观感相符；换成 `terrainHilly(150,50,0)`
  会变成普通的沙漠丘陵。

**若你要完全照抄**，改法是把
`RealisticBiomeVanillaDesert.TerrainVanillaDesert.generateNoise` 的最后一行换成：

```java
return terrainHilly(x, y, rtgWorld, river, 150f, 50f, 0f, 260f, 68f);
```

（与 `BOPColdDesert` 现在的写法一致），代价是 `sandDuneHeight` 滑条失效。
该注释已写进源码，随时可切。

### 附带发现：RWG 的 `brushland` 被加了**两次**

`SupportBOP.java:131-144` 与 `145-…` 各 `addBiome` 了一次 `brushland`：
前者 `TerrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)`，
后者 `TerrainDuneValley(300f)` —— **同一个 MC 群系挂两种地形**。

RWG 用 `List.add` 两次，于是它在池里占**两个位置**（权重翻倍），而
`RealisticBiomeBase.getBiome(biomeID)` 的数组槽位只留最后一个。

rtgc 的模型是"一个 MC 群系 ↔ 一个 `IRealisticBiome`"，**无法表达重复**。
当前 `BOPBrushland` 取**第一个**（GrasslandHills）。这是一处结构性的不可照抄，
已记录，不做改造。

（同一文件里 `heathland` 条目在 rtgc 无对应群系，`outback` 已正确映射到
`terrainDuneValley(300f)`。）

---

## 21. 全部 20 个地形函数的系统性并排比对（本轮）

上一轮的"逐行打印比对"连着两次抓到真问题，本轮把它**工具化并跑满全部函数**：
`tools/terrain-fidelity-diff.ps1` 会把 RWG 的 `rwg/terrain/TerrainXxx.java` 与
rtgc 的 `TerrainBase.terrainXxx` 按**已记录的移植约定**归一化后并排打印。

> **工具原则**：刻意**不**做自动序列比对。早期两次"常量序列比对"都误报，本工具只归一化
> 约定性差异（`perlin.noise2(`→`noise2f(`、`cell.noise(a,b,1D)`→`CELLDIST(cell.eval2D(a,b))`），
> 其余原样打印、由人（或模型）读。已验证：工具对**等价写法**（`x * INV_20` ≡ `x / 20f`、
> `? :` ≡ `Math.min/max`、`0.4f` ≡ `/ 2.5f`）不会误报为差异之外的东西 —— 它**会**标出来，
> 但一眼可辨。

> **⚠ 工具自身的坑（第三次踩）**：`.ps1` 里**不能出现任何非 ASCII 字面量**。
> Windows PowerShell 5.1 按 ANSI 读取无 BOM 的 `.ps1`，中文注释会把解析器搞崩
> （报"缺少右括号"）。本工具已纯 ASCII 化。

### 🔴 抓到 1：`terrainMarsh` 的基准高度**低了 0.5 格**（10 个群系）

RWG 的 `TerrainMarsh` 是 `return 62f + h;`，而 **62 就是它的水面顶** ——
`ChunkGeneratorRealistic.generateTerrain:292` 写的是
`if (k < 63) blocks[p] = Blocks.water;`，即水占 `y ≤ 62`。

rtgc 把水位做成可配置（F-41），把 62 提成形参 —— 这是对的。
但 **10 个调用点全部传了 `WaterLevel.current().riverSurface()`**，那是 `seaLevel - 1.5` = **61.5**。
正确的值是 `waterSurfaceTop()` = `seaLevel - 1` = **62**（默认海平面下与 RWG 逐位相同）。

后果：整个沼泽 / 湿地族（`BOPBog` `BOPDeadSwamp` `BOPFlowerField` `BOPMarsh` `BOPOasis`
`BOPQuagmire` `BOPWetland` `VanillaSwampland` `VanillaSwamplandM`
`VanillaMushroomIslandShore`）**整体低 0.5 格**，水面覆盖面积因此偏差。

**已修**：10 处全部改为 `waterSurfaceTop()`，并在 `terrainMarsh` 的 javadoc 里写明
"必须传 `waterSurfaceTop()`，不是 `riverSurface()`"。

### 🔴 抓到 2：cell 坐标的**精度写法**与自己声明的约定矛盾（10 处）

本文件在 `terrainDunes` 的注释里明确声明过：

> ③ cell 项同样用 RWG 的 `x / 25D` 双精度写法，**而非** `x * 0.04f`。

RWG 的 `cell.noise(x / 25D, y / 25D, 1D)` 用的是**双精度除法**；`x * 0.04f` 是**单精度乘法**。
两者在 x 较大时的差距足以把 Voronoi **采样点挪过单元边界** —— 那会在世界里留下可见的接缝。

实测有 **10 处**违反了该约定（我的第一次 grep 只查了 `0.0x` 形态，漏了 `INV_` 形态）：

| 原写法 | 处数 | 改为（RWG 字面量） |
|---|---|---|
| `x * 0.04f` | 5 | `x / 25D` |
| `x * 0.005f` | 1 | `x / 200D` |
| `x * 0.02f` | 1 | `x / 50D` |
| `x * INV_70` | 1 | `x / 70D` |
| `x * INV_30` | 2 | `x / 30f`（RWG 这里就是 `30f` 单精度） |

另把 4 处 `(h - 35f) * 0.66666667f` 改回 RWG 的字面写法 `(h - 35f) / 1.5f`
（除法与乘倒数的结果可能差 1 ulp；既然要照抄就照抄）。

### ✅ 逐行比对确认为**忠实**的函数（全部差异均为等价写法）

| 函数 | 备注 |
|---|---|
| `terrainHilly` | `INV_20`/`INV_35`/`INV_30`/`INV_8`、`0.4f`≡`/2.5f`、`0.05f`≡`/20f`、`0.04f`≡`/25f` 全部等价 |
| `terrainMountain` | 同上；`d2` 的 `/1.5f` 已拨正 |
| `terrainMountainRiver` | 同上 |
| `terrainMountainSpikes` | 同上；`/200D` 已拨正 |
| `terrainSwampMountain` | 同上；`/1.5f` 已拨正 |
| `terrainSwampRiver` | 逐字等价 |
| `terrainGrasslandHills` | `* 0.025f`≡`/40f`、`Math.min`≡三元、`Math.max(l, 8f)`≡`l < 8f ? 8f : l` |
| `terrainGrasslandMountains` | `m += sm + c` ≡ `m += sm; m += c` |
| `terrainCanyon` | `r` 的双重钳制等价；**`sb` 等价** —— RWG 的 `sb < 0f ? 0f` 位于 `if (b > 0f)` 内，是**死分支**，故 rtgc 的 `Math.min(b, 7f)` 与之等价 |
| `terrainDuneValley` | 逐字等价 |
| `terrainFlatLakes` | 函数体逐字一致 |
| `terrainHighland`（两个重载） | 4 参版与 RWG 的 4 参构造一样转发到 9 参版并传 `1f`；9 参版逐行一致 |
| `terrainDunes` `terrainSmallIsland` `terrainSmallSupport` `terrainMesa` | 上一轮已确认（§20） |

### 结论

**20 个地形函数现已全部逐行核对过**，本轮修掉的是最后两类系统性偏差：
一个是**基准高度的量纲选错**（沼泽低 0.5 格），一个是**cell 坐标的精度写法与自己声明的约定矛盾**。
两者都不是"公式抄错"，而是"抄对了公式却喂错了数值" —— 与 1.0.10 那次 river 反相属于同一类。

---

## 22. 布局与地表的逐行核对（本轮）

地形函数核对完之后，剩下的验证前沿是**布局**（`RtgBiomeLayout` vs `ChunkManagerRealistic`）
与**本轮新写的 `SurfaceMountainStoneMix1`**。

### `RtgBiomeLayout` 的常量：1:1 对应

| 常量 | RWG | rtgc | |
|---|---|---|---|
| `SHALLOW_OCEAN_WIDTH` | 300f | 300f | ✓ |
| `CLIMATE_WARP_SCALE_MULTIPLIER` | .4f | .4f | ✓ |
| `CLIMATE_WARP_STRENGTH_MULTIPLIER` | .8f | .8f | ✓ |
| `BIOME_WARP_SCALE_MULTIPLIER` | .4f | .4f | ✓ |
| `BIOME_WARP_STRENGTH_MULTIPLIER` | .175f | .175f | ✓ |
| `SNOW/COLD/HOT_CLIMATE_LIMIT` | .16875/.545/.78 | 同 | ✓ |
| `CLIMATE_BORDER_DISTANCE_DIFFERENCE` | 288D | 288D | ✓ |
| `LITTORAL_WIDTH` | 432f | 432f | ✓ |
| `SMALL_BIOME_RADIUS` | 75D | 75D | ✓ |
| `climateWidth` / `biomeWidth` | 1400f / 500f | 同 | ✓ |

RWG 多出的 `THREE_CLIMATE_SNOW/COLD_LIMIT` 是**三气候模式**用的（`wetEnabled == false`）；
rtgc 选了 4 气候模式（RWG 的 `RWG_CONTINENT` 完整形态），故不需要。
`VOLCANO_RIVER_SAMPLE_SPACING` 属火山（豁免）。

**rtgc 多出的两个常量经核对也都来自 RWG**：
- `CLIMATE_SHIFT = 4000D` ← RWG `:707` `biomecell.noise((warped[0] + 4000D) / climateWidth, …)`；
- `COAST_WIDTH = 24f` ← RWG `:590` `continent < 24f`。

### 选择逻辑：逐条对应

RWG `getBiomeDataAt`（`:539-605`）与 rtgc `computeBiomeDataAt` 的分支顺序**完全一致**：

```
缓存查询 → continent < 0 → 海洋
         → 火山（rtgc 整支省略）
         → islandTier >= 0 → selectIslandBiome
         → 仍为 null → getLandBiomeAt，若 continent < LITTORAL_WIDTH 且滨海池非空 → 换滨海
         → 非岛屿且非滨海 且 continent < COAST_WIDTH → coastIce / coastDunes
```

`getClimateValue` / `warpClimateCoordinates` / `sampleBiomeSelector` 的域扭曲偏移
（`+4000D`、`+1731f/-2459f`、`-8191f/+3137f`、`+5171f/-6971f`）与 `getTerrainOceanValue`
（`clamp(1 + continent/100, 0, 2)`）、`getOceanBiome` 的阈值（`-90f` / `> 0f` /
`-20f~-150f` / `> .07f`、`patch` 的 `180f/0.7 + 55f/0.3`）也全部一致。

### 三处**已记录的适配**（不是偏离）

1. **不缓存 null**：RWG 的 `biomeDataMap.put(coords, output)` 无条件执行，
   于是"某列拿不到群系"会被**永久缓存**。rtgc 刻意不缓存 null，让核心池未注册时
   **每列都能重试**（fail-soft）。这是有意的行为差异。
2. **火山分支省略**：RWG 的条件是
   `Support.volcanoIsland instanceof RealisticBiomeIslandVolcano && …`，
   在 `volcanoIsland == null`（rtgc 的火山豁免）时本就为假 ⇒ **省略是行为保持的**。
3. **海洋槽 null 兜底**：RWG 的 `oceanDeep*`/`oceanShallow*` 是按气候写死的 8 个实例；
   rtgc 的海洋群系不齐（见 §17 ①），故槽位为 null 时退化并记录日志。

### `SurfaceMountainStoneMix1`（本轮新写）：逐行一致

与 RWG 同名类逐条对应（唯一替换是已记录的
`CliffCalculator.calc(x, y, noise)` → `TerrainBase.calcCliff(x, z, noise, river)`，
参数顺序与 rtgc 的 `noise[x*16+z]` 索引一致。⚠ 注意这个替换**不是纯改名**：
4 参版比 RWG 的 3 参版多一条"水线附近且靠河心（`river > 0.85`）时取四邻最小"的抑制，
见 §0.5.2 的偏离表）：

- `depth == 0` 段：`k < 63` 的 beach/gravel、`p = noise3(i/8, j/8, k/8) * 0.5f`、
  `cliff = 1 / 2` 的两个判定、`cliff == 1` 的 `nextInt(3) == 0 ? cobble : stone`、
  `cliff == 2` 的染色粘土、`k < 63` 的 beach / `k < 62` filler / else top、
  `noise2(i/12, j/12) > mixHeight` 的 mix 分支 —— 全部一致；
- `depth < 6` 段：`cliff==1 → hcStone()`（RWG 是 `Blocks.stone`）、`cliff==2 → clay`、
  `gravel → beachBlock`、`m → mix`、else `fillerBlock` —— 全部一致。

实现层面的等价替换（已核对）：
- RWG 的 `Blocks.stained_hardened_clay + metadata 9` → `BlockUtil.getStateClay(CYAN)`
  （1.7.10 的 `stained_hardened_clay` 元数据 9 就是**青色**）；
- RWG 的 `Blocks.stone`/`Blocks.cobblestone` → `hcStone()`/`hcCobble()`
  （来自备份群系 config，默认值正是 STONE / COBBLESTONE）；
- RWG 的 `topByte`（恒为 0）无需写元数据；
- `gravel` 只在 `beach` 为真时置真，故 `beachBlock` 为 null 时不会 NPE（山地链构造传 `beach = false`）。

---

## 23. 五个离线标定工具实测（本轮）—— 并修正一处此前的模糊说法

目标写着"凡能离线验证的（标定工具）跑一遍"。这一路我**一次都没跑过它们**，
而本轮删除 `bayesianAdjustment` / `VoronoiBasinEffect` / `VoronoiPlateauEffect` /
`actualBiomeIdBound` 之后，它们**有可能已经编译不过**。

### 结果：`compilePreviewJava` 通过，五个工具全部跑通

| 工具 | 关键实测值 |
|---|---|
| `calibrateClimateBands` | **气候带面积：SNOW 13.99% / COLD 29.77% / HOT 28.45% / WET 27.79%** |
| | 气候边界带（用 `border` 列表）：**27.31%**（占全图） |
| | 极端边界（`\|Δclimate\| > 1`，即**山地链**）：**12.03%**（占全图） |
| `calibrateContinentalField` | 海洋 51.27% / 陆地 48.73%；深海 36.82% / 浅海 14.45% |
| | 海带带 47.00% / 珊瑚带 6.29% / 海岸带 1.09% / 滨海带 16.23% / 内陆 32.50% |
| | 岛屿 2.473%（tier0 0.95% / tier1 1.52%） |
| | **地标确认失效**：`getVolcanoCoordinates` 与 `getLavaCaveCoordinates` 全部 `Long.MIN_VALUE`，命中 0 —— 火山/地标豁免**由实测确认**，且 `ContinentalNoise` 未改一行 |
| `calibrateLayoutApportionment` | 分支占比：海洋 52.27%、岛屿 2.13%、滨海 13.05%（其中内海岸 0.82%）、内陆 32.56%、小型群系圆盘 0.46%、气候边界 13.04%（其中极端 5.60%）、核心 19.51% |
| `calibrateCellularNoise` | `rwgCellDistance()` 的高度比 **rtgc/RWG = 1.000**（C-5 换算正确）；`borderDistance` 均值比 **1.1710**（即那个标定常数）；隧道带 6.5 格 = 全图 **4.276%**；该带内 `riverStrength` min **0.637** / mean **0.943**；河网带占全图 **33.07%** |
| `calibrateNoiseBackend` | Perlin 与 OpenSimplex 的平坦占比：`/230` 36.34% vs 14.60%；山地幅度比 **0.4832** |
| `calibrateBorderPools`（本轮新增） | 跑**真正的** `RtgBiomeLayout`（Proxy 桩），同 seed 建四个布局逐列比对：命题 A（镜像 vs 留空）**差异 19.73%**、但两边成员占比都≈25% ⇒ **分布相同、逐点不同**；命题 B（旧镜像 vs 新 RWG 成员）**差异 10.65%**，且**核心列 0**、只在边界族；命题 C 山地链变体数 **16 → 4**（生产量级 104 → ~4） |

### ⚠ 修正 §14 的一处模糊说法

§14 里我写边界带"意味着这条边界带**占陆地很大一块**" —— 当时没有数据。
实测（`calibrateClimateBands`，占**全图**百分比）：

| 带 | 占全图 | 占陆地（陆地 = 48.73%） |
|---|---|---|
| 气候边界（`border` / `cold` / `hot` 三池） | 27.31% | **≈ 56%** |
| 极端边界（山地链） | 12.03% | **≈ 25%** |

所以 C1（填边界池）与 B4（填山地链池）影响的**不是边缘情况**：
**过半的陆地列**会走边界池，**四分之一的陆地列**是山地链。
（`calibrateLayoutApportionment` 给的 13.04% / 5.60% 是**已排除岛屿与滨海之后**的陆地细分，
两者口径不同，都对。）

### D3 的目标值（此前一直缺的就是它）

`calibrateClimateBands` 的带面积就是"每个气候应该分到多少群系"的目标：

| 气候 | 带面积 | 修 D3 前的群系数 | 占比 | **倍率（占比/带面积）** | 目标 |
|---|---|---|---|---|---|
| SNOW | 13.99% | 13 | 11.5% | 0.82 | 1.0 |
| COLD | 29.77% | 53 | 46.9% | **1.58** | 1.0 |
| HOT | 28.45% | 20 | 17.7% | **0.62** | 1.0 |
| WET | 27.79% | 27 | 23.9% | 0.86 | 1.0 |

COLD 以 29.8% 的地表占了 46.9% 的群系 —— 这就是"COLD 带内反复出现同几个群系"的量化形式。

**为了让这件事在运行期一眼可见**，`RtgBiomeCategorizer` 新增了
`logClimateSkew`：它把每个气候的「群系占比 / 带面积 / 倍率」并排打出来，
倍率偏离 1 超过 ±60% 就 `WARN`。带面积用的是本工具的实测值
（运行期无法廉价算出 Voronoi 划分的面积，故硬编码为常量并注明来源）。

这样 D3 的修正效果**不需要再跑一遍标定工具**就能从游戏日志确认。

---

## 24. D4 的仪表：补上一直没装的那一半（本轮）

目标 D4 的原文是：

> 查证"没有地表装饰"的原因（**在 populate 里临时记录区块中心群系与 rDecorate 放置数**）

1.0.13 定位并修掉了**根因**（`ChunkInfo` 高度图转置，导致全部 32 个装饰类定位错列），
但**那个仪表从未装上**。本轮补齐。

### 设计：计数放在 `ChunkInfo`

`ChunkInfo` 是**唯一**会传进每一个 deco 的对象（见 `IRealisticBiome#rDecorate` 与山地链的同名覆写），
所以只需要在**两个** `deco.generate(...)` 调用点前各加一行 `ChunkInfo.noteInvocation()`，
就能覆盖全部装饰 —— **不必去改约 32 个装饰类**。

```java
// ChunkInfo
private static int chunkInvocations;
public static void resetInvocations() { chunkInvocations = 0; }
public static void noteInvocation()   { chunkInvocations++; }
public static int  chunkInvocations() { return chunkInvocations; }
```

静态字段与世界生成的单线程前提一致（同 `WaterLevel` / `RtgLayoutAccess`）。

### 为什么"有多少 deco"与"调用了几个"必须分开看

`该群系有多少 deco` 与 `实际被调用了几个` 是**两件事**，三条路径都会把它们拉开：

| 路径 | 效果 |
|---|---|
| `deco.preGenerate(river)` | 按河强过滤掉一部分 deco |
| 山地链的**概率缩放**（1.0.16） | 陡坡上按平缓点比例随机跳过 |
| `RTG.decorationsDisable()` / `DISABLE_RTG_DECORATIONS` | **整段跳过**，只跑原版 `decorate` |

只打一个数会分不清是"没配装饰"还是"被过滤光了"。

### 用法

启动参数加 `-Drtg.debugDecorations`（默认关闭 —— 每区块一行会把日志刷爆）：

```
[RTG-DECO] chunk(12,-3) biome=biomesoplenty:lavender_fields chain=false \
           hasDecos=9 invoked=7 river=0.042 riverBranch=false rtgDecoOff=false \
           vanillaTrees=true mapFeatures=true
```

逐项含义：
- `biome` —— **区块中心群系**（取样点与 RWG 一致：`(x+16, z+16)`），
  也就是装饰实际使用的那个群系；
- `chain` —— 是否是山地链（链会用备份群系的 deco，并按平缓点比例缩放）；
- `hasDecos` —— 该 `IRealisticBiome` 的 deco 列表长度；
- `invoked` —— **本区块实际调用了几次 `deco.generate`**；
- `river` / `riverBranch` —— 装饰期河强，以及是否走了 `getRiverBiome()` 分支
  （河强 > `RIVER_DECORATION_THRESHOLD`(0.8) 时装饰换成河流群系）；
- `rtgDecoOff` —— 是否因开关而**只跑原版装饰**；
- `vanillaTrees` —— `allowVanillaTrees()`（为 false 时 RTG 会先把原版
  树/草/花的密度清零，见 `disableVanillaVegetation`）；
- `mapFeatures` —— 影响洞穴藤蔓与结构。

**排查口诀**：`hasDecos > 0 且 invoked == 0` ⇒ 被过滤或走了关闭分支；
`hasDecos == 0` ⇒ 该群系本身没配装饰；
两者都正常但仍看不到植被 ⇒ 问题在**落点**（回到 1.0.13 的 `ChunkInfo` 高度图）。
`rtgDecoOff=true` 则说明是配置/启动参数把 RTG 装饰关了。

### 接线核验

| 位置 | 内容 |
|---|---|
| `ChunkInfo` | 计数器与三个静态方法 |
| `IRealisticBiome.rDecorate`（默认实现） | `ChunkInfo.noteInvocation()` |
| `RealisticBiomeMountainChain.rDecorate` | 同上（在其概率缩放**之后**，只统计真正执行的） |
| `ChunkGeneratorRTG.populate` | `resetInvocations()` + `if (RTG.decoDebug())` 打日志 |
| `RTG` | `DECO_DEBUG` ← `System.getProperties().containsKey("rtg.debugDecorations")` |

---

## 25. 地形参数元组审计：又抓出两个家族（本轮）

上一个"沙漠族"的错配是靠手工翻代码发现的。本轮把它**工具化**：
`tools/terrain-param-audit.ps1` 把每个 rtgc 群系的地形调用参数元组
（去掉 `x, y, rtgWorld, river` 四个）与 RWG 全仓的 `new TerrainXxx(...)` 比对，
列出**在 RWG 里查无此文**的元组。

### 工具自己的两个 bug（都已修，值得记）

1. `[A-Za-z_]\w*` 判断"是否字面量"时，**`180f` 里的 `f` 被当成了标识符** ⇒ 全部 74 条都被标成
   `[NON-LITERAL]`，看起来毫无用处。修法：先剥掉数字后的 `f/F/d/D` 后缀再判。
2. RWG 写 `.3f`、rtgc 写 `0.3f`，归一化后是 `.3` 与 `0.3` ⇒ 把 `BOPGrove` 那条**误报**为无匹配。
   修法：把前导 `.` 补成 `0.`。

**教训**：审计工具本身也会骗人。这两次都是"输出里 100% 都是同一类告警"或
"告警和我手工核对过的结论矛盾"才暴露出来的 —— 和之前"常量序列比对误报"是同一类问题。

### 发现 1：沙漠族的两个群系（已修）

`VanillaDesertHills` / `VanillaDesertM` 用的是
`terrainGrasslandHills(70f, 200f, 7f, 100f, 38f, 260f, base)`。
RWG 全仓**只有** steppe/thicket 的 `GrasslandHills(70f, 180f, …)`，**而且那两条都被 `/* */` 注释掉了** ——
`(70f, 200f, 7f, …)` 这个组合在 RWG 里**不存在**。

RWG 沙漠族只有四个地形：

| RWG | 地形 |
|---|---|
| `desert\Desert` | `TerrainHilly(150f, 50f, 0f)` |
| `desert\DesertMountains` | `TerrainHilly(230f, 120f, 0f)` |
| `desert\DuneValley` | `TerrainDunes()` |
| `desert\Oasis` | `TerrainHilly(230f, 120f, 20f, 60f, 63f)` |

**已改为** `terrainHilly(230f, 120f, 0f, 260f, 68f)`（= `DesertMountains` 的配方；
`red\RedDesertMountains` 用的是同一组参数）。死掉的构造参数与字段一并删除。

### 发现 2：**7 个 "Hills" 变体比它们的基准群系还平**（已修）

`VanillaForestHills` `VanillaBirchForestHills` `VanillaBirchForestHillsM` `VanillaJungleHills`
`VanillaTaigaHills` `VanillaMegaTaigaHills` `VanillaRedwoodTaigaHills`
全都用 `terrainGrasslandHills(70f, 180f, 7f, 100f, 38f, 260f, 68f)` —— 同样在 RWG 查无此文。

而它们的**基准群系**：

| rtgc 基准 | 地形 | RWG 对应 |
|---|---|---|
| `VanillaForest` `VanillaBirchForest` `VanillaBirchForestM` | `terrainHilly(230f,120f,0f)` | `forest\WoodHills` |
| `VanillaJungle` `VanillaJungleEdge` | `terrainHilly(230f,120f,50f)` | `land\JungleHills` |
| `VanillaTaiga` `VanillaMegaTaiga` `VanillaColdTaiga` | `terrainMountainRiver()` | `land\TaigaHills` |

**即"Hills"变体的起伏只有基准的约 1/8（`varHeight=7` vs `strength=120`）—— 方向是反的。**

RWG 的 Hills 族与其基准共用同一地形（`WoodHills` / `JungleHills` / `TaigaHills`）。
**已改**为各自镜像基准群系：

| 群系 | 新地形 |
|---|---|
| `VanillaForestHills` `VanillaBirchForestHills` `VanillaBirchForestHillsM` | `terrainHilly(230f, 120f, 0f, 260f, 68f)` |
| `VanillaJungleHills` | `terrainHilly(230f, 120f, 50f, 260f, 68f)` |
| `VanillaTaigaHills` `VanillaMegaTaigaHills` `VanillaRedwoodTaigaHills` | `terrainMountainRiver(x, y, rtgWorld, river)` |

### 审计终态

`tools/terrain-param-audit.ps1` 现在报告 **0** 个字面量元组在 RWG 里查无此文。
剩下 20 条是**表达式实参**（非字面量），逐条判定如下：

| 条目 | 判定 |
|---|---|
| `TerrainMarsh … WaterLevel.current().waterSurfaceTop()` | F-41 的可配置水位（1.0.18 已修对）✓ |
| `TerrainMesa … border, river` | 工具口径差异：mesa 的签名第 4 参是 `border` 而非 `river` ✓ |
| `TerrainCanyon … height, booRiver, …` | 同上（Canyon 的 rtgc 签名与 RWG 构造参数不同形）✓ |
| `TerrainPolar stPitch, stFactor, …` @VanillaDesert | §20 已记录的有意偏离 ✓ |
| `TerrainHilly width, strength, 50f, 260f, base` @VanillaExtremeHillsPlus | 该群系自己的字段化调参（RTG 时代遗留，非 RWG 元组） |
| `TerrainGrasslandHills hHeight, hWidth, …` @VanillaTaigaM | 字段名与形参一一对应，语义正确 ✓ |

---

## 26. "群系观与 F3 不一致"：装饰侧根因（本轮）

你的原话是"很多地块实际生成的群系观与 F3 显示的不一致"。这一节记录排查路径和结论，
包括**被排除**的可能性，因为排除过程本身就是有价值的证据。

### 先确认：F3 与地表**同源**，不是 bug

F3 的群系来自 `world.getBiome(pos)`；对已加载区块它返回**区块里存的群系数组**。
该数组由 `ChunkGeneratorRTG:327-336` 用 `xyinverted` 转置后逐列填入，源是
`baseBiomesList[i] = landscape.biome[i].baseBiome()`（L268 附近）。
而地表方块的替换也读同一个 `landscape.biome[]`（经 `jitteredBiomes`）。
**两者共用一份数据源，索引转置在算术上正确**，因此"F3 与地表不符"不可能来自这里。

于是只剩两种可能：**地形**或**装饰**。

### 三条读取路径确实同源（逐个查到函数）

| 读取方 | 调用链 | 落点 |
|---|---|---|
| F3 / 区块群系数组 | `world.getBiome` → `BiomeProviderRTG.getBiome(:56)` → `RtgLayoutAccess.mcBiomeAt(:78)` | `layout.biomeAt(x,z)` |
| `landscape.biome[]`（地表替换的源） | `ChunkGeneratorRTG` 逐列调 `biomeProvider.getBiome(pos)` | 同上 |
| 地形 `biomeData` | `getNewerNoise` → `RtgLayoutAccess.biomeAt(x,z)` → `RtgRealisticIndex.idFor` | 同上 |

`mcBiomeAt` 就是 `biomeAt(...).baseBiome()`（`RtgLayoutAccess:78-80`），所以三者是**同一个
`layout.biomeAt` 调用**，不存在"两条路取到不同群系"的可能。这一条排除得很干净。

地形也排除了：`getNewerNoise` 的抛物线混合在 RWG 里同样存在（`parabolicField` 就是它），
区块中心早退（单一群系）在 `biomeWidth` 尺度下几乎总是成立，因此单个区块内的地形
与它自己的群系是一致的。

**唯一剩下的分歧就是装饰**，而它确实错着 —— 见下。

### 结论：`populate` 取了"下一个区块的原点"这**一个**群系

```java
// 改前（ChunkGeneratorRTG.populate）
mpos.setPos(blockPos.getX() + 16, 0, blockPos.getZ() + 16);   // ← 本区块东北方向的下一区块原点
final IRealisticBiome biome = ...biomeAt(mpos.getX(), mpos.getZ());
...
biome.rDecorate(this.rtgWorld, this.rand, chunkPos, river, hasVillage, landscape.noise);  // 整块只用它
```

`blockPos` 是区块原点，`+16` 就落到了**正东北方向下一个区块的原点**。所以：

- 只要群系边界从本区块的东北角擦过，本区块就会**整块被隔壁群系的装饰覆盖**，
  自己群系的树/花/草**一棵都没有**；
- 反过来，本区块的装饰会跑到东北方向的邻区块去。

这就是"F3 显示 A、地上长的却是 B 的植被"。气候边界带占全世界 27.31%（§23 实测），
群系单元格并不大，所以受影响的区块**很多** —— 与你说的"很多地块"吻合。

### RWG 的做法（`ChunkGeneratorRealistic:971-1015`）

```java
for (int bx = -4; bx <= 4; bx++)
    for (int by = -4; by <= 4; by++)
        borderNoise[cmr.getBiomeDataAt(x + 24 + bx * 16, y + 24 + by * 16).biomeID] += 0.01234569f;
        // ↑ +24 的偏移让采样点落在区块**内部**而不是角上
...
for (int bn = 0; bn < 256; bn++)
    if (borderNoise[bn] > 0f) {
        if (borderNoise[bn] >= 1f) borderNoise[bn] = 1f;
        b = RealisticBiomeBase.getBiome(bn);
        if (b instanceof RealisticBiomeOcean) deferredOceanDecorations[bn] = borderNoise[bn];
        else b.rDecorate(this.worldObj, this.rand, x, y, perlin, cell, borderNoise[bn], river);
        ...
    }
```

要点四条，都已在 rtgc 侧一并照做：

1. **9×9 邻域、每命中一次 +1/81**（`0.01234569f`，81 × 0.01234569 = 1.00000189）。
2. **累加必须早于 `DecorateBiomeEvent.Pre`**（RWG:971 vs RWG:977）。rtgc 现已如此。
3. **按真实群系编号升序**逐个装饰，不是按命中顺序。
4. 权重上限 1f。

### rtgc 侧的两处必要适配（都是接口形状差异，不是偷工）

- **没有 `strength` 形参。** RWG 把权重传给装饰器，装饰器自己算
  `(int)(count * strength)`；rtgc 的 `IRealisticBiome.rDecorate(...)` 签名里没有这个参数，
  **改动它会波及全部 130 个群系类**。因此这里用**概率等价**：以权重 w 的概率整份装饰。
  期望装饰量与 RWG 相同，方差不同（RWG 每块都有少量，rtgc 是一部分块有全量）。
  这是本轮唯一的功能性近似，已在代码注释里标明。
- **海洋群系仍走 `rDecorate`（空实现）**，没有 `deferredOceanDecorations` 分流 ——
  因为 rtgc 根本没有 `rDecorateAfterIce`（见下）。

### 顺带确认的两件事

- **典型情形下行为不变。** 区块完全落在一个群系内时，9×9 邻域 81 个采样点全命中同一
  群系、权重 = 1.0，与改动前**完全一致**；只有跨边界的区块才分摊。所以这个改动不会
  把原来正确的地块改坏。
- **新增诊断字段。** `-Drtg.debugDecorations` 的 `[RTG-DECO]` 行新增
  `decoBiomes=`（本区块分摊到几个群系）与 `centreWeight=`（区块中心群系的权重）。
  只命中一个群系时应看到 `decoBiomes=1 centreWeight=1.0000`；边界区块会看到
  `decoBiomes=2..4` 且 `centreWeight<1`。这两个值就是本改动的直接证据。

### 本轮新记录、**未修**的两个欠账

| 欠账 | 依据 | 为什么不修 |
|---|---|---|
| `rDecorateAfterIce` 从未移植 | RWG `RealisticBiomeOcean:48-52`（`decorateBaseBiome && strength > 0.3f` 时调 `baseBiome.decorate`，即**海洋群系的原版装饰**）；调用点 `ChunkGeneratorRealistic:1115-1120`，在原版结冰之后。全库 grep `rDecorateAfterIce` = 0 命中 | 海洋 `rDecorate` 本就是空实现，所以**本轮改动不涉及它**；但它是一笔独立的、明确的欠账（BOP kelp/coral 的原版装饰一直没有发生） |
| 地表抖动的取样点算错 | `ChunkGeneratorRTG:297-301`：把抖动后的**世界坐标** `pX/pZ` 又用 `(pX & 15)` 折回**本区块**的列去索引 `landscape.biome[]`，而本区块数组里没有邻区块的群系 ⇒ 本意是"邻群系渗入"，实际取到本区块的另一列 | 只有 3 个海滩群系会打开 `SURFACE_BLEED_IN/OUT`，且本区块内绝大多数列同属一个群系，所以目前**近似空操作**，不产生可见错误。记录以免以后误判 |

---

## 27. "F3 显示蘑菇群系、地上却是树林" —— 根因在 provider 的 null 回退

用户的原始描述（这次是决定性的一条）：

> 单纯的实际生成的是实际生成的，F3 显示的是 F3 显示的。
> 实际生成的装饰没问题，就是正常的树林，但是**显示的是蘑菇群系**。

也就是说：**生成侧全对，只有 F3 显示的群系不对**。这直接推翻了 §26 里"F3 与地表同源"
能推出的全部结论，也把范围锁到"F3 读的那条路"上。

### 第一步：搞清 F3 到底读什么（读原版源码，不靠记忆）

原版源码在 `build/rfg/mcp_patched_minecraft-sources.jar`（RFG 解出来的），读了三处：

| 文件 | 关键行 | 结论 |
|---|---|---|
| `Chunk.java` | `getBiome(BlockPos, BiomeProvider)` L1290-1310 | 用的是 `blockBiomeArray`（`byte[256]`）；`k == 255`（未知）时**客户端直接给 PLAINS**、服务端才问 provider |
| `SPacketChunkData.java` | L151 `buf.writeBytes(chunkIn.getBiomeArray())` | **这 256 字节会发给客户端** |
| `BiomeProvider.java` / `BiomeCache.java` | — | `super.getBiome()` 走的是 GenLayer + `BiomeCache` |

⇒ **客户端 F3 = 服务端 `blockBiomeArray` 里的那个字节**，不是客户端重算的。
（Forge 在 `Chunk.getBiome` 里留了注释说明这是有意为之：客户端重算会因为
`IntCache` 非线程安全而损坏服务端的数组。）

### 第二步：那个字节省是谁写的

`ChunkGeneratorRTG:343-351`：

```java
int value = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
this.byteBiomeArray[i] = (byte) value;
...
chunk.setBiomeArray(this.byteBiomeArray);
```

而 `baseBiomesList[i] = landscape.biome[i].baseBiome()`（`:284-286`）。
转置表 `BiomeAnalyzer.xyinverted()` 核对过：`result[i*16+j] = j*16+i`，且
`Chunk.getBiome` 的本地索引是 `z*16+x` ⇒ 取 `xyinverted[z*16+x] = x*16+z`，
正是布局的 `(x*16+z)` 约定 ✓ **转置没问题**（这一步专门查过，因为它是"地表正确、
F3 错位"最可能的形态）。

### 第三步：`landscape.biome[]` 从哪来 —— 两条路，一条会拐到别的生成器

`ChunkGeneratorRTG.generateLandscape:969-978`：

```java
getNewerNoise(biomeProvider, blockX, blockZ, landscape);   // ← 填 biomeData（地形用）
for (x) for (z)
    biomes[x*16+z] = biomeProvider.getBiome(biomePos);     // ← 填 landscape.biome[]
analyzer.newRepair(biomes, this.biomeData, landscape);     // ← MC 群系 → 现实主义群系
```

- **地形** 走 `RtgLayoutAccess.biomeAt`（`getNewerNoise` 内，`:1052`）；
- **F3 / 地表 / 装饰** 走 `biomeProvider.getBiome` → `newRepair` → `RTGAPI.getRTGBiome(mcId)`。

而 `WorldTypeRTG:43-49`：

```java
if (ModCompat.Mods.biomesoplenty.isLoaded()) return new BiomeProviderBOP(world);
else                                          return new BiomeProviderRTG(RTGWorld.getInstance(world));
```

**装了 BOP ⇒ 用的是 `BiomeProviderBOP`。** 它的 `getBiome`（`:49-59`）确实是"先问布局"，
但**布局返回 null 时回落到 `super.getBiome`** —— 也就是 **BOP 自己的 GenLayer**：

```java
mainBranch = new GenLayerAddMushroomIsland(5L, mainBranch);   // BiomeProviderBOP:190
```

⇒ 这些列的 F3 / 地表 / 装饰拿到的是一个**与本列地形毫无关系**的群系。最典型的就是蘑菇岛。
而地形侧对同一个 null 的处理只是"这一列不进混合场"，于是**看起来像正常的邻列地形（森林）**。

**两条回退路径后果不对称，这就是"地上是树林、F3 是蘑菇岛"的全部成因。**

### 修法（改在唯一入口上，而不是打补丁）

`RtgLayoutAccess.biomeAt` 是所有调用方（provider 侧与地形侧）的**唯一**入口，故修在这里：

1. **有布局时保证不返回 null。** 拿不到时改用新增的
   `RtgBiomeLayout.lastResortAt(x, z)` —— 本气候核心池的第一个非 null 成员；
   核心池也空时才退到非空的海洋槽，最后才是 `Biomes.PLAINS` 的现实主义版本。
   这与 `selectIslandBiome` 里已有的 C3 适配是**同一条原则**（宁可给气候正确的群系，
   也不要回落到原版 GenLayer），只是这次把它上升成了全局不变量。
2. **异常路径同样走兜底。** 原先 `catch (Throwable)` 是 `return null`，等于把异常
   也变成"回落到别的生成器"。
3. **池的 null 成员不再外泄。** `selectBiome` / `selectCombinedBiome` 原先
   `biomes.get(i)` 可能直接返回 null；现在从选中位置起**环形查找第一个非 null 成员**。
   `List<IRealisticBiome>` 里出现 null 是完全可能的（某个群系没被分类/注册），
   而它此前会被当成"该列没有群系"。
4. **兜底可观测。** 触发计数 + 节流日志（前 5 次、之后每 10 万次一条，含坐标与累计次数）。
   如果池里真有空位，下一次日志就能看见 —— 不再需要靠猜。

### 顺带把"为什么 `selectIslandBiome` 早就有这条适配"补齐

§18 当时只说了"RWG 返回 null、rtgc 返回 null 会让岛屿列回落到原版 GenLayer"，
但没意识到**同样的漏洞在核心池/边界池/海洋槽上都会触发**，而且触发后的表现
（F3 与地形不一致）比岛屿那一处更难定位。本条把该结论上升为不变量后，
这一类问题在结构上不再可能发生。

### 仍未做

> **⚠ 历史快照（写于该轮，勿当待办）**：本节两条**都已处理** ——
> ① `rDecorateAfterIce`（海洋群系的原版装饰 + BOP 珊瑚/海带清理）已实现
> （`IRealisticBiome.rDecorateAfterIce` + `OceanDecorationSanitizer`）；
> ② 地表抖动取样点的越界读取已修（`ChunkGeneratorRTG` 改为按**世界坐标**问布局）。
> 日志里那条 `布局在 (...) 没有可用群系` 的兜底 warn 至今未出现，触发频率仍是未知 ——
> 它现在只是安全网，不再是待查项。

- `rDecorateAfterIce`（海洋群系的原版装饰）与地表抖动取样点两处欠账**未修**，
  依据与理由见 §26 末尾的表格。
- 兜底是**结构性保证**，不是"某池空了"的修复：本次没有观察到
  `[RTG] 布局在 (...) 没有可用群系` 的日志（用户最新日志里没有），所以
  **触发频率仍是未知的**。装 1.0.26 后如果这条 warn 完全没出现，
  说明 null 另有来源（例如 `getBiome` 被别处调用、或 BOP 的 `GenLayer` 参与了
  其它路径），需要按同一条链路继续查；如果出现了，日志会直接给出坐标。

---

## 28. "过渡太生硬"：四层 HUGE→SMALL 混合被短路绕过（本轮）

用户的口径是决定性的：

> 恢复 RTG 特色的噪声混合技术来混合群系，现在过渡太生硬了。
> RTG 的噪声混合不是四次混合吗，HUGE 层到 SMALL 层。

所以目标机制就是 `hugeRender[81]` → `smallRender[625]` 的四层 `mix4` 金字塔
（HUGE 1 / HUGE 2 / SMALL 1 / SMALL 2 / SMALL 3 / SMALL 4）。

### 第一步：先确认金字塔本身有没有抄错 —— 结论是没有

逐阶段与 RWG `ChunkGeneratorRealistic:342-441` 对照：

| 阶段 | RWG 行 | 源索引 | 目标 | 奇偶条件 | rtgc |
|---|---|---|---|---|---|
| 抛物线填充 | :342-354 | 21×21 网格 ±parabolicSize | HUGE (2i+2, 2j+2)，i,j∈[-1,3] | 无 | ✓ 一致 |
| HUGE 1 | :366-374 | (2i,2j) (2i+2,2j) (2i,2j+2) (2i+2,2j+2) | HUGE (2i+1, 2j+1) | 无 | ✓ 一致 |
| HUGE 2 | :377-393 | (i,j+1) (i+1,j) (i+1,j+2) (i+2,j+1) | SMALL (4i, 4j) | XOR | ✓ 一致 |
| SMALL 1 | :396-404 | (4i,4j) (4i+4,4j) (4i,4j+4) (4i+4,4j+4) | SMALL (4i+2, 4j+2) | 无 | ✓ 一致 |
| SMALL 2 | :407-417 | (2i,2j+2) (2i+2,2j) (2i+2,2j+4) (2i+4,2j+2) | SMALL (2i+2, 2j+2) | XOR | ✓ 一致 |
| SMALL 3 | :420-428 | (2i+2,2j+2) (2i+4,2j+2) (2i+2,2j+4) (2i+4,2j+4) | SMALL (2i+3, 2j+3) | 无 | ✓ 一致 |
| SMALL 4 | :431-441 | (i+3,j+4) (i+4,j+3) (i+4,j+5) (i+5,j+4) | SMALL (i+4, j+4) | XOR | ✓ 一致 |

`mix4` / `clearActiveBiomes` / `copyActiveBiomes` 也与 RWG `:646-666` 一致
（`mix4` 就是四向量算术平均，不是加权）。**金字塔是对的。**

顺带确认了写入顺序的安全性：六个阶段的目标格奇偶类互不重叠
（HUGE 1 写奇奇、HUGE 2 写偶偶、SMALL 1 写 ≡2 mod 4、SMALL 2 写"一偶一 ≡0 mod 4"、
SMALL 3 写奇奇、SMALL 4 写"一奇一偶"），所以不存在"读到自己刚写的半成品"。

### 第二步：问题在"有时不跑它" —— 中心群系短路

原代码在金字塔之后有这样一段（本轮删除）：

```java
float[] center = hugeRender[40];
int soleBiomeId = -1;
for (int id = 0; id < bound; id++) {
    if (center[id] != 0f) {
        if (soleBiomeId >= 0) { soleBiomeId = -1; break; }   // 两个非零 → 放弃短路
        soleBiomeId = id;
    }
}
if (soleBiomeId >= 0) { dominantBiome = ...; dominantWeight = center[soleBiomeId]; }
...
if (dominantBiome != null) {
    baseHeights[k] = dominantBiome.rNoise(..., dominantWeight, ...) * dominantWeight;
} else {
    for (bid...) baseHeights[k] += rNoise(...) * weight;     // 完整混合
}
```

**这个判定用错了数组。** 几何对照：

| 量 | 覆盖的 21×21 网格范围 | 换算到世界坐标 |
|---|---|---|
| `hugeRender[40]`（判定用） | 行/列 **2..18** | 采样行 s ↔ `(s-8)*8-8` ⇒ **-56 .. +72** |
| `smallRender[(i+4)*25+(j+4)]`（每列真正用的） | 由整张 9×9 HUGE 格平均而来，其源节点覆盖 **0..12 与 4..20** | 约 **-72 .. +96** |

每列真正使用的权重向量**比判定用的那个节点更宽**，所以：

> `hugeRender[40]` 只有一个非零项  ⇏  该列的 `smallRender` 只有一项

过渡带上的列就是这样：中心节点看到的是单一群系，而整张 HUGE 格已经含进第二个群系。
短路把这些**真实存在的**邻居贡献一刀切掉，于是同一片过渡带上：
被短路的区块 = 纯 `h_dom(1.0)`，相邻区块 = 混合值 ⇒ 断差落在**区块边界**上，
表现为 16 格长的直线台阶，并且**整片过渡带都没有渐变**。

### 第三步：参照 RWG —— 它从来不在高度上短路

`ChunkGeneratorRealistic:356-363` 确实也有一个 `b != null` 判定，但它的**唯一用途**是：

```java
if (b != null) {                       // :446-451
    randBiome = false;
    for (i = 0; i < 256; i++) biomes[i] = b;      // ← 只覆盖**地表**用的 biomes[]
}
```

高度求和（`:485-510`）**永远无条件执行**：

```java
for (int activeBiomeIndex = 0; activeBiomeIndex < activeBiomeCount; activeBiomeIndex++) {
    k = activeBiomeIds[activeBiomeIndex];
    if (smallRender[l][k] > 0f) {
        float weight = smallRender[l][k];
        testHeight[i * 16 + j] += noiseBiome.rNoise(..., weight, river + 1f, continent) * weight;
        if (noiseBiome instanceof RealisticBiomeMountainChain) mountainChainWeight += weight;
    }
}
```

本类把这个判定挪用到了高度上，属于实现偏离。已删除。

### 第四步：同时补回被短路吃掉的那部分性能

RWG 的遍历不是 `0..256`，而是 `activeBiomeIds` —— 本区块 21×21 采样网里**实际出现过**的
群系编号，**升序**（RWG `:337` 专门注明"保留旧的升序编号遍历顺序，以保证确定性的选择与
浮点累加顺序"）。现已恢复：

- 新增 `activeBiomeIds` / `activeBiomeFlags` 字段，采样网格建好后一次性构建；
- `mix4` / `clearActiveBiomes` / `copyActiveBiomes` / 高度求和全部只遍历这张表；
- 用 `copyActiveBiomes` 取代了原来的 `System.arraycopy(..., bound)`。

**等价性论证**：非 active 编号在整张 HUGE/SMALL 金字塔里恒为 0（数组初始值）；
每个被写入的格子都先对**当前** active 表清零，因此：
（a）非 active 项从不被写入任何非零值；（b）所有读取都限定在 active 表内。
所以结果与遍历全 256 项**逐位相同**。

**收益**：`mix4` 559 次 × 256 项 → 559 次 × activeCount（通常 1–8）；
高度求和 256 列 × 256 项 → 256 列 × activeCount。这是净提速。

### 本轮改动的可见范围（重要）

均匀区块（21×21 采样网只有一个群系）下，`smallRender` 的权重仍是精确的 1.0，
新路径算出 `h(1.0) * 1.0`，与原短路**逐位相同**。
**所以这次改动只影响过渡带，内陆地形一点没动** —— 这也反过来印证了
"生硬"只出现在过渡带，与用户的描述一致。

### `randBiome` 已实施（本轮）—— 用户解开了那个取舍

此前记的"没有实施的原因"是：开抖动必须把 `landscape.biome[]` 拆成两个数组，
而过渡带内 F3 与地表会不一致。**用户裁定**：「允许在交接地带出现 F3 与实际不统一的情况，
本来就是交界处，无可厚非」⇒ 障碍消失，已按 RWG 原样接上。

实施位置与两处**有意**差异见 `CHANGELOG.md`「接回 RWG 的地表侧群系边界抖动」。
关键点：新增 `ChunkLandscape.surfaceBiome[]`；抖动在 `getNewerNoise` 的同一个循环里算
（累积排在高度累加之前）；`BiomeAnalyzer.newRepair` 里**被替换过的列**（河面下→河流群系、
旱河→陆地回落）必须让地表跟随主群系，否则河床会被抖出来的陆地群系刷掉。

**仍未验证**：观感与副作用（我无法运行游戏）。

### 仍存的一处 RWG 机制（**已实施，见上**）

RWG 还有一处噪声驱动的**群系边界抖动**（`ChunkGeneratorRealistic:444-495` 的 `randBiome`）：

```java
bRand = 0.5f + perlin.noise2((x + i) / 15f, (y + j) / 15f);   // 15 格尺度的噪声
...
for (activeBiomeIndex...) {
    if (randBiome && bCount <= 1f) {
        bCount += smallRender[l][k];
        if (bCount > bRand) { biomes[j*16+i] = RealisticBiomeBase.getBiome(k); bCount = 2f; }
    }
    ...高度求和...
}
```

它用噪声场去扫各群系的**累积权重区间**，决定该列**地表**用哪个群系 —— 这就是
"用噪声混合群系"的另外一半，让群系边界呈噪声状而不是直线。
注意它**只会改写权重本来就混合的过渡带**（内陆单群系处第一个群系的累积权重就是 1.0，
`bRand ∈ [0,1)` 必然被它跨过，不可能改选别的群系），所以不会产生"森林里冒出蘑菇岛"。

**没有实施的原因**：在 rtgc 里 `landscape.biome[]` **同时**供 F3/区块群系数组
（`baseBiomesList`）与地表（`jitteredBiomes`/`newRepair`）使用。要开这个抖动，
必须把两者拆成两个数组（`ChunkLandscape` 里新增一个"地表现选群系"数组，
因为 `smallRender` 是生成器字段、而 landscape 会被缓存，不能在 `generateChunk` 里回读）。
这会让混合带内的 **F3 群系与地表方块不一致** —— 而"F3 与地表不一致"正是上一轮
（1.0.26）刚修掉的问题，所以这里不擅自开启，等你定。

---

## 29. 两条实测反馈的定位（本轮）

用户给了两条具体观测：

> 冰刺之地显示 MushroomIslandSh 什么的
> 生物群系边界依旧过度生硬

### 29.1 `MushroomIslandShore` 出现在冰刺之地一带

**先把"它只可能从哪来"锁死。** `MUSHROOM_ISLAND_SHORE`（id 15）在 rtgc 里只有两处：
`RealisticBiomeVanillaMushroomIslandShore.biome`，以及
`RealisticBiomeVanillaMushroomIsland.preferredBeach()`（后者只喂 `getBeachBiome()`，
而海滩三阶段修复在 D2 已删，所以不是它）。
⇒ **`landscape.biome[i]` 就是 `RealisticBiomeVanillaMushroomIslandShore`。**

而布局里能产出它的分支只有 `LITTORAL` 与 `coastIce`/`coastDunes`（它不在 core；`ISLAND`
被 `!name.contains("mushroomisland")` 排除）。逐条查：

**第一步：原版数据（从 `mcp_patched_minecraft-sources.jar` 读，不靠记忆）**

```
Biome.java      : registerBiome(15, "mushroom_island_shore",
                      new BiomeMushroomIsland(… setTemperature(0.9F).setRainfall(1.0F)));
BiomeDictionary : addTypes(Biomes.MUSHROOM_ISLAND_SHORE, MUSHROOM, BEACH, RARE);
Biome.java      : registerBiome(140, "mutated_ice_flats",
                      …setTemperature(0.0F).setRainfall(0.5F).setSnowEnabled());
BiomeDictionary : addTypes(Biomes.MUTATED_ICE_FLATS, COLD, SNOWY, HILLS, RARE);
```

⇒ `mushroom_island_shore` **真的带 BEACH 标签**（不只是名字像海滩），温度 0.9；
`mutated_ice_flats` 温度 0.0。

**第二步：rtgc 的判定被两条规则同时命中**

```java
static boolean isBeach(final String name, final Biome b) {
    return has(b, Type.BEACH) || name.contains("beach") || name.contains("shore");
}
```

`mushroom_island_shore`：字典 BEACH ✓、名字含 "shore" ✓ —— 两条都中。
而 `isBeach` 分支当时排在 `specialFor` / `islandBiome` **之前**（`:131` vs `:143/:151`）
⇒ 它永远走不到 RWG 给它定的位置。

**第三步：它还抢走了 `coastDunes`**

原实现：
```java
} else if (isBeach(name, base)) {
    layout.add(realistic, climate, Placement.LITTORAL);
    if (base.getDefaultTemperature() < 0.15f) {
        if (layout.pool(climate, Placement.LITTORAL).size() == 1) layout.setCoastIce(realistic);
    } else if (layout.pool(climate, Placement.LITTORAL).size() == 1) {
        layout.setCoastDunes(realistic);
    }
```
`mushroom_island_shore` 的 `climateFor` = **WET**（`rain 1.0 ≥ 0.9 && temp 0.9 ≥ 0.8`），
temp 0.9 ≥ 0.15 ⇒ 若它是 WET LITTORAL 的第一个成员，就 **`coastDunes = mushroom_island_shore`**。
而 `coastIce`/`coastDunes` 是**全局单字段**（`RtgBiomeLayout:155-156`），
各气候的"第一个成员"互相覆盖 ⇒ 谁最后写入谁生效。

**第四步：与 RWG 对照 —— 这是实现偏离，不是 RWG 行为**

```java
// RWG RealisticBiomeBase:132/136 —— 两个固定实例
public static RealisticBiomeBase coastIce   = new RealisticBiomeCoastIce();
public static RealisticBiomeBase coastDunes = new RealisticBiomeCoastDunes();

// RWG ChunkManagerRealistic:590-593 —— 二选一，与"谁进了 LITTORAL"无关
if (!islandBiomeSelected && !littoralBiomeSelected && continent < 24f) {
    output = output.baseBiome.temperature < 0.15f ? RealisticBiomeBase.coastIce
                                                  : RealisticBiomeBase.coastDunes;
}
```
RWG 用的是**两个专用群系类**（`RealisticBiomeCoastIce` = `baseSnowDesert` + 压实冰地表；
`RealisticBiomeCoastDunes` = `baseOceanTemperate`），
而且 RWG `Support.java:104-115` 把 `mushroomIsland` 明确放在 **WET / SMALL_ISLAND**，
**RWG 里根本没有 mushroomIslandShore 这个现实主义群系**。

**修法**

1. `specialFor(key)` 提到 `isBeach` 之前 ⇒ `mushroom_island_shore` 归入 `WET / SMALL_ISLAND`。
   只提前 special、不动 `islandBiome`（避免把"岛屿+海滩"双标签群系从 LITTORAL 挪走）。
2. `coastIce`/`coastDunes` 改为**显式指定**，对齐 rtgc 自己的两个移植：
   `COLD_BEACH`（`RealisticBiomeVanillaColdBeach` 注释写明是 RWG `RealisticBiomeCoastIce` 的移植）
   与 `BEACH`（`terrainCoastDunes` 是 RWG `CoastDunes.rNoise` 的逐行移植）。
3. 启动日志新增 `[RTG] 海岸群系：coastIce=… coastDunes=…` 与
   `[RTG]   <气候> LITTORAL 成员：…` —— 这类"群系被塞进了哪个池"的问题
   从此可以直接读日志核对，不必再靠推理。

### 29.2 边界过渡生硬 —— 补上 `randBiome` 噪声抖动

§28 已经恢复了高度的四层混合，但**群系边界的"观感"还有另一半**：
RWG 的地表群系是用噪声在混合权重轴上挑的。

RWG `ChunkGeneratorRealistic:456-495`：
```java
bRand = 0.5f + perlin.noise2((x + i) / 15f, (y + j) / 15f);   // 15 格尺度的噪声场
bRand = clamp(bRand, 0f, 0.99999f);

for (activeBiomeIndex ...) {                 // 升序
    k = activeBiomeIds[activeBiomeIndex];
    if (smallRender[l][k] > 0f) {
        if (randBiome && bCount <= 1f) {
            bCount += smallRender[l][k];
            if (bCount > bRand) { biomes[j * 16 + i] = RealisticBiomeBase.getBiome(k); bCount = 2f; }
        }
    }
}
```
`bRand` 当作累积权重轴上的阈值：**哪个群系的累积区间跨过它，这一列地表就用哪个群系**。
边界因此变成噪声状的犬牙交错，而不是一条直线。

**实现**（本轮）：
- 新增 `ChunkLandscape.surfaceBiome[256]`，在 `getNewerNoise` 的列循环里按上述规则求出
  （`rtgWorld.simplexInstance(0)` 即经典 Perlin —— `RTGWorld:86` 明确
  `simplexNoiseInstances[i] = new PerlinNoise(seed + i)`）。
- `generateChunk` 的地表替换（`jitteredBiomes`）改用 `landscape.surfaceBiome`；
  **`landscape.biome`（F3 / 区块群系数组 / 装饰）完全不变**，1.0.26 修的 F3 一致性保持。
- 必须存进 `ChunkLandscape`：`smallRender` 是生成器字段，而 landscape 可能来自缓存
  （与 `mountainChainWeight` 同理，见该字段的 javadoc）。
- **自限性**：内陆单群系处累积权重恒为 1.0，`bRand < 1` 必然被第一个群系跨过
  ⇒ 不可能改选别的群系。所以它只影响**权重本来就混合的过渡带**，
  不会产生"森林里冒出蘑菇岛"那类远处群系。

### 29.3 顺带确认：地形侧没有第二个台阶来源

`generateTerrain`（`ChunkGeneratorRTG:389-403`）逐列 `int height = (int) noise[x*16+z]`，
`noise` 就是混合后的高度场，**没有任何额外平滑、插值或量化**。
所以地形台阶只可能来自高度场本身 —— §28 的短路已删除，本轮无新增。

### 仍未验证

- 两条修正都**没有在游戏里确认过**（我无法运行游戏）。
  可核对的最小证据是启动日志的两行新输出：`海岸群系：coastIce=… coastDunes=…`
  应为 `minecraft:cold_beach` 与 `minecraft:beach`；`WET LITTORAL 成员` 里**不应**再出现
  `minecraft:mushroom_island_shore`（它现在应出现在 `SMALL_ISLAND`）。
- `randBiome` 用的是 `rtgWorld.simplexInstance(0)`；RWG 用的是它自己的 `perlin`
  （`NoiseSelector.createNoiseGenerator(seed)`）。两者都是经典 Perlin、同一角色，
  但**种子/实例偏移是否逐位相同未验证** —— 抖动是随机化机制，不追求逐位一致，
  但记录在此以免以后误判为已验证。

### 29.4 第三条观测：F3 只有 `Ocean` + 两个 `MushroomIsland`

> 而且我发现 F3 显示的生物群系只有 Ocean 和两个 mushroomIsland

**这是全篇最有信息量的一条。** `Ocean`=id **0**、`MushroomIsland`=**14**、
`MushroomIslandShore`=**15**。而 `BiomeProviderBOP:190` 里就是：

```java
mainBranch = new GenLayerAddMushroomIsland(5L, mainBranch);   // 往海洋里撒蘑菇岛
```

`GenLayerShore` 再给它们补岸。**"海洋 + 蘑菇岛 + 蘑菇岛岸"正是 BOP GenLayer 的输出特征**，
而 rtgc 只在**布局未命中**时才回落到它。所以：

> **F3 读到的群系不是 rtgc 布局给的，而是 BOP 自己的 GenLayer 给的。**

这与 29.1（同一张 GenLayer 的另一处副作用）以及"F3 与实际生成不一致"是**同一个根因**。
`BiomeProviderBOP` 构造函数第 27-28 行的注释其实早就写了这一点：

> note on the client side, chunkProviderSettings is an empty string
> … it might have some consequences when the biomes/genlayers are different
> between client and server

rtgc 只覆写了 `getBiome`，`getBiomes` / `areBiomesViable` / `findBiomePosition` 仍走父类
（见该类的 javadoc），所以**回落一旦发生，F3 与地形就分家了**。

**同时查出一处真缺陷：装了 REID 时只写了 int 数组。**

```java
this.useIntBiomeArray = Loader.isModLoaded("jeid") || Loader.isModLoaded("neid") || Loader.isModLoaded("reid");
...
if (this.useIntBiomeArray) {
    ((INewChunk) chunk).setIntBiomeArray(this.intBiomeArray);   // ← 装了 REID 走这里
} else {
    chunk.setBiomeArray(this.byteBiomeArray);                   // ← 于是原版字节数组从不被写
}
```

原版 `Chunk` 的群系存储是 `byte[256] blockBiomeArray`，初值 `(byte)-1 = 255`
（`Chunk.java:111`），255 是"未知"哨兵；而**客户端是从网络包里读这个字节数组的**
（`Chunk.java:1263 buf.readBytes(this.blockBiomeArray)`；发送端 `SPacketChunkData:151
buf.writeBytes(chunkIn.getBiomeArray())`）。
所以"F3 读到哪个数组"取决于 REID 有没有把 int 数组同步过去 —— 这是个不该存在的赌博。
**已改为无条件两个数组都写。**

### 29.5 本轮新增的 5 条探针（把"未知"变成"可读"）

因为无法在本地运行游戏，判定点全部做成日志：

| 关键字 | 位置 | 判读 |
|---|---|---|
| `[RTG] 建立布局：side=… seed=… rtgBiomes=…` | `RtgLayoutAccess.forSeed` | 应有 CLIENT 与 SERVER 两条且 seed 相同、rtgBiomes>0 |
| `[RTG] ⚠ 布局尚未建立就收到群系查询` | `RtgLayoutAccess.biomeAt`（`layout == null`） | 出现即 F3 走了回落 |
| `[RTG] ⚠ 布局未命中，F3/群系查询回落到…GenLayer：… → <群系名>` | `BiomeProviderBOP/RTG.getBiome` | **出现即根因确认**；群系名若是 `minecraft:ocean`/`mushroom_island` 则与本条观测完全吻合 |
| `[RTG-BIOME] chunk(…) 中心列：期望=… int数组=… 字节=… 读回=…` | `generateChunk` 末尾回读 | 「期望」vs「读回」不一致 ⇒ 数组路径；一致但 F3 仍不对 ⇒ 客户端读数 |
| `[RTG] 布局在 (…) 没有可用群系` | `RtgLayoutAccess.noteLastResort` | 池为空或池内有 null 成员 |

**边界**：29.1 与 29.2 是按 RWG 源码对齐的确定性修正；29.3/29.4 的数组双写是确定性修正；
上面 5 条探针是**诊断**，不构成"已修好"的声明。哪条出现就说明卡在哪一段。





