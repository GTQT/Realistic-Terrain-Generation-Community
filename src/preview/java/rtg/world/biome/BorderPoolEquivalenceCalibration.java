package rtg.world.biome;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.UnaryOperator;

import rtg.api.world.biome.IRealisticBiome;
import rtg.world.biome.RtgBiomeLayout.Climate;
import rtg.world.biome.RtgBiomeLayout.Placement;


/**
 * 边界池改动的**数值验收**（离线，跑真正的 {@link RtgBiomeLayout}，不是复制一份逻辑）。
 *
 * <h2>要验证的三个命题</h2>
 *
 * <ol>
 *   <li><b>命题 A（推翻 C1 的依据）</b>：把核心池镜像进三个方向池，对**群系选择是恒等变换**。
 *       做法：同一 seed 建两个布局，一个只有 core，一个额外把 core 复制进
 *       {@code BORDER}/{@code COLD_BORDER}/{@code HOT_BORDER}（**都不**建极端池），
 *       逐列比对 {@code getLandBiomeAt} 的返回值身份。预期差异列数 = 0。</li>
 *   <li><b>命题 B（本轮真正的改动）</b>：生产配置（RWG 的方向池成员）与旧配置（镜像整个核心池）
 *       的差异**只出现在气候边界列**，且极端边界（山地链）的可选群系从"每个陆地群系各一条链"
 *       降到 RWG 那几条。做法：两个布局都调用
 *       {@code rebuildExtremeBorderMountains(wrap)}，逐列比对并按分支分类计数。</li>
 *   <li><b>命题 C（可选项数）</b>：四个气候的极端边界池大小之和 = 山地链的变体数
 *       （生产值应回到 RWG 的量级，而不是 104）。</li>
 * </ol>
 *
 * <h2>为什么用 {@link Proxy} 而不是真群系</h2>
 * 本工具只关心"选到了哪一个池成员"，不关心它是谁。用动态代理造出**身份可区分**的桩，
 * 既能跑真实的 {@code add()} / {@code getLandBiomeAt()} / {@code rebuildExtremeBorderMountains()}，
 * 又不需要 MC 运行时（真群系要 {@code Biome} 实例，那要游戏进程）。
 * {@code getLandBiomeAt} 全程不碰 {@code baseBiome()}，所以桩返回 null 是安全的。
 *
 * <p>运行：{@code gradlew calibrateBorderPools}
 */
public final class BorderPoolEquivalenceCalibration {

    private static final long SEED = 1_234_567_891_011_121L;
    private static final int STEP = 31;
    private static final int EXTENT = 11_000;
    /** 每个气候的核心池放几个桩（旧配置会把这几个全部镜像进方向池）。 */
    private static final int CORE_PER_CLIMATE = 4;

    private BorderPoolEquivalenceCalibration() {}

    // ------------------------------------------------------------------ 桩 ----

    /** 身份可区分的 {@link IRealisticBiome} 桩。{@code id} 唯一，用于比对"选到了谁"。 */
    private static final class Stub implements InvocationHandler {

        final int id;
        final String label;

        Stub(final int id, final String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            switch (method.getName()) {
                case "hashCode":
                    return this.id;
                case "equals":
                    return proxy == (args == null || args.length == 0 ? null : args[0]);
                case "toString":
                    return this.label;
                default:
                    return defaultOf(method.getReturnType());
            }
        }

        private static Object defaultOf(final Class<?> t) {
            if (!t.isPrimitive()) return null;
            if (t == boolean.class) return Boolean.FALSE;
            if (t == float.class) return 0f;
            if (t == double.class) return 0d;
            if (t == long.class) return 0L;
            if (t == int.class) return 0;
            if (t == short.class) return (short) 0;
            if (t == byte.class) return (byte) 0;
            if (t == char.class) return (char) 0;
            return null;
        }
    }

    private static IRealisticBiome stub(final int id, final String label) {
        return (IRealisticBiome) Proxy.newProxyInstance(
                BorderPoolEquivalenceCalibration.class.getClassLoader(),
                new Class<?>[] { IRealisticBiome.class },
                new Stub(id, label));
    }

    /** 从桩的标签（{@code "WET#2"} 或 {@code "chain(WET#2)"}）里取出 core 下标；取不到给 0。 */
    private static int coreIndexOf(final String label) {
        final int hash = label.lastIndexOf('#');
        if (hash < 0 || hash + 1 >= label.length()) {
            return 0;
        }
        int end = hash + 1;
        while (end < label.length() && Character.isDigit(label.charAt(end))) {
            end++;
        }
        try {
            final int k = Integer.parseInt(label.substring(hash + 1, end));
            return k >= 0 && k < CORE_PER_CLIMATE ? k : 0;
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static void printHist(final int[] hist, final long total) {
        for (int k = 0; k < hist.length; k++) {
            System.out.printf("core[%d]=%.1f%%  ", k, total == 0 ? 0d : 100.0 * hist[k] / total);
        }
        System.out.println();
    }

    // -------------------------------------------------------------- 构建配置 ----

    /** 生产配置里的方向池成员（抄 {@code RtgBiomeCategorizer.RWG_PLACEMENTS} 能对上的那几条）。 */
    private static void addRwgDirectionalMembers(final RtgBiomeLayout layout, final IRealisticBiome[][] core) {
        // chaparral → HOT / COLD_BORDER
        layout.add(core[Climate.HOT.ordinal()][0], Climate.HOT, Placement.COLD_BORDER);
        // meadow → COLD / COLD_BORDER
        layout.add(core[Climate.COLD.ordinal()][0], Climate.COLD, Placement.COLD_BORDER);
        // rainforest / tropicalRainforest → WET / COLD_BORDER
        layout.add(core[Climate.WET.ordinal()][0], Climate.WET, Placement.COLD_BORDER);
        layout.add(core[Climate.WET.ordinal()][1], Climate.WET, Placement.COLD_BORDER);
        // borealForest → SNOW / HOT_BORDER
        layout.add(core[Climate.SNOW.ordinal()][0], Climate.SNOW, Placement.HOT_BORDER);
        // ⚠ jadeCliffs（COLD / HOT_BORDER）在 BOP 1.12.2 已不存在 ⇒ COLD 的 veryHotBorder 为空
    }

    /** 旧配置：把核心池镜像进 BORDER / COLD_BORDER / HOT_BORDER（= 已删除的 mirrorCoreIntoBorders）。 */
    private static void mirrorCore(final RtgBiomeLayout layout) {
        for (final Climate c : Climate.values()) {
            final java.util.List<IRealisticBiome> corePool = layout.pool(c, Placement.CORE);
            layout.pool(c, Placement.BORDER).addAll(corePool);
            layout.pool(c, Placement.COLD_BORDER).addAll(corePool);
            layout.pool(c, Placement.HOT_BORDER).addAll(corePool);
        }
    }

    /**
     * 建一个布局。
     *
     * @param mirror     是否镜像核心池进方向池（旧配置）
     * @param rwgMembers 是否加 RWG 的方向池成员（生产配置）
     * @param wrap       是否建极端边界（山地链）池；{@code true} 时把方向池成员包成新桩
     * @param chainIds   用于给"链"分配新的桩 id（引用数组，便于回传）
     */
    private static RtgBiomeLayout build(final boolean mirror, final boolean rwgMembers,
                                        final boolean wrap, final int[] chainIds) {
        final RtgBiomeLayout layout = new RtgBiomeLayout(SEED);
        final IRealisticBiome[][] core = new IRealisticBiome[4][CORE_PER_CLIMATE];
        int id = 1;
        for (final Climate c : Climate.values()) {
            for (int k = 0; k < CORE_PER_CLIMATE; k++) {
                core[c.ordinal()][k] = stub(id, c + "#" + k);
                layout.add(core[c.ordinal()][k], c, Placement.CORE);
                id++;
            }
        }
        if (mirror) {
            mirrorCore(layout);
        }
        if (rwgMembers) {
            addRwgDirectionalMembers(layout, core);
        }
        if (wrap) {
            final UnaryOperator<IRealisticBiome> wrapFn = b -> stub(chainIds[0]++, "chain(" + b + ")");
            layout.rebuildExtremeBorderMountains(wrapFn);
        }
        return layout;
    }

    // ---------------------------------------------------------------- 主流程 ----

    public static void main(final String[] args) {

        final int[] chainIdsOld = { 100_000 };
        final int[] chainIdsNew = { 200_000 };

        // A：都不建极端池 —— 只有"镜像"这一个差别
        final RtgBiomeLayout aCoreOnly = build(false, false, false, new int[] { 0 });
        final RtgBiomeLayout aMirrored = build(true, false, false, new int[] { 0 });

        // B：都建极端池 —— 旧(镜像) vs 新(RWG 成员)
        final RtgBiomeLayout bOld = build(true, false, true, chainIdsOld);
        final RtgBiomeLayout bNew = build(false, true, true, chainIdsNew);

        System.out.println("=== 边界池改动的数值验收（离线，跑真正的 RtgBiomeLayout）===");
        System.out.printf("seed=%d  步长=%d  范围=±%d%n", SEED, STEP, EXTENT);
        System.out.println();

        System.out.println("--- 池大小 ---");
        for (final Climate c : Climate.values()) {
            System.out.printf("  %-5s CORE=%d | 旧: COLD_BORDER=%d HOT_BORDER=%d VERY_COLD=%d VERY_HOT=%d"
                            + " | 新: COLD_BORDER=%d HOT_BORDER=%d VERY_COLD=%d VERY_HOT=%d%n",
                    c,
                    bOld.poolSize(c, Placement.CORE),
                    bOld.poolSize(c, Placement.COLD_BORDER), bOld.poolSize(c, Placement.HOT_BORDER),
                    bOld.poolSize(c, Placement.VERY_COLD_BORDER), bOld.poolSize(c, Placement.VERY_HOT_BORDER),
                    bNew.poolSize(c, Placement.COLD_BORDER), bNew.poolSize(c, Placement.HOT_BORDER),
                    bNew.poolSize(c, Placement.VERY_COLD_BORDER), bNew.poolSize(c, Placement.VERY_HOT_BORDER));
        }
        int oldChains = 0;
        int newChains = 0;
        for (final Climate c : Climate.values()) {
            oldChains += bOld.poolSize(c, Placement.VERY_COLD_BORDER) + bOld.poolSize(c, Placement.VERY_HOT_BORDER);
            newChains += bNew.poolSize(c, Placement.VERY_COLD_BORDER) + bNew.poolSize(c, Placement.VERY_HOT_BORDER);
        }
        System.out.printf("  山地链变体数：旧 = %d   新 = %d%n", oldChains, newChains);
        System.out.println();

        // ---- 逐列采样 ----
        long n = 0;
        long ocean = 0;
        long island = 0;
        long smallDisc = 0;
        long climateBorder = 0;
        long extremeBorder = 0;

        long aDiff = 0;

        long bDiff = 0;
        long bDiffSmall = 0;
        long bDiffBorder = 0;
        long bDiffExtreme = 0;
        long bDiffCore = 0;

        final java.util.Set<String> aDiffSamples = new java.util.LinkedHashSet<>();
        final java.util.Set<String> bDiffSamples = new java.util.LinkedHashSet<>();
        final int[] aHistCoreOnly = new int[CORE_PER_CLIMATE];
        final int[] aHistMirrored = new int[CORE_PER_CLIMATE];
        long aBorderCols = 0;

        for (int x = -EXTENT; x <= EXTENT; x += STEP) {
            for (int z = -EXTENT; z <= EXTENT; z += STEP) {
                n++;

                // ---- 分支分类（用布局自己的诊断访问器，不复制判定逻辑）----
                final boolean isOcean = aCoreOnly.getContinentValue(x, z) < 0f;
                final int climate = aCoreOnly.getClimateAt(x, z);
                final int tier = isOcean ? -1
                        : aCoreOnly.continents().getIslandSizeTier(
                                RtgBiomeLayout.landmassX(x), RtgBiomeLayout.landmassZ(z));
                final boolean isSmall = !isOcean && tier < 0
                        && aCoreOnly.diagHasSmallBiomes(climate)
                        && aCoreOnly.diagSmallBiomeDistance(x, z) < RtgBiomeLayout.DIAG_SMALL_BIOME_RADIUS;
                final int neighbor = aCoreOnly.diagNeighborClimateAt(x, z);
                final boolean isBorder = !isOcean && !isSmall
                        && neighbor != climate
                        && aCoreOnly.diagClimateBorderMetric(x, z) < RtgBiomeLayout.DIAG_CLIMATE_BORDER_DISTANCE;
                final boolean isExtreme = isBorder && Math.abs(neighbor - climate) > 1;

                if (isOcean) ocean++;
                else if (tier >= 0) island++;
                else if (isSmall) smallDisc++;
                else if (isBorder) climateBorder++;
                if (isExtreme) extremeBorder++;

                // ---- 命题 A ----
                // ⚠ 必须比**标签**而不是对象身份：两个布局各自 new 了一批桩，
                // "同一个池成员"在两个布局里是两个不同的代理对象。第一版就是栽在这里
                // （比身份 ⇒ 100% "差异"，看起来像"命题 A 被推翻"，其实是工具错了）。
                final String pa = String.valueOf(aCoreOnly.getLandBiomeAt(x, z, climate));
                final String pb = String.valueOf(aMirrored.getLandBiomeAt(x, z, climate));
                if (!pa.equals(pb)) {
                    aDiff++;
                    if (aDiffSamples.size() < 5) {
                        aDiffSamples.add("(" + x + "," + z + ") climate=" + climate
                                + " coreOnly=" + pa + " mirrored=" + pb);
                    }
                }
                // 同时统计**分布**：逐点不同不等于比例不同。只在气候边界列统计
                // （镜像只在那条分支被读到），看两边选到 core[k] 的频率。
                if (isBorder) {
                    aHistCoreOnly[coreIndexOf(pa)]++;
                    aHistMirrored[coreIndexOf(pb)]++;
                    aBorderCols++;
                }

                // ---- 命题 B ----
                if (!isOcean) {
                    final String qa = String.valueOf(bOld.getLandBiomeAt(x, z, climate));
                    final String qb = String.valueOf(bNew.getLandBiomeAt(x, z, climate));
                    if (!qa.equals(qb)) {
                        bDiff++;
                        if (bDiffSamples.size() < 5) {
                            bDiffSamples.add("(" + x + "," + z + ") climate=" + climate
                                    + (isSmall ? " [small]" : isExtreme ? " [extreme]" : isBorder ? " [border]" : " [core]")
                                    + " 旧=" + qa + " 新=" + qb);
                        }
                        if (isSmall) bDiffSmall++;
                        else if (isExtreme) bDiffExtreme++;
                        else if (isBorder) bDiffBorder++;
                        else bDiffCore++;
                    }
                }
            }
        }

        System.out.println("--- 采样分支占比（本工具的口径：海洋/岛屿/小型圆盘/边界按同一优先级）---");
        System.out.printf("  样本=%d  海洋=%.2f%%  岛屿=%.2f%%  小型群系圆盘=%.2f%%  气候边界=%.2f%%（其中极端=%.2f%%）%n",
                n, 100.0 * ocean / n, 100.0 * island / n, 100.0 * smallDisc / n,
                100.0 * climateBorder / n, 100.0 * extremeBorder / n);
        System.out.println();

        System.out.println("--- 命题 A：镜像核心池 vs 池留空（都不建极端池）---");
        System.out.printf("  差异列数 = %d / %d  （%.2f%%）%n", aDiff, n, 100.0 * aDiff / n);
        for (final String s : aDiffSamples) {
            System.out.println("    例: " + s);
        }
        System.out.println();
        System.out.printf("--- 命题 A 的**分布**部分：只看气候边界列（%d 列）---%n", aBorderCols);
        System.out.print("  池留空  : ");
        printHist(aHistCoreOnly, aBorderCols);
        System.out.print("  镜像核心: ");
        printHist(aHistMirrored, aBorderCols);
        System.out.println("  判读：**逐点选择不同、但宏观占比几乎不变**——");
        System.out.println("        `selectBiome(core,4)` 把 value 切成 4 段（每段 0.25 → core[k]），");
        System.out.println("        而 `selectCombinedBiome(core,core)` 切成 8 段（core[0..3] 各出现两次）。");
        System.out.println("        两边的分段**边界不在同一位置** ⇒ 同一列会选到不同的 core[k]；");
        System.out.println("        但每个成员仍各占约 1/4 ⇒ 宏观比例不变、逐点选择变了。");
        System.out.println("        （两个直方图都不会精确等于 25%：`sampleBiomeSelector` 在有限采样点上");
        System.out.println("          并非均匀分布；镜像那一版反而更接近均匀，因为它把每段又对半切了一次。）");
        System.out.println();
        System.out.println(aDiff == 0
                ? "  ⇒ 恒等变换（逐点相同）。"
                : "  ⇒ ⚠ **不是恒等变换**（上一轮的说法要改）：分布相同、逐点不同。");
        System.out.println();

        System.out.println("--- 命题 B：旧配置（镜像整个核心池）vs 新配置（RWG 方向池成员）---");
        System.out.printf("  差异列数 = %d / %d  （%.2f%%）%n", bDiff, n, 100.0 * bDiff / n);
        System.out.printf("    其中：小型圆盘=%d  极端边界=%d  普通气候边界=%d  其余=%d%n",
                bDiffSmall, bDiffExtreme, bDiffBorder, bDiffCore);
        for (final String s : bDiffSamples) {
            System.out.println("    例: " + s);
        }
        System.out.println("  判读：差异应当**只落在气候边界列**（因为方向池只在边界分支被读，");
        System.out.println("        小型圆盘分支在边界判定之前就返回了）。落在\"其余\"说明有意外影响。");
        System.out.println();

        System.out.println("--- 命题 C：极端边界（山地链）变体数 ---");
        System.out.printf("  旧 = %d（= 每个气候的整个核心池各镜像一次）  新 = %d（= RWG 标注能对上的那几条）%n",
                oldChains, newChains);
        System.out.println("  判读：`syntheticCount()` 的实测值此前是 104，与\"旧\"同量级；");
        System.out.println("        RWG 自身只有 6 个（chaparral/meadow/rainforest/tropicalRainforest/");
        System.out.println("        borealForest/jadeCliffs），其中 jadeCliffs 在 BOP 1.12.2 不存在。");
    }
}
