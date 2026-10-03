package rtg.world.biome.realistic.land;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.ChunkInfo;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.biome.RealisticBiomeBase.BeachType;
import rtg.api.world.biome.RealisticBiomeBase.RiverType;
import rtg.api.world.deco.AbstractDeco;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStoneMix1;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgRealisticIndex;

import java.util.Collection;
import java.util.Random;


/**
 * 山地链 —— RWG {@code rwg/biomes/realistic/land/RealisticBiomeMountainChain.java} 的移植。
 *
 * <h2>它是什么</h2>
 *
 * RWG 用它填 {@code veryColdBorder}/{@code veryHotBorder}：气候带**隔带相接**的地方
 * （雪原紧挨热带之类）不放大厦般的突变，而是插一条**山脉链**。它是「备份群系的岩石版本」：
 * 共用同一个 MC 群系与同一份装饰，只把地形换成
 * {@code TerrainHilly(230f, 120f, 0f, 260f, 120f)}、地表换成
 * {@link SurfaceMountainStoneMix1}。
 *
 * <h2>与 RWG 的差异（结构性，非公式）</h2>
 *
 * RWG 的 {@code RealisticBiomeMountainChain extends RealisticBiomeBase}，并
 * {@code super(0, backingBiome.baseBiome, …)} —— 靠 RWG 自己的现实主义编号空间让
 * 「链」与「备份群系」共存于同一个 MC 群系之下。
 *
 * <p>rtgc 原先没有这层编号空间，故本类改为：
 * <ul>
 *   <li><b>直接实现 {@link IRealisticBiome}</b>，除地形/地表/替换外**全部委托**给备份群系。
 *       这样可以避免 {@code RealisticBiomeBase} 构造时新建第二份 {@link BiomeConfig}
 *       （同一配置文件读两遍，且 {@code initConfig()} 为空时有把备份群系配置刷掉的风险）。</li>
 *   <li>通过 {@link RtgRealisticIndex} 在合成编号空间里占一个**独立槽位**，
 *       使采样混合数组能把「链」与「备份群系」区分开 —— 这正是 RWG 的 {@code super(0, …)}
 *       所达到的效果。</li>
 * </ul>
 *
 * <h2>装饰：按「平缓采样点比例」缩放（已实现）</h2>
 *
 * RWG 的 {@code rDecorate} 先在 4×4 个采样点上量地表起伏，数出"高度变化 ≤ 4"的平缓点，
 * 再把 {@code strength × 平缓点比例} 传给备份群系的 {@code rDecorate} ——
 * 效果是**陡峭的山体上不长成片森林**，平缓处照常。
 *
 * <p>rtgc 的 {@code IRealisticBiome.rDecorate} **没有 {@code strength} 形参**，
 * 无法表达"按比例缩放"。故这里用**概率**等价：逐个 deco 以
 * {@code 平缓点比例} 的概率触发，期望装饰量与 RWG 的按比例缩放一致
 * （单区块的确定性会不同，已在文档标明）。
 *
 * <p>采样网格也做了一处适配：RWG 用区块内偏移 {@code 8,12,16,20}（会跨到下一区块），
 * 而本方法只拿得到**本区块**的 256 列高度数组，故改用偏移 {@code 4,8,12}
 *（{@code 3×3 = 9} 个采样点，±4 邻居越界时按 RWG 的方式跳过）。
 *
 * @since 1.0.12
 */
public class RealisticBiomeMountainChain implements IRealisticBiome {

    /** 每个 MC 群系一个变体（RWG 的 {@code VARIANTS[biomeID]}）。 */
    private static final RealisticBiomeMountainChain[] VARIANTS = new RealisticBiomeMountainChain[512];

    private final IRealisticBiome backingBiome;
    private final TerrainBase terrain;
    private final SurfaceBase surface;
    private final int indexId;

    private RealisticBiomeMountainChain(final IRealisticBiome backingBiome) {

        this.backingBiome = backingBiome;
        this.terrain = new TerrainMountainChain();

        final Biome backing = backingBiome.baseBiome();
        final IBlockState top = backing.topBlock;
        final IBlockState fill = backing.fillerBlock;

        // RWG: createSurface(...) -> SurfaceMountainStoneMix1(top, fill, false, null,
        //      0f, 1.5f, 60f, 65f, 1.5f, Blocks.stone, 0.20f)
        this.surface = new SurfaceMountainStoneMix1(
                backingBiome.getConfig(), top, fill,
                false, null, 0f, 1.5f, 60f, 65f, 1.5f,
                BlockUtil.getStateClay(net.minecraft.item.EnumDyeColor.CYAN), 0.20f);

        this.indexId = RtgRealisticIndex.register(this);
    }

    /**
     * 取（必要时创建）该备份群系的山地链变体。幂等。
     *
     * @return 该备份群系的链变体；参数不是 rtgc 的现实主义群系时返回 {@code null}
     */
    public static RealisticBiomeMountainChain forBiome(final IRealisticBiome backingBiome) {

        if (backingBiome == null) {
            return null;
        }
        if (backingBiome instanceof RealisticBiomeMountainChain) {
            return (RealisticBiomeMountainChain) backingBiome;
        }
        final int id = backingBiome.baseBiomeId();
        if (id < 0 || id >= VARIANTS.length) {
            return null;
        }
        RealisticBiomeMountainChain variant = VARIANTS[id];
        if (variant == null) {
            variant = new RealisticBiomeMountainChain(backingBiome);
            VARIANTS[id] = variant;
        }
        return variant;
    }

    /** 该链变体在 {@link RtgRealisticIndex} 里的合成编号（{@code -1} 表示槽位耗尽）。 */
    public int indexId() {
        return indexId;
    }

    public IRealisticBiome backingBiome() {
        return backingBiome;
    }

    // ==================================================================
    // 地形与地表：本类唯一不复用备份群系的两件事
    // ==================================================================

    /**
     * RWG {@code new TerrainHilly(230f, 120f, 0f, 260f, 120f)} 的等价物。
     *
     * <p>注意末位是 {@code 120f} 而不是 {@code TerrainHilly` 3 参构造的默认 {@code 68f}} ——
     * 山脉链的基准高度比普通丘陵高出 52 格，这是它"成链"的关键。
     */
    public static class TerrainMountainChain extends TerrainBase {

        @Override
        public float generateNoise(final RTGWorld rtgWorld, final int x, final int y,
                                   final float border, final float river) {

            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 120f);
        }
    }

    @Override
    public TerrainBase terrain() {
        return terrain;
    }

    @Override
    public SurfaceBase surface() {
        return surface;
    }

    @Override
    public TerrainBase initTerrain() {
        return terrain;
    }

    @Override
    public SurfaceBase initSurface() {
        return surface;
    }

    @Override
    public float rNoise(final RTGWorld rtgWorld, final int x, final int y, final float border, final float river) {

        return terrain.generateNoise(rtgWorld, x, y, border, river);
    }

    @Override
    public void rReplace(final ChunkPrimer primer, final int i, final int j, final int x, final int y,
                         final int depth, final RTGWorld rtgWorld, final float[] noise,
                         final float river, final Biome[] base) {

        // RWG: base[x * 16 + y] = backingBiome.baseBiome
        if (base != null) {
            base[x * 16 + y] = backingBiome.baseBiome();
        }
        surface.paintTerrain(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void rReplace(final ChunkPrimer primer, final BlockPos blockPos, final int x, final int y,
                         final int depth, final RTGWorld rtgWorld, final float[] noise,
                         final float river, final Biome[] base) {

        rReplace(primer, blockPos.getX(), blockPos.getZ(), x, y, depth, rtgWorld, noise, river, base);
    }

    // ==================================================================
    // 装饰：按「平缓采样点比例」缩放备份群系的装饰
    // ==================================================================

    /** RWG {@code DECORATION_SAMPLE_STEP}：相邻采样点的间距（格）。 */
    private static final int DECORATION_SAMPLE_STEP = 4;

    /** RWG {@code MAX_DECORATED_HEIGHT_CHANGE}：高度变化不超过它才算"平缓点"。 */
    private static final int MAX_DECORATED_HEIGHT_CHANGE = 4;

    /**
     * RWG {@code RealisticBiomeMountainChain.rDecorate} 的移植。
     *
     * <p>RWG 的做法是量出"平缓点比例"再把它乘进 {@code strength} 传给备份群系。
     * rtgc 的 {@code rDecorate} 没有 {@code strength} 形参，故改用**概率等价**：
     * 逐个 deco 以该比例为概率触发。期望装饰量与 RWG 一致；
     * 单区块的确定性不同（会多消耗随机数），这是本移植唯一的行为近似。
     */
    @Override
    public void rDecorate(final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos,
                          final float river, final boolean hasVillage, final float[] noise) {

        final float fraction = gentleFraction(noise);

        if (fraction > 0f) {
            final ChunkInfo info = new ChunkInfo(chunkPos, rtgWorld, noise);
            for (final DecoBase deco : backingBiome.getDecos()) {
                if (!deco.preGenerate(river)) {
                    continue;
                }
                // 概率缩放：fraction = 1 时全部触发（与备份群系直连时一致），
                // fraction 很小时只有极少数触发 —— 陡坡上因此不会长成片森林。
                if (fraction < 1f && rand.nextFloat() >= fraction) {
                    continue;
                }
                ChunkInfo.noteInvocation();      // D4 诊断：与实际调用点同步计数
                // 与 RWG 一致：deco 收到的是**备份群系**（RWG 的链是把 rDecorate 转给
                // backingBiome.rDecorate，由它把 backups 传给每个 deco），而不是链本身。
                deco.generate(backingBiome, rtgWorld, rand, chunkPos, river, hasVillage, info);
            }
        }

        // 原版装饰：走 `IRealisticBiome#vanillaDecorate` 的**同一份实现**（不再复制一遍，
        // 免得两处漂移）。链永远不会"推迟到冰雪之后"，所以这里语义与以前完全一致。
        vanillaDecorate(rtgWorld, rand, chunkPos);
    }

    /**
     * 本区块「平缓采样点」所占比例（0–1）。RWG {@code :61-78} 的等价物。
     *
     * <p>采样偏移用 {@code 4, 8, 12}（3×3 = 9 点），邻居步长 4 格。
     * RWG 用的是 {@code 8, 12, 16, 20}，但那些坐标会跨到下一区块，
     * 而本方法只拿得到**本区块**的 256 列高度；越界的邻居按 RWG 的做法跳过。
     */
    private static float gentleFraction(final float[] noise) {
        if (noise == null || noise.length < 256) {
            return 1f;      // 拿不到高度就不缩放（回到"照常装饰"的安全默认）
        }

        int sampleCount = 0;
        int gentleCount = 0;

        for (int offsetX = DECORATION_SAMPLE_STEP; offsetX <= 3 * DECORATION_SAMPLE_STEP;
                offsetX += DECORATION_SAMPLE_STEP) {
            for (int offsetZ = DECORATION_SAMPLE_STEP; offsetZ <= 3 * DECORATION_SAMPLE_STEP;
                    offsetZ += DECORATION_SAMPLE_STEP) {

                final float height = noise[offsetX * 16 + offsetZ];
                float change = 0f;
                if (offsetX - DECORATION_SAMPLE_STEP >= 0) {
                    change = Math.max(change, Math.abs(height
                            - noise[(offsetX - DECORATION_SAMPLE_STEP) * 16 + offsetZ]));
                }
                if (offsetX + DECORATION_SAMPLE_STEP < 16) {
                    change = Math.max(change, Math.abs(height
                            - noise[(offsetX + DECORATION_SAMPLE_STEP) * 16 + offsetZ]));
                }
                if (offsetZ - DECORATION_SAMPLE_STEP >= 0) {
                    change = Math.max(change, Math.abs(height
                            - noise[offsetX * 16 + (offsetZ - DECORATION_SAMPLE_STEP)]));
                }
                if (offsetZ + DECORATION_SAMPLE_STEP < 16) {
                    change = Math.max(change, Math.abs(height
                            - noise[offsetX * 16 + (offsetZ + DECORATION_SAMPLE_STEP)]));
                }

                if (change <= MAX_DECORATED_HEIGHT_CHANGE) {
                    gentleCount++;
                }
                sampleCount++;
            }
        }
        return sampleCount == 0 ? 1f : (float) gentleCount / sampleCount;
    }

    // ==================================================================
    // 以下全部委托给备份群系
    // ==================================================================

    @Override
    public Biome baseBiome() {
        return backingBiome.baseBiome();
    }

    @Override
    public ResourceLocation baseBiomeResLoc() {
        return backingBiome.baseBiomeResLoc();
    }

    @Override
    public int baseBiomeId() {
        return backingBiome.baseBiomeId();
    }

    @Override
    public RiverType getRiverType() {
        return backingBiome.getRiverType();
    }

    @Override
    public BeachType getBeachType() {
        return backingBiome.getBeachType();
    }

    @Override
    public IRealisticBiome getRiverBiome() {
        return backingBiome.getRiverBiome();
    }

    @Override
    public IRealisticBiome getBeachBiome() {
        return backingBiome.getBeachBiome();
    }

    @Override
    public Biome preferredBeach() {
        return backingBiome.preferredBeach();
    }

    @Override
    public BiomeConfig getConfig() {
        return backingBiome.getConfig();
    }

    @Override
    public double waterLakeMult() {
        return backingBiome.waterLakeMult();
    }

    @Override
    public double lavaLakeMult() {
        return backingBiome.lavaLakeMult();
    }

    @Override
    public void initDecos() {
        // 装饰由备份群系在 MC 群系层面完成；本对象不持有装饰列表。
    }

    @Override
    public void initConfig() {
        // 配置就是备份群系那份，不做任何新增。
    }

    @Override
    public boolean allowVanillaTrees() {
        return backingBiome.allowVanillaTrees();
    }

    @Override
    public boolean overridesHardcoded() {
        return backingBiome.overridesHardcoded();
    }

    @Override
    public Collection<DecoBase> getDecos() {
        return backingBiome.getDecos();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Collection<TreeRTG> getTrees() {
        return backingBiome.getTrees();
    }

    // 移植上游新树系统 / T5：山地链是合成包装，树装饰一律委托给被包装的群系。
    @Override
    public AbstractDeco getTreeDecos() {
        return backingBiome.getTreeDecos();
    }

    @Override
    public String toString() {
        return "MountainChain[" + backingBiome.baseBiomeResLoc() + "#" + indexId + "]";
    }
}
