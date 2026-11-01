package rtg.world.gen;

import rtg.api.world.biome.IRealisticBiome;



/**
 * @author Zeno410
 */
public class ChunkLandscape {

    public float[] noise = new float[256];
    public IRealisticBiome [] biome = new IRealisticBiome [256];
    public float[] river = new float[256];

    /**
     * **地表**该列用哪个群系（RWG {@code randBiome} 的产物），与 {@link #biome} 分开。
     *
     * <p>RWG {@code ChunkGeneratorRealistic:443-499} 用一张 15 格尺度的噪声
     * （{@code bRand = 0.5f + perlin.noise2(x/15f, y/15f)}）去扫各群系的**累积权重区间**，
     * 谁先越过 {@code bRand} 就把 {@code biomes[]} 改成谁 —— 于是过渡带的**地表**呈噪声状，
     * 而不是沿着混合权重的等值线走成直线。
     *
     * <p><b>为什么必须与 {@link #biome} 分开</b>：{@code biome[]} 同时供
     * ①F3／区块群系数组（{@code ChunkGeneratorRTG.baseBiomesList}）与
     * ②地表方块使用。开抖动之后这两者必然在**过渡带内**不一致 ——
     * 用户已明确同意（「本来就是交界处，无可厚非」），但需要一个独立数组才能做到：
     * F3 继续用 {@link #biome}（= 权重最大的那个），地表用本数组。
     *
     * <p>与 {@link #mountainChainWeight} 同理，**必须随区块保存**：
     * {@code landscape} 可能来自 {@code landscapeCache}，而地表替换发生在之后。
     */
    public IRealisticBiome[] surfaceBiome = new IRealisticBiome[256];

    /**
     * 该列是否被开凿了山间河洞，以及洞顶高度。
     * <p>
     * {@code 0} = 无隧道；否则是**洞顶 y**。由 {@link UndergroundRiver#carve}
     * 在开凿时写入，供装饰期的 {@code RiverCaveVines} 直接读取 ——
     * 这样挂藤蔓不需要在装饰期把隧道门控（干高度 + 上覆岩层 + Voronoi）再算一遍。
     */
    public int[] riverCaveCeiling = new int[256];

    /**
     * 山地链在该列混合权重里占的比例（RWG {@code mountainChainWeight}）。
     * 由 {@code ChunkGeneratorRTG.getNewerNoise} 在混合时写入。
     *
     * <p><b>必须存在这里，不能放生成器字段</b>：{@code landscape} 可能是
     * {@code landscapeCache} 里的对象，而 {@code UndergroundRiver.carve} 是**稍后**才跑的。
     * 若放生成器字段，缓存命中时读到的会是**上一个区块**的值。
     * （{@link #riverCaveCeiling} 当初放进本类也是同一个原因。）
     */
    public float[] mountainChainWeight = new float[256];

    /**
     * 山地链**宿主强度** = {@code max(链权重, 邻域链影响)}（RWG {@code mountainChainRiverHost}）。
     * 山地链内地下河隧道与洞厅的门控就看它。同上，必须随区块保存。
     */
    public float[] mountainChainRiverHost = new float[256];

    /**
     * 该列**未受河道雕刻**的地形高度（俗称"干高度"）。
     *
     * <p>只是诊断/探针用的中间量（{@code /rtg probe} 会读它），**不再参与任何门控**：
     * 暗河的门控已按 RWG 改回 {@link #mountainChainRiverHost}（见
     * {@link UndergroundRiver}）。用"海拔够高"当山体判据会把高原、丘陵、山坳都当成山，
     * 那正是"不是山的地方也生成暗河"的来源。
     *
     * <p>由 {@code ChunkGeneratorRTG} 在施加河道雕刻**之前**写入；没写的路径（例如将来新增的）
     * 使用方一律按 {@code <= 0} 回退到 {@link #noise}。
     */
    public float[] dryHeight = new float[256];

    /**
     * 该列**河道雕刻之后**的高度（RWG {@code riverBedHeight}），即外侧地表河真正的水下河床。
     *
     * <p>RWG 用它决定隧道底：{@code normalRiverFloor = min(62, floor(riverBedHeight) + 1)}、
     * 再取 {@code min(normalRiverFloor, 62 - round(curve * 4))}。所以隧道底**永远不会高于**
     * 外面那条河的河床 —— 划船从洞里出来时不会撞上一道"水下台阶"。
     *
     * <p>与 {@link #dryHeight} 的区别：后者是**雕刻前**的山体高度（可能 90+），本字段是
     * 雕刻后的河床（≈59）。两者不可互换。
     */
    public float[] riverBedHeight = new float[256];
}
