package rtg.world.biome;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.UnaryOperator;

import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.RtgBiomeLayout.Climate;
import rtg.world.biome.RtgBiomeLayout.Placement;


/**
 * 「地下河 / 天窗为什么几乎看不到」的**离线实测**（跑真正的 {@link RtgBiomeLayout}，不复制逻辑）。
 *
 * <h2>要量的东西</h2>
 *
 * {@code UndergroundRiver.carve} 的门控是一条**四级与**（代码行号见该类注释）：
 * <ol>
 *   <li>{@code getRiverStrength(x,z) < 0} —— 列在河网带内；</li>
 *   <li>{@code getRiverTunnelStrength > 0 || getRiverJunctionStrength > 0} —— 在隧道带 / 交汇盘内；</li>
 *   <li>{@code mountainChainRiverHost > 0.10} —— 山地链宿主（本工具只能用**离散选择**做下界，
 *       生产用的是四层混合权重 + 21×21 邻域膨胀，覆盖更宽）；</li>
 *   <li>天窗另加 {@code junction × mountainHost × overheadHost > 0.70} —— 三个 ≤1 的因子相乘。</li>
 * </ol>
 *
 * 本工具用生产环境的**池大小**（取自 {@code run/logs/latest.log} 的 debugLayout 输出）建桩，
 * 于是"落在极端边界列"的比例、河网/隧道/交汇带的占比、以及交汇强度的分布都能精确量出来。
 * 第 3 条只能给**下界**（离散选择），第 4 条里的 {@code overheadHost} 需要真实地形高度，
 * 离线拿不到，故只报告 {@code junction} 因子的分布并说明它与另两个因子相乘。
 *
 * <p>运行：{@code gradlew calibrateRiverTunnels}
 */
public final class RiverTunnelFrequencyCalibration {

    private static final long SEED = 1_234_567_891_011_121L;
    /** 隧道带只有 9 格宽（{@code 9/1250} 单元），步长必须够小，否则会漏采样。 */
    private static final int STEP = 4;
    private static final int EXTENT = 2_000;

    /** 生产环境的池大小（run/logs/latest.log 的 debugLayout 输出，SNOW/COLD/HOT/WET）。 */
    private static final int[] CORE = { 13, 39, 22, 25 };
    private static final int[] COLD_BORDER = { 0, 1, 1, 2 };
    private static final int[] HOT_BORDER = { 1, 0, 0, 0 };

    private RiverTunnelFrequencyCalibration() {}

    // ------------------------------------------------------------------ 桩 ----

    private static final class Stub implements InvocationHandler {

        final int id;
        final String label;

        Stub(final int id, final String label) { this.id = id; this.label = label; }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            switch (method.getName()) {
                case "hashCode": return this.id;
                case "equals": return proxy == (args == null || args.length == 0 ? null : args[0]);
                case "toString": return this.label;
                default: return defaultOf(method.getReturnType());
            }
        }

        private static Object defaultOf(final Class<?> t) {
            if (!t.isPrimitive()) return null;
            if (t == boolean.class) return Boolean.FALSE;
            if (t == float.class) return 0f;
            if (t == double.class) return 0d;
            if (t == int.class) return 0;
            if (t == long.class) return 0L;
            if (t == short.class) return (short) 0;
            if (t == byte.class) return (byte) 0;
            if (t == char.class) return (char) 0;
            return null;
        }
    }

    private static IRealisticBiome stub(final int id, final String label) {
        return (IRealisticBiome) Proxy.newProxyInstance(
                RiverTunnelFrequencyCalibration.class.getClassLoader(),
                new Class<?>[] { IRealisticBiome.class },
                new Stub(id, label));
    }

    // -------------------------------------------------------------- 构建布局 ----

    private static RtgBiomeLayout buildProductionShapedLayout(final int[] chainIds) {
        final RtgBiomeLayout layout = new RtgBiomeLayout(SEED);
        int id = 1;
        for (final Climate c : Climate.values()) {
            for (int k = 0; k < CORE[c.ordinal()]; k++) {
                layout.add(stub(id++, c + "#" + k), c, Placement.CORE);
            }
            for (int k = 0; k < COLD_BORDER[c.ordinal()]; k++) {
                layout.add(stub(id++, c + "-cold#" + k), c, Placement.COLD_BORDER);
            }
            for (int k = 0; k < HOT_BORDER[c.ordinal()]; k++) {
                layout.add(stub(id++, c + "-hot#" + k), c, Placement.HOT_BORDER);
            }
        }
        // 与 RtgLayoutAccess 一样：极端边界池由方向池镜像而来，并包成山地链
        final UnaryOperator<IRealisticBiome> wrap = b -> stub(chainIds[0]++, "chain(" + b + ")");
        layout.rebuildExtremeBorderMountains(wrap);
        return layout;
    }

    private static boolean isChain(final IRealisticBiome b) {
        return b != null && b.toString().startsWith("chain(");
    }

    // ---------------------------------------------------------------- 主流程 ----

    public static void main(final String[] args) {

        final RtgBiomeLayout layout = buildProductionShapedLayout(new int[] { 100_000 });

        System.out.println("=== 地下河 / 天窗频率实测（离线，跑真正的 RtgBiomeLayout）===");
        System.out.printf("seed=%d  步长=%d  范围=±%d%n", SEED, STEP, EXTENT);
        System.out.println();
        System.out.println("--- 池大小（应与 run/logs/latest.log 一致）---");
        for (final Climate c : Climate.values()) {
            System.out.printf("  %-5s CORE=%d COLD_BORDER=%d HOT_BORDER=%d VERY_COLD=%d VERY_HOT=%d%n",
                    c, layout.poolSize(c, Placement.CORE), layout.poolSize(c, Placement.COLD_BORDER),
                    layout.poolSize(c, Placement.HOT_BORDER),
                    layout.poolSize(c, Placement.VERY_COLD_BORDER),
                    layout.poolSize(c, Placement.VERY_HOT_BORDER));
        }
        System.out.println();

        long total = 0;
        long riverBand = 0;
        long tunnelBand = 0;
        long junctionBand = 0;
        long tunnelOrJunction = 0;
        long extreme = 0;
        long extremeRiver = 0;
        long extremeRiverTunnel = 0;
        long skylightJunction = 0;              // junction > 0.70（天窗的第一因子）
        long skylightChainJunction = 0;
        final long[] tunnelHist = new long[5];      // >0.1 / >0.3 / >0.5 / >0.7 / >0.9
        final long[] junctionHist = new long[5];

        for (int x = -EXTENT; x <= EXTENT; x += STEP) {
            for (int z = -EXTENT; z <= EXTENT; z += STEP) {
                total++;

                final float river = layout.getRiverStrength(x, z);
                final boolean inRiver = river < 0f;
                if (inRiver) riverBand++;

                final float tunnel = layout.getRiverTunnelStrength(x, z);
                final float junction = layout.getRiverJunctionStrength(x, z);
                final boolean inTunnel = tunnel > 0f;
                final boolean inJunction = junction > 0f;
                if (inTunnel) tunnelBand++;
                if (inJunction) junctionBand++;
                if (inTunnel || inJunction) tunnelOrJunction++;

                for (int b = 0; b < 5; b++) {
                    final float t = 0.1f + b * 0.2f;
                    if (tunnel > t) tunnelHist[b]++;
                    if (junction > t) junctionHist[b]++;
                }
                if (junction > 0.70f) skylightJunction++;

                final boolean chain = isChain(layout.getLandBiomeAt(x, z));
                if (chain) {
                    extreme++;
                    if (inRiver) {
                        extremeRiver++;
                        if (inTunnel || inJunction) extremeRiverTunnel++;
                    }
                    if (junction > 0.70f) skylightChainJunction++;
                }
            }
        }

        final double pct = 100d / total;
        System.out.printf("采样列数            : %d%n", total);
        System.out.println();
        System.out.println("--- 四级门控各自的命中率 ---");
        System.out.printf("① 河网带 river<0            : %8d  %7.3f%%%n", riverBand, riverBand * pct);
        System.out.printf("② 隧道带 tunnel>0           : %8d  %7.4f%%%n", tunnelBand, tunnelBand * pct);
        System.out.printf("   交汇盘 junction>0        : %8d  %7.4f%%%n", junctionBand, junctionBand * pct);
        System.out.printf("   合并 (tunnel|junction)>0 : %8d  %7.4f%%%n", tunnelOrJunction, tunnelOrJunction * pct);
        System.out.printf("③ 山地链列（**离散选择**下界，生产更宽）: %8d  %7.3f%%%n", extreme, extreme * pct);
        System.out.println();
        System.out.println("--- 交集（真正的『隧道会被开凿』）---");
        System.out.printf("③∩①   链 ∧ 河网带                 : %8d  %7.4f%%%n", extremeRiver, extremeRiver * pct);
        System.out.printf("③∩①∩② 链 ∧ 河网 ∧ (隧道|交汇)    : %8d  %7.5f%%%n",
                extremeRiverTunnel, extremeRiverTunnel * pct);
        System.out.println();
        System.out.println("--- 天窗：还需 junction × mountainHost × overheadHost > 0.70 ---");
        System.out.printf("junction>0.70（全体）              : %8d  %7.4f%%%n", skylightJunction, skylightJunction * pct);
        System.out.printf("junction>0.70 ∧ 链列               : %8d  %7.5f%%%n",
                skylightChainJunction, skylightChainJunction * pct);
        System.out.println("（另两个因子：mountainHost=smoothstep((chainHost-0.10)/0.40)、"
                + "overheadHost=smoothstep((surface-76)/24) —— 都要接近 1 才行）");
        System.out.println();
        System.out.println("--- 强度分布（决定带宽；天窗只取 >0.70 那一段）---");
        System.out.println("阈值      tunnel     junction");
        for (int b = 0; b < 5; b++) {
            System.out.printf(">%.1f   %7.4f%%   %7.4f%%%n",
                    0.1f + b * 0.2f, tunnelHist[b] * pct, junctionHist[b] * pct);
        }

        geometryComparison();
        degenerateShare();
    }

    // --------------------------------------------- 退化列（"一格水线"）的占比 ----

    /**
     * 纯隧道列（{@code junction == 0}）里，{@code floor == ceiling} 的占比 —— 那种列只会写出
     * **一格水在石头里**（上下皆实心），正是"地下河变少、水面只有一层"的观感来源。
     * 隧道强度在带内由弱到强，所以**离带外最近的**恰恰是这些列，{@code /rtg tunnels} 按距离排序
     * 时最容易把它们报出来。
     */
    private static void degenerateShare() {
        long carved = 0, oneLayer = 0, twoOrLess = 0;
        for (int x = -EXTENT; x <= EXTENT; x += STEP) {
            for (int z = -EXTENT; z <= EXTENT; z += STEP) {
                final float tunnel = layoutOf().getRiverTunnelStrength(x, z);
                final float junction = layoutOf().getRiverJunctionStrength(x, z);
                if (tunnel <= 0f && junction <= 0f) continue;
                if (junction > 0f) continue;                  // 只看纯隧道列
                carved++;
                final float tc = (float) Math.sqrt(tunnel);
                final int floor = 62 - Math.round(tc * 4f);
                final int ceiling = 62 + Math.round(tc * 8f);   // rtgc 微调：RWG 是 11（见 UndergroundRiver.ROOF_RISE）
                if (ceiling - floor < 1) oneLayer++;           // floor == ceiling ⇒ 只有一格水
                if (ceiling - floor < 2) twoOrLess++;
            }
        }
        final double p = 100d / Math.max(1, carved);
        System.out.println();
        System.out.println("--- 纯隧道列的退化占比（守卫要挡掉的就是这些）---");
        System.out.printf("纯隧道列            : %8d%n", carved);
        System.out.printf("floor == ceiling（一格水，上下皆实心）: %8d  %6.3f%%%n", oneLayer, oneLayer * p);
        System.out.printf("水层 <2 格          : %8d  %6.3f%%%n", twoOrLess, twoOrLess * p);
    }

    private static RtgBiomeLayout layoutOf() {
        if (SHARED == null) {
            SHARED = buildProductionShapedLayout(new int[] { 300_000 });
        }
        return SHARED;
    }

    private static RtgBiomeLayout SHARED;

    // ------------------------------------------------- 几何对照（选项 A 的效果）----

    /**
     * 同一列在**已撤销的下移版**与**现行（RWG 几何 + 洞顶 8）**下的洞体与水线。
     *
     * <p>现行版的三条要点都在表里：
     * <ul>
     *   <li><b>水面恒 62</b>（W 列）⇒ 洞里与外面的河同一个水平面；</li>
     *   <li><b>埋深 = 地表 − 洞顶</b>：正值 = 带顶棚的暗河；**负值 = 洞顶冒出地表 = 天然河口/峡谷**
     *       ⇒ 那里就是从河面划船进山的地方（不用潜水）；</li>
     *   <li>下移版（已撤销）的埋深恒为 +10（永远进不去），且水面被压到 29–60（接不上外面的河）。</li>
     * </ul>
     * 取 tunnel=1、junction=1、mountainHost=overheadHost=1 的最强情形。
     */
    private static void geometryComparison() {
        System.out.println();
        System.out.println("--- 几何对照（最强洞厅；W=水面顶，埋深 = 地表 − 洞顶，<0 表示洞顶冒出地表 = 河口）---");
        System.out.println("surface | 下移(已撤销)          | 现行(RWG 几何,洞顶8)   | 形态");
        final int[] surfaces = { 50, 59, 63, 70, 73, 76, 83, 90, 110 };
        for (final int surface : surfaces) {
            final int[] a = oldGeometry(surface, 1f, 1f);
            final int[] b = rwgGeometry(surface, 1f, 1f);
            final int burialB = surface - b[1];
            System.out.printf("%7d | %3d..%3d W=%3d 埋%+4d | %3d..%3d W=%3d 埋%+4d | %s%n",
                    surface,
                    a[0], a[1], a[2], surface - a[1],
                    b[0], b[1], b[2], burialB,
                    burialB < 0 ? "洞顶露出地表 ⇒ 河口（可划船进）"
                            : (burialB < 10 ? "屋顶很薄（接近河口）" : "带顶棚的暗河"));
        }
        System.out.println("（下移版：水面跟着地形掉、洞底被挖到 y≈34、洞顶恒在地表下 10 格 ⇒ 深沟 + 进不去；"
                + "现行版：水面平在 62、洞底 58–62、地表低于洞顶处天然开口）");
    }

    /** 已撤销的"按地表下移"版（保留作对照）。返回 {floor, ceiling, waterTop}。 */
    private static int[] oldGeometry(final int surface, final float tunnel, final float junction) {
        final int center = Math.min(62, surface - 10 - 11);
        final float tc = (float) Math.sqrt(tunnel);
        int floor = center - Math.round(tc * 4f);
        int ceiling = center + Math.round(tc * 11f);
        final float chamber = junction;                      // mountainHost = overheadHost = 1
        if (chamber > 0f) {
            final float cc = (float) Math.sqrt(chamber);
            floor = Math.min(floor, center + 1 - Math.round(cc * 23f));
            ceiling = Math.max(ceiling, center + 1 + Math.round(cc * 42f));
            ceiling = Math.min(ceiling, surface - 10);
            ceiling = Math.max(ceiling, center + Math.round(tc * 11f));
            ceiling = Math.max(ceiling, floor + 1);
        }
        return new int[] { floor, ceiling, center };
    }

    /** 现行版 = RWG 原样（中心恒 62、洞厅基准 63、水面恒 62）。返回 {floor, ceiling, waterTop}。 */
    private static int[] rwgGeometry(final int surface, final float tunnel, final float junction) {
        final float tc = (float) Math.sqrt(tunnel);
        int floor = 62 - Math.round(tc * 4f);
        int ceiling = 62 + Math.round(tc * 8f);      // rtgc 微调：RWG 是 11
        if (junction > 0f) {
            final float cc = (float) Math.sqrt(junction);
            floor = Math.min(floor, 63 - Math.round(cc * 23f));
            ceiling = Math.max(ceiling, 63 + Math.round(cc * 42f));
            ceiling = Math.min(ceiling, surface - 10);
            ceiling = Math.max(ceiling, 62 + Math.round(tc * 8f));
        }
        return new int[] { floor, ceiling, 62 };
    }
}
