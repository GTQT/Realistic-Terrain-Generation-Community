package rtg.api.util.noise;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 火山 / 熔岩洞**地标密度的离线标定**（不是模组代码，Gradle 不打包它）。
 *
 * <p>为什么需要它：火山原本被 {@code RwgLayoutConfig.averageLandmarksPerTypeAndContinent = 0}
 * 整个关掉，现在按用户要求写回 RWG 原值 {@code 0.25f}。开关"打开"和"真的会生成"是两件事 ——
 * 本项目已经吃过一次亏（群系布局改成团块后"面积占比正确但玩家一个群系也看不到"），
 * 所以这里直接量：**给定种子，火山/熔岩洞到底出现在哪里、多密**。
 *
 * <p>量的是 {@link ContinentalNoise}（纯计算，与生产同一份代码）：
 * <ul>
 *   <li>火山候选列（{@code getVolcanoCoordinates != Long.MIN_VALUE}）；</li>
 *   <li>火山影响圈（{@code getVolcanoVicinityCoordinates}，半径 {@code VOLCANO_ISLAND_RADIUS}）；</li>
 *   <li>岛屿火山（{@code isIslandVolcano}，由 {@code largeIslandVolcanoChance} 决定）；</li>
 *   <li>熔岩洞（{@code getLavaCaveCoordinates}）；</li>
 *   <li>去重后的**火山中心**个数 ⇒ 换算成平均间距。</li>
 * </ul>
 *
 * <p>运行：{@code gradlew calibrateVolcanoPlacement}
 */
public final class VolcanoPlacementCalibration {

    /** 采样窗口（格，以原点为中心）。20000² 够看到十几座火山。 */
    private static final int WINDOW = 20_000;
    /** 采样步长（格）。32 足够粗（火山半径 130）。 */
    private static final int STEP = 32;

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 123_456_789L;
        // 与 RtgBiomeLayout 构造时完全同一个种子盐（见 RtgBiomeLayout:203）
        ContinentalNoise noise = new ContinentalNoise(seed ^ 0x6A09E667F3BCC909L);

        System.out.printf("seed=%d  window=%d  step=%d  （RwgLayoutConfig: 地标密度=%.2f 大岛火山概率=%.2f）%n",
                seed, WINDOW, STEP,
                RwgLayoutConfig.averageLandmarksPerTypeAndContinent, RwgLayoutConfig.largeIslandVolcanoChance);
        System.out.printf("半径常量: 火山影响圈 VOLCANO_ISLAND_RADIUS=%.0f  火山锥 VOLCANO_RADIUS=%.0f%n%n",
                ContinentalNoise.VOLCANO_ISLAND_RADIUS, ContinentalNoise.VOLCANO_RADIUS);

        long volcanoColumns = 0;
        long vicinityColumns = 0;
        long islandVolcanoColumns = 0;
        long lavaCaveColumns = 0;
        long samples = 0;
        Set<Long> centers = new HashSet<>();
        Set<Long> lavaCaveCenters = new HashSet<>();

        for (int z = -WINDOW / 2; z < WINDOW / 2; z += STEP) {
            for (int x = -WINDOW / 2; x < WINDOW / 2; x += STEP) {
                samples++;
                if (noise.getVolcanoCoordinates(x, z) != Long.MIN_VALUE) {
                    volcanoColumns++;
                    centers.add(noise.getVolcanoCenterCoordinates(x, z));
                }
                if (noise.getVolcanoVicinityCoordinates(x, z) != Long.MIN_VALUE) {
                    vicinityColumns++;
                }
                if (noise.isIslandVolcano(x, z)) {
                    islandVolcanoColumns++;
                }
                if (noise.getLavaCaveCoordinates(x, z) != Long.MIN_VALUE) {
                    lavaCaveColumns++;
                    lavaCaveCenters.add(noise.getLavaCaveCenterCoordinates(x, z));
                }
            }
        }

        System.out.println("--- 列命中率（采样列数 " + samples + "）---");
        row("火山锥内（可长火山）", volcanoColumns, samples);
        row("火山影响圈内（含外围收坡）", vicinityColumns, samples);
        row("其中是岛屿火山", islandVolcanoColumns, samples);
        row("熔岩洞内", lavaCaveColumns, samples);

        System.out.println();
        System.out.println("--- 去重后的中心点 ---");
        System.out.println("  火山中心数 = " + centers.size() + "   熔岩洞中心数 = " + lavaCaveCenters.size());
        if (!centers.isEmpty()) {
            double area = (double) WINDOW * WINDOW;
            double perVolcano = area / centers.size();
            System.out.printf("  ⇒ 平均每座火山占地 %.0f 格²（≈ 间距 %.0f 格）%n",
                    perVolcano, Math.sqrt(perVolcano));
        } else {
            System.out.println("  ⚠ 窗口内**一座火山都没有** —— 开关虽打开，但密度或门控仍然挡住了它");
        }
        if (!lavaCaveCenters.isEmpty()) {
            double perCave = (double) WINDOW * WINDOW / lavaCaveCenters.size();
            System.out.printf("  ⇒ 每个熔岩洞占地 %.0f 格²（≈ 间距 %.0f 格）%n",
                    perCave, Math.sqrt(perCave));
        }

        // 中心最近邻距离：只对火山做，样本量小，直接 O(n²)
        if (centers.size() > 1 && centers.size() <= 4000) {
            long[] xs = new long[centers.size()];
            long[] zs = new long[centers.size()];
            int i = 0;
            for (long c : centers) {
                xs[i] = c >> 32;
                zs[i] = (int) c;
                i++;
            }
            double[] nearest = new double[xs.length];
            for (int a = 0; a < xs.length; a++) {
                double best = Double.MAX_VALUE;
                for (int b = 0; b < xs.length; b++) {
                    if (a == b) {
                        continue;
                    }
                    double dx = xs[a] - xs[b];
                    double dz = zs[a] - zs[b];
                    best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
                }
                nearest[a] = best;
            }
            Arrays.sort(nearest);
            System.out.printf("  火山中心最近邻间距：p10=%.0f p50=%.0f p90=%.0f 格%n",
                    q(nearest, 0.10), q(nearest, 0.50), q(nearest, 0.90));
        }

        System.out.println();
        System.out.println("--- 山顶门控（RealisticBiomeIslandVolcano.canGenerateAtHeight）---");
        System.out.println("  判据：baseHeight + rimHeightOffset(40~60) + 2 <= 250  ⇒ baseHeight 上限约 188~208");
        for (float base : new float[] {63f, 80f, 100f, 150f, 190f, 210f}) {
            // 与 RealisticBiomeIslandVolcano.getRimHeightOffset 同一公式（那里是 private static）
            float rim = Math.max(40f, Math.min(60f, 44f + (base - 63f) * 0.2f));
            System.out.printf("  baseHeight=%5.0f ⇒ 岩缘 +%.0f、山顶 %.0f ⇒ %s%n",
                    base, rim, base + rim + 2f, (base + rim + 2f <= 250f) ? "通过" : "**拒绝**（整座火山不生成）");
        }
    }

    private static void row(String label, long hit, long total) {
        System.out.printf("  %-26s %8d / %8d = %6.3f%%%n", label, hit, total, 100.0D * hit / total);
    }

    private static double q(double[] sorted, double p) {
        int i = (int) Math.round(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }
}
