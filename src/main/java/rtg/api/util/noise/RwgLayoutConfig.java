package rtg.api.util.noise;


/**
 * RWG 大陆场/群系布局的**常量配置**（垫片）。
 *
 * <h2>来源</h2>
 * RWG 把这些放在 {@code rwg/config/ConfigRWG.java} 里作为 Forge 配置项。
 * 移植时按用户要求**不再新增可调配置**（此前已把逃生开关全部删除），
 * 故这里直接硬编码 RWG 的**默认值**，与 {@code ConfigRWG} 的字段初值逐位一致。
 *
 * <h2>为什么这些值会改变世界</h2>
 * {@code ContinentalNoise} 读取其中 10 个字段来构造大陆场；RWG 在其文档里说明
 * {@code getValue()} 的含义是**「离岸距离，单位为格，陆地为正、海洋为负」**。
 * 群系布局里所有与海有关的阈值（{@code 0f} / {@code -20f} / {@code -90f} / {@code -150f} /
 * {@code -300f} / {@code 24f} / {@code 432f}）都是在这个尺度上比较的，
 * 因此**改动这些值等于整体缩放海陆比例**。
 *
 * <h2>默认值的后果（已推演，未实测）</h2>
 * {@code minimumContinentWidth == maximumContinentWidth == 2700} ⇒ 大陆宽度无随机范围；
 * {@code maximumOceanFraction == 1.0} ⇒ {@code continentDilation} 立即返回 0（海洋占比不加权），
 * 也就是海洋与陆地的比例完全由大陆场的几何决定。
 *
 * @since 1.0.10
 */
public final class RwgLayoutConfig {

    private RwgLayoutConfig() {}

    // ---- 大陆 ----
    /** 大陆宽度下限（格）。RWG 默认 {@code 2700f}。 */
    public static final float minimumContinentWidth = 2700f;
    /** 大陆宽度上限（格）。RWG 默认 {@code 2700f}（与下限相等 ⇒ 无随机范围）。 */
    public static final float maximumContinentWidth = 2700f;

    // ---- 海洋 ----
    /** 平均海洋宽度（格）。用于推算 Voronoi 半径。RWG 默认 {@code 100f}。 */
    public static final float averageOceanWidth = 100f;
    /** 海洋宽度下限（格）。RWG 默认 {@code 50f}。 */
    public static final float minimumOceanWidth = 50f;
    /**
     * 海洋占比上限。RWG 默认 {@code 1.0f}。
     * <p>
     * {@code == 1.0f} 时 {@code ContinentalNoise.continentDilation} 立即返回 0，
     * 即不做海洋占比矫正。
     */
    public static final float maximumOceanFraction = 1.0f;

    // ---- 岛屿 ----
    /** 岛屿宽度下限（格）。RWG 默认 {@code 300f}。 */
    public static final float minimumIslandWidth = 300f;
    /** 岛屿宽度上限（格）。RWG 默认 {@code 600f}。 */
    public static final float maximumIslandWidth = 600f;
    /** 岛屿出现概率。RWG 默认 {@code 0.30f}。 */
    public static final float islandPlacementChance = 0.30f;
    /** 大岛屿带火山的概率。RWG 默认 {@code 0.15f}（照抄）。 */
    public static final float largeIslandVolcanoChance = 0.15f;
    /**
     * 每个大陆每种地标的平均数量。RWG 默认 {@code 0.25f}（照抄）。
     * <p>
     * 这是地标子系统的**唯一开关**：{@code ContinentalNoise.sampleLandform} 里
     * 地标采样被包在 {@code if (continent >= 0D && RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f)}
     * 中。置 0 之后该分支永不进入，于是火山与熔岩洞**完全不会出现**。
     * <p>
     * <b>历史</b>：这条一度是 {@code 0f} —— 用户先前明确要求删除火山与地标，
     * 当时就靠这一个数把它整个关掉（{@code ContinentalNoise} 本身一行都不用改，保持照抄）。
     * 后来用户要求「**写回火山的全部内容**」，故恢复 RWG 原值 0.25f；
     * 火山本体（`MapVolcano` / `RealisticBiomeIslandVolcano` / 岩浆房 / 熔岩洞地标 /
     * 地标装饰）也已按 RWG 逐行移植接线，不再是"关掉即完事"。
     */
    public static final float averageLandmarksPerTypeAndContinent = 0.25f;

    // ---- 坐标偏移 ----
    // RWG 用它们做「同一种子下的不同地貌取景」。默认全 0，与 RWG 一致。
    /** 大陆场 X 偏移。RWG 默认 {@code 0}。 */
    public static final int landmassOffsetX = 0;
    /** 大陆场 Z 偏移。RWG 默认 {@code 0}。 */
    public static final int landmassOffsetZ = 0;
    /** 群系场 X 偏移。RWG 默认 {@code 0}。 */
    public static final int biomeOffsetX = 0;
    /** 群系场 Z 偏移。RWG 默认 {@code 0}。 */
    public static final int biomeOffsetZ = 0;
}
