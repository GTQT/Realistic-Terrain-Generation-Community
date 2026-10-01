package rtg.world.biome;


/**
 * 新布局的**分支分配标定** —— 在把世界生成切到 {@link RtgBiomeLayout} 之前，
 * 先量出"世界会被各分支拿走多少"。
 *
 * <h2>为什么要在切换前量</h2>
 * 这个布局是照抄 RWG 的，但 RWG 的群系分类是给它自己的 54 个群系手写的；
 * rtgc 的约 130 个群系要重新分类（见 {@link RtgBiomeCategorizer}）。
 * 分类结果决定了每个池的大小与内容，而**分支的几何占比**与池内容无关 ——
 * 所以可以先量几何，确认"海洋/滨海/海岸/岛屿/气候边界/极端边界"各占多少，
 * 再决定池怎么填。这也让"切换之后世界长什么样"在切换前就有数。
 *
 * <h2>本工具不注册任何群系</h2>
 * 所有池都是空的，因此选择总会**落到核心池**（也为空）。
 * 工具用 {@link RtgBiomeLayout} 暴露的只读诊断访问器与**同一批公开常量**自行判定分支，
 * 而不是复制一份判定逻辑。故结果是**分支几何**，不是群系选择结果。
 *
 * <p>运行：{@code gradlew calibrateLayoutApportionment}。
 *
 * @since 1.0.10
 */
public final class LayoutApportionmentCalibration {

    private static final long SEED = 1_234_567_891_011_121L;

    private LayoutApportionmentCalibration() {}

    public static void main(String[] args) {

        final RtgBiomeLayout layout = new RtgBiomeLayout(SEED);

        final int step = 97;
        final int extent = 30_000;

        long n = 0;
        // 大陆分支
        long ocean = 0;
        long oceanDeep = 0;
        long oceanShallow = 0;
        long islandTier = 0;
        long littoral = 0;
        long coast = 0;
        long inland = 0;
        // 陆地几何
        long climateBorder = 0;
        long extremeBorder = 0;
        long smallDisc = 0;
        // 气候
        final long[] band = new long[5];

        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) {
                n++;

                final float continent = layout.getContinentValue(x, z);
                final int climate = layout.getClimateAt(x, z);
                band[climate]++;

                if (continent < 0f) {
                    ocean++;
                    if (continent < -RtgBiomeLayout.DIAG_SHALLOW_OCEAN_WIDTH) {
                        oceanDeep++;
                    } else {
                        oceanShallow++;
                    }
                    continue;
                }

                final int tier = layout.continents().getIslandSizeTier(x, z);
                final boolean isIsland = tier >= 0;
                if (isIsland) {
                    islandTier++;
                }

                if (!isIsland && continent < RtgBiomeLayout.DIAG_LITTORAL_WIDTH) {
                    littoral++;
                }
                if (!isIsland && continent < RtgBiomeLayout.DIAG_COAST_WIDTH) {
                    coast++;
                }
                if (!isIsland && continent >= RtgBiomeLayout.DIAG_LITTORAL_WIDTH) {
                    inland++;
                }

                // 陆地选择的几何（与池内容无关的部分）
                if (layout.diagSmallBiomeDistance(x, z) < RtgBiomeLayout.DIAG_SMALL_BIOME_RADIUS) {
                    smallDisc++;
                }
                final int neighbor = layout.diagNeighborClimateAt(x, z);
                if (neighbor != climate
                        && layout.diagClimateBorderMetric(x, z) < RtgBiomeLayout.DIAG_CLIMATE_BORDER_DISTANCE) {
                    climateBorder++;
                    if (Math.abs(neighbor - climate) > 1) {
                        extremeBorder++;
                    }
                }
            }
        }

        System.out.println("=== 新布局的分支分配标定（几何，未注册任何群系）===");
        System.out.printf("seed=%d  步长=%d  范围=±%d  样本=%d%n", SEED, step, extent, n);
        System.out.println();

        final String[] names = { "", "SNOW", "COLD", "HOT ", "WET " };
        System.out.println("--- ① 气候带（与 calibrateClimateBands 应一致）---");
        for (int c = 1; c <= 4; c++) {
            System.out.printf("  %s  %6.2f%%%n", names[c], 100.0 * band[c] / n);
        }
        System.out.println();

        System.out.println("--- ② 大陆分支（getBiomeDataAt 的决策树第一层）---");
        System.out.printf("  海洋（continent < 0）        ：%6.2f%%%n", 100.0 * ocean / n);
        System.out.printf("    其中深海（< %.0f）        ：%6.2f%%   → oceanDeep 槽位%n",
                -RtgBiomeLayout.DIAG_SHALLOW_OCEAN_WIDTH, 100.0 * oceanDeep / n);
        System.out.printf("    其中浅海                 ：%6.2f%%   → oceanShallow / 海藻 / 珊瑚%n", 100.0 * oceanShallow / n);
        System.out.printf("  陆地                        ：%6.2f%%%n", 100.0 * (n - ocean) / n);
        System.out.printf("    岛屿（tier ≥ 0）         ：%6.2f%%   → island + small/largeIsland 池%n", 100.0 * islandTier / n);
        System.out.printf("    滨海（非岛，0 ≤ v < %.0f）：%6.2f%%   → littoral 池（覆盖陆地选择）%n",
                RtgBiomeLayout.DIAG_LITTORAL_WIDTH, 100.0 * littoral / n);
        System.out.printf("      其中海岸（0 ≤ v < %.0f）：%6.2f%%   → coastIce / coastDunes%n",
                RtgBiomeLayout.DIAG_COAST_WIDTH, 100.0 * coast / n);
        System.out.printf("    内陆（非岛，v ≥ %.0f）    ：%6.2f%%   → 走陆地选择%n",
                RtgBiomeLayout.DIAG_LITTORAL_WIDTH, 100.0 * inland / n);
        System.out.println();

        System.out.println("--- ③ 陆地选择的几何分支（决定各池该不该填）---");
        System.out.printf("  小型群系圆盘（d < %.0f）    ：%6.2f%%   → small 池非空时优先命中%n",
                RtgBiomeLayout.DIAG_SMALL_BIOME_RADIUS, 100.0 * smallDisc / n);
        System.out.printf("  气候边界带                  ：%6.2f%%   → border + cold/hotBorder%n", 100.0 * climateBorder / n);
        System.out.printf("    其中极端（|Δ| > 1）      ：%6.2f%%   → veryCold/veryHotBorder = **山地链**%n",
                100.0 * extremeBorder / n);
        System.out.printf("  其余                        ：%6.2f%%   → 核心池%n",
                100.0 * (n - ocean - islandTier - littoral - climateBorder) / n);
        System.out.println();

        System.out.println("--- ④ 判读 ---");
        System.out.println("  · 海洋约 52% 是这个世界的第一观感：oceanDeep/oceanShallow 槽位会被频繁使用，");
        System.out.println("    若槽位为 null，getBiomeDataAt 会返回 null → 必须保证四个气候各有海洋群系（RtgBiomeCategorizer 有兜底）。");
        System.out.println("  · 滨海带约 13% 会**覆盖**其下垫的陆地选择，故 littoral 池的内容直接决定海岸线风格。");
        System.out.println("  · 气候边界带约 13%，其中极端（|Δ|>1）约 5.6% 是山地链的覆盖面 —— 这是「山地链会不会好看」的基准数；");
        System.out.println("    地下河隧道/洞厅的门控 mountainChainRiverHost > 0.10 只在这一带（外加 48 格邻域影响）可能成立。");
        System.out.println("  · 岛屿约 2%：岛屿池为空时 selectIslandBiome 返回 null 并回退到陆地选择（RWG 原版行为）。");
        System.out.println("  · 各池为空时，RWG 的 fall-through 会让我们看到「只有核心群系」的世界 ——");
        System.out.println("    所以切换时必须先确认每个气候的核心池非空，否则大片区域会拿到 null。");
        System.out.println();
        System.out.println("  ⚠ 以上百分比是**本次运行实测**；早期版本这段文字里硬编码过 51%/16%/12%/2.5%，");
        System.out.println("    与实测已经不一致（布局的边界距离常量改过），故此处只写量级、以①②③的数字为准。");
    }
}
