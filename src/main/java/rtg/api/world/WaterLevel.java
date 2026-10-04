package rtg.api.world;


/**
 * 水位基准的**单一真相源**。
 * <p>
 * 背景：{@code seaLevel} 在世界创建界面可调（31–95，见 {@code GuiCustomizeWorldScreenRTG}），
 * 但地形代码里 {@code 61.5 / 62 / 63 / 64.5 / 69} 等水位相关字面量散落在
 * {@code TerrainBase}、{@code BiomeAnalyzer}、各 {@code Surface*} / {@code RealisticBiome*} 中，
 * 导致调整海平面只改变水面高度，沙滩线、悬崖带、河流水面、表层门槛全部不动。
 * 本类把这些值统一派生自 {@link #seaLevel()}。
 * <p>
 * <b>为什么是静态</b>：{@code TerrainBase.calcCliff()} 与 {@code TerrainBase.rwgRiverBed()}
 * 被约 300 个群系类调用，改签名会波及数百个文件。二者都是无 {@code RTGWorld} 参数的静态方法，
 * 因此这里用"生成期当前水位"的静态持有者桥接。
 * <p>
 * <b>约束</b>：RTG 的区块生成依赖实例级可复用缓冲（{@code hugeRender}/{@code smallRender} 等），
 * 本身即为单线程串行生成；{@code ChunkGeneratorRTG} 构造时设置本值，同一时刻只有一个生成器在跑。
 * 新增并行生成能力时必须改为显式传参。
 *
 * @since 1.0.8
 */
public final class WaterLevel {

    /** 1.12.2 原版海平面，也是 {@code RTGChunkGenSettings.Factory#seaLevel} 的默认值。 */
    public static final int DEFAULT_SEA_LEVEL = 63;

    private static volatile WaterLevel current = new WaterLevel(DEFAULT_SEA_LEVEL);

    private final int seaLevel;

    private WaterLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    /** 生成期当前水位；未显式设置时为原版默认值。 */
    public static WaterLevel current() {
        return current;
    }

    /** 由 {@code ChunkGeneratorRTG} 在其构造时调用。 */
    public static void setCurrent(int seaLevel) {
        current = new WaterLevel(seaLevel);
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** 水面顶部方块高度（原字面量 62）。 */
    public int waterSurfaceTop() {
        return seaLevel - 1;
    }

    /** 河流水面高度（原字面量 61.5f）。 */
    public float riverSurface() {
        return seaLevel - 1.5f;
    }

    /** 河岸/悬崖判定带下沿，亦为海滩下沿（原字面量 61.5f，见 {@code TerrainBase#calcCliff}）。 */
    public float cliffBandLow() {
        return seaLevel - 1.5f;
    }

    /** 河岸/悬崖判定带上沿，亦为沙滩上限（原字面量 64.5f）。 */
    public float cliffBandHigh() {
        return seaLevel + 1.5f;
    }

    /** 表层顶块的最低高度门槛（原字面量 61，见 {@code SurfaceRiverOasis}）。 */
    public int surfaceTopMin() {
        return seaLevel - 2;
    }

    /** 浅海表层强制铺沙的高度上限（原字面量 69，见 {@code RealisticBiomeVanillaOcean}）。 */
    public int shallowOceanTop() {
        return seaLevel + 6;
    }
}
