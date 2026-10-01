package rtg.api.util.noise;


/**
 * 大陆场离线标定 —— 验证 RWG {@code ContinentalNoise} 与 {@code RwgLayoutConfig} 的默认值。
 *
 * <h2>为什么需要它</h2>
 * 群系布局里所有与海有关的阈值都在 {@code ContinentalNoise.getValue()} 的尺度上比较
 * （{@code 0f} / {@code -20f} / {@code -90f} / {@code -150f} / {@code -300f} / {@code 24f} / {@code 432f}），
 * 而该值的含义是**「离岸距离，单位为格，陆地为正、海洋为负」**。
 * 因此"世界有多少海洋""滨海带占多少"完全由大陆场的分布决定。
 * 在把它接进世界生成之前，必须先知道默认参数产出的是什么样的世界。
 *
 * <h2>本工具量什么</h2>
 * <ol>
 *   <li>{@code getValue} 的分布与**海陆比**；</li>
 *   <li>布局实际会用到的每个**阈值带**的面积占比
 *       （深海 / 珊瑚带 / 海藻带 / 浅海 / 海岸替换带 / 滨海带 / 内陆）；</li>
 *   <li>岛屿占比与大小分级（tier −1/0/1）；</li>
 *   <li><b>地标是否真的被关闭</b> —— {@code RwgLayoutConfig.averageLandmarksPerTypeAndContinent = 0}
 *       应当使火山与熔岩洞**全部返回 {@code Long.MIN_VALUE}**。这是"不改 {@code ContinentalNoise}
 *       一行代码就关掉地标"这一做法的验证。</li>
 * </ol>
 *
 * <p>运行：{@code gradlew calibrateContinentalField}。
 *
 * @since 1.0.10
 */
public final class ContinentalFieldCalibration {

    private static final long SEED = 1_234_567_891_011_121L;

    /** 布局里用到的阈值（逐字取自 RWG {@code ChunkManagerRealistic}）。 */
    private static final float DEEP_OCEAN = -300f;      // < 此值为深海
    private static final float CORAL_MIN = -150f;       // 珊瑚带下界
    private static final float CORAL_MAX = -20f;        // 珊瑚带上界
    private static final float KELP = -90f;             // 海藻带
    private static final float COAST = 24f;             // 海岸替换带
    private static final float LITTORAL = 432f;         // 滨海带

    private ContinentalFieldCalibration() {}

    public static void main(String[] args) {
        final ContinentalNoise continents = new ContinentalNoise(SEED);

        System.out.println("=== 大陆场标定：RWG ContinentalNoise + RwgLayoutConfig 默认值 ===");
        System.out.printf("seed=%d%n", SEED);
        System.out.printf("大陆宽度 %.0f..%.0f 格；平均海洋宽 %.0f；海洋占比上限 %.2f%n",
                RwgLayoutConfig.minimumContinentWidth, RwgLayoutConfig.maximumContinentWidth,
                RwgLayoutConfig.averageOceanWidth, RwgLayoutConfig.maximumOceanFraction);
        System.out.printf("岛屿宽度 %.0f..%.0f；岛屿概率 %.2f%n",
                RwgLayoutConfig.minimumIslandWidth, RwgLayoutConfig.maximumIslandWidth,
                RwgLayoutConfig.islandPlacementChance);
        System.out.printf("地标密度 %.2f（0 = 关闭地标）%n", RwgLayoutConfig.averageLandmarksPerTypeAndContinent);
        System.out.println();

        final int step = 97;
        final int extent = 30_000;

        final Stats value = new Stats();
        long n = 0;
        long ocean = 0;
        long deep = 0;
        long coral = 0;
        long kelp = 0;
        long shallow = 0;
        long coast = 0;
        long littoral = 0;
        long inland = 0;
        long islands = 0;
        final long[] tiers = new long[3];   // index: tier+1 → 0 小岛 / 1 非岛 / 2 大岛
        long volcanoHits = 0;
        long lavaCaveHits = 0;

        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                n++;
                final float v = continents.getValue(x, z);
                value.add(v);

                if (v < 0f) {
                    ocean++;
                    if (v < DEEP_OCEAN) deep++;
                    if (v < KELP) kelp++;
                    if (v > CORAL_MIN && v < CORAL_MAX) coral++;
                } else {
                    if (v < COAST) coast++;
                    if (v < LITTORAL) littoral++;
                    else inland++;
                }
                if (v < 0f && v >= DEEP_OCEAN) shallow++;

                final int tier = continents.getIslandSizeTier(x, z);
                tiers[tier + 1]++;
                if (continents.isIsland(x, z)) islands++;

                if (continents.getVolcanoCoordinates(x, z) != Long.MIN_VALUE) volcanoHits++;
                if (continents.getLavaCaveCoordinates(x, z) != Long.MIN_VALUE) lavaCaveHits++;
            }
        }

        System.out.println("--- ① getValue 分布（单位：格，陆地为正） ---");
        System.out.println("  " + value.fmt());
        System.out.println();

        System.out.printf("--- ② 海陆比（%d 个样本，步长 %d，范围 ±%d）---%n", n, step, extent);
        System.out.printf("  海洋 (v < 0)                  ：%6.2f%%%n", 100.0 * ocean / n);
        System.out.printf("  陆地 (v ≥ 0)                  ：%6.2f%%%n", 100.0 * (n - ocean) / n);
        System.out.println();

        System.out.println("--- ③ 布局阈值带的面积占比（皆以全图为分母） ---");
        System.out.printf("  深海   v < %-6.0f            ：%6.2f%%   → 用 oceanDeep* 群系%n", DEEP_OCEAN, 100.0 * deep / n);
        System.out.printf("  浅海   %-6.0f ≤ v < 0       ：%6.2f%%   → 用 oceanShallow* / 海藻 / 珊瑚%n", DEEP_OCEAN, 100.0 * shallow / n);
        System.out.printf("  海藻带 v < %-6.0f（寒/冷带）：%6.2f%%   → 用 oceanShallowKelp%n", KELP, 100.0 * kelp / n);
        System.out.printf("  珊瑚带 %-6.0f < v < %-6.0f   ：%6.2f%%   → 用 oceanShallowCoral%n", CORAL_MIN, CORAL_MAX, 100.0 * coral / n);
        System.out.printf("  海岸带 0 ≤ v < %-6.0f        ：%6.2f%%   → 被 coastIce / coastDunes 替换%n", COAST, 100.0 * coast / n);
        System.out.printf("  滨海带 0 ≤ v < %-6.0f        ：%6.2f%%   → 被 littoral 列表替换%n", LITTORAL, 100.0 * littoral / n);
        System.out.printf("  内陆   v ≥ %-6.0f            ：%6.2f%%   → 走气候带核心/边界选择%n", LITTORAL, 100.0 * inland / n);
        System.out.println();

        System.out.println("--- ④ 岛屿 ---");
        System.out.printf("  isIsland                     ：%6.3f%%%n", 100.0 * islands / n);
        System.out.printf("  tier = -1（非岛）            ：%6.2f%%%n", 100.0 * tiers[0] / n);
        System.out.printf("  tier =  0（小岛 → smallIsland）：%6.2f%%%n", 100.0 * tiers[1] / n);
        System.out.printf("  tier =  1（大岛 → largeIsland）：%6.2f%%%n", 100.0 * tiers[2] / n);
        System.out.println();

        System.out.println("--- ⑤ 地标是否确实被关闭 ---");
        System.out.printf("  getVolcanoCoordinates 非 MIN 的样本 ：%d%n", volcanoHits);
        System.out.printf("  getLavaCaveCoordinates 非 MIN 的样本：%d%n", lavaCaveHits);
        if (volcanoHits == 0 && lavaCaveHits == 0) {
            System.out.println("  ✓ 全部为 Long.MIN_VALUE —— averageLandmarksPerTypeAndContinent = 0 生效，");
            System.out.println("    地标子系统整体失效，且 ContinentalNoise 未改动一行。");
        } else {
            System.out.println("  ✗ 仍有地标出现 —— 关闭逻辑未生效，需要检查。");
        }
        System.out.println();
        System.out.println("判读：海洋占比是这个世界观感的第一决定因素。RWG 的大陆宽度是 2700 格（无随机范围），");
        System.out.println("      若海洋占比异常（过低或过高），应先调 RwgLayoutConfig 再动布局代码。");
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
            return String.format("min=%10.2f  mean=%10.2f  max=%10.2f", min, mean(), max);
        }
    }
}
