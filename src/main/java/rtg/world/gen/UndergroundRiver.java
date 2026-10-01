package rtg.world.gen;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;


/**
 * **地下暗河与交汇洞厅（WP-3）的全部逻辑**：门控 → 断面几何 → 开凿 → 诊断读数。
 *
 * <p>为什么集中成一个类：这套东西原本散在 {@link ChunkGeneratorRTG} 里（常量 + `carveRiverTunnels`
 * + `dryHeightHost` + 探针读数 + `smoothstep`），前后被反复微调，很容易改乱。现在
 * **要改暗河只进这一个文件**；生成器那边只剩一行调用（外加地表替换顺序的约束）。
 *
 * <h2>判定链（逐列，任何一步不过就跳过）</h2>
 * <ol>
 *   <li><b>⓪ 在河网带内</b> {@code landscape.river[k] > 0} —— 纯性能前置，且**可证明等价**：
 *       隧道带（{@code 25/1250}）严格落在河网带（{@code 50/300}）内部。放在 Voronoi 之前能挡掉
 *       绝大多数列（曾经把顺序写反，等于每列白算一遍 Voronoi —— 见离线校准工具
 *       {@code CellularNoiseCalibration} 记的 F-39）。</li>
 *   <li><b>① 在隧道带 / 交汇盘内</b> {@code getRiverTunnelStrength > 0 || getRiverJunctionStrength > 0}
 *       （同一个扭曲 Voronoi：带宽 {@code 25/1250}、交汇半径 {@code 60/1250}）。</li>
 *   <li><b>② 是"山"</b> {@code max(链宿主, 干高度门控) > }{@link #HOST_MIN}。</li>
 *   <li><b>③ 断面</b>：洞心恒 {@link #CENTER_Y}，上下 {@code ±(√强度 × 起伏)}，再按门控权重**渐隐**；
 *       洞厅另加扩张；最后过两条守卫。</li>
 *   <li><b>④ 填充</b>：{@code y ≤ 62 ? 水 : 空气}（**水面恒在水面高度**）；洞厅够强时向上开天窗。</li>
 * </ol>
 *
 * <h2>与 RWG 的关系</h2>
 *
 * 几何与门控照抄 {@code ChunkGeneratorRealistic.carveMountainChainRivers:581-626}。
 * rtgc 只有 4 处差异，每一处都有用户裁定与实测依据：
 * <ol>
 *   <li><b>门控叠加"普通山"</b>：{@code smoothstep((干高度 − }{@link #MIN_DRY_HEIGHT}{@code ) / 24)}
 *       —— 用户要求（RWG 只认山地链，而链只占 7.7% 的列）；</li>
 *   <li><b>隧道带宽度</b>：在 {@link RtgBiomeLayout#getRiverTunnelStrength}（RWG {@code 9/1250} → {@code 25/1250}）
 *       —— 用户反馈"太稀"；</li>
 *   <li><b>洞顶起伏</b>：11 → {@link #ROOF_RISE}（8）—— 用户反馈"侧切山体…收一点点"；</li>
 *   <li><b>两条守卫</b>：{@code ceiling <= floor}（只会写"一格水在石头里"）与 {@code floor >= surface}
 *       （会写出悬空水）—— 只挡垃圾，不改变可玩性。</li>
 * </ol>
 *
 * 另外断面按门控权重 {@code mountainHost} **渐隐**：隧道是"方块开挖"（直壁、平底），
 * 地表河是"高度混合"（噪声河床），门控边缘一刀切会留下钝头 ⇒ 尾部逐格变浅直至消失，
 * 硬开挖平滑交回给地表河。链内部 {@code mountainHost ≥ 0.5} 时渐隐系数为 1 ⇒ 与 RWG 逐位一致。
 *
 * <h2>必须放在 {@code replaceBiomeBlocks} 之后</h2>
 * 地表替换按 {@code depth} 计数涂刷；若先开凿，隧道内的空气会重置 {@code depth}，
 * 导致隧道底被误刷上草/沙。
 *
 * <h2>频率实测（{@code gradlew calibrateRiverTunnels}，1.0e6 列）</h2>
 * 河网带 56.4%、隧道带 8.06%、交汇盘 2.42%、山地链列 7.7%（离散下界）⇒
 * {@code 链 ∧ 河网 ∧ (隧道|交汇) ≈ 0.61%}；天窗还要 {@code junction>0.70 ∧ 链列 ≈ 0.0004%}
 * 再乘两个 ≤1 的 smoothstep ⇒ 实际几乎为零（RWG 原公式的必然结果，不改）。
 * **地表可见性不靠天窗**，靠"水面恒 62 + 地表低于洞顶处天然开口"。
 */
public final class UndergroundRiver {

    // ==================================================================
    // 常量（RWG 原值 + 逐条标注哪几处是 rtgc 微调）
    // ==================================================================

    /** 洞心高度，同时也是**水面高度**（RWG 写死 62；原版海平面 63 的水面顶就是它）。 */
    public static final int CENTER_Y = 62;

    /**
     * 洞顶相对洞心的最大起伏（格）。RWG 是 **11**；rtgc 收成 **8**。
     *
     * <p>它就是"水线以上被掏空的高度" —— 也就是山体在水平面处被咬进去那道凹槽（浪蚀龛/凹岸）
     * 的高度。用户反馈"暗河会侧切山体…收一点点" ⇒ 11 → 8（咬痕 −27%，划船头顶仍 ≥6 格），
     * 河口能出现的地表高度上限也随之从 73 降到 70。
     */
    public static final int ROOF_RISE = 8;

    /** 洞底相对洞心的最大下探（格）。RWG 原值 **4**（所以河底只在水面下 4–5 格，不会挖成深沟）。 */
    public static final int FLOOR_DROP = 4;

    /**
     * 洞厅顶部相对地表要保留的岩层（格）。RWG 原值 **10**。
     *
     * <p>⚠ 它只作用于"洞厅扩张"，而且会被紧随其后的 {@code max(ceiling, tunnelCeiling)} 顶回去
     *（RWG 的原始顺序）—— 地表低时洞顶会朝天空开口，**那正是"河口"出现的地方**，不是 bug。
     */
    public static final int ROOF_CLEARANCE = 10;

    /** 洞厅/天窗的"地表高度权重"基准（RWG {@code ChunkGeneratorRealistic:602} 的 76）。 */
    public static final float MIN_SURFACE = 76f;

    /** 山体门控的下限（RWG {@code :586} 的 0.10）。 */
    public static final float HOST_MIN = 0.10f;

    /**
     * 「算不算山」的**干高度**阈值（格）。
     *
     * <p>RWG 没有这条门控（只认山地链宿主）⇒ 普通的山一滴暗河都没有。rtgc 叠加本条：
     * {@code smoothstep((干高度 − 68) / 24) > 0.10} ⇒ **有效阈值约 73 格**。
     *
     * <p>⚠ 必须用**干高度**（河道雕刻前的地形，{@link ChunkLandscape#dryHeight}）：
     * 隧道带完全落在河网带内部，那里的地表已被河流压到河床 ≈59，用已雕刻地表做高度门控
     * **永远不成立** —— F-39 那条死路就是这么来的（当年 profiler 实测 {@code RIVER_TUNNELS}
     * 仅 0.01 ms/区块）。
     */
    public static final float MIN_DRY_HEIGHT = 68f;

    /** 干高度门控的邻域采样步长（格）：同区块内取 3×3 个采样点，抵消山脊侧翼的小凹陷。 */
    public static final int DRY_DILATION_STEP = 8;

    /** 洞厅强度里"地表够不够高"的 smoothstep 宽度（RWG 24）。 */
    private static final float OVERHEAD_WIDTH = 24f;
    /** 洞厅强度里"山体权重"的 smoothstep 宽度（RWG 0.40）。 */
    private static final float HOST_WIDTH = 0.40f;
    /** RWG 洞厅扩张用的**写死基准**（{@code ChunkGeneratorRealistic:606-607}）。 */
    private static final int CHAMBER_BASE_Y = 63;
    /** 天窗门槛与收口坡度（RWG {@code :614-623}）。 */
    private static final float SKYLIGHT_MIN_CHAMBER = 0.70f;
    private static final float SKYLIGHT_TAPER = 0.15f;

    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private UndergroundRiver() {}

    // ==================================================================
    // 开凿
    // ==================================================================

    /**
     * 开凿一个区块的地下河隧道与交汇洞厅。
     *
     * @param primer    区块方块容器
     * @param chunkX    区块 X（方块坐标 = chunkX * 16 + 局部坐标）
     * @param chunkZ    区块 Z
     * @param landscape 本区块的地形数据（读 {@code noise/river/mountainChainRiverHost/dryHeight}，
     *                  写 {@code riverCaveCeiling} 供 {@link RiverCaveVines} 装饰期使用）
     */
    public static void carve(final ChunkPrimer primer, final int chunkX, final int chunkZ,
                             final ChunkLandscape landscape) {

        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;                     // 布局未就绪：本区块不凿暗河（fail-soft）
        }
        final float[] heights = landscape.noise;
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        final float[] strengths = new float[2];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int k = x * 16 + z;
                final int surface = Math.min(255, (int) heights[k]);

                // ---- ⓪ 在河网带内（纯性能前置；隧道带 ⊂ 河网带，故等价）----
                if (landscape.river[k] <= 0f) {
                    continue;
                }

                // ---- ① 在隧道带 / 交汇盘内 ----
                strengths[0] = layout.getRiverTunnelStrength(baseX + x, baseZ + z);
                strengths[1] = layout.getRiverJunctionStrength(baseX + x, baseZ + z);
                final float tunnel = strengths[0];
                final float junction = strengths[1];
                if (tunnel <= 0f && junction <= 0f) {
                    continue;
                }

                // ---- ② 是"山"：山地链宿主 ∪ 普通山（干高度）----
                final float chainHost = landscape.mountainChainRiverHost[k];
                final float heightHost = dryHeightHost(landscape, k);
                if (chainHost <= HOST_MIN && heightHost <= HOST_MIN) {
                    continue;
                }

                // RWG:601-602 —— 山体权重与地表高度各做一次 smoothstep：
                // 前者决定"这属于山的哪一部分"，后者保证洞厅上方有足够岩层（天窗只在地表够高时开）。
                final float mountainHost = smoothstep(
                        (Math.max(chainHost, heightHost) - HOST_MIN) / HOST_WIDTH);
                final float overheadHost = smoothstep((surface - MIN_SURFACE) / OVERHEAD_WIDTH);

                // ---- ③ 断面：洞心恒 62；上下起伏按门控权重渐隐 ----
                //
                // ⚠ 洞心**不能**按地表下移。曾经试过 `center = min(62, surface - 21)`，三件事一起坏：
                //   ① 水面跟着洞心掉 ⇒ 洞里与外面接不上；
                //   ② 洞底被挖到 y≈34 ⇒ 河床被掏成两边深沟；
                //   ③ 洞顶恒在地表下 10 格 ⇒ 永远只能潜水进，不能划船进。
                // RWG 的做法本身就是答案：水面恒 62（见 ④），地表高于洞顶处成地道、低于处成河口。
                final float taper = mountainHost;        // 门控边缘 0 → 内部 1（见类注释"渐隐"）
                final float tunnelCurve = (float) Math.sqrt(tunnel) * taper;

                int floor = CENTER_Y - Math.round(tunnelCurve * FLOOR_DROP);
                int ceiling = CENTER_Y + Math.round(tunnelCurve * ROOF_RISE);

                final float chamberStrength = junction * mountainHost * overheadHost;
                if (chamberStrength > 0f) {
                    final float chamberCurve = (float) Math.sqrt(chamberStrength);
                    // RWG:606-609：基准写死 63，`min(surface-10)` 在 `max()` 之前（随后被顶回去）。
                    floor = Math.min(floor, CHAMBER_BASE_Y - Math.round(chamberCurve * 23f));
                    ceiling = Math.max(ceiling, CHAMBER_BASE_Y + Math.round(chamberCurve * 42f));
                    ceiling = Math.min(ceiling, surface - ROOF_CLEARANCE);
                    ceiling = Math.max(ceiling, CENTER_Y + Math.round(tunnelCurve * ROOF_RISE));
                }

                // ---- 两条守卫（有意偏离 RWG 的字面循环：只挡垃圾）----
                //   · `ceiling <= floor`：只会写出**孤零零一格水在石头里**（上下皆实心），
                //     看不见、走不进，还会被 {@link ChunkLandscape#riverCaveCeiling} 记成"一条暗河"；
                //   · `floor >= surface`：整条洞体在地面之上 ⇒ 会写出**悬空水**。
                if (ceiling <= floor || floor >= surface) {
                    continue;
                }

                // ---- ④ 填充：RWG L640-644 `blockY <= 62 ? water : air` ----
                // **水面恒在水面高度**：洞里与外面的河同一个水平面，62 以上是气道 ⇒ 划船进得去。
                // 不要把它改成跟着洞心走。
                for (int y = Math.max(1, floor); y <= Math.min(255, ceiling); y++) {
                    primer.setBlockState(x, y, z, y <= CENTER_Y ? WATER : AIR);
                }

                // 记录洞顶，供装饰期挂洞穴藤蔓 —— 避免装饰期重算隧道门控
                landscape.riverCaveCeiling[k] = ceiling;

                // ---- 天窗：洞厅足够强时自洞顶向上打通（RWG L614-623）----
                if (chamberStrength > SKYLIGHT_MIN_CHAMBER && surface > ceiling) {
                    final int openingBottom = Math.max(ceiling + 1, CENTER_Y + 1);   // RWG: max(caveCeiling+1, 63)
                    for (int y = openingBottom; y <= surface; y++) {
                        final float fraction = (y - openingBottom)
                                / (float) Math.max(1, surface - openingBottom);
                        if (chamberStrength >= SKYLIGHT_MIN_CHAMBER + fraction * SKYLIGHT_TAPER) {
                            primer.setBlockState(x, y, z, AIR);
                        }
                    }
                }
            }
        }
    }

    // ==================================================================
    // 门控读数（与开凿共用，供 /rtg probe —— 免得诊断与代码漂移）
    // ==================================================================

    /**
     * 该列的**山体门控权重** {@code max(链宿主, 干高度门控)}。
     * 开凿与 {@code /rtg probe} 用的是同一个函数，避免"读数与实现不一致"。
     */
    public static float mountainHostAt(final ChunkLandscape landscape, final int k) {
        return Math.max(landscape.mountainChainRiverHost[k], dryHeightHost(landscape, k));
    }

    /**
     * 「这一列算不算山」的权重：以**干高度**（河道雕刻前的地形）为准，
     * 取同区块内 3×3 个 ±{@link #DRY_DILATION_STEP} 格采样点的**最大值**，
     * 免得隧道在山脊侧翼的小凹陷处断成几截。
     *
     * <p>只做 ±8 格（同区块内）而不是链宿主那种 ±48 格：那需要邻居区块的 landscape，
     * 跨区块取会把缓存打散；山腰的连贯性已由"阈值 68（有效 ≈73）"解决。
     */
    private static float dryHeightHost(final ChunkLandscape landscape, final int k) {
        final int i = k >> 4;
        final int j = k & 15;
        float maxDry = landscape.dryHeight[k];
        for (int di = -1; di <= 1; di++) {
            final int ii = i + di * DRY_DILATION_STEP;
            if (ii < 0 || ii > 15) {
                continue;
            }
            for (int dj = -1; dj <= 1; dj++) {
                final int jj = j + dj * DRY_DILATION_STEP;
                if (jj < 0 || jj > 15) {
                    continue;
                }
                final float d = landscape.dryHeight[ii * 16 + jj];
                if (d > maxDry) {
                    maxDry = d;
                }
            }
        }
        if (maxDry <= 0f) {
            maxDry = landscape.noise[k];        // 没写干高度的路径回退到地表
        }
        return smoothstep((maxDry - MIN_DRY_HEIGHT) / OVERHEAD_WIDTH);
    }

    /** RWG 的 smoothstep 等价物（把海拔/强度映射成 0–1 的门控权重）。 */
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
