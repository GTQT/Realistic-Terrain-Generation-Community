package rtg.api.util.noise;


/**
 * 气候带离线标定 —— 验证 RWG {@code ChunkManagerRealistic} 的气候系统，并量出**山地链**的出现面积。
 *
 * <h2>为什么需要它</h2>
 * 移植 RWG 群系布局时，有两条关键结论只是**代数推导**、没有实测，直接照搬有风险：
 * <ol>
 *   <li>{@code biomecell.useDistance} 在 RWG 里**从未置 true**，故
 *       {@code biomecell.noise(...)} 返回的是最近特征点格子的 {@code valueNoise2D} 哈希，
 *       而不是距离。{@code valueNoise2D = 1 - (hash & 0x7fffffff) / 2^30}，
 *       而 {@code (hash & 0x7fffffff)/2^30 ∈ [0,2)}，所以值域是 <b>(-1, 1]</b>，
 *       于是 {@code climateValue = noise*0.5 + 0.5 ∈ (0, 1]}。
 *       有分析曾据「(0,1]」推出 {@code SNOW_CLIMATE_LIMIT = 0.16875} 不可达 —— <b>本工具用来证伪它</b>。</li>
 *   <li>{@code (d₂ − d₁) × climateWidth < 288} 里的 288 到底是多少格。
 *       {@code sampleTwo2D} 返回的是**未归一化**的欧氏距离差（不除 √2），
 *       而格距是 {@code coord / climateWidth}，故乘积的单位是格。</li>
 * </ol>
 *
 * <h2>本工具量什么</h2>
 * <ol>
 *   <li>{@code climateValue} 的实际值域与直方图，以及**四个气候带各自占世界的面积比**；</li>
 *   <li>{@code (d₂ − d₁) × climateWidth} 的分布 —— 即 288 这个阈值对应的实际带宽；</li>
 *   <li><b>「极端边界」面积占比</b>：RWG 只有在「邻点气候跨过至少一整带（|Δ| &gt; 1）
 *       且边界距 &lt; 288」时才换用 {@code veryCold/HotBorder} 列表，
 *       而那正是 {@code RealisticBiomeMountainChain} 挂进去的地方。
 *       <b>这个占比直接决定山地链在世界里有多常见。</b></li>
 * </ol>
 *
 * <p>运行：{@code gradlew calibrateClimateBands}（本类零 MC 依赖，也可直接 javac 运行）。
 *
 * @since 1.0.10
 */
public final class ClimateBandCalibration {

    // ---- RWG ChunkManagerRealistic 的原始常量（逐字对齐）----
    private static final float CLIMATE_WIDTH = 1400f;
    private static final float CLIMATE_WARP_SCALE_MULTIPLIER = .4f;
    private static final float CLIMATE_WARP_STRENGTH_MULTIPLIER = .8f;
    private static final float SNOW_CLIMATE_LIMIT = .16875f;
    private static final float COLD_CLIMATE_LIMIT = .545f;
    private static final float HOT_CLIMATE_LIMIT = .78f;
    private static final double CLIMATE_BORDER_DISTANCE_DIFFERENCE = 288D;

    private static final long SEED = 1_234_567_891_011_121L;

    private ClimateBandCalibration() {}

    public static void main(String[] args) {
        // climateWarp 在 RWG 里是 NoiseSelector.createNoiseGenerator(seed ^ 0xBB67AE8584CAA73BL)。
        // 我们已确认 RWG 默认走经典 Perlin，故这里用 PerlinNoise。
        final PerlinNoise climateWarp = new PerlinNoise(SEED ^ 0xBB67AE8584CAA73BL);
        final RwgCellNoise biomecell = new RwgCellNoise(SEED, (short) 0);   // useDistance 保持 false，与 RWG 一致

        System.out.println("=== 气候带标定：RWG ChunkManagerRealistic 的气候系统 ===");
        System.out.printf("seed=%d  climateWidth=%.0f  warpScale=%.0f  warpStrength=%.0f%n",
                SEED, CLIMATE_WIDTH, CLIMATE_WIDTH * CLIMATE_WARP_SCALE_MULTIPLIER,
                CLIMATE_WIDTH * CLIMATE_WARP_STRENGTH_MULTIPLIER);
        System.out.println("限值（四带）：" + SNOW_CLIMATE_LIMIT + " / " + COLD_CLIMATE_LIMIT + " / " + HOT_CLIMATE_LIMIT);
        System.out.println();

        // ---------------------------------------------------------------
        // 1) climateValue 值域 + 四带面积比
        // ---------------------------------------------------------------
        final int step = 53;
        final int extent = 30_000;
        final Stats valueStats = new Stats();
        final long[] bandCount = new long[5];   // 1..4 使用

        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                final float v = climateValue(climateWarp, biomecell, x, z);
                valueStats.add(v);
                bandCount[climateFromValue(v)]++;
            }
        }

        System.out.println("--- ① climateValue 的实际分布 ---");
        System.out.printf("%s%n", valueStats.fmt());
        System.out.println("  推导值域 (0, 1]；若实测覆盖到接近 0 与 1，即证明 SNOW 限值可达。");
        System.out.println();

        final long total = bandCount[1] + bandCount[2] + bandCount[3] + bandCount[4];
        System.out.println("--- ② 四个气候带的面积比 ---");
        final String[] names = { "", "SNOW", "COLD", "HOT ", "WET " };
        for (int c = 1; c <= 4; c++) {
            System.out.printf("  %s (climate=%d)  %6.2f%%   样本 %d%n",
                    names[c], c, 100.0 * bandCount[c] / total, bandCount[c]);
        }
        System.out.println("  参考：RWG 源码注释称限值经「预览覆盖率」校准，故四带应大致均衡（雪带可略小）。");
        System.out.println();

        // ---------------------------------------------------------------
        // 2) 边界距离量纲 + 3) 极端边界（= 山地链）面积比
        // ---------------------------------------------------------------
        final Stats borderMetric = new Stats();
        long inBorderStrip = 0;
        long extremeBorder = 0;
        long climateBorderCount = 0;
        long samples = 0;

        final double[] warped = new double[2];
        final double[] near = new double[4];

        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                samples++;

                warpClimateCoordinates(climateWarp, x, z, warped);
                biomecell.sampleTwo2D((warped[0] + 4000D) / CLIMATE_WIDTH, warped[1] / CLIMATE_WIDTH, 1D, near);
                // sampleTwo2D: out[0]=最近距离, out[1]=其值, out[2]=次近距离, out[3]=其值（均为未除 √2 的欧氏距离）
                final double metric = (near[2] - near[0]) * CLIMATE_WIDTH;
                borderMetric.add(metric);

                final int climate = climateFromValue(climateValue(climateWarp, biomecell, x, z));
                final int neighborClimate = climateFromValue((float) (near[3] * .5D + .5D));

                final boolean border = neighborClimate != climate
                        && metric < CLIMATE_BORDER_DISTANCE_DIFFERENCE;
                if (border) {
                    climateBorderCount++;
                    inBorderStrip++;
                    if (Math.abs(neighborClimate - climate) > 1) {
                        extremeBorder++;
                    }
                }
            }
        }

        System.out.println("--- ③ 边界距离量纲 (d₂ − d₁) × climateWidth ---");
        System.out.printf("%s%n", borderMetric.fmt());
        System.out.println("  单位：格。阈值 288 ⇒ 约 ±144 格的边界带（Voronoi 边界附近 d₂ − d₁ ≈ 2×到边界距离）。");
        System.out.println();

        System.out.println("--- ④ 边界与「极端边界」= 山地链的出现面积 ---");
        System.out.printf("  临近气候边界（换用 border 列表）      ：%6.2f%%%n", 100.0 * climateBorderCount / samples);
        System.out.printf("  其中「极端」|Δclimate| > 1（山地链）  ：%6.2f%%   ← 这就是 veryCold/HotBorder 的覆盖面%n",
                100.0 * extremeBorder / samples);
        System.out.println();
        System.out.println("  说明：只有 |Δclimate| > 1 且边界距 < 288 时，getLandBiomeAt 才会去查");
        System.out.println("        veryColdBorderBiomes / veryHotBorderBiomes —— Support.rebuildExtremeBorderMountains");
        System.out.println("        把 RealisticBiomeMountainChain 包装体放进的就是这两个列表。");
        System.out.println("        该占比过低 ⇒ 山地链几乎看不到；过高 ⇒ 山地链遍地。这是调参的基准数字。");
        System.out.println();

        // 解析「极端」的构成
        System.out.println("--- ⑤ 极端边界的构成分解（当前带 → 邻带）---");
        long[][] extremePairs = new long[5][5];
        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                warpClimateCoordinates(climateWarp, x, z, warped);
                biomecell.sampleTwo2D((warped[0] + 4000D) / CLIMATE_WIDTH, warped[1] / CLIMATE_WIDTH, 1D, near);
                final double metric = (near[2] - near[0]) * CLIMATE_WIDTH;
                final int climate = climateFromValue(climateValue(climateWarp, biomecell, x, z));
                final int neighborClimate = climateFromValue((float) (near[3] * .5D + .5D));
                if (neighborClimate != climate && metric < CLIMATE_BORDER_DISTANCE_DIFFERENCE
                        && Math.abs(neighborClimate - climate) > 1) {
                    extremePairs[climate][neighborClimate]++;
                }
            }
        }
        for (int a = 1; a <= 4; a++) {
            for (int b = 1; b <= 4; b++) {
                if (extremePairs[a][b] > 0) {
                    final String dir = b < a ? "veryColdBorder（向下跳）" : "veryHotBorder（向上跳）";
                    System.out.printf("  %s(%d) → %s(%d)  %6.2f%%   列表：%s[%d]%n",
                            names[a].trim(), a, names[b].trim(), b,
                            100.0 * extremePairs[a][b] / samples, dir, a - 1);
                }
            }
        }
        System.out.println();
        System.out.println("  与 Support.rebuildExtremeBorderMountains 对照：");
        System.out.println("    snow.veryHotBorder  ← snow.hotBorder     （SNOW→HOT/WET）");
        System.out.println("    cold.veryHotBorder  ← cold.hotBorder     （COLD→WET）");
        System.out.println("    hot.veryColdBorder  ← hot.coldBorder     （HOT→SNOW）");
        System.out.println("    wet.veryColdBorder  ← wet.coldBorder     （WET→SNOW/COLD）");
        System.out.println("  其余组合（如 SNOW→COLD 向下、HOT→WET 向上）在默认注册下**池为空**，");
        System.out.println("  会 fall through 到普通 cold/hotBorder 选择 —— 这一点必须与实测构成核对。");
    }

    // ==================================================================
    // RWG ChunkManagerRealistic 的对应实现（逐行对齐 L694-715 / L698-702 / L704-708）
    // ==================================================================

    /** RWG L698-702：四带模式（{@code wetEnabled == true}，默认如此）。 */
    static int climateFromValue(float climate) {
        return climate < SNOW_CLIMATE_LIMIT ? 1 : climate < COLD_CLIMATE_LIMIT ? 2 : climate < HOT_CLIMATE_LIMIT ? 3 : 4;
    }

    /** RWG L704-708。 */
    static float climateValue(PerlinNoise climateWarp, RwgCellNoise biomecell, int x, int y) {
        final double[] warped = new double[2];
        warpClimateCoordinates(climateWarp, x, y, warped);
        return (biomecell.noise((warped[0] + 4000D) / CLIMATE_WIDTH, warped[1] / CLIMATE_WIDTH, 1D) * .5f) + .5f;
    }

    /** RWG L710-715。 */
    static void warpClimateCoordinates(PerlinNoise climateWarp, int x, int y, double[] output) {
        final float scale = CLIMATE_WIDTH * CLIMATE_WARP_SCALE_MULTIPLIER;
        final float strength = CLIMATE_WIDTH * CLIMATE_WARP_STRENGTH_MULTIPLIER;
        output[0] = x + climateWarp.noise2f(x / scale, y / scale) * strength;
        output[1] = y + climateWarp.noise2f((x + 1731f) / scale, (y - 2459f) / scale) * strength;
    }

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
            return String.format("min=%9.5f  mean=%9.5f  max=%9.5f", min, mean(), max);
        }
    }
}
