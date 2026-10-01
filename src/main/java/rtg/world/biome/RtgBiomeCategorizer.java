package rtg.world.biome;

import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.RtgBiomeLayout.Climate;
import rtg.world.biome.RtgBiomeLayout.Placement;


/**
 * 把 rtgc 的现实主义群系分类进 RWG 布局的「气候 × 位置」格子里。
 *
 * <h2>能抄的部分已经抄了：{@link #RWG_PLACEMENTS}</h2>
 * RWG 的 {@code Support*.java} 对它的群系**逐个手写**了 {@code BiomeCategory} 与
 * {@code BiomePlacement}。凡 rtgc 里存在同名群系（BOP 与 MC 的那些），那份标注就是
 * **可抄的权威数据** —— 1.0.33 同版本追加起由 {@link #RWG_PLACEMENTS} 抄录并**优先于一切启发式**。
 * 剩下只有 rtgc 独有的群系（约 110 个）才走下面的规则。
 *
 * <h2>气候判定的依据</h2>
 * RWG 的气候带是**噪声场**（与群系自身的温湿度无关），但要把群系放进某个气候带，
 * 唯一有意义的判据就是该群系**自身的气候属性**。实测各带面积比为
 * SNOW 13.97% / COLD 29.75% / HOT 28.48% / WET 27.81%（见 {@code calibrateClimateBands}），
 * 因此雪带必须**只收真正的严寒**，否则地球上会出现大片雪原。
 *
 * <table>
 *   <tr><th>带</th><th>规则</th><th>典型群系</th></tr>
 *   <tr><td>SNOW</td><td>{@code temp ≤ 0.15} 或 SNOWY 标签</td><td>冰原、冰刺平原、雪山</td></tr>
 *   <tr><td>WET</td><td>JUNGLE / SWAMP / <b>WET</b> 标签，或名字含 swamp/jungle/rainforest</td><td>丛林、沼泽、雨林</td></tr>
 *   <tr><td>HOT</td><td>HOT / SAVANNA 标签，或 {@code temp ≥ 0.9}，或 {@code temp ≥ 0.7 && rain ≤ 0.35}，
 *       或名字含 desert/mesa/savanna/outback/dune/oasis/xeric/steppe/volcanic/wasteland</td><td>沙漠、台地、热带草原</td></tr>
 *   <tr><td>COLD</td><td>其余（温带森林 / 平原 / 针叶林）</td><td>森林、平原、针叶林</td></tr>
 * </table>
 *
 * <p>⚠ 标签（WET / HOT / SAVANNA / SWAMP / JUNGLE / SNOWY）是 **Forge 自己按阈值打的**
 * （{@code BiomeDictionary.makeBestGuess}，见 {@link #climateFor} 的 javadoc），
 * 不是我们发明的判据。自造的温雨阈值只保留 {@code temp ≥ 0.7 && rain ≤ 0.35} 一条。
 *
 * <h2>位置的判定（顺序即优先级）</h2>
 * <ol>
 *   <li><b>海洋</b>（OCEAN 标签或名字含 {@code ocean}）→ 填进 {@code oceanDeep} / {@code oceanShallow}
 *       槽位，**不进**陆地核心池。其中又分三种：
 *       <ol>
 *         <li><b>patch 群系</b>（BOP 的 kelpForest / coralReef，{@link #oceanPatchFor}）→
 *             **不占槽位**，只接到 RWG 的两个 patch 钩子（{@code SupportBOP.java:39-47}）；</li>
 *         <li><b>rtgc 自己的海洋</b>（{@link RtgOceanBiome}，即 RWG {@code baseOcean*} 的等价物）→
 *             槽位**优先级最高**，在循环结束后覆盖（否则会被原版 {@code minecraft:ocean} 顶掉）；</li>
 *         <li>其余（原版/BOP 的海洋）→ 直接写入槽位，可能被上面第 2 类覆盖。</li>
 *       </ol></li>
 *   <li><b>RWG 显式标注</b>（{@link #RWG_PLACEMENTS}）→ 只进该 placement 池，
 *       **不进 core**（忠实于 {@code Support.addBiome} 在 {@code Support.java:218-221} 的 {@code return}）；
 *       其中就包含 RWG 的 {@code LITTORAL} 成员（沼泽/红树）与三个边界池的成员；</li>
 *   <li><b>河流</b>（RIVER 标签或名字含 {@code river}）→ **不进布局**：rtgc 的河流由
 *       {@code IRealisticBiome.getRiverBiome()} 机制处理，RWG 的布局也没有河流池；</li>
 *   <li><b>岛屿</b>（名字含 {@code island}，{@code mushroomIsland} 除外）→ {@code ISLAND} 池
 *       （C3 适配：RWG 这个池实际是空的，见 {@link #islandBiome}）；</li>
 *   <li><b>其余</b>（含**海滩**群系）→ {@code CORE}。
 *       海滩不再进 {@code LITTORAL} —— RWG 没有任何海滩群系进池，它的海岸只有
 *       {@code continent < 24} 处的 {@code coastIce} / {@code coastDunes}
 *       （已按 {@code COLD_BEACH} / {@code BEACH} 接好）。</li>
 * </ol>
 *
 * <h2>各池的填充状态（截至本轮）</h2>
 * <ul>
 *   <li>{@code BORDER} / {@code COLD_BORDER} / {@code HOT_BORDER}：**照抄** RWG 的显式标注
 *       （chaparral / meadow / rainforest / tropicalRainforest / borealForest 等）；
 *       没标到的气候与方向由 {@code RtgBiomeLayout.getLandBiomeAt} 落到核心池
 *       —— 这与 RWG 的 fall-through 一致。旧版那个
 *       {@code RtgBiomeLayout.mirrorCoreIntoBorders()}（"边界池 = 整个核心池"）已删除；</li>
 *   <li>{@code VERY_COLD_BORDER} / {@code VERY_HOT_BORDER}：由
 *       {@code rebuildExtremeBorderMountains(RealisticBiomeMountainChain::forBiome)}
 *       从上面两个方向池镜像成**山地链**（B4）。因为源池现在是 RWG 的那几条，
 *       山地链个数从 104 回到 RWG 的量级（rtgc 可对应的 4 个）；</li>
 *   <li>{@code LITTORAL}：RWG 的 {@code bayou / deadSwamp / lushSwamp / mangrove}（全 WET）。
 *       因此**只有湿带**有滨海覆盖，与 RWG 一致；</li>
 *   <li>{@code SMALL} / {@code SMALL_ISLAND}：RWG 的 {@code flowerField / quagmire / oasis /
 *       mutatedIceFlats / mutatedPlains / ominousWoods / mushroomIsland}；</li>
 *   <li>{@code ISLAND}：rtgc 自带名字的岛屿群系（C3 适配）；</li>
 *   <li>{@code LARGE_ISLAND}：**仍为空** —— rtgc 没有 RWG 的 {@code fungiForest}
 *       （BOP 1.12 里它是下界群系，rtgc 只处理主世界）/ {@code hotPlainsCanyonIsland} 对应物。
 *       {@code selectIslandBiome} 会逐级退化到核心池，故不会出现 null。</li>
 *   <li><b>海洋槽位（4 深 + 4 浅）</b>：本轮收尾后**全部是实位**，没有借用 ——
 *       SNOW 浅海 = 原版 {@code frozen_ocean}，COLD 深海 = 原版 {@code deep_ocean}
 *       （海底神殿只认它），其余 6 个 = rtgc 自己的 {@code RtgOceanBiome}；
 *       kelp/coral 两个 patch 钩子 = BOP 的海带森林/珊瑚礁。</li>
 * </ul>
 *
 * @since 1.0.10
 */
public final class RtgBiomeCategorizer {

    private RtgBiomeCategorizer() {}

    /** 分类统计，供日志核对。 */
    public static final class Report {

        public int oceanDeep;
        public int oceanShallow;
        /**
         * RWG {@code SupportBOP.java:39-47} 的两个"只做 patch"的海洋群系
         * （BOP 的 kelpForest / coralReef）：它们**不占**海洋槽位，而是被接到
         * {@code Support.oceanShallowKelp} / {@code oceanShallowCoral} 两个钩子上。
         */
        public IRealisticBiome oceanKelp;
        public IRealisticBiome oceanCoral;
        /** 归到上面两个钩子上的群系数（0 或 2）。 */
        public int oceanPatch;
        /** 按 RWG `Support*.java` 的**显式** placement 归位的群系数（不再进 core）。 */
        public int rwgPlaced;
        public int river;
        public int land;
        public int untyped;
        /** C3：进了 {@code ISLAND} 池的岛屿群系数量。 */
        public int island;
        /** C2 / C3：进了 {@code SMALL} / {@code SMALL_ISLAND} 池的特殊群系数量。 */
        public int special;
        /** 每个气候带收到多少个陆地群系（下标 = climate ordinal）。 */
        public final int[] landByClimate = new int[4];
    }

    /**
     * 遍历 {@link RTGAPI#RTG_BIOMES} 并填进布局。幂等性**不保证**：调用方应只调用一次。
     *
     * @return 统计，便于记录日志
     */
    public static Report apply(final RtgBiomeLayout layout) {

        final Report report = new Report();
        MATCHED_PLACEMENTS.clear();

        // rtgc 自己的海洋（{@link RtgOceanBiome} 那 6 个）：先记下，循环结束后统一覆盖槽位。
        // 理由见下面海洋分支的注释（注册顺序会让原版 `minecraft:ocean` 赢过 `shallow_cold_ocean`）。
        final IRealisticBiome[] authShallow = new IRealisticBiome[4];
        final IRealisticBiome[] authDeep = new IRealisticBiome[4];

        for (final Map.Entry<Biome, IRealisticBiome> entry : RTGAPI.RTG_BIOMES) {
            if (entry == null) {
                continue;
            }
            final IRealisticBiome realistic = entry.getValue();
            if (realistic == null) {
                continue;
            }
            final Biome base = realistic.baseBiome();
            if (base == null) {
                report.untyped++;
                continue;
            }

            final Climate climate = climateFor(base);
            final String name = nameOf(base);
            // ⚠ 必须**去掉下划线**再做名字匹配：注册名是 `flower_field` / `ominous_woods` /
            // `mushroom_island`，而 SPECIALS 里的片段写作 `flowerfield` 等不带下划线的形式。
            // 1.0.24 之前少了这一步，那三条**永远匹配不上** ——
            // 实测启动日志：SMALL 只有 HOT(oasis)/WET(quagmire) 命中，
            // COLD 的 flower_field 与 SMALL_ISLAND 的两条（ominous_woods / mushroom_island）全是 0。
            final String key = name.replace("_", "");

            if (isOcean(name, base)) {
                final OceanPatch patch = oceanPatchFor(key);
                if (patch != null) {
                    // ---- RWG `SupportBOP.java:39-47`：海带/珊瑚**不占槽位**，只做 patch ----
                    //
                    // 上游原文：
                    //     if (BOPCBiomes.kelpForest != null)
                    //         Support.oceanShallowKelp  = new RealisticBiomeBOPOcean(BOPCBiomes.kelpForest, …);
                    //     if (BOPCBiomes.coralReef != null)
                    //         Support.oceanShallowCoral = new RealisticBiomeBOPOcean(BOPCBiomes.coralReef, …);
                    // 注意它**没有**覆盖 `oceanShallowCold` —— 海带森林不是"冷带的浅海"，
                    // 它只是冷带（climate 1/2）浅海里那两块"海带斑块"（见 RtgBiomeLayout.getOceanBiome）。
                    //
                    // ⚠ 这正是 rtgc 此前最大的海洋偏差：kelpForest / coralReef 靠字典的 OCEAN 标签
                    // 被当成普通海洋，谁在 RTG_BIOMES 里最后写入谁就赢 ⇒ 实测 COLD/HOT/WET
                    // 三个浅海槽位**全都是** `biomesoplenty:kelp_forest`（见 CHANGELOG 的日志）。
                    if (patch == OceanPatch.KELP) {
                        report.oceanKelp = realistic;
                    } else {
                        report.oceanCoral = realistic;
                    }
                    report.oceanPatch++;
                } else if (base instanceof RtgOceanBiome) {
                    // ---- rtgc 自己的海洋（= RWG `baseOcean*`）：槽位优先级最高 ----
                    //
                    // 为什么要分开：RWG 的浅海冷/热/湿槽位用的是**它自己注册的**海洋群系
                    // （`Support.java:161-170` 的 baseOceanCold/Hot/Wet），而 rtgc 的槽位是
                    // 遍历 RTG_BIOMES 逐个写入的 —— 注册顺序上 rtgc 的海洋在 preInit 就加进来了，
                    // 原版的 `minecraft:ocean` 反而更晚写入、会把 `shallow_cold_ocean` 顶掉。
                    // 故这里先记下，循环结束后统一覆盖（见下方 authShallow/authDeep）。
                    (name.contains("deep") ? authDeep : authShallow)[climate.ordinal()] = realistic;
                } else {
                    layout.setOceanBiome(climate, name.contains("deep"), realistic);
                }
            } else if (rwgPlacementFor(key) != null) {
                // ---- RWG 显式 placement（**照抄**，不是推断）----
                //
                // 顺序很关键：RWG 的归属来自 `Support*.java` 里的**显式调用**，是权威数据；
                // 而 rtgc 只能用名字/字典推断。推断必须有优先级，否则会被更宽的名字规则吃掉。
                //
                // 实测踩到的坑：`mushroom_island_shore` 的
                //   ① 名字含 "shore"；
                //   ② BiomeDictionary 里**确实**被标了 `MUSHROOM, BEACH, RARE`
                //      （Forge `BiomeDictionary`：`addTypes(Biomes.MUSHROOM_ISLAND_SHORE, MUSHROOM, BEACH, RARE)`）
                // 两条都命中 `isBeach` ⇒ 它被当成**海滩**塞进 LITTORAL 池，
                // 而且因为 temp=0.9 ≥ 0.15，还会去抢全局的 `coastDunes`。
                // 于是**每个暖海岸都可能变成 Mushroom Island Shore** ——
                // 用户实测就是"冰刺之地一带显示 MushroomIslandShore"。
                //
                // RWG 的归属（`Support.java:104-115`）是 **WET / SMALL_ISLAND**，
                // 而且 RWG **根本没有** mushroomIslandShore 这个现实主义群系。
                //
                // ⚠ 与 RWG 一致：`placement != CORE` 的群系**只进该池，不进 core**
                //（`Support.addBiome` 在 `Support.java:218-221` 直接 `return`）。
                final RwgPlace place = rwgPlacementFor(key);
                layout.add(realistic, place.climate, place.placement);
                report.rwgPlaced++;
                report.landByClimate[place.climate.ordinal()]++;
                MATCHED_PLACEMENTS.add(place.name);
            } else if (isRiver(name, base)) {
                report.river++;      // 不进布局
            } else if (islandBiome(key)) {
                // C3：RWG 的 `ISLAND` 池（`placement != CORE` ⇒ **不进 core**）。
                // RWG 那边这个池实际是空的，岛屿靠 smallIsland/largeIsland 两级；
                // rtgc 没有 RWG 的两级成员（见 RWG_PLACEMENTS 注释），所以把 rtgc **自带名字**
                // 的岛屿群系放进这一级，再由 RtgBiomeLayout.selectIslandBiome 做核心池兜底。
                layout.add(realistic, climate, Placement.ISLAND);
                report.island++;
                report.landByClimate[climate.ordinal()]++;
            } else {
                // ---- 其余一律进 core ----
                //
                // ⚠ 本轮改动：**海滩群系也走这里**（不再进 LITTORAL）。
                //
                // 依据：RWG 的 `Support*.java` 里**没有任何**海滩群系被放进任何池；
                // 它的海岸只有 `continent < 24` 处的 `coastIce` / `coastDunes` 两个专用群系
                //（`ChunkManagerRealistic:590-593`，rtgc 已按 `COLD_BEACH` / `BEACH` 接好）。
                // 而 RWG 的 LITTORAL 池里装的是**沼泽/红树**（bayou / deadSwamp / lushSwamp /
                // mangrove / sludgepit / tropics，全部 WET），见上方 `RWG_PLACEMENTS`。
                //
                // 旧写法把"字典标了 BEACH 或名字含 beach/shore"的群系全塞进 LITTORAL，
                // 于是**每个气候**的 LITTORAL 池都非空 ⇒ `getBiomeDataAt` 的滨海覆盖
                //（`continent < 432`）在**全世界 13% 的面积**上生效，把那一圈填成海滩群系；
                // 而 RWG 只有 WET 有滨海池（雪/冷/热三带的 432 带就是普通陆地）。
                //
                // ⚠ 这是**适配**而非照抄：RWG 根本没有"以 MC 海滩群系为原型的现实主义群系"，
                // 所以它没有对应条目。rtgc 有 5 个（VanillaBeach / VanillaStoneBeach /
                // BOPWhiteBeach / BOPGravelBeach / BOPOriginBeach），其中前两个的同类
                // （`BEACH` / `COLD_BEACH`）已经被接成 coastDunes / coastIce 专职海岸线。
                // 这里选"进 core"而不是"不放进任何池"：RWG 的 `Support.addBiome(b, cat)`
                // 默认分支就是 core，且这样 5 个群系不会变成永远不出现的死群系。
                // 可见后果：它们会像普通陆地群系一样出现在内陆（BOP 白沙滩那块会是白沙地）。
                layout.add(realistic, climate, Placement.CORE);
                report.land++;
                report.landByClimate[climate.ordinal()]++;
            }
        }

        // ---- rtgc 自己的海洋覆盖槽位（RWG `baseOcean*` 的等价物，优先级最高）----
        //
        // 放在这里而不是循环里：同一条气候槽位可能被**多个**海洋群系写入
        // （rtgc 的 `shallow_*_ocean`、原版的 `minecraft:ocean`、BOP 的 kelp_forest …），
        // 谁赢取决于 `RTGAPI.RTG_BIOMES` 的插入顺序 —— 而 rtgc 自己的海洋在 preInit 就注册了，
        // 顺序上**最早**，会被后来的顶掉。RWG 的槽位表用的就是它自己的 `baseOcean*`
        // （`Support.java:161-170`），故这里让 rtgc 自己的海洋最终覆盖。
        for (int i = 0; i < 4; i++) {
            if (authShallow[i] != null) {
                layout.setOceanBiome(Climate.values()[i], false, authShallow[i]);
            }
            if (authDeep[i] != null) {
                layout.setOceanBiome(Climate.values()[i], true, authDeep[i]);
            }
        }

        // 计数 = **最终占住槽位的**海洋群系数（每带每深度各一个，满格 = 4/4），
        // 不是"参与竞争的个数" —— 后者会把被顶掉的原版海洋也算进去，反而看不出缺口。
        for (final Climate c : Climate.values()) {
            if (layout.oceanSlot(c, true) != null) {
                report.oceanDeep++;
            }
            if (layout.oceanSlot(c, false) != null) {
                report.oceanShallow++;
            }
        }

        // ---- coastIce / coastDunes：RWG 是**两个专用群系类**，不是"第一个海滩群系" ----
        //
        // RWG `RealisticBiomeBase:132/136`：
        //     public static RealisticBiomeBase coastIce   = new RealisticBiomeCoastIce();
        //     public static RealisticBiomeBase coastDunes = new RealisticBiomeCoastDunes();
        // 即固定的两个实例，由 `ChunkManagerRealistic:590-593` 在 `continent < 24` 时按
        // `output.baseBiome.temperature < 0.15f` 二选一。
        //
        // 原实现改成了"该气候 LITTORAL 池里第一个温度合适的成员"，而 `coastIce`/`coastDunes`
        // 是**全局单字段**（不是按气候分的），于是各气候的"第一个成员"会互相覆盖，
        // 最终胜出者与 RWG 毫无关系 —— 上述 MushroomIslandShore 就是从这里漏出去的。
        //
        // 现在改为显式对齐 rtgc 自己的两个移植：
        //   · RWG `RealisticBiomeCoastIce`   → `RealisticBiomeVanillaColdBeach`
        //     （见该类的注释"照抄 RWG coast\RealisticBiomeCoastIce.java:34-56"）
        //   · RWG `RealisticBiomeCoastDunes` → `RealisticBiomeVanillaBeach`
        //     （其 `terrainCoastDunes` 即 RWG CoastDunes.rNoise 的逐行移植）
        layout.setCoastIce(RTGAPI.getRTGBiome(net.minecraft.init.Biomes.COLD_BEACH));
        layout.setCoastDunes(RTGAPI.getRTGBiome(net.minecraft.init.Biomes.BEACH));
        if (layout.coastIce() == null || layout.coastDunes() == null) {
            Logger.warn("[RTG] ⚠ 海岸群系未注册（coastIce={} coastDunes={}）—— continent < 24 的海岸列不会被覆盖。",
                    layout.coastIce(), layout.coastDunes());
        }

        fillMissingOceanSlots(layout);

        // ---- RWG 的两个海洋 patch 钩子 ----
        //
        // 上游有**两处**赋值，顺序很关键：
        //
        //   `Support.java:161-168`（先，rtgc 侧是 COLD 的浅海槽位）
        //        oceanShallowCold      = new RealisticBiomeOcean(RWGBiomes.baseOceanCold, true, false, …);
        //        oceanShallowKelp      = oceanShallowCold;
        //        oceanShallowTemperate = oceanShallowCold;
        //        oceanShallowCoral     = oceanShallowTemperate;
        //
        //   `SupportBOP.java:39-47`（后，**装 BOP 时覆盖上面两条**）
        //        Support.oceanShallowKelp  = new RealisticBiomeBOPOcean(BOPCBiomes.kelpForest, …);
        //        Support.oceanShallowCoral = new RealisticBiomeBOPOcean(BOPCBiomes.coralReef, …);
        //
        // 它们只在 `RtgBiomeLayout.getOceanBiome` 的两条 patch 分支里被用到：
        //   冷气候(climate 1/2) 且 continent < -90 且 patch > 0     → kelp
        //   热气候(climate 3/4) 且 -150 < continent < -20 且 patch > .07 → coral
        //
        // ⚠ 上一轮的注释在这里写错过一次（原文写"RWG 1.7.10 没有独立的海带/珊瑚群系，
        // 这两个字段都是 oceanShallowCold 的别名"，于是把两个钩子都指向了 COLD 的浅海槽位，
        // 并在备选里写"把 BOP 的两个海洋群系接上去是**偏离** RWG"）。**那两句都错**：
        // `SupportBOP.java` 的 override 就在同一次 `Support.init()` 里、紧接着执行
        // （`Support.java:177-179`），装 BOP 时钩子拿到的**正是** BOP 的海带森林与珊瑚礁。
        // 所以现在：BOP 在场就用 BOP 的那两个群系，不在场才退回 COLD 浅海（= RWG 的默认值）。
        //
        // 与之配套：kelpForest / coralReef 在**槽位**上被排除（见上方 `oceanPatchFor`），
        // 它们只从这两条 patch 分支出场。于是 COLD/HOT/WET 三个浅海槽位才能拿回真正的海洋群系。
        layout.setOceanShallowKelp(report.oceanKelp != null
                ? report.oceanKelp : oceanSlot(layout, Climate.COLD, false));
        layout.setOceanShallowCoral(report.oceanCoral != null
                ? report.oceanCoral : oceanSlot(layout, Climate.COLD, false));

        // 极端边界池的填充**不在这里**：它由 RtgLayoutAccess.forSeed 在 apply() 返回后调用
        //     layout.rebuildExtremeBorderMountains(RealisticBiomeMountainChain::forBiome);
        // 顺序要求：必须在 coldBorder / hotBorder 已填好之后（即本方法返回之后）。
        // 见 logExtremeBorderReport() 的说明：边界池为空时山地链不会显形。

        return report;
    }

    /**
     * 把分类结果记进日志。
     * <p>
     * 这是**切换后唯一的核对手段**：各池大小无法在游戏外得知，只能靠运行期日志。
     * 核心池为空会被记为 {@code ERROR} —— 那意味着该气候带会拿到 null 群系。
     */
    public static void logReport(final RtgBiomeLayout layout, final Report report) {

        // 池大小/成员属于"要看的时候才看"的明细，只在 -Drtg.debugLayout 时打印；
        // 下面的 ERROR/WARN 是安全网，始终生效。
        if (rtg.RTG.layoutDebug()) {
            Logger.info("[RTG] RWG 群系布局已建立。各「气候 × 位置」池大小：");
            for (final Climate c : Climate.values()) {
                final StringBuilder sb = new StringBuilder();
                for (final Placement p : Placement.values()) {
                    sb.append(p.name()).append('=').append(layout.poolSize(c, p)).append(' ');
                }
                Logger.info("[RTG]   {}: {}", c, sb.toString());
            }
            Logger.info("[RTG] 具名计数：深海槽位={}/4 浅海槽位={}/4 海洋patch={} RWG显式归位={} "
                    + "河流(未进布局)={} 岛屿={} 陆地={} 无法分类={}",
                    report.oceanDeep, report.oceanShallow, report.oceanPatch, report.rwgPlaced, report.river,
                    report.island, report.land, report.untyped);

            // ---- 边界/滨海池的成员：过渡带与海岸长什么样，全看这几行 ----
            for (final Climate c : Climate.values()) {
                Logger.info("[RTG]   {} COLD_BORDER={} HOT_BORDER={} BORDER={}",
                        c, layout.poolMembers(c, Placement.COLD_BORDER),
                        layout.poolMembers(c, Placement.HOT_BORDER),
                        layout.poolMembers(c, Placement.BORDER));
                Logger.info("[RTG]   {} LITTORAL 成员：{}", c, layout.poolMembers(c, Placement.LITTORAL));
            }
            // ---- CORE 成员名单：D3 去偏斜**必须**看这个 ----
            // 只报计数不够：偏斜的根因是"哪些群系被规则误判到了 COLD"，
            // 而 COLD 现在是"其余一切"的兜底。没有名单就只能靠猜（这正是上一轮的教训）。
            for (final Climate c : Climate.values()) {
                Logger.info("[RTG]   {} CORE 成员（{}）：{}",
                        c, layout.poolSize(c, Placement.CORE), layout.poolMembers(c, Placement.CORE));
            }
            Logger.info("[RTG] 海岸群系：coastIce={} coastDunes={}",
                    nameOrDash(layout.coastIce()), nameOrDash(layout.coastDunes()));
            // ---- 海洋槽位（D5）----
            // RWG 给四个气候各写了深/浅海（`Support.java:156-174`），rtgc 用自己的 6 个海洋群系
            // 加原版的 frozen_ocean / deep_ocean 填满 8 个槽位。**注册顺序与优先级**决定了谁占位
            // （见 apply() 里的 authShallow/authDeep），所以必须能看见"哪个气候的哪个槽位是
            // 借来的/被顶掉的" —— 否则"全世界的浅海都是海带森林"这种事故查不出来。
            for (final Climate c : Climate.values()) {
                Logger.info("[RTG]   {} 海洋：浅海={} 深海={}", c,
                        nameOrDash(layout.oceanSlot(c, false)), nameOrDash(layout.oceanSlot(c, true)));
            }
            Logger.info("[RTG] 海洋 patch 钩子：kelp={} coral={}"
                    + "（RWG 装 BOP 时为 BOP 的海带森林/珊瑚礁，否则退回 COLD 浅海）",
                    nameOrDash(layout.oceanShallowKelp()), nameOrDash(layout.oceanShallowCoral()));
            // RWG 的 placement 表覆盖率：哪些条目在 rtgc 找不到对应群系（**缺口要看得见**）
            logPlacementCoverage();
            // 逐个群系的归类依据（含 temp/rain/标签）—— D3 去偏斜的定位依据
            logClassification();
            // 「陆地群系按气候」的详细核对改由 logClimateSkew 输出（见其 javadoc）
            logClimateSkew(report);
        }

        for (final Climate c : Climate.values()) {
            if (layout.poolSize(c, Placement.CORE) == 0) {
                Logger.error("[RTG] ⚠ {} 的**核心池为空** —— 该气候带的陆地会拿到 null 并回退到原版布局。"
                        + "请检查 RtgBiomeCategorizer 的规则。", c);
            }
            final boolean shallow = layout.oceanSlot(c, false) != null;
            final boolean deep = layout.oceanSlot(c, true) != null;
            if (!shallow || !deep) {
                Logger.warn("[RTG] ⚠ {} 的海洋槽位有空缺（浅海={} 深海={}）—— 已尝试从其它气候借用。",
                        c, shallow, deep);
            }
        }
        if (layout.oceanSlot(Climate.HOT, false) == null && layout.oceanSlot(Climate.HOT, true) == null) {
            Logger.error("[RTG] ⚠ 连 HOT 的海洋槽位都是空的 —— 全世界 52% 的海洋列会回退到原版布局。");
        }
    }

    /**
     * 把 {@code RWG_PLACEMENTS} 的**覆盖率**打出来：RWG 标了、但 rtgc 里找不到对应群系的条目。
     *
     * <p>为什么必须看得见：这些条目**决定了池子有没有内容**。例如
     * {@code jadeCliffs → COLD / HOT_BORDER} 缺失，就意味着 `veryHotBorder[COLD]`
     * （COLD 朝更热侧的极端边界 = 山地链）会是空的 —— 那一带的极端边界列会照
     * RWG 的 fall-through 落到核心池，**不会有山地链**。这是能力缺口，不是 bug，
     * 但必须能一眼看出"哪个方向没有链"，否则又会回到"看不出来只能猜"。
     */
    private static void logPlacementCoverage() {
        final StringBuilder missing = new StringBuilder();
        int hit = 0;
        for (final RwgPlace p : RWG_PLACEMENTS) {
            if (MATCHED_PLACEMENTS.contains(p.name)) {
                hit++;
            } else {
                missing.append(p.name).append('(').append(p.climate).append('/')
                       .append(p.placement).append(") ");
            }
        }
        Logger.info("[RTG] RWG placement 覆盖：{}/{} 条在 rtgc 找到对应群系。", hit, RWG_PLACEMENTS.length);
        if (missing.length() > 0) {
            Logger.info("[RTG]   未匹配（rtgc 无此群系 ⇒ 该池为空，按 RWG 一样落到核心池）：{}", missing.toString());
        }
    }

    /**
     * 把一个现实主义群系的注册名打出来，空时给 "-"。诊断日志用。
     */
    private static String nameOrDash(final IRealisticBiome biome) {
        if (biome == null) {
            return "-";
        }
        final net.minecraft.util.ResourceLocation rl = biome.baseBiomeResLoc();
        return rl == null ? "?" : rl.toString();
    }

    /**
     * D3 的**离线目标值**：由 {@code gradlew calibrateClimateBands} 实测的气候带面积占比。
     *
     * <p>下标 = {@link Climate} 的 ordinal（SNOW, COLD, HOT, WET）。
     * 这些占比**无法在运行期廉价算出**（它们由噪声分布的 Voronoi 划分决定），
     * 所以只能把标定结果硬编码进来，用于把"池大小"与"带面积"放在一起显示 ——
     * 偏斜因此一眼可见，不需要另外跑一遍标定工具。
     *
     * <p>实测条件：4 气候模式、{@code seed=1234567891011121}、10 万+ 采样点。
     *
     * <h3>这个目标值**不是**我们发明的（本轮补的证据）</h3>
     *
     * 本轮把 RWG 自己的 {@code Support*.java} 统计了一遍（只数真实 {@code addBiome(...)} 调用，
     * 排除 {@code Support.listFor} 方法体）：RWG 一共 97 条带分类的注册，分布是
     * <pre>
     *   COLD 30   WET 27   HOT 24   SNOW 13   (SMALL 3 是全局池，不计入气候)
     * </pre>
     * 去掉 3 条 SMALL 后按 94 条折算，与带面积（14.0 / 29.8 / 28.4 / 27.8）对比：
     * <pre>
     *   SNOW 13.8%/14.0% = 0.99     COLD 31.9%/29.8% = 1.07
     *   HOT  25.5%/28.4% = 0.90     WET  28.7%/27.8% = 1.03
     * </pre>
     * 即 **RWG 作者本人就是按气候带面积分配群系的**（最大偏离 10%）。
     * 所以"群系数应与带面积成正比"是 RWG 的实际形态，不是 rtgc 自造的口径 ——
     * 这让 D3 的去偏斜有了对照依据，而不是凭感觉调阈值。
     */
    private static final double[] MEASURED_BAND_SHARE = { 0.1399, 0.2977, 0.2845, 0.2779 };

    /** RWG 自身的分布（去掉 3 条全局 SMALL 后的 94 条），用于并排显示"我们离 RWG 有多远"。 */
    private static final double[] RWG_BIOME_SHARE = { 13d / 94d, 30d / 94d, 24d / 94d, 27d / 94d };

    /**
     * 把「陆地群系数」与「气候带面积占比」并排打印，直接暴露归类偏斜（D3）。
     *
     * <p>目标：**每个气候的群系数应与它所占的地表面积成正比**。
     * 否则某片气候带会反复出现同几个群系（COLD 曾以 29.8% 的带面积占 47% 的群系）。
     */
    private static void logClimateSkew(final Report report) {

        int land = 0;
        for (final int n : report.landByClimate) {
            land += n;
        }
        if (land <= 0) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        boolean skewed = false;
        for (final Climate c : Climate.values()) {
            final int i = c.ordinal();
            final int count = report.landByClimate[i];
            final double share = count / (double) land;
            final double expected = MEASURED_BAND_SHARE[i];
            final double ratio = expected <= 0d ? 0d : share / expected;
            final double rwgRatio = RWG_BIOME_SHARE[i] <= 0d ? 0d : share / RWG_BIOME_SHARE[i];
            sb.append(c).append('=').append(count)
              .append("(占比").append(String.format(java.util.Locale.ROOT, "%.1f%%", share * 100d))
              .append("/带面积").append(String.format(java.util.Locale.ROOT, "%.1f%%", expected * 100d))
              .append(",倍率").append(String.format(java.util.Locale.ROOT, "%.2f", ratio))
              .append("/RWG").append(String.format(java.util.Locale.ROOT, "%.2f", rwgRatio))
              .append(") ");
            if (ratio < 0.6d || ratio > 1.6d) {
                skewed = true;
            }
        }

        Logger.info("[RTG] D3 气候归类核对（倍率 = 我们的群系占比 / 该带面积；/RWG = 对 RWG 自身分布的倍率）：{}",
                sb.toString());
        if (skewed) {
            Logger.warn("[RTG] ⚠ 有气候的「群系占比 / 带面积」倍率偏离 1 超过 ±60% —— "
                    + "该气候带内会出现同几个群系反复刷屏。调整 RtgBiomeCategorizer.climateFor 的判据。"
                    + "（RWG 自身的倍率是 0.99/1.07/0.90/1.03 —— 它**就是**按带面积分配的，"
                    + "所以这是有对照的目标，不是我们自造的口径。看上面那几行 CORE 成员名单定位误判。）");
        }
    }

    /**
     * 记录极端气候边界（山地链）池的填充结果。
     * <p>
     * 极端边界池是**镜像**自 cold/hot 方向池的（{@link RtgBiomeLayout#rebuildExtremeBorderMountains}）。
     * 因为方向池现在只装 RWG 显式标注的那几条（chaparral / meadow / rainforest /
     * tropicalRainforest / borealForest），这里的数字应当是 **rtgc 能对应的那几个**，
     * 而不是"每个陆地群系各一条链"（旧版 C1 镜像造成的 104 条）。
     */
    public static void logExtremeBorderReport(final RtgBiomeLayout layout) {

        final StringBuilder sb = new StringBuilder();
        int total = 0;
        for (final Climate c : Climate.values()) {
            final int veryCold = layout.poolSize(c, Placement.VERY_COLD_BORDER);
            final int veryHot = layout.poolSize(c, Placement.VERY_HOT_BORDER);
            total += veryCold + veryHot;
            sb.append(c).append("(veryCold=").append(veryCold)
              .append(",veryHot=").append(veryHot).append(") ");
        }

        if (total == 0) {
            Logger.warn("[RTG] 山地链池（极端边界）为空：{}—— 已接线但**不会显形**。"
                    + "根因是 cold/hot 方向池里没有 RWG 标注的成员（见 RWG placement 覆盖那几行）。",
                    sb.toString());
        } else {
            Logger.info("[RTG] 山地链池（极端边界）：{}共 {} 个变体，合成编号 {} 个。",
                    sb.toString(), total, RtgRealisticIndex.syntheticCount());
        }
    }

    /**
     * 把空着的海洋槽位补上。
     * <p>
     * RWG 的 {@code oceanShallow*}/{@code oceanDeep*} 是按气候写死的 12 个实例；
     * rtgc 的海洋群系数量不齐（例如「湿带的深海」可能根本不存在），
     * 而 {@link RtgBiomeLayout#getOceanBiome} 在槽位为 null 时会返回 null。
     * 为不让世界生成拿到 null，这里用已填好的其它气候槽位**兜底**（优先 HOT，再 COLD、WET、SNOW），
     * 并在日志中说明。
     *
     * <p>⚠ 兜底是**静默偏差**：借来的槽位意味着"这一带的海与那一带一模一样"。
     * RWG 的 12 个槽位都是它自己写的实例，没有借用；rtgc 补齐 6 个自己的海洋之后
     * 8 个槽位（4 深 + 4 浅）应当**全部**由实位填满，本方法只在缺群系时才会生效
     * —— 日志里 {@code 海洋槽位={}/4} 不是 4 就说明还有缺口。
     */
    private static void fillMissingOceanSlots(final RtgBiomeLayout layout) {
        for (final Climate c : Climate.values()) {
            for (final boolean deep : new boolean[] { true, false }) {
                if (oceanSlot(layout, c, deep) != null) {
                    continue;
                }
                for (final Climate donor : new Climate[] { Climate.HOT, Climate.COLD, Climate.WET, Climate.SNOW }) {
                    final IRealisticBiome b = oceanSlot(layout, donor, deep);
                    if (b != null) {
                        layout.setOceanBiome(c, deep, b);
                        Logger.debug("[RTG] 海洋槽位兜底：{} 的{}海用 {} 的群系 {}",
                                c, deep ? "深" : "浅", donor, nameOf(b.baseBiome()));
                        break;
                    }
                }
            }
        }
    }

    private static IRealisticBiome oceanSlot(final RtgBiomeLayout layout, final Climate c, final boolean deep) {
        return layout.oceanSlot(c, deep);
    }

    /**
     * RWG 里"只做 patch、**不占**槽位"的两个海洋群系。
     *
     * <p>依据是 {@code SupportBOP.java:39-47}：BOP 的海带森林与珊瑚礁被赋给
     * {@code Support.oceanShallowKelp} / {@code oceanShallowCoral} 两个钩子，
     * 而**没有**赋给任何一个 {@code oceanShallow*}/{@code oceanDeep*} 槽位。
     *
     * <p>为什么必须显式排除：这两个群系在 BiomeDictionary 里带 {@code OCEAN} 标签
     * （所以 {@link #isOcean} 认它们），若不排除就会被当成普通海洋去抢槽位 ——
     * 实测后果是 COLD/HOT/WET 三个浅海槽位全变成 {@code biomesoplenty:kelp_forest}。
     */
    private enum OceanPatch {
        /** {@code Support.oceanShallowKelp}。 */
        KELP,
        /** {@code Support.oceanShallowCoral}。 */
        CORAL
    }

    /** 注册名（小写、去下划线）→ patch 钩子；不是 patch 群系则返回 {@code null}。 */
    private static OceanPatch oceanPatchFor(final String key) {
        if ("kelpforest".equals(key)) {
            return OceanPatch.KELP;
        }
        if ("coralreef".equals(key)) {
            return OceanPatch.CORAL;
        }
        return null;
    }

    // ==================================================================
    // 纯判定函数（可离线审阅 / 测试）
    // ==================================================================

    /**
     * 气候判定，见类注释的规则表。
     *
     * <h2>为什么改用 Forge 的字典标签而不是自己定温雨阈值</h2>
     *
     * Forge 的 {@code BiomeDictionary.makeBestGuess(Biome)}（本仓库 Forge 源码
     * {@code net/minecraftforge/common/BiomeDictionary.java:263-367}）在第一次查询某群系的标签时，
     * 会**按它自己的阈值**批量打上 WET / DRY / HOT / COLD / SAVANNA / SANDY / MESA / SWAMP …
     * （原文：{@code rainfall > 0.85f → WET}、{@code rainfall < 0.15f → DRY}、
     * {@code temperature > 0.85f → HOT}、{@code temperature < 0.15f → COLD}、
     * {@code isHighHumidity() && heightVariation < 0 && baseHeight ∈ [0,0.3] → SWAMP}、
     * {@code topBlock == sand → SANDY}、{@code fillerBlock == hardened_clay → MESA}）。
     *
     * <p>也就是说**这套阈值本来就在手上**，我们却在这里另写了一套自造的
     *（{@code rain >= 0.9f && temp >= 0.8f}、{@code temp >= 0.7f && rain <= 0.35f}）。
     * 两套不一致的地方就是"误判"的来源：自造的 WET 判据要求 {@code temp >= 0.8f}，
     * 于是**雨的绝对值很高但不算热**的群系（沼泽 / 酸沼 / 湿地那一族）既不是 WET、也不是 HOT，
     * 全部落进 COLD 兜底 —— 这正是 COLD 倍率 1.5 的来源。
     * 现在 WET 直接用 {@code WET} 标签（Forge: rain &gt; 0.85），不再叠温度条件。
     *
     * <p>⚠ 顺便纠正一句**写错的注释**：本 Forge 版本的 {@code BiomeDictionary.Type}
     * **有** {@code MESA}（也有 DRY / WET / SANDY / WASTELAND / LUSH / DENSE / SPARSE），
     * 没有的只有 {@code DESERT}（实测：{@code javap net.minecraftforge.common.BiomeDictionary$Type}）。
     * 旧注释写的是"没有 DESERT / MESA 常量"，MESA 那半句是错的。
     *
     * <p>没改的两处（都是**保守**选择，不是遗漏）：HOT 的 {@code temp >= 0.7f && rain <= 0.35f}
     * 保留（它比 Forge 的 DRY 更宽，去掉会让 HOT 更少）；不加 {@code SANDY → HOT}
     *（海滩的顶方块也是沙，把 SANDY 当热带会把温带海滩整体推到 HOT）。
     */
    public static Climate climateFor(final Biome b) {

        final float temp = b.getDefaultTemperature();
        final float rain = b.getRainfall();
        final String name = nameOf(b);

        // SNOW：RWG 雪带实测只占 13.97%，故只收真正的严寒
        if (temp <= 0.15f || has(b, BiomeDictionary.Type.SNOWY)) {
            return Climate.SNOW;
        }

        // WET：RWG 的湿带是丛林与沼泽。
        // `WET` 标签 = Forge 的 `rainfall > 0.85f`（不含温度条件）—— 用它取代原先自造的
        // `rain >= 0.9f && temp >= 0.8f`，把"很湿但不热"的沼泽族从 COLD 兜底里救出来。
        if (has(b, BiomeDictionary.Type.JUNGLE) || has(b, BiomeDictionary.Type.SWAMP)
                || has(b, BiomeDictionary.Type.WET)
                || name.contains("swamp") || name.contains("jungle") || name.contains("rainforest")) {
            return Climate.WET;
        }

        // HOT：沙漠 / 台地 / 热带草原。
        // 判据 = 已确认存在的标签（HOT / SAVANNA）+ 温湿二维（暖且干）+ 注册名兜底。
        // 注册名兜底同时照顾 BOP 群系 —— 它们不一定都有字典标签。
        //
        // D3 修正：原先只有 `temp >= 0.9f` 这一条纯温度判据，于是大量**暖而干**的群系
        // （steppe / scrubland / outback / lushDesert / wasteland / xericShrubland …，
        // 温度多在 0.7–0.85）全部落进 COLD 兜底 —— 实测 COLD=53 / HOT=20，明显偏斜。
        // 按温湿二维补一条 `temp >= 0.7f && rain <= 0.35f`：这正是「暖且干」的定义，
        // 而这些名字里没有 desert/mesa 的群系本来就该归热带。
        // 阈值取得保守（rain ≤ 0.35 是真正的干旱），以免把温带森林误判成热带。
        if (temp >= 0.9f || has(b, BiomeDictionary.Type.HOT) || has(b, BiomeDictionary.Type.SAVANNA)
                || (temp >= 0.7f && rain <= 0.35f)
                || name.contains("desert") || name.contains("mesa") || name.contains("badlands")
                || name.contains("savanna") || name.contains("outback") || name.contains("dune")
                || name.contains("oasis") || name.contains("xeric") || name.contains("steppe")
                || name.contains("volcanic") || name.contains("wasteland")) {
            return Climate.HOT;
        }

        // COLD：温带森林 / 平原 / 针叶林，以及其余一切
        return Climate.COLD;
    }

    /**
     * 诊断：把**每一个陆地群系的归类依据**打成一行（{@code -Drtg.debugLayout} 时）。
     *
     * <p>为什么要它：D3 的偏斜表现为"COLD 收得太多"，而 COLD 是兜底分支 —— 光看
     * 每条气候的成员名单，看不出**是哪一个判据把某个群系漏掉的**。这里把
     * {@code temp / rain / 命中的标签}一并打出，误判可以直接指到某一条规则上，
     * 不必再"猜一轮、改一轮"。
     */
    private static void logClassification() {
        for (final Map.Entry<Biome, IRealisticBiome> entry : RTGAPI.RTG_BIOMES) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            final Biome b = entry.getKey();
            if (b == null || isOcean(nameOf(b), b) || isRiver(nameOf(b), b)) {
                continue;
            }
            // ⚠ 必须用 `String.format` 而不是 Logger 的 `{}` 占位符：`{:<6}` 这种宽度写法
            // SLF4J 不认，会**原样打印**，而 `{}` 只认相邻的一对花括号 ⇒ 参数会整体错位。
            // 第一版就是错的：日志打出来是 `[类] {:<6} {:<34} temp=COLD rain=plains 标签=0.80`
            // （temp 位置印的是气候、rain 位置印的是名字、标签位置印的是温度，最后两个参数被丢掉）。
            Logger.info(String.format(java.util.Locale.ROOT,
                    "[RTG]   [类] %-6s %-34s temp=%.2f rain=%.2f 标签=%s",
                    climateFor(b), nameOf(b),
                    b.getDefaultTemperature(), b.getRainfall(), tagsOf(b)));
        }
    }

    /** 诊断：把该群系命中的相关字典标签列出来（只列归类用得到的那几个）。 */
    private static String tagsOf(final Biome b) {
        final StringBuilder sb = new StringBuilder();
        for (final BiomeDictionary.Type t : new BiomeDictionary.Type[] {
                BiomeDictionary.Type.SNOWY, BiomeDictionary.Type.WET, BiomeDictionary.Type.DRY,
                BiomeDictionary.Type.HOT, BiomeDictionary.Type.COLD, BiomeDictionary.Type.SAVANNA,
                BiomeDictionary.Type.JUNGLE, BiomeDictionary.Type.SWAMP,
                BiomeDictionary.Type.MESA, BiomeDictionary.Type.SANDY,
                BiomeDictionary.Type.WASTELAND }) {
            if (has(b, t)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(t);
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /**
     * RWG 在 {@code Support*.java} 里**逐条手写**的 placement —— 本表是它的抄录，不是推断。
     *
     * <h2>为什么这张表取代了原来的 {@code SPECIALS}</h2>
     *
     * 原 {@code SPECIALS} 只抄了 RWG 的 {@code SMALL} / {@code SMALL_ISLAND} 两类（5 条）；
     * 而 `SupportBOP.java` 里还有 **{@code COLD_BORDER} / {@code HOT_BORDER} / {@code LITTORAL}** 三类
     * 共 12 条同样明确、同样可抄的标注 —— 它们此前被漏掉了，由
     * {@code RtgBiomeLayout.mirrorCoreIntoBorders()}（阶段 C1）用一个**结构性偏离**代替：
     * 把每个气候的整个核心池复制进三个边界池。
     *
     * <p>该偏离的代价实测有两项：
     * <ol>
     *   <li>RWG 的边界池是**手挑的少数"温和"群系**（例如 HOT 朝冷侧的过渡只有 chaparral），
     *       而"复制整个核心池"意味着边界上会出现该气候**最极端**的群系；</li>
     *   <li>更严重：{@code rebuildExtremeBorderMountains} 正是从 {@code coldBorder} /
     *       {@code hotBorder} **镜像**出极端边界（山地链）的池子。核心池被复制进去之后，
     *       RWG 那边的 **6 个**山地链在 rtgc 变成了**每个陆地群系各一个** ——
     *       实测 {@code syntheticCount()} = 104。也就是说"每种地形都能变成山地链"。</li>
     * </ol>
     *
     * <p><b>还有个事实需要纠正</b>：C1 的 javadoc 曾写"三池皆空 ⇒ 边界列拿到 null ⇒
     * 回落原版 GenLayer"。这不成立 —— {@link RtgBiomeLayout#getLandBiomeAt} 在三个池都空时
     * 会落到 {@code selectBiome(core[climate], …)}，与 RWG `ChunkManagerRealistic:785-798`
     * 的 fall-through 完全一致（rtgc 的这段本来就是逐行移植）。而
     * {@code selectCombinedBiome(shared=core, directional=core)} 的取值分布与
     * {@code selectBiome(core)} **逐点相同**（长度翻倍但前半段与后半段都是同一个列表）。
     * 所以那个镜像对**群系选择是恒等变换**，唯一真实作用是喂给极端边界镜像。
     *
     * <h2>匹配口径</h2>
     *
     * 只做**精确**匹配（注册名小写、去下划线），不做 {@code contains} ——
     * 因为 {@code tropicalrainforest} 含 {@code rainforest}、{@code mushroomislandshore}
     * 含 {@code mushroomisland}，用子串匹配会取决于表的顺序。
     * RWG 有而 rtgc 没有的条目（{@code jadeCliffs} / {@code tropics} / {@code garden} /
     * {@code fungiForest} / {@code sludgepit} / {@code landOfLakesMarsh} / {@code baseRiver*}）**不写进来**，
     * 免得把不存在的东西"填"进池里；它们的缺口在启动日志里单独报告。
     */
    private static final RwgPlace[] RWG_PLACEMENTS = {
            // ── SupportBOP.java（行号为 RWG 参考仓库中的行号）────────────────
            new RwgPlace("bayou", Climate.WET, Placement.LITTORAL), // :93
            new RwgPlace("borealforest", Climate.SNOW, Placement.HOT_BORDER), // :128
            new RwgPlace("chaparral", Climate.HOT, Placement.COLD_BORDER), // :183
            new RwgPlace("deadswamp", Climate.WET, Placement.LITTORAL), // :264
            new RwgPlace("flowerfield", Climate.COLD, Placement.SMALL), // :318
            new RwgPlace("garden", Climate.COLD, Placement.SMALL_ISLAND), // :376（BOP 1.12 无此群系）
            new RwgPlace("jadecliffs", Climate.COLD, Placement.HOT_BORDER), // :444（BOP 1.12 无此群系）
            new RwgPlace("landoflakesmarsh", Climate.HOT, Placement.HOT_BORDER), // :470（rtgc 只有 land_of_lakes）
            new RwgPlace("lushswamp", Climate.WET, Placement.LITTORAL), // :510
            new RwgPlace("mangrove", Climate.WET, Placement.LITTORAL), // :544
            new RwgPlace("meadow", Climate.COLD, Placement.COLD_BORDER), // :559
            new RwgPlace("ominouswoods", Climate.SNOW, Placement.SMALL_ISLAND), // :602
            new RwgPlace("rainforest", Climate.WET, Placement.COLD_BORDER), // :649
            new RwgPlace("quagmire", Climate.WET, Placement.SMALL), // :663
            new RwgPlace("sludgepit", Climate.WET, Placement.LITTORAL), // :743（BOP 1.12 无此群系）
            new RwgPlace("tropics", Climate.WET, Placement.LITTORAL), // :799（BOP 1.12 无此群系）
            new RwgPlace("tropicalrainforest", Climate.WET, Placement.COLD_BORDER), // :785
            new RwgPlace("oasis", Climate.HOT, Placement.SMALL), // :849
            // ── Support.java ────────────────────────────────────────────────
            new RwgPlace("mushroomisland", Climate.WET, Placement.SMALL_ISLAND), // :104-115
            new RwgPlace("mutatediceflats", Climate.SNOW, Placement.SMALL), // :127-139（RWG 的 icePlainsSpikes）
            new RwgPlace("mutatedplains", Climate.COLD, Placement.SMALL), // :140-152（RWG 的 sunflowerPlains）
            // ── SupportTC.java ──────────────────────────────────────────────
            // RWG 按**群系显示名**解析 Thaumcraft（`"Magical Forest"` / `"Tainted Land"`），
            // 且有两条 Magical Forest（SNOW 与 COLD，配方相同）。本表一名一行 ⇒ 取第一条。
            // `taintedland` 不写：rtgc 没有对应类（见 §0.5.1 的推断缺口）。
            new RwgPlace("magicalforest", Climate.SNOW, Placement.SMALL_ISLAND), // :38-50
            // ── rtgc 特有的补充（RWG 无此条目，理由如下）────────────────────
            //
            // `mushroom_island_shore`：RWG 没有这个现实主义群系（它只包了 `mushroomIsland`）。
            // 但 rtgc 有，且它**必须**跟它的母群系同池 —— 否则会退回"名字含 shore ⇒ 海滩"，
            // 把每个暖海岸变成蘑菇岛海岸（这是用户实测报过的 bug，见本类 1.0.28 的记录）。
            new RwgPlace("mushroomislandshore", Climate.WET, Placement.SMALL_ISLAND),
    };

    /** 精确匹配：注册名小写去下划线 → RWG 的 placement。 */
    private static RwgPlace rwgPlacementFor(final String key) {
        for (final RwgPlace p : RWG_PLACEMENTS) {
            if (key.equals(p.name)) {
                return p;
            }
        }
        return null;
    }

    /** 本次 {@link #apply} 中命中过的条目名（建布局后用于报告覆盖率）。 */
    private static final java.util.Set<String> MATCHED_PLACEMENTS =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /**
     * rtgc 自带名字的岛屿群系 → {@code ISLAND} 池。
     *
     * <p>RWG 的 {@code islandBiomes}（{@code ISLAND} 池）实际是**空的**，
     * 岛屿只看 {@code smallIsland} / {@code largeIsland} 两级。rtgc 没有那两级里
     * 除 {@code mushroomIsland} / {@code ominousWoods} 之外的可对应成员，
     * 故把 rtgc 自己的岛屿群系放进这一级，并让
     * {@code RtgBiomeLayout.selectIslandBiome} 在池空时退到本气候核心池 ——
     * 这样岛屿列至少拿到**气候正确**的群系，而不是回落到原版 GenLayer 的无关群系。
     *
     * <p>推断依据仅"名字含 island"，故在日志里单独计数以便核对。
     * {@code mushroomIsland} 已按 RWG 归入 {@code SMALL_ISLAND}，此处排除。
     */
    private static boolean islandBiome(final String name) {
        return name.contains("island") && !name.contains("mushroomisland");
    }

    /** {@link #RWG_PLACEMENTS} 的一行：名字（注册名小写去下划线）→ RWG 给的气候与 placement。 */
    private static final class RwgPlace {
        final String name;
        final Climate climate;
        final Placement placement;

        RwgPlace(final String name, final Climate climate, final Placement placement) {
            this.name = name;
            this.climate = climate;
            this.placement = placement;
        }
    }

    static boolean isOcean(final String name, final Biome b) {
        return has(b, BiomeDictionary.Type.OCEAN) || name.contains("ocean");
    }

    static boolean isRiver(final String name, final Biome b) {
        return has(b, BiomeDictionary.Type.RIVER) || name.contains("river");
    }

    private static boolean has(final Biome b, final BiomeDictionary.Type type) {
        try {
            return BiomeDictionary.hasType(b, type);
        } catch (final Throwable t) {
            // 群系未进字典（某些模组群系在初始化早期）时不该让世界生成崩掉
            return false;
        }
    }

    static String nameOf(final Biome b) {
        final ResourceLocation rl = b.getRegistryName();
        return rl == null ? "" : rl.getPath().toLowerCase(java.util.Locale.ROOT);
    }
}
