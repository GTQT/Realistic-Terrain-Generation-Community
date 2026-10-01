package rtg.api.util.noise;

import java.util.Random;


/**
 * C-5 离线标定：验证 {@link VoronoiResult#rwgCellDistance()} 确实与 RWG 的
 * {@code CellNoise.noise(x, z, 1D)}（{@code useDistance = true}）同量纲。
 *
 * <h2>背景</h2>
 * {@code TerrainBase} 里有 7 处 RWG-GRAND 地形函数是 RWG 原版的逐行对译，
 * 唯独 cell 项被写成 {@code cell.eval2D(x * 0.04f, y * 0.04f).getShortestDistance()}，
 * 而 RWG 原版是 {@code cell.noise(x / 25D, y / 25D, 1D)}。传入坐标相同，返回量纲不同。
 *
 * <h2>本工具做什么</h2>
 * 把 RWG 的 {@code CellNoise}（{@code valueNoise2D} 哈希 + {@code noise()} + {@code sampleTwo2D()}）
 * **原样**移植进预览工具（它零 MC 依赖），然后在同一批世界坐标上同时求：
 * <ul>
 *   <li>{@code rwgRef} —— RWG 原版返回值，这是我们要对齐的目标；</li>
 *   <li>{@code rtgcRaw} —— 修复前的 {@code getShortestDistance()}（平方距离）；</li>
 *   <li>{@code rtgcFixed} —— 修复后的 {@link VoronoiResult#rwgCellDistance()}。</li>
 * </ul>
 * 若 {@code mean(rtgcFixed) / mean(rwgRef)} ≈ 1 且值域相当，则换算正确；
 * 同时报告修复前的比值，以量化原偏差。
 *
 * <h2>不能期望逐点相等</h2>
 * 两者的点集生成方式不同（RWG 是每单位方格一个抖动格点，rtgc 是每单位方格 25 个
 * 硬核约束点），且只有 rtgc 侧做了域扭曲。按路线图 §0.2 的既定口径，
 * 移植目标是"统计等价 / 景观特征可辨识"，不是"逐块一致"。故判据是**分布**而不是逐点。
 *
 * <p>运行：{@code gradlew calibrateCellularNoise}（可选参数见 {@link #main}）。
 *
 * @since 1.0.8
 */
public final class CellularNoiseCalibration {

    /** RWG 对 2D 距离的归一化常数（{@code CellNoise.SQRT_2}）。 */
    private static final double SQRT_2 = 1.4142135623730950488;

    private CellularNoiseCalibration() {}

    /** Java 8 目标下没有 {@code String.repeat}。 */
    private static String dashes(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('-');
        }
        return sb.toString();
    }

    private static int intProperty(String key, int fallback) {
        try {
            final String raw = System.getProperty(key);
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // ---- 特征尺度：沿直线的自相关半衰长度 ----

    private static final int LINE_SAMPLES = 40_000;
    private static final int LINE_STRIDE = 1;
    private static final int MAX_LAG = 200;

    /** RWG 场 f(x) = cell.noise(x/pitch, z0/pitch, 1) 的自相关半衰长度（世界格）。 */
    private static double rwgCorrelationLength(float pitch) {
        final RwgCellNoise rwg = new RwgCellNoise(9_876_543_211L);
        final double[] f = new double[LINE_SAMPLES];
        for (int i = 0; i < LINE_SAMPLES; i++) {
            f[i] = rwg.noise(i * LINE_STRIDE / pitch, 1234.0D / pitch, 1.0D);
        }
        return halfDecayLag(f);
    }

    /** rtgc 场 f(x) = eval2D(x/pitch, z0/pitch).rwgCellDistance() 的自相关半衰长度（世界格）。 */
    private static double rtgcCorrelationLength(float pitch) {
        final SpacedCellularNoise spaced = new SpacedCellularNoise(9_876_543_211L);
        final double[] f = new double[LINE_SAMPLES];
        for (int i = 0; i < LINE_SAMPLES; i++) {
            f[i] = spaced.eval2D(i * LINE_STRIDE / pitch, 1234.0D / pitch).rwgCellDistance();
        }
        return halfDecayLag(f);
    }

    /**
     * 归一化自相关降到 0.5 时的滞后，换算成世界格。
     * <p>
     * 这是对"特征尺寸"的**直接**测量，不依赖任何关于点集几何的假设——
     * 之所以不用"最近距离均值 ≈ 0.5 × 单元边长"这种启发式，是因为它对本类不成立：
     * {@code SpacedCellularNoise.generatedAreaPoints} 让每个整数方格都从**同一个母版点集**
     * 取点（只是整体平移），于是跨方格会出现大量近重合点，最近距离远小于几何预期。
     */
    private static double halfDecayLag(double[] f) {
        final int n = f.length;
        double mean = 0;
        for (double v : f) {
            mean += v;
        }
        mean /= n;

        double variance = 0;
        for (double v : f) {
            variance += (v - mean) * (v - mean);
        }
        variance /= n;
        if (variance <= 0) {
            return Double.NaN;
        }

        for (int lag = 1; lag <= MAX_LAG; lag++) {
            double cov = 0;
            for (int i = 0; i + lag < n; i++) {
                cov += (f[i] - mean) * (f[i + lag] - mean);
            }
            cov /= (n - lag);
            if (cov / variance < 0.5) {
                return lag * (double) LINE_STRIDE;
            }
        }
        return MAX_LAG * (double) LINE_STRIDE;
    }

    // ==================================================================
    // RWG CellNoise 的原样移植（仅保留本标定需要的方法）
    // 源：rwg/util/CellNoise.java（1.7.10 侧 rwg2 分支）
    // ==================================================================

    /**
     * RWG {@code CellNoise.valueNoise2D}：用坐标与种子的哈希代替存储特征点。
     * <p>
     * 注意 {@code n * n * n} 在 Java 中是 {@code long} 溢出回绕，这是**原版行为**，
     * 不能改成 {@code Math.pow} 或加溢出保护，否则点集完全不同。
     */
    static double valueNoise2D(int x, int z, long seed) {
        long n = (1619 * x + 6971 * z + 1013 * seed) & 0x7fffffff;
        n = (n >> 13) ^ n;
        return 1.0 - ((double) ((n * (n * n * 60493 + 19990303) + 1376312589) & 0x7fffffff) / 1073741824.0);
    }

    /** RWG {@code CellNoise} 的最小可用子集。{@code distanceMethod == 0}，{@code useDistance == true}。 */
    static final class RwgCellNoise {

        private final long seed;
        private final long seedOffset;

        RwgCellNoise(long seed) {
            this.seed = seed;
            this.seedOffset = new Random(seed).nextLong();
        }

        /** RWG {@code CellNoise.noise(x, z, frequency)}，{@code useDistance == true} 分支。 */
        float noise(double x, double z, double frequency) {
            x *= frequency;
            z *= frequency;

            int xInt = (x > .0 ? (int) x : (int) x - 1);
            int zInt = (z > .0 ? (int) z : (int) z - 1);

            double minDist = 32000000.0;
            double xCandidate = 0;
            double zCandidate = 0;

            for (int zCur = zInt - 2; zCur <= zInt + 2; zCur++) {
                for (int xCur = xInt - 2; xCur <= xInt + 2; xCur++) {
                    double xPos = xCur + valueNoise2D(xCur, zCur, seed);
                    double zPos = zCur + valueNoise2D(xCur, zCur, seedOffset);
                    double xDist = xPos - x;
                    double zDist = zPos - z;
                    double dist = xDist * xDist + zDist * zDist;
                    if (dist < minDist) {
                        minDist = dist;
                        xCandidate = xPos;
                        zCandidate = zPos;
                    }
                }
            }

            double xDist = xCandidate - x;
            double zDist = zCandidate - z;
            return (float) (Math.sqrt(xDist * xDist + zDist * zDist) / SQRT_2);
        }

        /**
         * RWG {@code CellNoise.sampleTwo2D}：输出最近与次近距离/值。
         * {@code out = {d1, v1, d2, v2}}，距离为**线性**。
         */
        void sampleTwo2D(double x, double z, double frequency, double[] out) {
            x *= frequency;
            z *= frequency;
            int xInt = x > 0D ? (int) x : (int) x - 1;
            int zInt = z > 0D ? (int) z : (int) z - 1;
            double firstDistanceSquared = Double.POSITIVE_INFINITY;
            double secondDistanceSquared = Double.POSITIVE_INFINITY;
            double firstValue = 0D;
            double secondValue = 0D;
            for (int zCur = zInt - 2; zCur <= zInt + 2; zCur++) {
                for (int xCur = xInt - 2; xCur <= xInt + 2; xCur++) {
                    double xPos = xCur + valueNoise2D(xCur, zCur, seed);
                    double zPos = zCur + valueNoise2D(xCur, zCur, seedOffset);
                    double xDistance = xPos - x;
                    double zDistance = zPos - z;
                    double distanceSquared = xDistance * xDistance + zDistance * zDistance;
                    double value = valueNoise2D((int) Math.floor(xPos), (int) Math.floor(zPos), seed);
                    if (distanceSquared < firstDistanceSquared) {
                        secondDistanceSquared = firstDistanceSquared;
                        secondValue = firstValue;
                        firstDistanceSquared = distanceSquared;
                        firstValue = value;
                    }
                    else if (distanceSquared < secondDistanceSquared) {
                        secondDistanceSquared = distanceSquared;
                        secondValue = value;
                    }
                }
            }
            out[0] = Math.sqrt(firstDistanceSquared);
            out[1] = firstValue;
            out[2] = Math.sqrt(secondDistanceSquared);
            out[3] = secondValue;
        }
    }

    // ==================================================================
    // 统计
    // ==================================================================

    private static final class Stats {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        long n = 0;

        void add(double v) {
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
            n++;
        }

        double mean() { return n == 0 ? Double.NaN : sum / n; }

        String fmt() {
            return String.format("min=%8.5f  mean=%8.5f  max=%8.5f", min, mean(), max);
        }
    }

    public static void main(String[] args) {
        // 世界坐标采样。默认半径 20k 格、步长 37（质数，避免与任何周期对齐）
        // ⇒ 每轴约 1081 点、共约 117 万样本。
        // 注意两个噪声函数都不便宜（RWG 侧每次要扫 5×5=25 个格点，rtgc 侧要建/查点集缓存），
        // 1.17 亿样本量级会跑十几分钟，故默认值刻意保守；可用 -Drtg.calib.extent / .stride 调整。
        final int extent = intProperty("rtg.calib.extent", 20_000);
        final int stride = intProperty("rtg.calib.stride", 37);
        final long seed = 1_234_567_891_011_121L;

        // 这些 pitch 取自 TerrainBase 里 7 处 cell 项的实际调用
        // （RWG 侧写法 x / P，rtgc 侧写法 x * (1/P)）
        final float[] pitches = { 25f, 30f, 200f };

        System.out.println("=== C-5 标定：rtgc 的 cell 项 vs RWG CellNoise.noise() ===");
        System.out.printf("seed=%d  extent=±%d  stride=%d  COORDINATE_SCALE=%.1f%n",
                seed, extent, stride, SpacedCellularNoise.COORDINATE_SCALE);
        System.out.println();
        System.out.println("目标：rwgCellDistance() 的分布应与 RWG 原版一致（均值比 ≈ 1.00）");
        System.out.println();

        final RwgCellNoise rwg = new RwgCellNoise(seed);
        final SpacedCellularNoise spaced = new SpacedCellularNoise(seed);

        System.out.printf("%-8s %-46s %-46s %10s%n", "pitch", "RWG cell.noise(x/P, y/P, 1)", "rtgc eval2D(x/P).rwgCellDistance()", "meanRatio");
        System.out.println(dashes(120));

        for (float pitch : pitches) {
            final Stats rwgRef = new Stats();
            final Stats rtgcRaw = new Stats();
            final Stats rtgcFixed = new Stats();

            for (int x = -extent; x <= extent; x += stride) {
                for (int z = -extent; z <= extent; z += stride) {
                    // RWG：cell.noise(x / P, y / P, 1D)
                    rwgRef.add(rwg.noise((double) x / pitch, (double) z / pitch, 1.0D));

                    // rtgc：cell.eval2D(x * (1/P), y * (1/P))
                    final VoronoiResult r = spaced.eval2D((double) x / pitch, (double) z / pitch);
                    rtgcRaw.add(r.getShortestDistance());
                    rtgcFixed.add(r.rwgCellDistance());
                }
            }

            System.out.printf("%-8s %-46s %-46s %10.4f%n",
                    String.format("/%.0f", pitch),
                    rwgRef.fmt(),
                    rtgcFixed.fmt(),
                    rtgcFixed.mean() / rwgRef.mean());
            System.out.printf("%-8s %-46s%n", "", "  （修复前 getShortestDistance(): " + rtgcRaw.fmt() + "）");
            System.out.printf("%-8s %-46s%n", "", String.format("  修复前均值比 = %.5f  ⇒ 原偏差约 %.1f 倍偏弱",
                    rtgcRaw.mean() / rwgRef.mean(), rwgRef.mean() / rtgcRaw.mean()));
            System.out.println();
        }

        // ---- 特征尺度核对 ----
        //
        // 用**自相关长度**直接量特征尺寸，而不是靠"最近距离均值 ≈ 0.5 × 单元边长"这种启发式
        // —— 实测表明该启发式对本类不成立（点集跨方格镜像复用同一母版点，会产生大量近重合点，
        // 使最近距离远小于几何预期）。
        // 判据：沿固定 z 的直线采样 f(x)，求归一化自相关降到 0.5 所需的滞后距离。
        System.out.println("--- 特征尺度核对（自相关长度，世界格）---");
        final float scalePitch = 25f;
        final double corrLenRwg = rwgCorrelationLength(scalePitch);
        final double corrLenRtg = rtgcCorrelationLength(scalePitch);
        System.out.printf("RWG   cell.noise(x/%.0f)     自相关半衰长度 ≈ %8.3f 格%n", scalePitch, corrLenRwg);
        System.out.printf("rtgc  rwgCellDistance()      自相关半衰长度 ≈ %8.3f 格%n", corrLenRtg);
        System.out.printf("  尺度比（rtgc/RWG）= %.3f%n", corrLenRtg / corrLenRwg);
        System.out.println("  若明显小于 1，说明 rtgc 的 cell 结构比 RWG 更细碎，");
        System.out.println("  则除幅值换算外还需放大 pitch —— 本轮未做，仅记录（见 docs 中的 C-5）。");
        System.out.println();

        System.out.println();
        System.out.println("--- sampleTwo2D 对照（WP-3 的河网宽度换算用到的 (d2-d1)）---");
        final Stats rwgEdge = new Stats();
        final Stats rtgcEdge = new Stats();
        final double[] out = new double[4];
        // 两者都必须换算到**世界格**才能比较。
        // RWG：sampleTwo2D 传入的已是 x/pitch，输出为 pitch 空间的线性距离 ⇒ ×pitch 得世界格。
        // rtgc：eval2D 内部把传入坐标再除以 COORDINATE_SCALE，故 VoronoiResult 里的距离是
        //       u 空间的 ⇒ 需先 ×COORDINATE_SCALE 回到传入坐标空间，再 ×pitch 得世界格。
        //       （borderDistance() 返回 (d2-d1)/2，故还要 ×2 才是 RWG 的 (d2-d1)。）
        final double rtgcScale = 2.0D * SpacedCellularNoise.COORDINATE_SCALE * 25.0D;
        for (int x = -extent; x <= extent; x += stride) {
            for (int z = -extent; z <= extent; z += stride) {
                rwg.sampleTwo2D((double) x / 25.0D, (double) z / 25.0D, 1.0D, out);
                rwgEdge.add((out[2] - out[0]) * 25.0D);

                final VoronoiResult r = spaced.eval2D((double) x / 25.0D, (double) z / 25.0D);
                rtgcEdge.add(r.borderDistance() * rtgcScale);
            }
        }
        System.out.printf("RWG   (d2-d1)×pitch            ：%s%n", rwgEdge.fmt());
        System.out.printf("rtgc  border×2×COORD_SCALE×pitch：%s%n", rtgcEdge.fmt());
        System.out.printf("  均值比 = %.4f —— 接近 1 即证明 WP-3 用的『×COORDINATE_SCALE』换算成立；%n",
                rtgcEdge.mean() / rwgEdge.mean());
        System.out.println("  残差来自两者点集几何不同（RWG 每方格 1 个抖动点，rtgc 每方格 25 个硬核点）。");

        wp3Geometry(extent, stride);
    }

    // ==================================================================
    // WP-3 几何：地下河隧道 / 交汇洞厅的实际覆盖率
    // ==================================================================

    /**
     * 复核 WP-3 的 {@code TerrainBase.toWorldBlocks()} 换算，并直接量出隧道与洞厅的**面积覆盖率**。
     * <p>
     * 这是离线能做的最接近"地下河到底存不存在"的检验：雕刻本身要区块方块、离线覆盖不到，
     * 但**门控宽度是否落在设计值上**完全可以在噪声层量出来。
     * <p>
     * 理论覆盖率（单元边长 L、半宽 t）：{@code ≈ 4t/L}。
     * 取 {@code L = riverSeparation = 975}、隧道 {@code t = 6.5} ⇒ 约 2.67%；
     * 洞厅按每单元约 2 个顶点、半径 42 ⇒ 约 1.2%。
     * <p>
     * 不施加 {@code warpedRiverVoronoi} 的域扭曲：扭曲是保面积的坐标变换，
     * 不改变覆盖率，只让边界变弯。故此处测的是**换算因子**本身。
     */
    private static void wp3Geometry(int extent, int stride) {
        final double riverSeparation = 975.0D;      // RTGWorld.RIVER_SEPARATION_BASE / riverFrequency(1.0)
        final double tunnelHalfWidth = 6.5D;        // TerrainBase.TUNNEL_HALF_WIDTH_BLOCKS
        final double junctionRadius = 42.0D;        // TerrainBase.JUNCTION_RADIUS_BLOCKS
        final double toWorld = SpacedCellularNoise.COORDINATE_SCALE * riverSeparation;

        final SpacedCellularNoise spaced = new SpacedCellularNoise(4_242_424_242L);

        final Stats borderPx = new Stats();      // 传入坐标（= 单元尺寸的倍数）下的到边界距离
        final Stats borderWorld = new Stats();   // 世界格
        final Stats vertexWorld = new Stats();   // 世界格
        long inTunnel = 0;
        long inChamber = 0;
        long n = 0;

        for (int x = -extent; x <= extent; x += stride) {
            for (int z = -extent; z <= extent; z += stride) {
                final VoronoiResult r = spaced.eval2D(x / riverSeparation, z / riverSeparation);
                final double bd = r.borderDistance();
                final double vd = r.vertexDistance();

                borderPx.add(bd * SpacedCellularNoise.COORDINATE_SCALE);
                borderWorld.add(bd * toWorld);
                vertexWorld.add(vd * toWorld);

                if (bd * toWorld < tunnelHalfWidth) {
                    inTunnel++;
                }
                if (vd * toWorld < junctionRadius) {
                    inChamber++;
                }
                n++;
            }
        }

        System.out.println();
        System.out.println("--- WP-3 几何：地下河隧道 / 交汇洞厅的覆盖率（riverSeparation=975）---");
        System.out.printf("到单元边界距离（传入坐标，单元尺寸=1.0）：%s%n", borderPx.fmt());
        System.out.printf("  ↑ 路线图 §5.4 表格里的 0.1418 / 0.6277 就是这一行（当时误标为『换算后』）%n");
        System.out.printf("到单元边界距离（世界格）：%s%n", borderWorld.fmt());
        System.out.printf("到单元顶点距离（世界格）：%s%n", vertexWorld.fmt());
        System.out.println();
        System.out.printf("隧道半宽 %.1f 格 ⇒ 覆盖率 %.3f%%（Voronoi 理论 8t/L = %.3f%%，方格近似 4t/L = %.3f%%）%n",
                tunnelHalfWidth, 100.0 * inTunnel / n,
                100.0 * 8 * tunnelHalfWidth / riverSeparation,
                100.0 * 4 * tunnelHalfWidth / riverSeparation);
        System.out.printf("洞厅半径 %.1f 格 ⇒ 覆盖率 %.3f%%（理论 2·πr²/L² = %.3f%%）%n",
                junctionRadius, 100.0 * inChamber / n,
                100.0 * 2 * Math.PI * junctionRadius * junctionRadius / (riverSeparation * riverSeparation));
        System.out.println("  Voronoi 的边长密度是 4/√A（方格近似只有 2/L），故用 8t/L 才对得上；");
        System.out.println("  洞厅实测高于 2·πr²/L²，因为 (d3−d1)/2 只是到顶点的**近似**距离，");
        System.out.println("  且在顶点附近增长缓慢 ⇒ 用半径阈值量出的覆盖带比圆盘大。两者都是量级一致的。");
        System.out.println("  实测≈理论 ⇒ toWorldBlocks() 的『×COORDINATE_SCALE×riverSeparation』换算成立，");
        System.out.println("  隧道确实是一条沿河网延伸、占比约 4% 的细带（既非到处都有，也非根本没有）。");

        // ---- 决定性检验：隧道带内的「河流强度」----
        //
        // 当时的 carveRiverTunnels（今 {@code UndergroundRiver.carve}）门控是 `surface >= 76`
        //（当时的 TUNNEL_MIN_SURFACE，今 {@code UndergroundRiver.MIN_SURFACE}），
        // 而 surface 就是 TerrainBase.getRiverStrength 压平之后的实际地形高度。
        // 若隧道带内 riverStrength 恒为接近 1，则地表必然被压到河面附近（约 63），
        // 门控**永不成立** —— 地下河从不下凿。
        //
        // getRiverStrength 的公式（TerrainBase:843-855）：
        //   riverFactor = bayesianAdjustment(voronoi.interiorValue(), 0.85f)
        //   riverFactor > riverValleyLevel ? 0 : 1 - riverFactor / riverValleyLevel
        // riverValleyLevel 默认 = RIVER_VALLEY_LEVEL_BASE(140/450) × riverSizeMult(1.0)
        //                          × riverFrequency(1.0) × riverSizeFactor()(1.0)
        final double riverValleyLevel = (140.0D / 450.0D) * 1.0D * 1.0D * 1.0D;
        final Stats riverInTunnelBand = new Stats();
        long riverBandColumns = 0;
        long tunnelBandColumns = 0;
        long tunnelBandNonRiver = 0;

        for (int x = -extent; x <= extent; x += stride) {
            for (int z = -extent; z <= extent; z += stride) {
                final VoronoiResult r = spaced.eval2D(x / riverSeparation, z / riverSeparation);
                final double tWorld = r.borderDistance() * toWorld;

                final double riverFactor = bayesianAdjustment((float) r.interiorValue(), 0.85f);
                final double riverStrength = riverFactor > riverValleyLevel
                        ? 0.0D
                        : 1.0D - riverFactor / riverValleyLevel;

                if (riverStrength > 0.0D) {
                    riverBandColumns++;
                }
                if (tWorld < tunnelHalfWidth) {
                    tunnelBandColumns++;
                    riverInTunnelBand.add(riverStrength);
                    if (riverStrength <= 0.0D) {
                        tunnelBandNonRiver++;
                    }
                }
            }
        }

        System.out.println();
        System.out.println("--- 决定性检验：隧道带（距边界 < 6.5 格）内的河流强度 ---");
        System.out.printf("riverStrength（隧道带内）：%s%n", riverInTunnelBand.fmt());
        System.out.printf("隧道带样本 %d，其中 riverStrength == 0 的：%d%n",
                tunnelBandColumns, tunnelBandNonRiver);
        System.out.println("  ⇒ 隧道带完全包含在河网带内部（两者共用同一个 warpedRiverVoronoi，同心）。");
        System.out.printf("河网带占全图比例：%.2f%%（riverValleyLevel=%.4f）%n",
                100.0 * riverBandColumns / n, riverValleyLevel);
        System.out.println();
        System.out.println("  ⚠ 由此得出 F-39：当时 carveRiverTunnels 的门控 `surface >= 76`（TUNNEL_MIN_SURFACE）");
        System.out.println("     在隧道带内**永不成立** —— 带内 riverStrength ≈ 1，地表被压平到河面附近（约 63）。");
        System.out.println("     地下河入口是死路径（profiler 实测 RIVER_TUNNELS 仅 0.01 ms/区块，与之吻合）。");
    }

    /** {@code TerrainBase.bayesianAdjustment} 的等价实现（避免加载 MC 相关的 TerrainBase）。 */
    private static float bayesianAdjustment(float probability, float multiplier) {
        if (probability >= 1f) {
            return probability;
        }
        if (probability <= 0f) {
            return probability;
        }
        float oneMinusProbability = 1f - probability;
        float newConfidence = probability * multiplier / oneMinusProbability;
        return newConfidence / (1f + newConfidence);
    }
}
