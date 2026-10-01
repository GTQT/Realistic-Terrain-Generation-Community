package rtg.api.util.noise;


/**
 * 噪声后端离线标定：RWG 的经典 Perlin（{@link PerlinNoise}）vs rtgc 一直用的
 * {@link OpenSimplexNoise}。
 *
 * <h2>为什么需要这个</h2>
 * rtgc 的地形常数是**逐行照抄** RWG 的（已逐个与 {@code rwg/terrain/*.java} 比对过），
 * 但长期"抄了却不像"。根因是**噪声场不同**：
 * <ul>
 *   <li>RWG 默认走经典 Perlin —— {@code RwgWorldSavedData.noiseImplementation} 初值为
 *       {@code UNKNOWN}，{@code NoiseSelector} 把 {@code UNKNOWN}/{@code DYNAMICPERLIN}
 *       都映射到 {@code useOpenSimplex = false}，于是
 *       {@code NoiseGeneratorWrapper.noise2()} 转发给 {@code PerlinNoise.noise2()}；</li>
 *   <li>rtgc 只有 {@code OpenSimplexNoise}。</li>
 * </ul>
 * 同样的公式喂不同的噪声场 ⇒ 不同的地貌。
 *
 * <h2>本工具量什么</h2>
 * <ol>
 *   <li><b>幅值</b>：各尺度下两种噪声的 min / mean / max / 标准差。地形常数都是直接乘噪声值的，
 *       所以标准差之比 ≈ 地形起伏之比。</li>
 *   <li><b>特征尺度</b>：沿直线的归一化自相关半衰长度（世界格）。这是对"地貌块头多大"的
 *       **直接**测量，不依赖任何关于谱形的假设。两者若差得多，同样的
 *       {@code noise2(x / width, y / width)} 会给出大小不同的山丘。</li>
 *   <li><b>平坦占比</b>：{@code |v| < 0.1} 的样本比例。经典 Perlin 的方格点阵
 *       在格心附近梯度贡献趋零，容易产出成片接近 0 的区域 —— 这直接关系到
 *       "看起来有一块块平台"的观感。</li>
 * </ol>
 *
 * <p>运行：{@code gradlew calibrateNoiseBackend}（本类零 MC 依赖，也可直接用 javac 单独编译运行）。
 *
 * @since 1.0.9
 */
public final class NoiseBackendCalibration {

    private NoiseBackendCalibration() {}

    /** 与地形函数实际用到的波长一致（取自 TerrainBase 的 INV_* 调用点）。 */
    private static final float[] PITCHES = { 260f, 230f, 180f, 130f, 70f, 50f, 37f, 30f, 20f, 12f, 7f, 5f };

    private static final double SQRT_2 = 1.4142135623730950488;

    public static void main(String[] args) {
        final long seed = 1_234_567_891_011_121L;

        System.out.println("=== 噪声后端标定：RWG 经典 Perlin  vs  rtgc OpenSimplex ===");
        System.out.printf("seed=%d%n", seed);
        System.out.println();
        System.out.println("说明：地形函数形如 value = noise2(x / pitch, y / pitch) * AMPLITUDE。");
        System.out.println("      故「标准差之比」≈ 同一常数下的地形起伏之比；");
        System.out.println("      「自相关半衰长度」≈ 同一 pitch 下山丘的实际块头（世界格）。");
        System.out.println();

        final SimplexNoise perlin = new PerlinNoise(seed);
        final SimplexNoise simplex = new OpenSimplexNoise(seed);

        System.out.printf("%-7s %-40s %-40s %9s %9s%n",
                "pitch", "RWG Perlin  (min / mean / max / std)", "rtgc OpenSimplex (min / mean / max / std)",
                "std比", "尺度比");
        System.out.println(dashes(122));

        for (float pitch : PITCHES) {
            final Stats sp = new Stats();
            final Stats ss = new Stats();
            for (int x = -3000; x <= 3000; x += 11) {
                for (int y = -3000; y <= 3000; y += 13) {
                    sp.add(perlin.noise2f(x / pitch, y / pitch));
                    ss.add(simplex.noise2f(x / pitch, y / pitch));
                }
            }

            final double corrP = correlationLength(perlin, pitch);
            final double corrS = correlationLength(simplex, pitch);

            System.out.printf("%-7s %-40s %-40s %9.4f %9.4f%n",
                    String.format("/%.0f", pitch),
                    sp.fmt(), ss.fmt(),
                    sp.std() / ss.std(),
                    corrP / corrS);
            System.out.printf("%-7s %-40s %-40s%n", "",
                    String.format("  自相关半衰 %.2f 格", corrP),
                    String.format("  自相关半衰 %.2f 格", corrS));
        }

        System.out.println();
        System.out.println("--- 平坦占比（|v| < 0.1，越低越「有起伏」）---");
        System.out.printf("%-7s %14s %14s%n", "pitch", "Perlin", "OpenSimplex");
        for (float pitch : new float[] { 230f, 70f, 20f }) {
            long fp = 0;
            long fs = 0;
            long n = 0;
            for (int x = -3000; x <= 3000; x += 11) {
                for (int y = -3000; y <= 3000; y += 13) {
                    if (Math.abs(perlin.noise2f(x / pitch, y / pitch)) < 0.1f) fp++;
                    if (Math.abs(simplex.noise2f(x / pitch, y / pitch)) < 0.1f) fs++;
                    n++;
                }
            }
            System.out.printf("%-7s %13.2f%% %13.2f%%%n", String.format("/%.0f", pitch),
                    100.0 * fp / n, 100.0 * fs / n);
        }

        System.out.println();
        System.out.println("--- noise1（RWG 的河曲用它：pX = x + noise1(y/240) * 220）---");
        final Stats p1 = new Stats();
        final Stats s1 = new Stats();
        for (int i = -20000; i <= 20000; i += 7) {
            p1.add(((PerlinNoise) perlin).noise1(i / 240f));
            s1.add(simplex.noise2f(i / 240f, 0f));
        }
        System.out.printf("Perlin  noise1(i/240)      : %s%n", p1.fmt());
        System.out.printf("（对照）Simplex noise2(i/240,0): %s%n", s1.fmt());
        System.out.println("  注：RWG 用 noise1 做河曲，rtgc 用 multiEval2D + getDeltaX/Y。");
        System.out.println("      PerlinNoise.multiEval2D 已按 RWG 原写法实现（两轴各用另一轴的一维噪声）。");

        // ---- 关键结论：同一常数下的地形起伏比 ----
        System.out.println();
        System.out.println("=== 结论 ===");
        final Stats ap = new Stats();
        final Stats as = new Stats();
        for (int x = -3000; x <= 3000; x += 11) {
            for (int y = -3000; y <= 3000; y += 13) {
                ap.add(Math.abs(perlin.noise2f(x / 230f, y / 230f)));
                as.add(Math.abs(simplex.noise2f(x / 230f, y / 230f)));
            }
        }
        System.out.printf("pitch=230（terrainHilly 的山体项）平均绝对幅值比 Perlin/Simplex = %.4f%n",
                ap.mean() / as.mean());
        System.out.println("  该比值直接乘在 RWG 抄来的 strength 上 ⇒ 决定山有多高。");
        System.out.println("  它偏离 1 不代表「抄错了」：RWG 的 strength 就是在 Perlin 场上调出来的，");
        System.out.println("  用 Perlin 才是复现 RWG 地貌的正解。");
    }

    /** 沿固定 z 的直线采样，求归一化自相关降到 0.5 的滞后（世界格）。 */
    private static double correlationLength(final SimplexNoise noise, final float pitch) {
        final int samples = 20_000;
        final int maxLag = 400;
        final double[] f = new double[samples];
        for (int i = 0; i < samples; i++) {
            f[i] = noise.noise2f(i / pitch, 1234f / pitch);
        }
        double mean = 0;
        for (double v : f) {
            mean += v;
        }
        mean /= samples;
        double variance = 0;
        for (double v : f) {
            variance += (v - mean) * (v - mean);
        }
        variance /= samples;
        if (variance <= 0) {
            return Double.NaN;
        }
        for (int lag = 1; lag <= maxLag; lag++) {
            double cov = 0;
            for (int i = 0; i + lag < samples; i++) {
                cov += (f[i] - mean) * (f[i + lag] - mean);
            }
            cov /= (samples - lag);
            if (cov / variance < 0.5) {
                return lag;
            }
        }
        return maxLag;
    }

    /** Java 8 目标下没有 {@code String.repeat}。 */
    private static String dashes(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append('-');
        }
        return sb.toString();
    }

    private static final class Stats {

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        double sumSq = 0;
        long n = 0;

        void add(double v) {
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
            sumSq += v * v;
            n++;
        }

        double mean() { return n == 0 ? Double.NaN : sum / n; }

        double std() {
            if (n == 0) {
                return Double.NaN;
            }
            final double m = mean();
            return Math.sqrt(Math.max(0, sumSq / n - m * m));
        }

        String fmt() {
            return String.format("%7.4f /%7.4f /%7.4f /%7.4f", min, mean(), max, std());
        }
    }
}
