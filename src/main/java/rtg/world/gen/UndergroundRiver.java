package rtg.world.gen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;


/**
 * 地下河：山体内部沿河网开凿的水道与交汇洞厅。
 * 几何照抄 RWG {@code ChunkGeneratorRealistic.carveMountainChainRivers:581-626}。
 *
 * <p>判定链（逐列，任一关不过即跳过）：
 * <pre>
 *   ⓪ {@code landscape.river > 0}                     在河网带内（性能前置；隧道带 ⊂ 河网带，故等价）
 *   ① {@code tunnel > 0 || junction > 0}              在隧道带 / 交汇盘内
 *   ② {@code mountainWeight(...) > 0}                 上方有山（见下）
 *   ③ 断面：洞心恒 {@value #CENTER_Y}，上下按山体权重渐隐；洞厅另加扩张；两条守卫
 *   ④ {@code y <= }{@value #CENTER_Y}{@code ? 水 : 空气}；洞厅够强时向上开天窗
 * </pre>
 *
 * <p>② 判"上方有没有山、够不够厚"，**全部是场判据（没有掷骰子）**。变量取"满断面洞顶
 * （{@code CENTER_Y + ROOF_RISE}）之上还剩几格岩层"：
 * <ul>
 *   <li><b>厚岩层</b>（山体内部，≥ {@value #MIN_ROOF_STRONG} 格）：总是开凿；</li>
 *   <li><b>薄岩层</b>（山坳、两山交接处、链边缘，≥ {@value #MIN_ROOF_WEAK} 格）：还要看
 *       <b>这段河道实不实在</b>（{@code max(tunnel, junction) ≥ }{@value #WEAK_MIN_RIVER_STRENGTH}）
 *       —— 只在河网最像河的那一段开，山坳的通口因此天然变窄、变短，交给地表河时不会突兀。</li>
 * </ul>
 * <b>RWG 没有这一层</b>：它只有 {@code mountainChainRiverHost > 0.10} 一条硬阈值（够高必开、
 * 不够必不开），"普通山"这条是 rtgc 按用户要求加的，两档的来历见
 * {@code docs/rwg-port-gaps.md} §0.5.2。
 *
 * <p>干高度取 3×3 采样点的**均值**而非最大值：山坳本身不高，取最大值会把 ±8 格外的峰采进来，
 * 整片鞍部被误判成"山"（这正是"两山交接处过多"的根因）。
 *
 * <p>⚠ 调用位置必须在 {@code replaceBiomeBlocks} **之后**：地表替换按 {@code depth} 计数涂刷，
 * 先开凿会让隧道里的空气重置 {@code depth}，把隧道底刷成草/沙。
 */
public final class UndergroundRiver {

    /** 洞心高度，同时也是水面高度（RWG 写死 62）。水面平在它上面 ⇒ 洞里与外面同一个平面，划船进得去。 */
    public static final int CENTER_Y = 62;

    // ---- RWG 原值（照抄） ----
    /** 洞顶起伏（RWG 11 → rtgc 8：就是"水线以上被掏空多高"，用户要求"侧切收一点点"）。 */
    private static final int ROOF_RISE = 8;
    /** 洞底下探（RWG 4：河床只在水面下 4–5 格，不会挖成深沟）。 */
    private static final int FLOOR_DROP = 4;
    /** 洞厅顶与地表之间保留的岩层（RWG 10；只有洞厅扩张用它，且随后会被 `max(ceiling, tunnelCeiling)` 顶回去）。 */
    private static final int ROOF_CLEARANCE = 10;
    /** 洞厅/天窗的"地表够不够高"：基准与 smoothstep 宽度（RWG 76 / 24，用的是**已雕刻地表**）。 */
    private static final float MIN_SURFACE = 76f;
    private static final float OVERHEAD_WIDTH = 24f;
    /** 山体权重的 smoothstep 宽度（RWG 0.40）。 */
    private static final float HOST_WIDTH = 0.40f;
    /** 洞厅基准与上下扩张量（RWG 63 / 23 / 42）。 */
    private static final int CHAMBER_BASE_Y = 63;
    private static final int CHAMBER_FLOOR_SPREAD = 23;
    private static final int CHAMBER_ROOF_SPREAD = 42;
    /** 天窗门槛与收口坡度（RWG 0.70 / 0.15）。 */
    private static final float SKYLIGHT_MIN_CHAMBER = 0.70f;
    private static final float SKYLIGHT_TAPER = 0.15f;

    // ---- rtgc 调整（RWG 原值 → 现值，依据见 docs/rwg-port-gaps.md §0.5.2） ----
    /** 链宿主下限：{@code 0.10 → 0.28}（同时是山体权重的渐隐起点）。 */
    private static final float HOST_MIN = 0.28f;
    /** 薄岩层档的链宿主下限（= RWG 原值）。 */
    private static final float HOST_MIN_WEAK = 0.10f;
    /** 厚岩层档：满断面洞顶之上至少留几格岩层（等价于原先的"干高度均值 ≥ 82"，有效约 19 格）。 */
    private static final float MIN_ROOF_STRONG = 12f;
    /** 薄岩层档：山坳/交界处的下限（等价于原先的"干高度均值 ≥ 72"，有效约 3 格）。 */
    private static final float MIN_ROOF_WEAK = 2f;
    /** 薄岩层档还要求"这段河道够实在"：{@code max(tunnel, junction)} 至少这么强。 */
    private static final float WEAK_MIN_RIVER_STRENGTH = 0.60f;
    /** 干高度邻域采样步长（格）：同区块内 3×3，抵消山脊侧翼的小凹陷。 */
    private static final int DRY_SAMPLE_STEP = 8;

    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private UndergroundRiver() {}

    /**
     * 开凿一个区块的地下河。
     *
     * @param landscape 本区块地形（读 {@code noise/river/mountainChainRiverHost/dryHeight}；
     *                  写 {@code riverCaveCeiling} 供装饰期 {@link RiverCaveVines} 用）
     */
    public static void carve(final ChunkPrimer primer, final int chunkX, final int chunkZ,
                             final ChunkLandscape landscape) {

        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;                                     // 布局未就绪：本区块不凿（fail-soft）
        }
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                carveColumn(primer, layout, landscape, x, z, baseX + x, baseZ + z);
            }
        }
    }

    private static void carveColumn(final ChunkPrimer primer, final RtgBiomeLayout layout,
                                    final ChunkLandscape landscape, final int x, final int z,
                                    final int worldX, final int worldZ) {

        final int k = x * 16 + z;
        if (landscape.river[k] <= 0f) {                 // ⓪ 河网带内
            return;
        }
        final float tunnel = layout.getRiverTunnelStrength(worldX, worldZ);
        final float junction = layout.getRiverJunctionStrength(worldX, worldZ);
        if (tunnel <= 0f && junction <= 0f) {           // ① 隧道带 / 交汇盘
            return;
        }
        final float mountainHost = mountainWeight(landscape, k, tunnel, junction);  // ② 上方有山
        if (mountainHost <= 0f) {
            return;
        }

        // RWG:601-602 —— 山体权重与地表高度各做一次 smoothstep；洞身按前者渐隐，后者只管洞厅/天窗。
        final int surface = Math.min(255, (int) landscape.noise[k]);
        final float overheadHost = smoothstep((surface - MIN_SURFACE) / OVERHEAD_WIDTH);
        final float tunnelCurve = (float) Math.sqrt(tunnel) * mountainHost;

        // ③ 断面：洞心恒 CENTER_Y —— **不能按地表下移**（水面会接不上、河床被掏成深沟、只能潜水进）。
        //    洞厅扩张的顺序照抄 RWG:606-609：基准写死 63，`min(surface-10)` 在 `max()` 之前。
        int floor = CENTER_Y - Math.round(tunnelCurve * FLOOR_DROP);
        int ceiling = CENTER_Y + Math.round(tunnelCurve * ROOF_RISE);
        final float chamberStrength = junction * mountainHost * overheadHost;
        if (chamberStrength > 0f) {
            final float chamberCurve = (float) Math.sqrt(chamberStrength);
            floor = Math.min(floor, CHAMBER_BASE_Y - Math.round(chamberCurve * CHAMBER_FLOOR_SPREAD));
            ceiling = Math.max(ceiling, CHAMBER_BASE_Y + Math.round(chamberCurve * CHAMBER_ROOF_SPREAD));
            ceiling = Math.min(ceiling, surface - ROOF_CLEARANCE);
            ceiling = Math.max(ceiling, CENTER_Y + Math.round(tunnelCurve * ROOF_RISE));
        }
        if (ceiling <= floor || floor >= surface) {     // 守卫：只挡"一格水缝"与"悬空水"
            return;
        }

        fillChannel(primer, x, z, floor, ceiling);      // ④
        landscape.riverCaveCeiling[k] = ceiling;        // 供装饰期挂洞穴藤蔓

        if (chamberStrength > SKYLIGHT_MIN_CHAMBER && surface > ceiling) {
            openSkylight(primer, x, z, surface, ceiling, chamberStrength);
        }
    }

    /** ④ 灌水：{@code y <= }{@value #CENTER_Y}{@code ? 水 : 空气}（RWG L640-644）。 */
    private static void fillChannel(final ChunkPrimer primer, final int x, final int z,
                                    final int floor, final int ceiling) {
        final int top = Math.min(255, ceiling);
        for (int y = Math.max(1, floor); y <= top; y++) {
            primer.setBlockState(x, y, z, y <= CENTER_Y ? WATER : AIR);
        }
    }

    /** 天窗：洞厅足够强时自洞顶向上打通，与地表连通（RWG L614-623）。 */
    private static void openSkylight(final ChunkPrimer primer, final int x, final int z,
                                     final int surface, final int ceiling, final float chamberStrength) {
        final int bottom = Math.max(ceiling + 1, CENTER_Y + 1);
        final float span = Math.max(1, surface - bottom);
        for (int y = bottom; y <= surface; y++) {
            if (chamberStrength >= SKYLIGHT_MIN_CHAMBER + (y - bottom) / span * SKYLIGHT_TAPER) {
                primer.setBlockState(x, y, z, AIR);
            }
        }
    }

    // ==================================================================
    // 门控：判"上方有没有山、岩层够不够厚"
    // ==================================================================

    /**
     * ② 该列的**山体权重**（{@code 0} = 不开凿）。
     *
     * <p>厚岩层档（山体内部）直接开；薄岩层档（山坳 / 交界处）再看这段河道实不实在
     * （{@link #WEAK_MIN_RIVER_STRENGTH}）；两档都用**各自的下限**做 smoothstep 起点，
     * 保证薄岩层档拿到的是完整的 0–1 权重，而不是被厚岩层档的下限压成 0。
     */
    private static float mountainWeight(final ChunkLandscape landscape, final int k,
                                        final float tunnel, final float junction) {
        final float chain = landscape.mountainChainRiverHost[k];

        final float thick = Math.max(chain, roofHostAt(landscape, k, MIN_ROOF_STRONG));
        if (thick > HOST_MIN) {
            return smoothstep((thick - HOST_MIN) / HOST_WIDTH);
        }

        final float thin = Math.max(chain, roofHostAt(landscape, k, MIN_ROOF_WEAK));
        if (thin <= HOST_MIN_WEAK || Math.max(tunnel, junction) < WEAK_MIN_RIVER_STRENGTH) {
            return 0f;
        }
        return smoothstep((thin - HOST_MIN_WEAK) / HOST_WIDTH);
    }

    /**
     * 该列的**厚岩层档**山体权重 {@code max(链宿主, 岩层门控)} —— 即"上方是不是一座够厚的山"。
     * 开凿与 {@code /rtg probe} 共用同一函数，避免读数与实现漂移。
     *
     * <p>⚠ 返回 0 **不代表**这列一定没有暗河：它可能落在薄岩层档（山坳），那要看河道实不实在。
     */
    public static float mountainHostAt(final ChunkLandscape landscape, final int k) {
        return Math.max(landscape.mountainChainRiverHost[k], roofHostAt(landscape, k, MIN_ROOF_STRONG));
    }

    /**
     * 岩层门控：把"满断面洞顶（{@code CENTER_Y + ROOF_RISE}）之上还剩几格岩层"归一化到 0–1。
     *
     * <p>用"还剩几格岩层"而不是"干高度 ≥ 某数"，是因为前者才是这条暗河真正需要的条件
     * —— 洞顶之上有岩，它才是洞；没岩，它就是在山谷里挖一条沟。
     *
     * <p>高度取同区块 3×3 个 ±{@value #DRY_SAMPLE_STEP} 格采样点的**均值**：取最大值会把山坳
     * 两侧的峰采进来，整片鞍部被误判成"山"。只在同区块内采样（±8 格），不做链宿主那种 ±48 格
     * —— 那需要邻居区块的 landscape，会打散缓存。
     */
    private static float roofHostAt(final ChunkLandscape landscape, final int k, final float minRoof) {
        final float roof = meanDryAt(landscape, k) - (CENTER_Y + ROOF_RISE);
        return smoothstep((roof - minRoof) / OVERHEAD_WIDTH);
    }

    /** 该列干高度（河道雕刻前的地形）的 3×3 邻域均值；未写干高度的路径回退到已雕刻地表。 */
    private static float meanDryAt(final ChunkLandscape landscape, final int k) {
        final int i = k >> 4;
        final int j = k & 15;
        float sum = 0f;
        int samples = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                final int ii = i + di * DRY_SAMPLE_STEP;
                final int jj = j + dj * DRY_SAMPLE_STEP;
                if (ii < 0 || ii > 15 || jj < 0 || jj > 15) {
                    continue;
                }
                sum += landscape.dryHeight[ii * 16 + jj];
                samples++;
            }
        }
        final float mean = samples > 0 ? sum / samples : landscape.dryHeight[k];
        return mean > 0f ? mean : landscape.noise[k];
    }

    /** RWG 的 smoothstep（0–1 门控权重）。 */
    private static float smoothstep(final float value) {
        if (value <= 0f) {
            return 0f;
        }
        if (value >= 1f) {
            return 1f;
        }
        return value * value * (3f - 2f * value);
    }
}
