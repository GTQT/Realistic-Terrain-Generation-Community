package rtg.world.biome;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Biomes;

import rtg.api.RTGAPI;
import rtg.api.util.noise.ContinentalNoise;
import rtg.api.util.noise.PoissonPointNoise;
import rtg.api.util.noise.PerlinNoise;
import rtg.api.util.noise.RwgCellNoise;
import rtg.api.util.noise.RwgLayoutConfig;
import rtg.api.world.biome.IRealisticBiome;


/**
 * RWG 群系布局的移植 —— {@code rwg/world/ChunkManagerRealistic.java}（1081 行）的选择逻辑部分。
 *
 * <h2>它做什么</h2>
 * 回答「(x,z) 这一列归哪个现实主义群系」。它**不产生地形**，只做选择；
 * 地形由 {@link IRealisticBiome#rNoise} 负责。
 *
 * <h2>移植范围（本阶段）</h2>
 * <ul>
 *   <li><b>已移植</b>：气候场、群系选择器、气候边界 / 极端边界（山地链）判定、核心/边界/滨海/岛屿/海岸选择、
 *       大陆场与海洋值、海洋群系选择、逐列缓存。</li>
 *   <li><b>未移植（有意）</b>：RWG 的河道族（{@code getRiverStrength} / {@code calculateRiver} /
 *       {@code getRiverTunnelStrength} / {@code getRiverJunctionStrength}）与火山 / 熔岩洞族。
 *       rtgc 的河道由既有的 WP-5 系统（{@code TerrainBase.getRiverStrength} + {@code rwgCalculateRiver}）提供，
 *       它同样是对 RWG {@code calculateRiver} 的忠实移植，只是 Voronoi 实现换成 rtgc 的。
 *       把河道也切到 RWG 的 {@code cell.border} 版本是**独立的一次改动**，会再次改变河道，
 *       故留待布局接好之后单独决定。</li>
 *   <li><b>已关闭</b>：火山与熔岩洞 —— 由 {@code RwgLayoutConfig.averageLandmarksPerTypeAndContinent = 0}
 *       使 {@code ContinentalNoise} 内的地标采样分支永不进入，无需改动该类一行代码。</li>
 * </ul>
 *
 * <h2>缓存键的说明（纠正一处此前的推断）</h2>
 * RWG 写作 {@code ChunkCoordIntPair.chunkXZ2Int(par1, par2)}，而它传入的是**世界坐标且未做 >>4**，
 * 故键是**逐列**的。曾有一份分析据「chunk」之名推断它按区块打包，从而推出
 * 「一个区块只有一个群系、边界会更块状」——那与可见源码不符。
 * <p>
 * 更关键的是：{@code getBiomeDataAt} 是 {@code (x,z,seed)} 的**纯函数**，
 * 因此**逐列缓存是语义透明的**（只影响保留多少条，不影响返回值）；
 * 而按区块缓存会丢弃列信息、真正改变结果。故这里实现为逐列键 —— 既是字面照抄，也永远安全。
 *
 * <h2>列表顺序即权重</h2>
 * {@code selectBiome} 是 {@code list.get((int)(selector * length))}，所以**增加或删除一个条目会改变
 * 其余每个条目的带宽**。注册顺序必须稳定，否则世界会变。
 *
 * @since 1.0.10
 */
public final class RtgBiomeLayout {

    // ==================================================================
    // RWG ChunkManagerRealistic L34-49 的常量（逐字）
    // ==================================================================

    /** 深海判定：{@code continent < -300f}（L34 / L678）。 */
    private static final float SHALLOW_OCEAN_WIDTH = 300f;
    private static final float CLIMATE_WARP_SCALE_MULTIPLIER = .4f;
    private static final float CLIMATE_WARP_STRENGTH_MULTIPLIER = .8f;
    private static final float BIOME_WARP_SCALE_MULTIPLIER = .4f;
    private static final float BIOME_WARP_STRENGTH_MULTIPLIER = .175f;
    private static final float SNOW_CLIMATE_LIMIT = .16875f;
    private static final float COLD_CLIMATE_LIMIT = .545f;
    private static final float HOT_CLIMATE_LIMIT = .78f;
    private static final double CLIMATE_BORDER_DISTANCE_DIFFERENCE = 288D;
    private static final float LITTORAL_WIDTH = 432f;
    private static final double SMALL_BIOME_RADIUS = 75D;
    /** 海岸替换带：{@code continent < 24f}（L590）。 */
    private static final float COAST_WIDTH = 24f;
    /** 气候场的去相关偏移（L707）。 */
    private static final double CLIMATE_SHIFT = 4000D;
    /** 缓存上限（L597）。 */
    private static final int BIOME_CACHE_MAX = 4096;

    /** 气候带。ordinal 即 RWG 的 {@code climate - 1}。 */
    public enum Climate {
        /** climate = 1。 */
        SNOW,
        /** climate = 2。 */
        COLD,
        /** climate = 3。 */
        HOT,
        /** climate = 4。 */
        WET
    }

    /** 位置类别。ordinal 顺序与 RWG {@code BiomePlacement} 一致（该顺序是其字节序列化契约）。 */
    public enum Placement {
        CORE,
        BORDER,
        COLD_BORDER,
        HOT_BORDER,
        VERY_COLD_BORDER,
        VERY_HOT_BORDER,
        LITTORAL,
        SMALL,
        ISLAND,
        SMALL_ISLAND,
        LARGE_ISLAND
    }

    // ==================================================================
    // 噪声源（RWG L138-146 的 rtgc 等价物；噪声实现已逐字移植）
    // ==================================================================

    /** RWG {@code NoiseSelector.createNoiseGenerator(seed)}；rtgc 已确认默认即经典 Perlin。 */
    private final PerlinNoise perlin;
    /** RWG L142：{@code seed ^ 0xBB67AE8584CAA73BL}，气候与群系选择的域扭曲。 */
    private final PerlinNoise climateWarp;
    /**
     * RWG L141 的 {@code biomecell}。**{@code setUseDistance} 在 RWG 里从未调用**，
     * 故其 {@code noise()} 返回最近特征点格子的 {@code valueNoise2D} 哈希而非距离 —— 必须保持关闭，
     * 否则气候值与群系选择全错。
     */
    private final RwgCellNoise biomecell;
    /**
     * RWG L139-140 的 {@code cell}。**必须 {@code setUseDistance(true)}** ——
     * 这是它与 {@link #biomecell} 的关键差别：{@code cell.border()} / {@code cell.junction()}
     * 都要求线性距离语义。
     */
    private final RwgCellNoise cell;
    /** RWG L144：{@code new PoissonPointNoise(seed ^ 0x510E527FADE682D1L, 1020D, 4)}。 */
    private final PoissonPointNoise smallBiomePoints;
    /** RWG L146：仅大陆模式使用。 */
    private final ContinentalNoise continents;

    private final float climateWidth;
    private final float biomeWidth;

    // ==================================================================
    // 群系列表（RWG L86-101）
    // ==================================================================

    /** 四个气候的核心池（按 climate-1 索引）。 */
    private final List<IRealisticBiome>[] core = newListArray();
    private final List<IRealisticBiome>[] border = newListArray();
    private final List<IRealisticBiome>[] coldBorder = newListArray();
    private final List<IRealisticBiome>[] hotBorder = newListArray();
    private final List<IRealisticBiome>[] veryColdBorder = newListArray();
    private final List<IRealisticBiome>[] veryHotBorder = newListArray();
    private final List<IRealisticBiome>[] littoral = newListArray();
    private final List<IRealisticBiome>[] small = newListArray();
    private final List<IRealisticBiome>[] island = newListArray();
    private final List<IRealisticBiome>[] smallIsland = newListArray();
    private final List<IRealisticBiome>[] largeIsland = newListArray();

    // ---- 海洋 / 海岸的固定槽位（RWG 用的是写死的实例，这里改为注入）----
    private final IRealisticBiome[] oceanDeep = new IRealisticBiome[4];
    private final IRealisticBiome[] oceanShallow = new IRealisticBiome[4];
    private IRealisticBiome oceanShallowKelp;
    private IRealisticBiome oceanShallowCoral;
    private IRealisticBiome coastIce;
    private IRealisticBiome coastDunes;

    /** 逐列缓存（见类注释「缓存键的说明」）。 */
    /**
     * 逐列缓存。
     *
     * <p><b>必须是线程安全的</b>：单人游戏里**客户端线程**与**服务端线程**会并发调用
     * {@link #getBiomeDataAt}（客户端在区块载入后要按布局回填群系数组，见
     * {@code EventHandlerClient}）。原先用普通 {@code HashMap}，并发 put/resize 可能损坏内部表结构。
     * 换成 {@link java.util.concurrent.ConcurrentHashMap}：读取无锁，写入安全。
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, IRealisticBiome> biomeCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ==================================================================
    // 构造
    // ==================================================================

    public RtgBiomeLayout(final long seed) {
        this(seed, 1400f, 500f);
    }

    /**
     * @param seed         世界种子
     * @param climateWidth 气候带宽度（RWG 默认 {@code 1400f}）
     * @param biomeWidth   群系单元宽度（RWG 默认 {@code 500f}）
     */
    @SuppressWarnings("unchecked")
    public RtgBiomeLayout(final long seed, final float climateWidth, final float biomeWidth) {

        this.climateWidth = climateWidth;
        this.biomeWidth = biomeWidth;

        this.perlin = new PerlinNoise(seed);
        this.climateWarp = new PerlinNoise(seed ^ 0xBB67AE8584CAA73BL);
        this.biomecell = new RwgCellNoise(seed, (short) 0);      // useDistance 保持 false，与 RWG 一致
        this.cell = new RwgCellNoise(seed, (short) 0);
        this.cell.setUseDistance(true);                          // RWG L140
        this.smallBiomePoints = new PoissonPointNoise(seed ^ 0x510E527FADE682D1L, 1020D, 4);
        this.continents = new ContinentalNoise(seed ^ 0x6A09E667F3BCC909L);
    }

    @SuppressWarnings("unchecked")
    private static List<IRealisticBiome>[] newListArray() {
        final List<IRealisticBiome>[] a = new List[4];
        for (int i = 0; i < 4; i++) {
            a[i] = new ArrayList<>();
        }
        return a;
    }

    // ==================================================================
    // 注册
    // ==================================================================

    /**
     * 注册一个群系。对应 RWG {@code Support.addBiome(b, cat, placement)}。
     * <p>
     * <b>顺序即权重</b>：{@code selectBiome} 按下标取，故顺序必须稳定。
     */
    public void add(final IRealisticBiome biome, final Climate climate, final Placement placement) {
        if (biome == null) {
            return;
        }
        final int idx = climate.ordinal();
        switch (placement) {
            case CORE:
                core[idx].add(biome);
                break;
            case BORDER:
                border[idx].add(biome);
                break;
            case COLD_BORDER:
                coldBorder[idx].add(biome);
                break;
            case HOT_BORDER:
                hotBorder[idx].add(biome);
                break;
            case VERY_COLD_BORDER:
                veryColdBorder[idx].add(biome);
                break;
            case VERY_HOT_BORDER:
                veryHotBorder[idx].add(biome);
                break;
            case LITTORAL:
                littoral[idx].add(biome);
                break;
            case SMALL:
                small[idx].add(biome);
                break;
            case ISLAND:
                island[idx].add(biome);
                break;
            case SMALL_ISLAND:
                smallIsland[idx].add(biome);
                break;
            case LARGE_ISLAND:
                largeIsland[idx].add(biome);
                break;
            default:
                throw new IllegalStateException("unhandled placement " + placement);
        }
    }

    /**
     * <b>已删除（1.0.33 同版本追加）：{@code mirrorCoreIntoBorders()}。</b>
     *
     * <p>它曾把每个气候的核心池复制进 {@code BORDER} / {@code COLD_BORDER} / {@code HOT_BORDER}，
     * 理由是"三池皆空会让边界列拿到 null 并回落原版 GenLayer"。**那个理由不成立**：
     * {@link #getLandBiomeAt} 在三个池都空时会落到
     * {@code selectBiome(core[climate], …)}（本方法就是 RWG
     * {@code ChunkManagerRealistic:785-798} 的逐行移植，fall-through 本来就在），
     * 而 {@code selectCombinedBiome(core, core)} 的取值分布与 {@code selectBiome(core)}
     * **逐点相同** —— 镜像对群系选择是恒等变换。
     *
     * <p>它唯一真实的作用是喂给 {@link #rebuildExtremeBorderMountains}：那个方法从
     * {@code coldBorder} / {@code hotBorder} 镜像出山地链池，于是"整个核心池"被复制进去后，
     * RWG 的 6 个山地链变成了**每个陆地群系各一个**（实测 104 个合成编号）。
     *
     * <p>现在三个边界池的内容改由 {@code RtgBiomeCategorizer.RWG_PLACEMENTS}
     * **照抄** RWG {@code Support*.java} 的显式标注填入；没被标到的气候/方向就照 RWG 一样
     * 落到核心池（fall-through）。
     */

    /**
     * RWG {@code Support.rebuildExtremeBorderMountains} 的等价物：
     * 把「极端气候边界」池填成对应方向普通边界池的**镜像**。
     * <p>
     * 上游写法：
     * <pre>
     * snow.veryHotBorder  ← snow.hotBorder      （SNOW→HOT/WET，向上跳）
     * cold.veryHotBorder  ← cold.hotBorder      （COLD→WET，向上跳）
     * hot.veryColdBorder  ← hot.coldBorder      （HOT→SNOW，向下跳）
     * wet.veryColdBorder  ← wet.coldBorder      （WET→SNOW/COLD，向下跳）
     * </pre>
     * 本方法与 RWG 一样接收一个「包装函数」，因为 RWG 会把每个边界群系包进
     * {@code RealisticBiomeMountainChain.forBiome(...)} 再放进去。
     * 传入 {@code null} 表示原样放入（尚未移植山地链时的退化行为）。
     */
    public void rebuildExtremeBorderMountains(final java.util.function.UnaryOperator<IRealisticBiome> wrap) {
        veryHotBorder[Climate.SNOW.ordinal()].clear();
        veryHotBorder[Climate.COLD.ordinal()].clear();
        veryColdBorder[Climate.HOT.ordinal()].clear();
        veryColdBorder[Climate.WET.ordinal()].clear();

        mirrorInto(hotBorder[Climate.SNOW.ordinal()], veryHotBorder[Climate.SNOW.ordinal()], wrap);
        mirrorInto(hotBorder[Climate.COLD.ordinal()], veryHotBorder[Climate.COLD.ordinal()], wrap);
        mirrorInto(coldBorder[Climate.HOT.ordinal()], veryColdBorder[Climate.HOT.ordinal()], wrap);
        mirrorInto(coldBorder[Climate.WET.ordinal()], veryColdBorder[Climate.WET.ordinal()], wrap);
    }

    private static void mirrorInto(final List<IRealisticBiome> source, final List<IRealisticBiome> target,
                                   final java.util.function.UnaryOperator<IRealisticBiome> wrap) {
        for (final IRealisticBiome b : source) {
            final IRealisticBiome wrapped = wrap == null ? b : wrap.apply(b);
            // wrap 可能返回 null（合成槽位耗尽、或该群系无法包成山地链）——跳过而不是塞 null 进池
            if (wrapped != null) {
                target.add(wrapped);
            }
        }
    }

    public void setOceanBiome(final Climate climate, final boolean deep, final IRealisticBiome biome) {
        (deep ? oceanDeep : oceanShallow)[climate.ordinal()] = biome;
    }

    public void setOceanShallowKelp(final IRealisticBiome biome) { this.oceanShallowKelp = biome; }

    public void setOceanShallowCoral(final IRealisticBiome biome) { this.oceanShallowCoral = biome; }

    /** 海洋 patch 钩子（RWG {@code Support.oceanShallowKelp}）。诊断用。 */
    public IRealisticBiome oceanShallowKelp() { return this.oceanShallowKelp; }

    /** 海洋 patch 钩子（RWG {@code Support.oceanShallowCoral}）。诊断用。 */
    public IRealisticBiome oceanShallowCoral() { return this.oceanShallowCoral; }

    public void setCoastIce(final IRealisticBiome biome) { this.coastIce = biome; }
    public void setCoastDunes(final IRealisticBiome biome) { this.coastDunes = biome; }

    /** 冷海岸群系（RWG {@code RealisticBiomeBase.coastIce}）。诊断用。 */
    public IRealisticBiome coastIce() { return this.coastIce; }

    /** 暖海岸群系（RWG {@code RealisticBiomeBase.coastDunes}）。诊断用。 */
    public IRealisticBiome coastDunes() { return this.coastDunes; }

    /** 某「气候 × 位置」池的成员名，供启动日志核对（顺序即选择顺序）。 */
    public String poolMembers(final Climate climate, final Placement placement) {
        final StringBuilder sb = new StringBuilder();
        for (final IRealisticBiome b : pool(climate, placement)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(b == null ? "null" : b.baseBiomeResLoc());
        }
        return sb.toString();
    }

    // ==================================================================
    // 气候（RWG L694-715，逐行对应）
    // ==================================================================

    /** RWG L694-696。返回 1..4。 */
    public int getClimateAt(final int x, final int y) {
        return getClimateFromValue(getClimateValue(x, y));
    }

    /** RWG L698-702。{@code wetEnabled} 在 RWG 里由「wet 核心池非空」决定；rtgc 恒为四带模式。 */
    public int getClimateFromValue(final float climate) {
        return climate < SNOW_CLIMATE_LIMIT ? 1
                : climate < COLD_CLIMATE_LIMIT ? 2
                : climate < HOT_CLIMATE_LIMIT ? 3 : 4;
    }

    /** RWG L704-708。 */
    public float getClimateValue(final int x, final int y) {
        final double[] warped = new double[2];
        warpClimateCoordinates(biomeX(x), biomeZ(y), warped);
        return (biomecell.noise((warped[0] + CLIMATE_SHIFT) / climateWidth, warped[1] / climateWidth, 1D) * .5f) + .5f;
    }

    /** RWG L710-715。 */
    private void warpClimateCoordinates(final int x, final int y, final double[] output) {
        final float scale = climateWidth * CLIMATE_WARP_SCALE_MULTIPLIER;
        final float strength = climateWidth * CLIMATE_WARP_STRENGTH_MULTIPLIER;
        output[0] = x + climateWarp.noise2(x / scale, y / scale) * strength;
        output[1] = y + climateWarp.noise2((x + 1731f) / scale, (y - 2459f) / scale) * strength;
    }

    // ==================================================================
    // 选择器（RWG L717-729 / L836-842，逐行对应）
    // ==================================================================

    /** RWG L717-721。 */
    private IRealisticBiome selectBiome(final List<IRealisticBiome> biomes, final int length,
                                        final int x, final int y) {
        if (length <= 0) {
            return null;
        }
        float value = sampleBiomeSelector(x, y);
        value = value < 0f ? 0f : value >= .9999999f ? .9999999f : value;
        return firstNonNull(biomes, (int) (value * length));
    }

    /**
     * 从 {@code from} 开始环形查找第一个非 null 成员。
     *
     * <p><b>为什么需要</b>：池是 {@code List<IRealisticBiome>}，而 {@code biomes.get(i)} 可能是
     * <b>null</b>（某个群系没能被分类/注册）。RWG 在这里会直接 NPE；rtgc 原先把它当成
     * "该列没有群系"返回 null，而 null 会被调用方当成"回落到别的生成器" —— 见
     * {@link #lastResortAt} 的说明。这里改成跳过 null，让"池里有几个可用群系"就够用。
     */
    private static IRealisticBiome firstNonNull(final List<IRealisticBiome> biomes, final int from) {
        final int n = biomes.size();
        if (n == 0) {
            return null;
        }
        final int start = from < 0 ? 0 : from >= n ? n - 1 : from;
        for (int step = 0; step < n; step++) {
            final IRealisticBiome candidate = biomes.get((start + step) % n);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** RWG L723-729。 */
    private float sampleBiomeSelector(final int x, final int y) {
        final float scale = biomeWidth * BIOME_WARP_SCALE_MULTIPLIER;
        final float strength = biomeWidth * BIOME_WARP_STRENGTH_MULTIPLIER;
        final float warpedX = x + climateWarp.noise2((x - 8191f) / scale, (y + 3137f) / scale) * strength;
        final float warpedY = y + climateWarp.noise2((x + 5171f) / scale, (y - 6971f) / scale) * strength;
        return (biomecell.noise(warpedX / biomeWidth, warpedY / biomeWidth, 1D) * .5f) + .5f;
    }

    /** RWG L836-842：{@code shared} 在前、{@code directional} 在后，按同一选择器取。 */
    private IRealisticBiome selectCombinedBiome(final List<IRealisticBiome> shared,
                                                final List<IRealisticBiome> directional,
                                                final int x, final int y) {
        final int length = shared.size() + directional.size();
        if (length <= 0) {
            return null;
        }
        float value = sampleBiomeSelector(x, y);
        value = Math.max(0f, Math.min(.9999999f, value));
        final int index = (int) (value * length);
        final IRealisticBiome picked = index < shared.size()
                ? firstNonNull(shared, index)
                : firstNonNull(directional, index - shared.size());
        if (picked != null) {
            return picked;
        }
        final IRealisticBiome fromShared = firstNonNull(shared, 0);
        return fromShared != null ? fromShared : firstNonNull(directional, 0);
    }

    /**
     * 布局的**最后兜底**：任何拿不到群系的列都在这里得到一个**气候正确的布局成员**。
     *
     * <p><b>为什么必须有这一步（本方法的唯一存在理由）</b>：
     * {@link RtgLayoutAccess#biomeAt} 的所有调用方都把 {@code null} 理解成"这次拿不到，我自己回退"。
     * 两条回退路径的后果完全不同：
     * <ul>
     *   <li>地形侧：该列不进混合场，用邻列的群系地形 —— 看不出来；</li>
     *   <li>群系 provider 侧：{@code BiomeProviderBOP.getBiome} 会回落到
     *       <b>BOP 自己的 GenLayer</b>，而那是一条**与 RWG 布局毫无关系**的群系表
     *       （它带 {@code GenLayerAddMushroomIsland}，见 {@code BiomeProviderBOP:190}）。</li>
     * </ul>
     * 后者会让 F3 显示一个与本列地形/地表/装饰**完全无关**的群系 —— 实测表现就是
     * "地上是正常的树林，F3 却显示蘑菇群系"。因为 {@code generateLandscape} 的
     * {@code landscape.biome[]} 正是由 provider 的返回值经 {@code BiomeAnalyzer.newRepair} 转回来的
     * （{@code ChunkGeneratorRTG:970-978}），这个回退值会**同时污染 F3、地表与装饰**。
     *
     * <p>所以这里改用"本气候核心池的第一个非 null 成员"。这与 {@code selectIslandBiome}
     * 里已有的 C3 适配是同一条原则（宁可给一个气候正确的群系，也不要回落到原版 GenLayer），
     * 见 {@code docs/rwg-port-gaps.md} §18 / §27。
     */
    public IRealisticBiome lastResortAt(final int x, final int z) {
        final int climateIndex = getClimateAt(x, z) - 1;
        final IRealisticBiome coreMember = firstNonNull(core[climateIndex], 0);
        if (coreMember != null) {
            return coreMember;
        }
        for (final IRealisticBiome[] pool : new IRealisticBiome[][] { oceanShallow, oceanDeep }) {
            for (final IRealisticBiome candidate : pool) {
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return RTGAPI.getRTGBiome(Biomes.PLAINS);
    }

    // ==================================================================
    // 陆地选择（RWG L752-798，含气候边界与极端边界）
    // ==================================================================

    /** RWG L731-733。 */
    public IRealisticBiome getLandBiomeAt(final int x, final int z) {
        return getLandBiomeAt(x, z, getClimateAt(x, z));
    }

    /** RWG L752-798 —— 布局的核心。 */
    public IRealisticBiome getLandBiomeAt(final int par1In, final int par2In, final int climate) {

        final int par1 = biomeX(par1In);
        final int par2 = biomeZ(par2In);
        final int climateIndex = climate - 1;

        // ---- 小型群系：优先于一切，且选择点用的是泊松特征点本身（故整片圆盘内恒定）----
        final List<IRealisticBiome> smallPool = small[climateIndex];
        if (!smallPool.isEmpty()) {
            final double[] point = new double[5];
            smallBiomePoints.sample(par1, par2, point);
            if (point[0] < SMALL_BIOME_RADIUS) {
                return selectBiome(smallPool, smallPool.size(), (int) point[3], (int) point[4]);
            }
        }

        // ---- 气候边界判定 ----
        final double[] warped = new double[2];
        final double[] climatePoints = new double[4];
        warpClimateCoordinates(par1, par2, warped);
        biomecell.sampleTwo2D((warped[0] + CLIMATE_SHIFT) / climateWidth, warped[1] / climateWidth, 1D, climatePoints);

        final int neighborClimate = getClimateFromValue((float) (climatePoints[3] * .5D + .5D));
        final boolean climateBorder = neighborClimate != climate
                && (climatePoints[2] - climatePoints[0]) * climateWidth < CLIMATE_BORDER_DISTANCE_DIFFERENCE;

        if (climateBorder) {
            final int borderDirection = neighborClimate < climate ? -1 : 1;
            final boolean extreme = Math.abs(neighborClimate - climate) > 1;

            if (extreme) {
                final List<IRealisticBiome> extremeDirectional = borderDirection < 0
                        ? veryColdBorder[climateIndex]
                        : veryHotBorder[climateIndex];
                if (!extremeDirectional.isEmpty()) {
                    return selectBiome(extremeDirectional, extremeDirectional.size(), par1, par2);
                }
                // 注意：RWG 在极端池为空时**不返回**，而是继续走下面的普通边界选择。
            }

            final List<IRealisticBiome> directional = borderDirection < 0
                    ? coldBorder[climateIndex]
                    : hotBorder[climateIndex];
            final List<IRealisticBiome> shared = border[climateIndex];
            if (!shared.isEmpty() || !directional.isEmpty()) {
                return selectCombinedBiome(shared, directional, par1, par2);
            }
        }

        return selectBiome(core[climateIndex], core[climateIndex].size(), par1, par2);
    }

    /** RWG L657-663：岛屿群系在整个岛屿上恒定（选择点用的是岛屿种子坐标）。 */
    public IRealisticBiome selectIslandBiome(final int tier, final int climate,
                                             final int seedX, final int seedY) {
        final List<IRealisticBiome> sized = tier == 0
                ? smallIsland[climate - 1]
                : largeIsland[climate - 1];
        final List<IRealisticBiome> general = island[climate - 1];
        if (general.isEmpty() && sized.isEmpty()) {
            // C3 适配：RWG 在 `Support.java` 里给四个气候都填了 island/smallIsland/largeIsland，
            // 而 rtgc 没有 RWG 的成员（fungiForest / hotPlainsCanyonIsland / garden /
            // tropics / baseRiver* 在 rtgc 都不存在），池可能是空的。
            // RWG 此时返回 null；rtgc 若也返回 null，岛屿列就会**回落到原版 GenLayer 群系**
            //（与所在气候无关），比拿到一个气候正确的群系更糟。
            // 故退一步用本气候的核心池 —— 岛屿会像该气候的"微缩大陆"，这是有意的适配，
            // 不是照抄。见 docs/rwg-port-gaps.md §18。
            final List<IRealisticBiome> fallback = core[climate - 1];
            return fallback.isEmpty()
                    ? null
                    : selectBiome(fallback, fallback.size(), biomeX(seedX), biomeZ(seedY));
        }
        return selectCombinedBiome(general, sized, biomeX(seedX), biomeZ(seedY));
    }

    // ==================================================================
    // 海洋 / 大陆值（RWG L677-692 / L279-296 / L482-519）
    // ==================================================================

    /** RWG L677-692。 */
    public IRealisticBiome getOceanBiome(final float continent, final int climate,
                                         final int x, final int y) {
        if (climate < 1 || climate > 4) {
            return null;
        }
        if (continent < -SHALLOW_OCEAN_WIDTH) {
            return oceanDeep[climate - 1];
        }
        final int bx = biomeX(x);
        final int bz = biomeZ(y);
        final float patch = perlin.noise2(bx / 180f, bz / 180f) * .7f + perlin.noise2(bx / 55f, bz / 55f) * .3f;
        if ((climate == 1 || climate == 2) && continent < -90f && patch > 0f) {
            return oceanShallowKelp != null ? oceanShallowKelp : oceanShallow[climate - 1];
        }
        if ((climate == 3 || climate == 4) && continent < -20f && continent > -150f && patch > .07f) {
            return oceanShallowCoral != null ? oceanShallowCoral : oceanShallow[climate - 1];
        }
        return oceanShallow[climate - 1];
    }

    /** RWG L279-296。大陆模式返回「离岸格数」，非大陆模式返回 {@code (getLegacyOceanValue - 1) * 100}。 */
    public float getContinentValue(final int x, final int y) {
        return continents.getValue(landmassX(x), landmassZ(y));
    }

    /** RWG L486-489：{@code clamp(1 + continent / 100, 0, 2)}。 */
    public static float getTerrainOceanValue(final float continent) {
        return Math.max(0f, Math.min(2f, 1f + continent / 100f));
    }

    // ==================================================================
    // 河道族（RWG L863-884 / L886-943，逐行对应）
    // ==================================================================

    /** RWG 硬编码的河床基准：{@code 59f}。 */
    private static final float RIVER_BED = 59f;
    /** RWG 的河道扭曲尺度（{@code noise1(y / 240f) * 220f}）。 */
    private static final float RIVER_WARP_DIVISOR = 240f;
    private static final float RIVER_WARP_STRENGTH = 220f;
    /** RWG 的河网单元尺寸（格）。 */
    private static final double RIVER_SEPARATION = 1250D;

    /** RWG L886-890：河强 {@code [-1, 0]}，{@code -1} 在河心。 */
    public float getRiverStrength(final int x, final int y) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        return getRiverStrength(x, y, pX, pY);
    }

    /**
     * RWG L892-899：带出扭曲后的坐标，供 {@link #calculateRiver(int, int, float, float, float[])}
     * 复用，避免重复算 {@code noise1}。
     * <p>
     * {@code sample} 需长度 ≥ 4：{@code [0]=pX, [1]=pY, [2]=雕刻权重（NaN 表示未算）, [3]=河床高度}。
     */
    public float getRiverStrength(final int x, final int y, final float[] sample) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        sample[0] = pX;
        sample[1] = pY;
        sample[2] = Float.NaN;
        return getRiverStrength(x, y, pX, pY);
    }

    /** RWG L901-905：地下河隧道强度 {@code [0,1]}，宽度 {@code 9/1250} 单元。 */
    public float getRiverTunnelStrength(final int x, final int y) {
        final float warpedX = x + perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        final float warpedY = y + perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        return -cell.border(warpedX / RIVER_SEPARATION, warpedY / RIVER_SEPARATION, 9D / RIVER_SEPARATION, 1f);
    }

    /** RWG L907-911：交汇洞厅强度 {@code [0,1]}，半径 {@code 60/1250} 单元。 */
    public float getRiverJunctionStrength(final int x, final int y) {
        final float warpedX = x + perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        final float warpedY = y + perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        return -cell.junction(warpedX / RIVER_SEPARATION, warpedY / RIVER_SEPARATION, 60D / RIVER_SEPARATION, 1f);
    }

    /**
     * RWG L913-933：原始河强 + **火山邻域抑制**。
     * <p>
     * rtgc 已关闭地标，故 {@code getVolcanoVicinityCoordinates} 恒返回 {@code Long.MIN_VALUE}，
     * 该抑制分支永不进入 —— 但代码照抄保留，以便将来若重新启用火山时行为正确。
     */
    private float getRiverStrength(final int x, final int y, final float pX, final float pY) {

        final float strength = getRawRiverStrength(pX, pY);

        final long coordinates = continents.getVolcanoVicinityCoordinates(landmassX(x), landmassZ(y));
        if (coordinates == Long.MIN_VALUE) {
            return strength;
        }
        final float localX = ContinentalNoise.unpackVolcanoX(coordinates);
        final float localY = ContinentalNoise.unpackVolcanoY(coordinates);
        final float distance = (float) Math.sqrt(localX * localX + localY * localY);
        if (distance <= ContinentalNoise.VOLCANO_RADIUS) {
            return Math.max(0f, strength);
        }

        final float blend = (float) ((distance - ContinentalNoise.VOLCANO_RADIUS)
                / (ContinentalNoise.VOLCANO_ISLAND_RADIUS - ContinentalNoise.VOLCANO_RADIUS));
        return strength < 0f ? strength * blend : strength;
    }

    /** RWG L941-943：河网 = {@code cell} 的单元边界；宽度 {@code 50/300}。 */
    private float getRawRiverStrength(final float pX, final float pY) {
        return cell.border(pX / RIVER_SEPARATION, pY / RIVER_SEPARATION, 50D / 300D, 1f);
    }

    /** RWG L863-875：把高度线性混合到**噪声河床** {@code 59 ± 3.5}；雕刻宽度 {@code 50/1300}。 */
    public float calculateRiver(final int x, final int y, final float st, final float biomeHeight) {
        if (st < 0f && biomeHeight > RIVER_BED) {
            final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
            final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
            final float riverCarving = cell.border(pX / RIVER_SEPARATION, pY / RIVER_SEPARATION, 50D / 1300D, 1f);
            return (biomeHeight * (riverCarving + 1f))
                    + ((RIVER_BED + perlin.noise2(x / 12f, y / 12f) * 2f + perlin.noise2(x / 8f, y / 8f) * 1.5f)
                            * (-riverCarving));
        }
        return biomeHeight;
    }

    /** RWG L877-884：同上，但复用 {@code sample} 里已算好的扭曲坐标与河床高度。 */
    public float calculateRiver(final int x, final int y, final float st, final float biomeHeight,
                                final float[] sample) {
        if (st >= 0f || biomeHeight <= RIVER_BED) {
            return biomeHeight;
        }
        if (Float.isNaN(sample[2])) {
            sample[2] = cell.border(sample[0] / RIVER_SEPARATION, sample[1] / RIVER_SEPARATION, 50D / 1300D, 1f);
            sample[3] = RIVER_BED + perlin.noise2(x / 12f, y / 12f) * 2f + perlin.noise2(x / 8f, y / 8f) * 1.5f;
        }
        return (biomeHeight * (sample[2] + 1f)) + (sample[3] * (-sample[2]));
    }

    // ==================================================================
    // 主查询（RWG L539-605 的决策树）
    // ==================================================================

    /**
     * RWG {@code getBiomeDataAt}。返回该列的现实主义群系。
     * <p>
     * <b>可能返回 null</b>：当核心池未注册（空）时。RWG 在这种情况下会在
     * {@code out.baseBiome.temperature} 处 NPE；rtgc 改为返回 null 且**不写入缓存**，
     * 由调用方兜底 —— 这样注册顺序稍晚也能自愈，而不是让世界生成崩掉。
     * <p>
     * 决策顺序（与 RWG 严格一致）：
     * <ol>
     *   <li>海洋：{@code continent < 0f} → {@link #getOceanBiome}；</li>
     *   <li>火山：{@code Support.volcanoIsland instanceof ...} —— rtgc 恒为 null，故跳过；</li>
     *   <li>岛屿：{@code islandSizeTier >= 0} → {@link #selectIslandBiome}（选择点在岛屿种子处）；</li>
     *   <li>陆地：{@link #getLandBiomeAt}（含小群系 / 气候边界 / 极端边界 / 核心）；</li>
     *   <li>滨海：{@code continent < 432f} 且 littoral 池非空 → 覆盖上一步结果；</li>
     *   <li>海岸：既非岛屿也非滨海，且 {@code continent < 24f} → {@code coastIce} / {@code coastDunes}。</li>
     * </ol>
     */
    public IRealisticBiome getBiomeDataAt(final int x, final int z) {

        final long key = cacheKey(x, z);
        final IRealisticBiome cached = biomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        final IRealisticBiome output = computeBiomeDataAt(x, z);
        if (output == null) {
            return null;    // 不缓存 null：核心池未注册时让它每次都能重试
        }

        if (biomeCache.size() > BIOME_CACHE_MAX) {
            biomeCache.clear();
        }
        biomeCache.put(key, output);
        return output;
    }

    private IRealisticBiome computeBiomeDataAt(final int x, final int z) {

        final float continent = getContinentValue(x, z);
        final int climate = getClimateAt(x, z);

        if (continent < 0f) {
            return getOceanBiome(continent, climate, x, z);
        }

        // RWG 的火山分支在此处；rtgc 的 Support.volcanoIsland 恒为 null，故整支不存在。

        boolean islandSelected = false;
        IRealisticBiome out = null;

        final int islandTier = continents.getIslandSizeTier(landmassX(x), landmassZ(z));
        if (islandTier >= 0) {
            final long seedCoordinates = continents.getIslandSeedCoordinates(landmassX(x), landmassZ(z));
            final int seedX = (int) (seedCoordinates >> 32);
            final int seedY = (int) seedCoordinates;
            final int islandClimate = getClimateAt(seedX, seedY);
            out = selectIslandBiome(islandTier, islandClimate, seedX, seedY);
            if (out != null) {
                islandSelected = true;
            }
        }

        boolean littoralSelected = false;
        if (out == null) {
            out = getLandBiomeAt(x, z, climate);
            if (continent < LITTORAL_WIDTH && !littoral[climate - 1].isEmpty()) {
                final List<IRealisticBiome> pool = littoral[climate - 1];
                out = selectBiome(pool, pool.size(), biomeX(x), biomeZ(z));
                littoralSelected = true;
            }
        }

        if (!islandSelected && !littoralSelected && continent < COAST_WIDTH) {
            final IRealisticBiome coast = out != null && out.baseBiome().getDefaultTemperature() < 0.15f
                    ? coastIce
                    : coastDunes;
            if (coast != null) {
                out = coast;
            }
        }

        return out;
    }

    // ==================================================================
    // 坐标与工具
    // ==================================================================

    /** 逐列缓存键：把两个世界坐标打包进一个 long（对应 RWG 的 {@code chunkXZ2Int}）。 */
    static long cacheKey(final int x, final int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    static int landmassX(final int x) { return x + RwgLayoutConfig.landmassOffsetX; }

    static int landmassZ(final int z) { return z + RwgLayoutConfig.landmassOffsetZ; }

    static int biomeX(final int x) { return x + RwgLayoutConfig.biomeOffsetX; }

    static int biomeZ(final int z) { return z + RwgLayoutConfig.biomeOffsetZ; }

    // ==================================================================
    // 只读诊断访问器（供切换前的离线分配标定使用；不参与选择）
    //
    // 这些方法把选择所依赖的**原始量**暴露出来，让标定工具用**同一批常量**做比较，
    // 而不是在工具里复制一份判定逻辑（复制会漂移）。阈值常量同步公开。
    // ==================================================================

    /** 气候边界阈值：{@code CLIMATE_BORDER_DISTANCE_DIFFERENCE}（格）。 */
    public static final double DIAG_CLIMATE_BORDER_DISTANCE = CLIMATE_BORDER_DISTANCE_DIFFERENCE;
    /** 滨海带阈值：{@code LITTORAL_WIDTH}（格）。 */
    public static final float DIAG_LITTORAL_WIDTH = LITTORAL_WIDTH;
    /** 海岸替换带阈值：{@code COAST_WIDTH}（格）。 */
    public static final float DIAG_COAST_WIDTH = COAST_WIDTH;
    /** 深海阈值：{@code SHALLOW_OCEAN_WIDTH}（格）。 */
    public static final float DIAG_SHALLOW_OCEAN_WIDTH = SHALLOW_OCEAN_WIDTH;
    /** 小型群系半径：{@code SMALL_BIOME_RADIUS}（格）。 */
    public static final double DIAG_SMALL_BIOME_RADIUS = SMALL_BIOME_RADIUS;

    /** 诊断：该列的**邻点气候**（{@code getLandBiomeAt} 用它判定是否处于气候边界）。 */
    public int diagNeighborClimateAt(final int x, final int z) {
        final double[] warped = new double[2];
        final double[] out = new double[4];
        warpClimateCoordinates(biomeX(x), biomeZ(z), warped);
        biomecell.sampleTwo2D((warped[0] + CLIMATE_SHIFT) / climateWidth, warped[1] / climateWidth, 1D, out);
        return getClimateFromValue((float) (out[3] * .5D + .5D));
    }

    /** 诊断：气候边界距离量 {@code (d₂ − d₁) × climateWidth}（格）。 */
    public double diagClimateBorderMetric(final int x, final int z) {
        final double[] warped = new double[2];
        final double[] out = new double[4];
        warpClimateCoordinates(biomeX(x), biomeZ(z), warped);
        biomecell.sampleTwo2D((warped[0] + CLIMATE_SHIFT) / climateWidth, warped[1] / climateWidth, 1D, out);
        return (out[2] - out[0]) * climateWidth;
    }

    /** 诊断：到最近小型群系特征点的距离（格）。 */
    public double diagSmallBiomeDistance(final int x, final int z) {
        final double[] point = new double[5];
        smallBiomePoints.sample(biomeX(x), biomeZ(z), point);
        return point[0];
    }

    /** 诊断：某气候的小型群系池是否非空（决定小型群系分支是否可能命中）。 */
    public boolean diagHasSmallBiomes(final int climate) {
        return !small[climate - 1].isEmpty();
    }

    /** 诊断：某气候的滨海池是否非空（决定滨海分支是否可能命中）。 */
    public boolean diagHasLittoral(final int climate) {
        return !littoral[climate - 1].isEmpty();
    }

    // ==================================================================
    // 供标定/诊断使用
    // ==================================================================

    /** 诊断：某个 气候 × 位置 池的大小。 */
    public int poolSize(final Climate climate, final Placement placement) {
        return pool(climate, placement).size();
    }

    /** 诊断：某个 气候 × 位置 池。 */
    public List<IRealisticBiome> pool(final Climate climate, final Placement placement) {
        final int i = climate.ordinal();
        switch (placement) {
            case CORE: return core[i];
            case BORDER: return border[i];
            case COLD_BORDER: return coldBorder[i];
            case HOT_BORDER: return hotBorder[i];
            case VERY_COLD_BORDER: return veryColdBorder[i];
            case VERY_HOT_BORDER: return veryHotBorder[i];
            case LITTORAL: return littoral[i];
            case SMALL: return small[i];
            case ISLAND: return island[i];
            case SMALL_ISLAND: return smallIsland[i];
            case LARGE_ISLAND: return largeIsland[i];
            default: throw new IllegalStateException("unhandled placement " + placement);
        }
    }

    /** 诊断：读取某个海洋槽位（{@code deep=true} 取深海）。 */
    public IRealisticBiome oceanSlot(final Climate climate, final boolean deep) {
        return (deep ? oceanDeep : oceanShallow)[climate.ordinal()];
    }

    /** 诊断：剩余的大陆场引用（供标定工具量岛屿分级）。 */
    public ContinentalNoise continents() {
        return continents;
    }
}
