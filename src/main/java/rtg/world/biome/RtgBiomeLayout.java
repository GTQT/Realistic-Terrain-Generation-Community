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
 * RWG {@code ChunkManagerRealistic} 群系选择逻辑的移植：回答「(x,z) 归哪个现实主义群系」，
 * 不产生地形（地形由 {@link IRealisticBiome#rNoise} 负责）。
 *
 * <p>列表顺序即权重：{@code selectBiome} 是 {@code list.get((int)(selector * length))}，
 * 增删条目会改变其余每个条目的带宽。
 *
 * @since 1.0.10
 */
public final class RtgBiomeLayout {

    // ==================================================================
    // 常量
    // ==================================================================

    private static final float SHALLOW_OCEAN_WIDTH = 300f;
    public static final float DIAG_SHALLOW_OCEAN_WIDTH = SHALLOW_OCEAN_WIDTH;
    private static final float CLIMATE_WARP_SCALE_MULTIPLIER = .4f;
    private static final float CLIMATE_WARP_STRENGTH_MULTIPLIER = .8f;
    private static final float BIOME_WARP_SCALE_MULTIPLIER = .4f;
    private static final float BIOME_WARP_STRENGTH_MULTIPLIER = .175f;
    private static final float SNOW_CLIMATE_LIMIT = .16875f;
    private static final float COLD_CLIMATE_LIMIT = .545f;
    private static final float HOT_CLIMATE_LIMIT = .78f;
    private static final double CLIMATE_BORDER_DISTANCE_DIFFERENCE = 288D;
    public static final double DIAG_CLIMATE_BORDER_DISTANCE = CLIMATE_BORDER_DISTANCE_DIFFERENCE;
    private static final float LITTORAL_WIDTH = 432f;
    public static final float DIAG_LITTORAL_WIDTH = LITTORAL_WIDTH;
    private static final double SMALL_BIOME_RADIUS = 75D;
    public static final double DIAG_SMALL_BIOME_RADIUS = SMALL_BIOME_RADIUS;
    private static final float COAST_WIDTH = 24f;
    public static final float DIAG_COAST_WIDTH = COAST_WIDTH;
    private static final double CLIMATE_SHIFT = 4000D;
    private static final int BIOME_CACHE_MAX = 4096;
    private static final float RIVER_BED = 59f;
    /**
     * 地下河隧道带的宽度标尺（RWG 是 {@code 9}，即 {@code 9/1250}）。**rtgc 改过两次，最终与河道同宽**：
     *
     * <ol>
     *   <li>RWG 的 {@code 9} 在 rtgc 的地形下"太稀"（覆盖率 2.93%），一度放宽到 {@code 25}（8.1%）；</li>
     *   <li>但 {@code 25/1250 = 0.02} 只有**河道雕刻带** {@code 50/1300 ≈ 0.0385} 的 <b>1/1.92 宽</b> ⇒
     *       地下河从山口露出水面时，水宽从河道的 ~48 格**突然收到**隧道的 ~25 格
     *       —— 用户实机看到的"地下河与普通河流过渡生硬，因为河流的宽度不一致，出现宽度断层"。</li>
     * </ol>
     *
     * 现在取 {@code 50 × 1250 / 1300}：把河道那条带在 {@code RIVER_SEPARATION = 1250} 的量纲下
     * **等值换算**过来 ⇒ 两条带**同宽**，地下河填满同一段河谷，接壤处不再有台阶。
     * 频率不再靠"把带收窄"来压，而是靠门控收紧（见 {@code UndergroundRiver.HOST_MIN} /
     * {@code MIN_DRY_HEIGHT} 与 {@code ChunkGeneratorRTG.MOUNTAIN_CHAIN_INFLUENCE_RADIUS}）。
     */
    private static final double TUNNEL_BAND_WIDTH = 50D * 1250D / 1300D;
    private static final float RIVER_WARP_DIVISOR = 240f;
    private static final float RIVER_WARP_STRENGTH = 220f;
    private static final double RIVER_SEPARATION = 1250D;
    private static final int VOLCANO_RIVER_SAMPLE_SPACING = 16;
    /** 三个地标缓存的上限。 */
    private static final int LANDMARK_CACHE_MAX = 256;

    private final PerlinNoise perlin;
    private final PerlinNoise climateWarp;
    /** 必须保持 false：返回哈希而非距离。 */
    private final RwgCellNoise biomecell;
    /** 必须 setUseDistance(true)：cell.border()/junction() 要求线性距离语义。 */
    private final RwgCellNoise cell;
    private final PoissonPointNoise smallBiomePoints;
    private final ContinentalNoise continents;
    private final float climateWidth;
    private final float biomeWidth;

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

    private final IRealisticBiome[] oceanDeep = new IRealisticBiome[4];
    private final IRealisticBiome[] oceanShallow = new IRealisticBiome[4];

    /**
     * 逐列缓存（键 = 世界坐标打包，见 {@link #cacheKey}）。
     * <p>必须线程安全：单人游戏客户端线程与服务端线程会并发调用。
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, IRealisticBiome> biomeCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Byte> volcanoEligibilityMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Byte> lavaCaveEligibilityMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Float> volcanoBaseHeightMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    private IRealisticBiome oceanShallowKelp;
    private IRealisticBiome oceanShallowCoral;
    private IRealisticBiome coastIce;
    private IRealisticBiome coastDunes;

    /**
     * 火山族逐列地形采样所需的 {@link RTGWorld}。父级应在
     * {@code RtgLayoutAccess.forSeed} 建好布局后调用一次 {@link #setTerrainWorld}。
     * 未接线时火山族走拒绝方向（采样返回哨兵值），不会生成错的地形。
     */
    private RTGWorld terrainWorld;

    /**
     * {@link #isBorderlessAt} 的编号桶（按「现实主义编号」索引）。
     * 因编号空间宽度由注册表决定，故按需分配；语义与 RWG 的 {@code new float[256]} 一致。
     * <p>线程约束同 RWG：单线程临时字段，仅服务端 map-gen 路径进入，无需加锁。
     */
    private float[] borderNoise = new float[0];

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
        this.biomecell = new RwgCellNoise(seed, (short) 0);
        this.cell = new RwgCellNoise(seed, (short) 0);
        this.cell.setUseDistance(true);
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

    private static void mirrorInto(final List<IRealisticBiome> source, final List<IRealisticBiome> target,
                                   final java.util.function.UnaryOperator<IRealisticBiome> wrap) {
        for (final IRealisticBiome b : source) {
            final IRealisticBiome wrapped = wrap == null ? b : wrap.apply(b);
            // wrap 可能返回 null —— 跳过而不是塞 null 进池
            if (wrapped != null) {
                target.add(wrapped);
            }
        }
    }

    /** 从 {@code from} 开始环形查找第一个非 null 成员（池成员可能为 null）。 */
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

    /** {@code clamp(1 + continent / 100, 0, 2)}。 */
    public static float getTerrainOceanValue(final float continent) {
        return Math.max(0f, Math.min(2f, 1f + continent / 100f));
    }

    /** 逐列缓存键。 */
    static long cacheKey(final int x, final int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    static int landmassX(final int x) {
        return x + RwgLayoutConfig.landmassOffsetX;
    }

    static int landmassZ(final int z) {
        return z + RwgLayoutConfig.landmassOffsetZ;
    }

    static int biomeX(final int x) {
        return x + RwgLayoutConfig.biomeOffsetX;
    }

    static int biomeZ(final int z) {
        return z + RwgLayoutConfig.biomeOffsetZ;
    }

    /**
     * 注册一个群系。
     * <p>顺序即权重，必须稳定。
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
     * 把「极端气候边界」池填成对应方向普通边界池的镜像：
     * <pre>
     * snow.veryHotBorder  ← snow.hotBorder
     * cold.veryHotBorder  ← cold.hotBorder
     * hot.veryColdBorder  ← hot.coldBorder
     * wet.veryColdBorder  ← wet.coldBorder
     * </pre>
     * {@code wrap} 为 null 表示原样放入（未移植山地链时的退化行为）。
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

    public void setOceanBiome(final Climate climate, final boolean deep, final IRealisticBiome biome) {
        (deep ? oceanDeep : oceanShallow)[climate.ordinal()] = biome;
    }

    public void setOceanShallowKelp(final IRealisticBiome biome) {
        this.oceanShallowKelp = biome;
    }

    public void setOceanShallowCoral(final IRealisticBiome biome) {
        this.oceanShallowCoral = biome;
    }

    public IRealisticBiome oceanShallowKelp() {
        return this.oceanShallowKelp;
    }

    public IRealisticBiome oceanShallowCoral() {
        return this.oceanShallowCoral;
    }

    public void setCoastIce(final IRealisticBiome biome) {
        this.coastIce = biome;
    }

    public void setCoastDunes(final IRealisticBiome biome) {
        this.coastDunes = biome;
    }

    public IRealisticBiome coastIce() {
        return this.coastIce;
    }

    public IRealisticBiome coastDunes() {
        return this.coastDunes;
    }

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

    /** 返回 1..4。 */
    public int getClimateAt(final int x, final int y) {
        return getClimateFromValue(getClimateValue(x, y));
    }

    public int getClimateFromValue(final float climate) {
        return climate < SNOW_CLIMATE_LIMIT ? 1
                : climate < COLD_CLIMATE_LIMIT ? 2
                : climate < HOT_CLIMATE_LIMIT ? 3 : 4;
    }

    public float getClimateValue(final int x, final int y) {
        final double[] warped = new double[2];
        warpClimateCoordinates(biomeX(x), biomeZ(y), warped);
        return (biomecell.noise((warped[0] + CLIMATE_SHIFT) / climateWidth, warped[1] / climateWidth, 1D) * .5f) + .5f;
    }

    private void warpClimateCoordinates(final int x, final int y, final double[] output) {
        final float scale = climateWidth * CLIMATE_WARP_SCALE_MULTIPLIER;
        final float strength = climateWidth * CLIMATE_WARP_STRENGTH_MULTIPLIER;
        output[0] = x + climateWarp.noise2(x / scale, y / scale) * strength;
        output[1] = y + climateWarp.noise2((x + 1731f) / scale, (y - 2459f) / scale) * strength;
    }

    private IRealisticBiome selectBiome(final List<IRealisticBiome> biomes, final int length,
                                        final int x, final int y) {
        if (length <= 0) {
            return null;
        }
        float value = sampleBiomeSelector(x, y);
        value = value < 0f ? 0f : value >= .9999999f ? .9999999f : value;
        return firstNonNull(biomes, (int) (value * length));
    }

    private float sampleBiomeSelector(final int x, final int y) {
        final float scale = biomeWidth * BIOME_WARP_SCALE_MULTIPLIER;
        final float strength = biomeWidth * BIOME_WARP_STRENGTH_MULTIPLIER;
        final float warpedX = x + climateWarp.noise2((x - 8191f) / scale, (y + 3137f) / scale) * strength;
        final float warpedY = y + climateWarp.noise2((x + 5171f) / scale, (y - 6971f) / scale) * strength;
        return (biomecell.noise(warpedX / biomeWidth, warpedY / biomeWidth, 1D) * .5f) + .5f;
    }

    /** {@code shared} 在前、{@code directional} 在后，按同一选择器取。 */
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
     * 布局的最后兜底：任何拿不到群系的列都得到一个气候正确的布局成员。
     * <p>调用方把 {@code null} 理解成「回落到原版 GenLayer」，那会污染 F3、地表与装饰
     * （见 {@code docs/rwg-port-gaps.md} §18 / §27）。
     */
    public IRealisticBiome lastResortAt(final int x, final int z) {
        final int climateIndex = getClimateAt(x, z) - 1;
        final IRealisticBiome coreMember = firstNonNull(core[climateIndex], 0);
        if (coreMember != null) {
            return coreMember;
        }
        for (final IRealisticBiome[] pool : new IRealisticBiome[][]{oceanShallow, oceanDeep}) {
            for (final IRealisticBiome candidate : pool) {
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return RTGAPI.getRTGBiome(Biomes.PLAINS);
    }

    public IRealisticBiome getLandBiomeAt(final int x, final int z) {
        return getLandBiomeAt(x, z, getClimateAt(x, z));
    }

    /** 布局的核心：小型群系 → 气候边界 → 极端边界 → 核心池。 */
    public IRealisticBiome getLandBiomeAt(final int par1In, final int par2In, final int climate) {

        final int par1 = biomeX(par1In);
        final int par2 = biomeZ(par2In);
        final int climateIndex = climate - 1;

        // ---- 小型群系：优先于一切，选择点用泊松特征点本身（整片圆盘内恒定）----
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
                // 极端池为空时不返回，继续走下面的普通边界选择
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

    /** 岛屿群系在整个岛屿上恒定（选择点用岛屿种子坐标）。 */
    public IRealisticBiome selectIslandBiome(final int tier, final int climate,
                                             final int seedX, final int seedY) {
        final List<IRealisticBiome> sized = tier == 0
                ? smallIsland[climate - 1]
                : largeIsland[climate - 1];
        final List<IRealisticBiome> general = island[climate - 1];
        if (general.isEmpty() && sized.isEmpty()) {
            // C3 适配：rtgc 没有 RWG 的岛屿成员，退回本气候核心池，避免回落到原版 GenLayer
            final List<IRealisticBiome> fallback = core[climate - 1];
            return fallback.isEmpty()
                    ? null
                    : selectBiome(fallback, fallback.size(), biomeX(seedX), biomeZ(seedY));
        }
        return selectCombinedBiome(general, sized, biomeX(seedX), biomeZ(seedY));
    }

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

    /** 返回「离岸格数」。 */
    public float getContinentValue(final int x, final int y) {
        return continents.getValue(landmassX(x), landmassZ(y));
    }

    /** 河强 {@code [-1, 0]}，{@code -1} 在河心。 */
    public float getRiverStrength(final int x, final int y) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        return getRiverStrength(x, y, pX, pY);
    }

    /**
     * 带出扭曲后的坐标，供 {@link #calculateRiver(int, int, float, float, float[])} 复用。
     * <p>{@code sample} 需长度 ≥ 4：{@code [0]=pX, [1]=pY, [2]=雕刻权重（NaN 表示未算）, [3]=河床高度}。
     */
    public float getRiverStrength(final int x, final int y, final float[] sample) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        sample[0] = pX;
        sample[1] = pY;
        sample[2] = Float.NaN;
        return getRiverStrength(x, y, pX, pY);
    }

    /** 地下河隧道强度 {@code [0,1]}。带宽是 rtgc 调过的（见 {@link #TUNNEL_BAND_WIDTH}）。 */
    public float getRiverTunnelStrength(final int x, final int y) {
        final float warpedX = x + perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        final float warpedY = y + perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        return -cell.border(warpedX / RIVER_SEPARATION, warpedY / RIVER_SEPARATION,
                TUNNEL_BAND_WIDTH / RIVER_SEPARATION, 1f);
    }

    /** 交汇洞厅强度 {@code [0,1]}，半径 {@code 60/1250} 单元。 */
    public float getRiverJunctionStrength(final int x, final int y) {
        final float warpedX = x + perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        final float warpedY = y + perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH;
        return -cell.junction(warpedX / RIVER_SEPARATION, warpedY / RIVER_SEPARATION, 60D / RIVER_SEPARATION, 1f);
    }

    /**
     * 原始河强 + 火山邻域抑制。
     * <p>rtgc 已关闭地标，抑制分支永不进入，但代码保留以便将来启用火山时行为正确。
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

    /** 河网 = {@code cell} 的单元边界；宽度 {@code 50/300}。 */
    private float getRawRiverStrength(final float pX, final float pY) {
        return cell.border(pX / RIVER_SEPARATION, pY / RIVER_SEPARATION, 50D / 300D, 1f);
    }

    /** 把高度线性混合到噪声河床 {@code 59 ± 3.5}；雕刻宽度 {@code 50/1300}。 */
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

    /** 同上，复用 {@code sample} 里已算好的扭曲坐标与河床高度。 */
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

    /** 注入地形采样用的 {@link RTGWorld}（见 {@link #terrainWorld}）。 */
    public void setTerrainWorld(final RTGWorld rtgWorld) {
        this.terrainWorld = rtgWorld;
    }

    public RTGWorld terrainWorld() {
        return this.terrainWorld;
    }

    public long getVolcanoCoordinates(final int x, final int y) {
        final long coordinates = continents.getVolcanoCoordinates(landmassX(x), landmassZ(y));
        return coordinates != Long.MIN_VALUE && canGenerateVolcanoAt(x, y) ? coordinates : Long.MIN_VALUE;
    }

    public long getVolcanoVicinityCoordinates(final int x, final int y) {
        final long coordinates = continents.getVolcanoVicinityCoordinates(landmassX(x), landmassZ(y));
        return coordinates != Long.MIN_VALUE && canGenerateVolcanoAt(x, y) ? coordinates : Long.MIN_VALUE;
    }

    public long getLavaCaveCoordinates(final int x, final int z) {
        final long coordinates = continents.getLavaCaveCoordinates(landmassX(x), landmassZ(z));
        return coordinates != Long.MIN_VALUE && canGenerateLavaCaveAt(x, z) ? coordinates : Long.MIN_VALUE;
    }

    /** 把中心坐标从「大陆场坐标」移回「世界坐标」。 */
    public long getLavaCaveCenterCoordinates(final int x, final int z) {
        final long center = continents.getLavaCaveCenterCoordinates(landmassX(x), landmassZ(z));
        if (center == Long.MIN_VALUE || !canGenerateLavaCaveAt(x, z)) {
            return Long.MIN_VALUE;
        }
        final int centerX = (int) (center >> 32) - RwgLayoutConfig.landmassOffsetX;
        final int centerZ = (int) center - RwgLayoutConfig.landmassOffsetZ;
        return (long) centerX << 32 | centerZ & 0xffffffffL;
    }

    /** 熔岩洞只生成在「中心列地表高度 > 63」处。 */
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
     * {@link RealisticBiomeIslandVolcano#volcanoIsland} 为 null 时整支不生成。
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
     * 火山邻域（半径 {@code VOLCANO_ISLAND_RADIUS}）里只要有一列落在河网内（河强 < 0）
     * 就拒绝这座火山。
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

    /** {@link #getRawRiverStrength(float, float)} 的「世界坐标」重载。 */
    private float getRawRiverStrength(final int x, final int y) {
        final float pX = x + (perlin.noise1(y / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        final float pY = y + (perlin.noise1(x / RIVER_WARP_DIVISOR) * RIVER_WARP_STRENGTH);
        return getRawRiverStrength(pX, pY);
    }

    /**
     * 火山锥体下方（尚未叠加火山覆盖）的地形高度。
     * <p>biome 为 null 或 terrainWorld 未注入时返回哨兵值 {@code 63f}（拒绝火山方向）。
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

    /** 不叠加火山锥体时该列的大岛地形高度。 */
    public float getVolcanoUnderlyingHeight(final int x, final int y) {
        final IRealisticBiome biome = getVolcanoUnderlyingBiome(x, y);
        if (biome == null || terrainWorld == null) {
            return 63f;
        }
        final float river = getRawRiverStrength(x, y);
        final float height = biome.rNoise(terrainWorld, x, y, 1f, river + 1f);
        return calculateRiver(x, y, river, height);
    }

    /** 注意非岛屿分支用的是入参 {@code (x, y)}（不是火山中心坐标）。 */
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

    /** 该列的地表高度（河心处直接给河床 59f）。 */
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

    /**
     * 该列现实主义群系在给定 {@code river} 下的地形高度。
     * <p>{@code ocean} 形参仅为与调用点同形（rtgc 的 rNoise 签名不需要它），方法体内不使用。
     */
    public float getNoiseWithRiverOceanAt(final int x, final int y, final float river, final float ocean) {
        final IRealisticBiome biome = getBiomeDataAt(x, y);
        if (biome == null || terrainWorld == null) {
            return 59f;
        }
        return biome.rNoise(terrainWorld, x, y, 1f, river);
    }

    /**
     * 在 (x,y) 周围按 16 格步长取 5×5 = 25 个采样点，若同一个现实主义群系占满全部 25 点
     * 就返回 true（火山锥可以整片跨区块生成而不会骑在群系交界上）。
     *
     * <p>索引表达式用 {@link RtgRealisticIndex#idFor}（不能只用 {@code baseBiomeId()}，
     * 那会把山地链与其备份群系折叠成一个桶）；{@code getBiomeDataAt} 返回 null 时跳过该点
     * （结果是更难满足 25/25，方向仍是拒绝火山）。
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

        return by == 1;
    }

    /**
     * 返回该列的现实主义群系。
     * <p>核心池未注册（空）时返回 null 且不写入缓存，由调用方兜底 —— 注册顺序稍晚也能自愈。
     * <p>决策顺序：海洋 → 火山（rtgc 恒跳过）→ 岛屿 → 陆地 → 滨海 → 海岸。
     */
    public IRealisticBiome getBiomeDataAt(final int x, final int z) {

        final long key = cacheKey(x, z);
        final IRealisticBiome cached = biomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        final IRealisticBiome output = computeBiomeDataAt(x, z);
        if (output == null) {
            return null;
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

    /** 诊断：该列的邻点气候。 */
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

    public boolean diagHasSmallBiomes(final int climate) {
        return !small[climate - 1].isEmpty();
    }

    public boolean diagHasLittoral(final int climate) {
        return !littoral[climate - 1].isEmpty();
    }

    public int poolSize(final Climate climate, final Placement placement) {
        return pool(climate, placement).size();
    }

    public List<IRealisticBiome> pool(final Climate climate, final Placement placement) {
        final int i = climate.ordinal();
        switch (placement) {
            case CORE:
                return core[i];
            case BORDER:
                return border[i];
            case COLD_BORDER:
                return coldBorder[i];
            case HOT_BORDER:
                return hotBorder[i];
            case VERY_COLD_BORDER:
                return veryColdBorder[i];
            case VERY_HOT_BORDER:
                return veryHotBorder[i];
            case LITTORAL:
                return littoral[i];
            case SMALL:
                return small[i];
            case ISLAND:
                return island[i];
            case SMALL_ISLAND:
                return smallIsland[i];
            case LARGE_ISLAND:
                return largeIsland[i];
            default:
                throw new IllegalStateException("unhandled placement " + placement);
        }
    }

    public IRealisticBiome oceanSlot(final Climate climate, final boolean deep) {
        return (deep ? oceanDeep : oceanShallow)[climate.ordinal()];
    }

    public ContinentalNoise continents() {
        return continents;
    }

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
}