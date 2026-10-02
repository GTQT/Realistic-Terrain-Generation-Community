package rtg.world.biome;

import net.minecraft.init.Biomes;
import rtg.api.RTGAPI;
import rtg.api.util.noise.*;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.realistic.land.RealisticBiomeIslandVolcano;

import java.util.ArrayList;
import java.util.List;


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
 *   <li><b>本次新增（外科手术式）</b>：火山 / 熔岩洞的**查询方法族**
 *       （RWG {@code ChunkManagerRealistic} L402-476 / L607-655，含其依赖的 L849-857 / L935-939）
 *       已逐行搬入本类，见下面「火山 / 熔岩洞族」一节。它们**尚未被任何调用方接上**：
 *       {@link #computeBiomeDataAt} 的火山分支、以及 {@code getBiomeDataAt} 里的熔岩洞标记群系
 *       分支仍是关闭状态 —— 本节只提供查询，不改任何既有行为。另需父级在布局建立后调用一次
 *       {@link #setTerrainWorld(RTGWorld)}，否则地形高度采样会退化为哨兵值（见该字段的说明）。</li>
 *   <li><b>另新增（同一次外科手术）</b>：火山**地图生成器**钩子所需的两个查询
 *       {@link #getNoiseWithRiverOceanAt}（RWG L859-861）与 {@link #isBorderlessAt}（RWG L945-963），
 *       以及它们共用的字段 {@link #borderNoise}。调用方是
 *       {@code RealisticBiomeBOPTropicalIsland.rMapGen}（RWG
 *       {@code RealisticBiomeIslandTropical:56-74}）；在 {@code ChunkGeneratorRTG} 接上
 *       {@code generateMapGen} 钩子之前，它们同样**零调用者**。</li>
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
    /**
     * 地下河隧道带的宽度（格）。RWG 是 {@code 9}；rtgc 按用户要求放宽到 {@code 25}
     * （实测覆盖率 2.93% → 8.1%，见 {@link #getRiverTunnelStrength}）。
     */
    private static final double TUNNEL_BAND_WIDTH = 25D;
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

    /**
     * RWG L901-905：地下河隧道强度 {@code [0,1]}。
     *
     * <p>⚠ <b>带宽是 rtgc 调过的</b>：RWG 用 {@code 9/1250}（9 格），实测只有 **2.93%** 的列落在
     * 隧道带内 ⇒ 一座山里绝大多数地方离河网边界太远，表现就是"很多山没有暗河"。
     * 按用户要求放宽到 {@code 25/1250}（25 格）⇒ 实测 **8.1%**（同一份标定：
     * {@code gradlew calibrateRiverTunnels}）。带更宽 = 暗河是一条明显的管道而不是细缝，
     * 走向仍严格贴在同一张河网上（见类注释里四个宽度参数的对照）。
     */
    public float getRiverTunnelStrength(final int x, final int y) {
        final float warpedX = x + perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        final float warpedY = y + perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        return -cell.border(warpedX / RIVER_SEPARATION, warpedY / RIVER_SEPARATION,
                TUNNEL_BAND_WIDTH / RIVER_SEPARATION, 1f);
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
    // 火山 / 熔岩洞族（RWG L402-476 / L607-655，另含其依赖 L849-857 / L935-939）
    //
    // 本节是**外科手术式新增**：方法体逐行照抄 RWG `ChunkManagerRealistic`，只做了三类
    // API 适配（都不是算法改动）：
    //   ① `ConfigRWG.landmassOffsetX/Z` → `RwgLayoutConfig.landmassOffsetX/Z`（L424-425 等）；
    //   ② `Support.volcanoIsland` → `RealisticBiomeIslandVolcano.volcanoIsland`（L446 等）；
    //   ③ 地形采样 `biome.rNoise(perlin, cell, x, y, ocean, border, river, continent)` →
    //      `biome.rNoise(terrainWorld, x, y, border, river)`：rtgc 的 `rNoise` 签名去掉了
    //      `ocean` 与 `continent`（地形函数自行向布局查询，见 `TerrainBase:794-797` 与
    //      `TerrainBase.oceanAt`），故 RWG 那两个实参**无从传递**；`border = 1f` 与
    //      `river = river + 1f` 与 RWG 逐字一致。
    //
    // ⚠ 另有两处 RWG 有、rtgc 没有的前置条件：
    //   · RWG 每个查询方法的首行都是 `if (!continental) return Long.MIN_VALUE;` ——
    //     rtgc 的布局**恒为大陆模式**（构造函数必定建立 continents，没有「非大陆」分支），
    //     故该分支在这里不存在；
    //   · {@link #terrainWorld} 可以为 null（见该字段的说明），此时地形采样退化为
    //     RWG 自己的哨兵值（63f / 59f）—— 方向是**拒绝火山**，不会生成错的地形。
    // ==================================================================

    /** RWG L49：火山邻域的河网采样步长（格）。 */
    private static final int VOLCANO_RIVER_SAMPLE_SPACING = 16;
    /** 火山 / 熔岩洞三个缓存的上限。RWG 在 L440 / L459 / L627 各自写作字面量 {@code 256}。 */
    private static final int LANDMARK_CACHE_MAX = 256;

    /**
     * 火山族做**逐列地形采样**所需的 {@link RTGWorld}。
     *
     * <p><b>为什么需要它</b>：RWG 的 {@code ChunkManagerRealistic} 自己持有 {@code perlin} /
     * {@code cell} 与群系列表，所以它的 {@code getVolcanoBaseHeight} / {@code getNoiseAt}
     * 能直接算出地形高度；rtgc 把地形函数搬进了 {@code IRealisticBiome.rNoise(RTGWorld, …)}，
     * 而本布局是按**种子**建立的（{@code RtgLayoutAccess.forSeed}），手里没有 {@code RTGWorld}。
     * 用「可注入字段」而不是「给每个方法加形参」是为了让本节所有方法保持 RWG 的原签名
     * （调用方 {@code RealisticBiomeIslandVolcano.generateMagmaChamber} 已经是两参数的写法）。
     *
     * <p><b>接线位置（在别的文件里，本次改动没有加）</b>：父级在
     * {@code RtgLayoutAccess.forSeed} 建好布局后调用一次 {@link #setTerrainWorld(RTGWorld)}
     * 即可（那里能拿到 {@code rtgWorld}）。**未接线时**火山族不会崩，但会走「拒绝」方向：
     * 高度采样返回 RWG 自己的哨兵值，`canGenerateAtHeight` 与 `getNoiseAt(...) &gt; 63f`
     * 都拿不到真实地形。
     */
    private RTGWorld terrainWorld;

    /**
     * 火山资格缓存（键 = {@code getVolcanoSeedKey}）。对应 RWG 的 {@code volcanoEligibilityMap}
     * （`TLongByteHashMap`，取值 {@code 1} = 合格、{@code 2} = 否）。
     *
     * <p>与 {@link #biomeCache} 同理**必须线程安全**：单人游戏里客户端线程与服务端线程会并发
     * 查询同一个布局。故用 {@code ConcurrentHashMap}，并且读取用 {@code get(...) != null}
     * 代替 RWG 的 {@code containsKey} + {@code get} —— 同一逻辑，但没有「两次调用之间被
     * {@code clear()} 掉」的那个窗口（trove 版同样有该窗口）。
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Byte> volcanoEligibilityMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 熔岩洞资格缓存（键 = {@code getLavaCaveSeedKey}）。对应 RWG 的 {@code lavaCaveEligibilityMap}。 */
    private final java.util.concurrent.ConcurrentHashMap<Long, Byte> lavaCaveEligibilityMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 火山基座高度缓存（键 = {@code getVolcanoSeedKey}）。对应 RWG 的 {@code volcanoBaseHeightMap}。 */
    private final java.util.concurrent.ConcurrentHashMap<Long, Float> volcanoBaseHeightMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 注入地形采样用的 {@link RTGWorld}（见 {@link #terrainWorld}；本类不会自己去取世界）。 */
    public void setTerrainWorld(final RTGWorld rtgWorld) {
        this.terrainWorld = rtgWorld;
    }

    /** 已注入的地形世界；未注入时为 {@code null}（诊断用）。 */
    public RTGWorld terrainWorld() {
        return this.terrainWorld;
    }

    /** RWG L402-406。 */
    public long getVolcanoCoordinates(final int x, final int y) {
        final long coordinates = continents.getVolcanoCoordinates(landmassX(x), landmassZ(y));
        return coordinates != Long.MIN_VALUE && canGenerateVolcanoAt(x, y) ? coordinates : Long.MIN_VALUE;
    }

    /** RWG L408-412。 */
    public long getVolcanoVicinityCoordinates(final int x, final int y) {
        final long coordinates = continents.getVolcanoVicinityCoordinates(landmassX(x), landmassZ(y));
        return coordinates != Long.MIN_VALUE && canGenerateVolcanoAt(x, y) ? coordinates : Long.MIN_VALUE;
    }

    /** RWG L414-418。 */
    public long getLavaCaveCoordinates(final int x, final int z) {
        final long coordinates = continents.getLavaCaveCoordinates(landmassX(x), landmassZ(z));
        return coordinates != Long.MIN_VALUE && canGenerateLavaCaveAt(x, z) ? coordinates : Long.MIN_VALUE;
    }

    /** RWG L420-427：把中心坐标从「大陆场坐标」移回「世界坐标」。 */
    public long getLavaCaveCenterCoordinates(final int x, final int z) {
        final long center = continents.getLavaCaveCenterCoordinates(landmassX(x), landmassZ(z));
        if (center == Long.MIN_VALUE || !canGenerateLavaCaveAt(x, z)) {
            return Long.MIN_VALUE;
        }
        final int centerX = (int) (center >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerZ = (int) center - RwgLayoutConfig.landmassOffsetZ;
        return (long) centerX << 32 | centerZ & 0xffffffffL;
    }

    /** RWG L429-443：熔岩洞只生成在「中心列地表高度 &gt; 63」处。 */
    private boolean canGenerateLavaCaveAt(final int x, final int z) {
        final int shiftedX = landmassX(x);
        final int shiftedZ = landmassZ(z);
        final long key = continents.getLavaCaveSeedKey(shiftedX, shiftedZ);
        if (key == Long.MIN_VALUE) {
            return false;
        }
        final Byte cached = lavaCaveEligibilityMap.get(key);
        if (cached != null) {
            return cached == 1;
        }

        final long center = continents.getLavaCaveCenterCoordinates(shiftedX, shiftedZ);
        final int centerX = (int) (center >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerZ = (int) center - RwgLayoutConfig.landmassOffsetZ;
        final boolean eligible = getNoiseAt(centerX, centerZ) > 63f;
        if (lavaCaveEligibilityMap.size() > LANDMARK_CACHE_MAX) {
            lavaCaveEligibilityMap.clear();
        }
        lavaCaveEligibilityMap.put(key, (byte) (eligible ? 1 : 2));
        return eligible;
    }

    /**
     * RWG L445-462。
     *
     * <p>火山群系实例来自 {@link RealisticBiomeIslandVolcano#volcanoIsland}
     * （RWG 是 {@code Support.volcanoIsland}）。**为 null 时整支不生成** —— 与 RWG 的
     * {@code instanceof} 判定同义。
     */
    private boolean canGenerateVolcanoAt(final int x, final int y) {
        if (!(RealisticBiomeIslandVolcano.volcanoIsland instanceof RealisticBiomeIslandVolcano)) {
            return false;
        }
        final int landmassX = landmassX(x);
        final int landmassZ = landmassZ(y);
        final long key = continents.getVolcanoSeedKey(landmassX, landmassZ);
        if (key == Long.MIN_VALUE) {
            return false;
        }
        final Byte cached = volcanoEligibilityMap.get(key);
        if (cached != null) {
            return cached == 1;
        }

        final long centerCoordinates = continents.getVolcanoCenterCoordinates(landmassX, landmassZ);
        final int centerX = (int) (centerCoordinates >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerZ = (int) centerCoordinates - RwgLayoutConfig.landmassOffsetZ;
        final boolean eligible = !hasRiverNearVolcano(centerX, centerZ)
                && RealisticBiomeIslandVolcano.volcanoIsland.canGenerateAtHeight(getVolcanoBaseHeight(x, y));
        if (volcanoEligibilityMap.size() > LANDMARK_CACHE_MAX) {
            volcanoEligibilityMap.clear();
        }
        volcanoEligibilityMap.put(key, (byte) (eligible ? 1 : 2));
        return eligible;
    }

    /**
     * RWG L464-476：火山邻域（半径 {@code ContinentalNoise.VOLCANO_ISLAND_RADIUS}）里只要有
     * 一列落在河网内（河强 &lt; 0）就拒绝这座火山。
     */
    private boolean hasRiverNearVolcano(final int centerX, final int centerZ) {
        final int radius = (int) Math.ceil(ContinentalNoise.VOLCANO_ISLAND_RADIUS);
        final int radiusSquared = radius * radius;
        for (int offsetX = -radius; offsetX <= radius; offsetX += VOLCANO_RIVER_SAMPLE_SPACING) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ += VOLCANO_RIVER_SAMPLE_SPACING) {
                if (offsetX * offsetX + offsetZ * offsetZ <= radiusSquared
                        && getRawRiverStrength(centerX + offsetX, centerZ + offsetZ) < 0f) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * RWG L935-939：{@link #getRawRiverStrength(float, float)} 的「世界坐标」重载
     * （先按 {@code RIVER_WARP_DIVISOR / RIVER_WARP_STRENGTH} 做河道扭曲）。
     * <p>RWG 把它紧挨着放在 2 参版上面；本节的两个火山方法都需要它。
     */
    private float getRawRiverStrength(final int x, final int y) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        return getRawRiverStrength(pX, pY);
    }

    /**
     * RWG L607-630：火山锥体**下方**（尚未叠加火山覆盖）的地形高度。
     *
     * <p>与 RWG 的两处差异（都属于上面节首声明的 API 适配）：
     * <ul>
     *   <li>RWG 把 {@code getTerrainOceanValue(continent)} 与 {@code continent} 作为
     *       {@code rNoise} 的第 5 / 第 8 个实参传下去；rtgc 的签名没有这两个形参，故这里
     *       **不计算** {@code continent}（RWG 里它只用于那两个实参）；</li>
     *   <li>RWG 在 biome 仍为 null 时会 NPE；rtgc 的 {@code getLandBiomeAt} 在池为空时
     *       会返回 null（见类注释「可能返回 null」），故返回 RWG 自己的哨兵值 {@code 63f}。</li>
     * </ul>
     */
    public float getVolcanoBaseHeight(final int x, final int y) {
        final int landmassX = landmassX(x);
        final int landmassZ = landmassZ(y);
        final long seedCoordinates = continents.getVolcanoSeedKey(landmassX, landmassZ);
        if (seedCoordinates == Long.MIN_VALUE) {
            return 63f;
        }
        final Float cached = volcanoBaseHeightMap.get(seedCoordinates);
        if (cached != null) {
            return cached;
        }
        final long centerCoordinates = continents.getVolcanoCenterCoordinates(landmassX, landmassZ);
        final int centerX = (int) (centerCoordinates >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerY = (int) centerCoordinates - RwgLayoutConfig.landmassOffsetZ;
        final int climate = getClimateAt(centerX, centerY);
        IRealisticBiome biome = continents.isIslandVolcano(landmassX, landmassZ)
                ? selectIslandBiome(1, climate, centerX, centerY)
                : getLandBiomeAt(centerX, centerY, climate);
        if (biome == null) {
            biome = getLandBiomeAt(centerX, centerY, climate);
        }
        if (biome == null || terrainWorld == null) {
            return 63f;
        }
        final float river = getRawRiverStrength(centerX, centerY);
        float height = biome.rNoise(terrainWorld, centerX, centerY, 1f, river + 1f);
        height = calculateRiver(centerX, centerY, river, height);
        if (volcanoBaseHeightMap.size() > LANDMARK_CACHE_MAX) {
            volcanoBaseHeightMap.clear();
        }
        volcanoBaseHeightMap.put(seedCoordinates, height);
        return height;
    }

    /**
     * RWG L632-640：不叠加火山锥体时该列的大岛地形高度。
     * <p>{@code biome == null} 时与 RWG 一样返回 {@code 63f}。
     */
    public float getVolcanoUnderlyingHeight(final int x, final int y) {
        final IRealisticBiome biome = getVolcanoUnderlyingBiome(x, y);
        if (biome == null || terrainWorld == null) {
            return 63f;
        }
        final float river = getRawRiverStrength(x, y);
        final float height = biome.rNoise(terrainWorld, x, y, 1f, river + 1f);
        return calculateRiver(x, y, river, height);
    }

    /**
     * RWG L642-655。
     * <p>注意 RWG 的**非岛屿**分支用的是入参 {@code (x, y)}（不是火山中心坐标）—— 照抄。
     */
    public IRealisticBiome getVolcanoUnderlyingBiome(final int x, final int y) {
        final int shiftedX = landmassX(x);
        final int shiftedZ = landmassZ(y);
        final long centerCoordinates = continents.getVolcanoCenterCoordinates(shiftedX, shiftedZ);
        if (centerCoordinates == Long.MIN_VALUE) {
            return null;
        }
        final int centerX = (int) (centerCoordinates >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerZ = (int) centerCoordinates - RwgLayoutConfig.landmassOffsetZ;
        final int climate = getClimateAt(centerX, centerZ);
        IRealisticBiome biome = continents.isIslandVolcano(shiftedX, shiftedZ)
                ? selectIslandBiome(1, climate, centerX, centerZ)
                : getLandBiomeAt(x, y, getClimateAt(x, y));
        if (biome == null) {
            biome = getLandBiomeAt(centerX, centerZ, climate);
        }
        return biome;
    }

    /**
     * RWG L849-857：该列的**地表高度**（河心处直接给河床 59f）。
     *
     * <p>RWG 用它给熔岩洞做门控（{@code > 63f}，见 {@link #canGenerateLavaCaveAt}）。
     * rtgc **没有这个方法**（逐列高度由 {@code ChunkGeneratorRTG.getNewerNoise} 在区块级别
     * 算出，布局拿不到；{@code RtgTerrainQuery} 也刻意没有移植 RWG 的
     * {@code areBiomesViable} 高度判定）。故这里照抄 RWG 的实现，只把它的两个外部依赖换成
     * rtgc 的等价物：{@code perlin} / {@code cell} / 群系列表 →
     * {@code biome.rNoise(terrainWorld, …)}。
     *
     * <p>RWG 在此取 {@code getTerrainOceanValue(x, y)} 作为 {@code rNoise} 的 {@code ocean}
     * 形参；rtgc 的签名没有它（地形函数自行向布局查询），故不计算。
     */
    public float getNoiseAt(final int x, final int y) {
        final float river = getRiverStrength(x, y) + 1f;
        if (river < 0.5f) {
            return 59f;
        }
        final IRealisticBiome biome = getBiomeDataAt(x, y);
        if (biome == null || terrainWorld == null) {
            return 59f;
        }
        return biome.rNoise(terrainWorld, x, y, 1f, river);
    }

    // ==================================================================
    // 火山地标的两个查询（RWG L859-861 / L945-963，逐行对应）
    //
    // 这两个方法的**唯一调用方**是 `RealisticBiomeBOPTropicalIsland.rMapGen`
    // （RWG `RealisticBiomeIslandTropical:56-74`）；它们此前没有对应的 rtgc 方法，
    // 是本次按 RWG 原文补上的。除下方各自注明的 API 适配外，判定逻辑逐字相同。
    // ==================================================================

    /**
     * RWG {@code ChunkManagerRealistic.borderNoise}（L112 声明 / L117 分配 {@code new float[256]}）：
     * {@link #isBorderlessAt} 的**编号桶**（即投影到编号轴上的直方图）。
     *
     * <p><b>为什么不是 {@code new float[256]}</b>：RWG 的桶按「现实主义编号」索引，而它的编号
     * 空间恰好是 {@code 0..255}。rtgc 的现实主义编号空间是
     * {@link RtgRealisticIndex}（MC 编号 + 256 个合成槽位，见其 {@code biomeIdBound()}），
     * 宽度由注册表与合成群系共同决定，故这里**按需分配**（分配推迟到第一次查询时，
     * 那时编号空间已冻结）。语义完全一致：只有本方法会写这个数组，且每次扫描结束时全部清零。
     *
     * <p><b>线程约束（与 RWG 相同）</b>：这是 RWG 那种**单线程临时字段**。本类的并发读者
     * （单人游戏的客户端线程）只走 {@link #getBiomeDataAt} 这条纯查询路径，
     * 而 {@code isBorderlessAt} 只从区块生成（服务端线程）的 map-gen 路径进入，
     * 因此与 RWG 一样不需要加锁。
     */
    private float[] borderNoise = new float[0];

    /**
     * RWG {@code ChunkManagerRealistic.getNoiseWithRiverOceanAt}（L859-861）：
     * 该列的现实主义群系在给定 {@code river} 下的地形高度。
     *
     * <p>RWG 原文：{@code return getBiomeDataAt(x, y).rNoise(perlin, cell, x, y, ocean, 1f, river);}
     *
     * <p><b>两处 API 适配</b>：
     * <ul>
     *   <li>{@code perlin/cell} 与 {@code ocean/continent} 形参：rtgc 的
     *       {@link IRealisticBiome#rNoise} 只收 {@code (rtgWorld, x, y, border, river)} ——
     *       地形函数自己向布局查询海洋/大陆值（见 {@code TerrainBase} 的 {@code oceanAt}），
     *       故 {@code ocean} 在 rtgc 侧**无从传递**。本方法保留该形参只为与 RWG 的调用点同形
     *       （调用方仍照抄地传 {@code cmr.getTerrainOceanValue(…)}），**方法体内不使用它**。
     *       {@code border = 1f} 与 RWG 逐字一致。</li>
     *   <li>{@code getBiomeDataAt} 返回 null 时：RWG 会 NPE，rtgc 返回 RWG 自己的哨兵值
     *       {@code 59f}（与同节的 {@link #getNoiseAt} 同口径）。方向是**拒绝火山**
     *       （门控是 {@code > 110f}），不会生成错的地形。</li>
     * </ul>
     */
    public float getNoiseWithRiverOceanAt(final int x, final int y, final float river, final float ocean) {
        final IRealisticBiome biome = getBiomeDataAt(x, y);
        if (biome == null || terrainWorld == null) {
            return 59f;
        }
        return biome.rNoise(terrainWorld, x, y, 1f, river);
    }

    /**
     * RWG {@code ChunkManagerRealistic.isBorderlessAt}（L945-963）的逐行照抄：
     * 在 (x,y) 周围按 **16 格** 步长取 5×5 = 25 个采样点，若**同一个现实主义群系占满全部 25 点**
     * 就返回 true。命名里的「borderless」即「不在群系边界上」——
     * <b>返回 true 表示这一带是同一种群系，火山锥可以整片跨区块生成而不会骑在群系交界上</b>。
     *
     * <pre>
     * RWG：                                                   rtgc：
     *   for (bx = -2; bx &lt;= 2; bx++)                           逐字
     *     for (by = -2; by &lt;= 2; by++)                         逐字
     *       borderNoise[getBiomeDataAt(x + bx*16, y + by*16)    索引表达式换成
     *                    .biomeID] += 0.04f;                     RtgRealisticIndex.idFor(群系)
     *   by = 0;                                                逐字
     *   for (bx = 0; bx &lt; 256; bx++) { if (borderNoise[bx] &gt; 0.98f) by = 1;
     *                                   borderNoise[bx] = 0; } 上界 256 → borderNoise.length
     *   return by == 1 ? true : false;                         逐字
     * </pre>
     *
     * <p>阈值口径证明等价性：25 × 0.04f = 1.0f，而 24 × 0.04f = 0.96f &lt; 0.98f，
     * 故 {@code > 0.98f} 当且仅当**某一个桶恰好累加到 25 次**（同一群系 25 个采样全中）。
     *
     * <p><b>无法逐字照抄的地方（本方法唯一的两处改写，都不改语义）</b>：
     * <ol>
     *   <li>RWG 直接用 {@code getBiomeDataAt(…).biomeID} 当数组下标。rtgc 的
     *       {@link IRealisticBiome} **没有** {@code biomeID} 字段，但有一个语义完全对应的
     *       「现实主义编号」取值器 —— {@link RtgRealisticIndex#idFor}
     *       （普通群系 = 其 MC 编号，合成群系 = 独立槽位；RWG 的
     *       {@code RealisticBiomeBase.getBiome(k)} 就是它的等价物）。故索引表达式换成
     *       {@code idFor(…)}，桶宽随之从 256 改为 {@code borderNoise.length}
     *       （见 {@link #borderNoise}），**不是**退化成对象身份计数。
     *       <br>⚠ 不能用 {@link IRealisticBiome#baseBiomeId()}：多个现实主义群系共用同一个
     *       MC 群系（山地链与它的备份群系就是如此），用 MC 编号会把它们折叠成一个桶，
     *       于是"边界上有两种不同的现实主义群系"会被误判成"无边界"。</li>
     *   <li>{@code getBiomeDataAt} 可能返回 null（见其说明：核心池未注册时），此时
     *       {@code idFor} 返回 {@code -1}。RWG 会直接 NPE；rtgc **跳过该点**
     *       （不计入任何桶）——结果是更难满足 25/25，即**拒绝火山**，与
     *       {@link #getNoiseWithRiverOceanAt} 的 fail-soft 方向一致。</li>
     * </ol>
     *
     * @return true = 这 25 个采样点全属同一个现实主义群系（不在群系边界上）
     */
    public boolean isBorderlessAt(final int x, final int y) {
        int bx, by;

        if (borderNoise.length < RtgRealisticIndex.biomeIdBound()) {
            borderNoise = new float[RtgRealisticIndex.biomeIdBound()];
        }

        for (bx = -2; bx <= 2; bx++) {
            for (by = -2; by <= 2; by++) {
                final int id = RtgRealisticIndex.idFor(getBiomeDataAt(x + bx * 16, y + by * 16));
                if (id >= 0) {
                    if (id >= borderNoise.length) {
                        borderNoise = java.util.Arrays.copyOf(borderNoise, id + 1);
                    }
                    borderNoise[id] += 0.04f;
                }
            }
        }

        by = 0;
        for (bx = 0; bx < borderNoise.length; bx++) {
            if (borderNoise[bx] > 0.98f) {
                by = 1;
            }
            borderNoise[bx] = 0;
        }

        return by == 1 ? true : false;
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
