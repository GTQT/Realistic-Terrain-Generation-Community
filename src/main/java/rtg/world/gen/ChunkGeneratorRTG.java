package rtg.world.gen;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.structure.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent.EventType;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.common.Loader;
import org.dimdev.jeid.INewChunk;
import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Category;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.util.noise.ISimplexData2D;
import rtg.api.util.noise.SimplexData2D;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.api.world.gen.feature.WorldGenPond;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;
import rtg.world.biome.RtgRealisticIndex;
import rtg.world.biome.realistic.land.RealisticBiomeMountainChain;
import rtg.world.biome.BiomeAnalyzer;
import rtg.world.gen.structure.WoodlandMansionRTG;

import javax.annotation.Nullable;
import java.util.*;

public class ChunkGeneratorRTG implements IChunkGenerator {

    private static final IBlockState STONE = Blocks.STONE.getDefaultState();
    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState BEDROCK = Blocks.BEDROCK.getDefaultState();

    /**
     * 洞厅（天窗连通口）的**地表高度权重**基准，RWG {@code ChunkGeneratorRealistic:602}
     * 的 {@code overheadHost = smoothstep((surface - 76f) / 24f)}。
     *
     * <p>⚠ 它**只是权重，不是门控**。真正的硬门控是 {@link #MOUNTAIN_CHAIN_RIVER_HOST_MIN}
     * （山地链宿主强度），见 {@link #carveRiverTunnels}。
     *
     * <p><b>历史（F-39，勿重犯）</b>：还没有山地链的时候，这里曾被当作硬门控
     * {@code 干高度 >= 76} 使用，而隧道带完全落在河网带内部、地表被河流压平到河面附近（约 63），
     * 于是 {@code surface >= 76} **永不成立**，整段开凿代码是死路径
     * （profiler 实测 {@code RIVER_TUNNELS} 仅 0.01 ms/区块，与之一致）。
     * 1.0.10 先改成"去掉河流后的干高度"，1.0.13 才换成 RWG 原本的山地链宿主门控。
     */
    private static final float TUNNEL_MIN_SURFACE = 76f;

    /** 隧道的中心高度与上/下起伏量（格）。RWG 原值：中心 62、上 +11、下 −4。 */
    private static final int TUNNEL_CENTER_Y = 62;
    private static final int TUNNEL_ROOF_RISE = 11;
    private static final int TUNNEL_FLOOR_DROP = 4;

    /**
     * 洞顶之上必须保留的岩层厚度（格）。
     * <p>
     * 当实际地表低于 {@code TUNNEL_CENTER_Y + TUNNEL_ROOF_RISE}（即 73）时，整条隧道**整体下移**，
     * 使洞顶始终留在地表以下。在山地（地表 ≥ 84）时该约束不起作用，
     * 隧道自动回到 RWG 的原始高度 58–73 —— 即"地形够高就照原版，不够高就沉下去"。
     */
    private static final int TUNNEL_ROOF_CLEARANCE = 10;

    /** 山地链邻域影响的半径（格）。RWG {@code MOUNTAIN_CHAIN_INFLUENCE_RADIUS = 48f}。 */
    private static final int MOUNTAIN_CHAIN_INFLUENCE_RADIUS = 48;

    /**
     * 山地链 fade 的起点与宽度（RWG {@code ChunkGeneratorRealistic:552}）：
     * {@code fade = clamp((mountainChainWeight − 0.35) / 0.65)}，再做 smoothstep。
     * {@code fade = 1} 时该列**完全不接受河道雕刻**。
     */
    private static final float MOUNTAIN_CHAIN_FADE_START = 0.35f;
    private static final float MOUNTAIN_CHAIN_FADE_WIDTH = 0.65f;
    /** 隧道/洞厅要求的最小山地链宿主强度（RWG {@code ChunkGeneratorRealistic:586}）。 */
    private static final float MOUNTAIN_CHAIN_RIVER_HOST_MIN = 0.10f;

    /**
     * 装饰阶段超过该值就打印一行归因（{@code [RTG-DECOPROF]}）。
     * 200ms 远高于平均的 ~17ms，正常游玩不会出现。
     */
    private static final long DECO_REPORT_THRESHOLD_NS = 200_000_000L;

    /**
     * RWG {@code ChunkGeneratorRealistic:975}：9×9 区块邻域中每个命中群系的装饰权重
     * （{@code 1f / 81f}）。RWG 源码里写作字面量 {@code 0.01234569f}。
     */
    private static final float RWG_DECO_NEIGHBOUR_WEIGHT = 0.01234569f;

    private static final IBlockState ICE = Blocks.ICE.getDefaultState();
    private static final IBlockState SNOW_LAYER = Blocks.SNOW_LAYER.getDefaultState();

    public final RTGWorld rtgWorld;
    private final RTGChunkGenSettings settings;
    private final MapGenBase caveGenerator;
    private final MapGenBase ravineGenerator;
    private final MapGenStronghold strongholdGenerator;
    private final WoodlandMansionRTG woodlandMansionGenerator;
    private final MapGenMineshaft mineshaftGenerator;
    private final MapGenVillage villageGenerator;
    private final MapGenScatteredFeature scatteredFeatureGenerator;
    private final StructureOceanMonument oceanMonumentGenerator;
    private final World world;
    /**
     * 生物群系 ID 维度的大小：1.12.2 原版为 256，装 JEID/NEID/REID 时为 65536。
     * 该值只用于按生物群系 ID 索引的数组；16*16=256 的列索引数组不可使用此值。
     */
    private final int biomeCount;
    private final float[][] hugeRender;
    private final float[][] smallRender;
    private final float parabolicFieldTotalInv;
    private final Map<ChunkPos, ChunkLandscape> landscapeCache;
    /**
     * {@code carveRiverTunnels} 用的临时数组：{@code [0]} = 隧道强度，{@code [1]} = 洞厅强度。
     * 两者共用同一个 Voronoi，故一次性求出，避免每列算两遍 {@code warpedRiverVoronoi}。
     */
    private final float[] riverStrengths = new float[2];

    /**
     * 本区块里"把原版装饰推迟到冰雪之后"的海洋群系 → 其混合权重
     * （RWG {@code ChunkGeneratorRealistic:998} 的 {@code deferredOceanDecorations}）。
     *
     * <p>RWG 的海洋群系 {@code rDecorate} 是空的、不装饰；它把装饰记在这里，
     * 等冰/雪 pass 跑完再由 {@link rtg.api.world.biome.IRealisticBiome#rDecorateAfterIce} 补上。
     * 这样随后铺的冰就不会把刚放下的珊瑚/海草压掉。
     */
    private float[] deferredOceanDecorations;
    private final int sampleSize = 8;
    private final int sampleArraySize = sampleSize * 2 + 5;
    private final int[] biomeData = new int[sampleArraySize * sampleArraySize];
    /**
     * 每区块预解析一次的「该采样点是不是山地链」表。
     * <p>
     * {@code nearbyMountainChainInfluence} 对**每一列**都要扫 21×21 个采样点，
     * 若在 441×256 ≈ 11 万次内层循环里反复解析编号＋判类型，代价可观。
     * 这里在采样网格刚建好时一次性解析 441 次，内层只剩一次布尔数组读取。
     */
    private final boolean[] sampleIsChain = new boolean[sampleArraySize * sampleArraySize];
    /**
     * RWG {@code ChunkGeneratorRealistic:320-340} 的 {@code activeBiomeIds} / {@code activeBiomeFlags}。
     *
     * <p>采样网格里**实际出现过**的群系编号（升序）。{@code mix4} 与高度求和都只遍历这张表，
     * 而不是 0..256：一张 21×21 的采样网通常只命中 1–8 个群系，所以这是 RWG 的核心性能设计。
     *
     * <p><b>升序是契约</b>：RWG 在 {@code :337} 专门注明"保留旧的升序编号遍历顺序，
     * 以保证确定性的选择与浮点累加顺序"。这里同样按升序构建。
     *
     * <p>为什么不会读到脏值：{@code hugeRender}/{@code smallRender} 的每一个被写入的格子
     * 都先对**当前** active 表清零（{@link #clearActiveBiomes}），而所有读取都只在 active 表内进行；
     * 非 active 的编号在整张金字塔里恒为 0（数组初始值），从不被读、也从不影响结果。
     */
    private final int[] activeBiomeIds;
    private final boolean[] activeBiomeFlags;
    private int activeBiomeCount;
    private final BiomeAnalyzer analyzer = new BiomeAnalyzer();
    private final int[] xyinverted = analyzer.xyinverted();
    private final boolean mapFeaturesEnabled;
    private final Random rand;
    private final WorldGenPond waterSurfaceLakeGenerator = new WorldGenPond(WATER);
    private final WorldGenPond lavaSurfaceLakeGenerator = new WorldGenPond(Blocks.LAVA.getDefaultState());
    private final WorldGenLakes waterLakeGenerator = new WorldGenLakes(Blocks.WATER);
    private final WorldGenLakes lavaLakeGenerator = new WorldGenLakes(Blocks.LAVA);
    private final WorldGenDungeons dungeonGenerator = new WorldGenDungeons();
    private final Biome[] baseBiomesList;
    private final Biome[] rawBiomes = new Biome[256];
    private final IRealisticBiome[] jitteredBiomes = new IRealisticBiome[256];
    private final ISimplexData2D surfaceJitterData = SimplexData2D.newDisk();
    private final ISimplexData2D riverJitterData = SimplexData2D.newDisk();
    private final float[] riverValues = new float[256];
    /** WP-5：与 {@link #riverValues} **同一次** Voronoi 求值带出的 {@code borderDistance()}。 */
    private final float[] riverBorders = new float[256];
    private final float[] baseHeights = new float[256];
    /**
     * RWG {@code ChunkGeneratorRealistic:971-1005}：装饰按 9×9 区块邻域**分摊**到各真实群系。
     *
     * <p>RWG 把 {@code borderNoise[realisticId] += 0.01234569f} 累加 81 次后，
     * 以该值作为 {@code strength} 传给 {@code rDecorate(...)}。rtgc 的
     * {@code rDecorate} 没有 {@code strength} 形参，所以这里用**概率等价**：
     * 期望装饰量 = Σ w·deco(群系) 与 RWG 相同。
     */
    private final float[] decoWeights;
    private final int parabolicSize;
    private final int parabolicArraySize;
    private final float[] parabolicField;
    private final MutableBlockPos mpos = new MutableBlockPos();
    private final boolean useIntBiomeArray;
    private final int[] intBiomeArray = new int[256];
    private final byte[] byteBiomeArray = new byte[256];
    private final MutableBlockPos snowCheckPos = new MutableBlockPos();

    public ChunkGeneratorRTG(RTGWorld rtgWorld) {
        Logger.debug("Instantiating CPRTG using generator settings: {}", rtgWorld.world().getWorldInfo().getGeneratorOptions());
        this.world = rtgWorld.world();
        this.rtgWorld = rtgWorld;
        this.settings = rtgWorld.getGeneratorSettings();
        this.world.setSeaLevel(this.settings.seaLevel);
        WaterLevel.setCurrent(this.settings.seaLevel);
        this.rand = new Random(rtgWorld.seed());
        this.rtgWorld.setRandom(this.rand);
        this.mapFeaturesEnabled = world.getWorldInfo().isMapFeaturesEnabled();
        this.useIntBiomeArray = Loader.isModLoaded("jeid") || Loader.isModLoaded("neid") || Loader.isModLoaded("reid");

        // 初始化结构生成器
        this.caveGenerator = TerrainGen.getModdedMapGen(
                new MapGenCavesRTG(this.settings.caveChance, this.settings.caveDensity), EventType.CAVE);
        this.ravineGenerator = TerrainGen.getModdedMapGen(
                new MapGenRavineRTG(this.settings.ravineChance), EventType.RAVINE);
        this.villageGenerator = (MapGenVillage) TerrainGen.getModdedMapGen(
                new MapGenVillage(StructureType.VILLAGE.getSettings(this.settings)), EventType.VILLAGE);
        this.strongholdGenerator = (MapGenStronghold) TerrainGen.getModdedMapGen(
                new MapGenStronghold(StructureType.STRONGHOLD.getSettings(this.settings)), EventType.STRONGHOLD);
        this.woodlandMansionGenerator = new WoodlandMansionRTG(
                this, StructureType.MANSION.getSettings(this.settings));
        this.mineshaftGenerator = (MapGenMineshaft) TerrainGen.getModdedMapGen(
                new MapGenMineshaft(StructureType.MINESHAFT.getSettings(this.settings)), EventType.MINESHAFT);
        this.scatteredFeatureGenerator = (MapGenScatteredFeature) TerrainGen.getModdedMapGen(
                new MapGenScatteredFeature(StructureType.TEMPLE.getSettings(this.settings)), EventType.SCATTERED_FEATURE);
        this.oceanMonumentGenerator = (StructureOceanMonument) TerrainGen.getModdedMapGen(
                new StructureOceanMonument(StructureType.MONUMENT.getSettings(this.settings)), EventType.OCEAN_MONUMENT);

        // 采样数组的**分配宽度**：MC 编号 + 合成槽位预留（山地链变体，见 RtgRealisticIndex）。
        //
        // 为什么取预留上界而不是实际值：1.12.2 的 WorldServer#createChunkProvider 是
        // `createChunkGenerator()` 先、`createBiomeProvider()` 后（Java 从左到右求值），
        // 即**本生成器先构造**，那一刻 RtgLayoutAccess 还没建好布局、山地链还不存在。
        // 预留 256 个槽位的代价只是约 0.7 MB 内存；每区块的热循环按
        // RtgRealisticIndex.usedBound()（动态）走，无链时与引入本类之前完全一致。
        this.biomeCount = RtgRealisticIndex.biomeIdBound();
        this.hugeRender = new float[81][biomeCount];
        this.smallRender = new float[625][biomeCount];
        this.decoWeights = new float[biomeCount];
        this.deferredOceanDecorations = new float[biomeCount];
        this.activeBiomeIds = new int[biomeCount];
        this.activeBiomeFlags = new boolean[biomeCount];

        this.baseBiomesList = new Biome[256]; // 16*16 列索引，与生物群系 ID 空间无关
        parabolicSize = sampleSize;
        parabolicArraySize = parabolicSize * 2 + 1;
        parabolicField = new float[parabolicArraySize * parabolicArraySize];
        float parabolicFieldTotal = 0;
        for (int j = -parabolicSize; j <= parabolicSize; ++j) {
            for (int k = -parabolicSize; k <= parabolicSize; ++k) {
                float f = 0.445f / (float) Math.sqrt(j * j + k * k + 0.3F);
                parabolicField[(j + parabolicSize) + (k + parabolicSize) * parabolicArraySize] = f;
                parabolicFieldTotal += f;
            }
        }
        this.parabolicFieldTotalInv = 1.0f / parabolicFieldTotal;

        this.landscapeCache = Collections.synchronizedMap(new LinkedHashMap<ChunkPos, ChunkLandscape>(RTGConfig.landscapeCacheSize(), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkPos, ChunkLandscape> eldest) {
                return size() > RTGConfig.landscapeCacheSize();
            }
        });
        Logger.debug("FINISHED instantiating CPRTG.");
    }

    @Override
    public Chunk generateChunk(final int cx, final int cz) {
        ChunkGenerationProfiler.beginChunk(cx, cz);
        long tChunkGen = ChunkGenerationProfiler.start(Category.CHUNK_TOTAL);

        final ChunkPos chunkPos = new ChunkPos(cx, cz);
        final BlockPos blockPos = new BlockPos(cx * 16, 0, cz * 16);
        final BiomeProvider biomeProvider = this.world.getBiomeProvider();
        this.rand.setSeed(cx * 341873128712L + cz * 132897987541L);
        final ChunkPrimer primer = new ChunkPrimer();

        // ---- Landscape ----
        long tLandscape = ChunkGenerationProfiler.start(Category.LANDSCAPE);
        final ChunkLandscape landscape = getLandscape(biomeProvider, chunkPos);
        ChunkGenerationProfiler.end(Category.LANDSCAPE, tLandscape);

        // ---- Terrain fill ----
        long tTerrain = ChunkGenerationProfiler.start(Category.TERRAIN_FILL);
        generateTerrain(primer, landscape.noise);
        ChunkGenerationProfiler.end(Category.TERRAIN_FILL, tTerrain);

        // 获取标准生物群系数据
        if (this.settings.useSingleBiome) {
            Biome singleBaseBiome = getSingleBiomeTarget().baseBiome();
            Arrays.fill(this.baseBiomesList, singleBaseBiome);
        } else {
            for (int i = 0; i < 256; i++) {
                this.baseBiomesList[i] = landscape.biome[i].baseBiome();
            }
        }

        // ---- Surface jitter ----
        //
        // ⚠ 这里读的是 `landscape.surfaceBiome`（RWG `randBiome` 的产物），**不是** `landscape.biome`。
        // 两者只在**过渡带**内不同：`biome[]` 取权重最大的那个（F3／区块群系数组仍用它），
        // `surfaceBiome[]` 是被 15 格噪声扫累积权重区间挑出来的那个（地表用它）。
        // 于是过渡带里 F3 与地表方块会有出入 —— 这是 RWG 的原始行为，用户已明确认可
        //（「本来就是交界处，无可厚非」）。`biome[]` 见 ChunkGeneratorRTG:294 的 baseBiomesList。
        long tJitter = ChunkGenerationProfiler.start(Category.SURFACE_JITTER);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = blockPos.getX() + i;
                int z = blockPos.getZ() + j;
                final int k = (x & 15) * 16 + (z & 15);
                IRealisticBiome actualbiome = landscape.surfaceBiome[k] != null
                        ? landscape.surfaceBiome[k]
                        : landscape.biome[k];                    // fail-soft：抖动未算到时回落到主群系
                IRealisticBiome chosen = actualbiome;

                // ---- 「地表渗透」（RTG 时代机制，RWG 没有）----
                //
                // 只有**显式开启**的群系参与，实测全仓只有 3 个：
                // `VanillaBeach` / `VanillaStoneBeach` / `BOPGravelBeach`
                //（`SURFACE_BLEED_IN` / `OUT` 默认 false，只有这三个在 initConfig 里置 true）。
                //
                // 🔴 旧写法在这里有一个**取错列**的 bug，用户报的「越界小圆点」就是它：
                //
                //     jitterbiome = landscape.biome[(pX & 15) * 16 + (pZ & 15)];
                //
                // `pX` / `pZ` 是**世界坐标**，而 `surfaceBlendRadius` 默认 **32**（= 2 个区块），
                // 所以那个位置通常**不在本区块**。`pX & 15` 只给出"它在**它自己**所在区块里的偏移"，
                // 却拿这个偏移去索引**本区块**的数组 ⇒ 取到的是**另一列**的群系。
                // 后果：开了渗透的海滩附近，地表会刷上一个与周围毫无关系的群系，
                // 而那些列的位置随噪声在 16 格周期上跳变 ⇒ **孤立的小斑块**。
                //
                // 它此前不明显，是因为 `actualbiome` 曾是"权重最大的群系"（很少是海滩）；
                // 接上 `randBiome` 之后 `actualbiome` 变成噪声挑中的地表群系，
                // 海滩在过渡带里被挑中的频率上升 ⇒ 这个 bug 才开始显形（"又出现了"）。
                //
                // 正确读法：直接问布局"那个世界坐标上是哪个群系"。
                // 这也是唯一可行的读法 —— 半径最大 32 格，邻居区块的 landscape 未必在缓存里。
                if (actualbiome.getConfig().SURFACE_BLEED_IN.get()) {
                    this.rtgWorld.simplexInstance(0).multiEval2D(x, z, surfaceJitterData);
                    final int pX = (int) Math.round(x + surfaceJitterData.getDeltaX() * RTGConfig.surfaceBlendRadius());
                    final int pZ = (int) Math.round(z + surfaceJitterData.getDeltaY() * RTGConfig.surfaceBlendRadius());
                    final IRealisticBiome far = RtgLayoutAccess.biomeAt(pX, pZ);
                    if (far != null && far.getConfig().SURFACE_BLEED_OUT.get()) {
                        chosen = far;
                    }
                }
                jitteredBiomes[i * 16 + j] = chosen;
            }
        }
        ChunkGenerationProfiler.end(Category.SURFACE_JITTER, tJitter);

        // ---- Surface replace ----
        long tReplace = ChunkGenerationProfiler.start(Category.SURFACE_REPLACE);
        replaceBiomeBlocks(cx, cz, primer, jitteredBiomes, this.baseBiomesList, landscape.noise, landscape.river);
        ChunkGenerationProfiler.end(Category.SURFACE_REPLACE, tReplace);

        // ---- Underground river tunnels & junction chambers (WP-3) ----
        long tTunnels = ChunkGenerationProfiler.start(Category.RIVER_TUNNELS);
        carveRiverTunnels(primer, cx, cz, landscape);
        ChunkGenerationProfiler.end(Category.RIVER_TUNNELS, tTunnels);

        // ---- Caves ----
        if (this.settings.useCaves) {
            long tCaves = ChunkGenerationProfiler.start(Category.CAVES);
            this.caveGenerator.generate(this.world, cx, cz, primer);
            ChunkGenerationProfiler.end(Category.CAVES, tCaves);
        }
        // ---- Ravines ----
        if (this.settings.useRavines) {
            long tRavines = ChunkGenerationProfiler.start(Category.RAVINES);
            this.ravineGenerator.generate(this.world, cx, cz, primer);
            ChunkGenerationProfiler.end(Category.RAVINES, tRavines);
        }
        // ---- Structures ----
        if (this.mapFeaturesEnabled) {
            long tStructures = ChunkGenerationProfiler.start(Category.STRUCTURES_GEN);
            if (settings.useMineShafts) this.mineshaftGenerator.generate(this.world, cx, cz, primer);
            if (settings.useStrongholds) this.strongholdGenerator.generate(this.world, cx, cz, primer);
            if (settings.useVillages) this.villageGenerator.generate(this.world, cx, cz, primer);
            if (settings.useTemples) this.scatteredFeatureGenerator.generate(this.world, cx, cz, primer);
            if (settings.useMonuments) this.oceanMonumentGenerator.generate(this.world, cx, cz, primer);
            if (settings.useMansions) this.woodlandMansionGenerator.generate(this.world, cx, cz, primer);
            ChunkGenerationProfiler.end(Category.STRUCTURES_GEN, tStructures);
        }

        // ---- Chunk finalize ----
        //
        // ⚠ **两个数组都必须写**（此前只在 REID 时写 int 数组，是本次修掉的一个真缺陷）。
        //
        // 原版 `Chunk` 的群系存储是 `byte[256] blockBiomeArray`，初值 **(byte)-1 = 255**
        // （`Chunk.java:111`），255 是"未知"哨兵；而客户端是从**网络包**里读这个字节数组的
        // （`Chunk.java:1263  buf.readBytes(this.blockBiomeArray)`）。
        // REID（`org.dimdev.jeid`）额外给 `Chunk` 加了 `int[]` 以支持 ≥256 的编号，
        // 由 `INewChunk.setIntBiomeArray` 写入。
        //
        // 旧写法是 `if (useIntBiomeArray) setIntBiomeArray(...) else setBiomeArray(...)` ——
        // **装了 REID 时字节数组永远停在初值**，于是"F3 读的是哪一个数组"就完全取决于
        // REID 有没有把 int 数组同步到客户端。实测症状是
        // F3 只显示少数几个编号（Ocean=0 / MushroomIsland=14 / MushroomIslandShore=15），
        // 与地表实际生成的群系完全对不上。
        //
        // 现在**无条件两个都写**：int 数组给 REID 的扩展路径，
        // 字节数组给原版 `SPacketChunkData`/`fillChunk` 的路径。
        // 编号 <256 时两者一致；编号 ≥256 时字节会截断，但那种编号本来也只能靠 REID 的 int 数组承载。
        long tFinalize = ChunkGenerationProfiler.start(Category.CHUNK_FINALIZE);
        Chunk chunk = new Chunk(this.world, primer, cx, cz);
        for (int i = 0; i < 256; ++i) {
            int value = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
            this.intBiomeArray[i] = value;
            this.byteBiomeArray[i] = (byte) value;
        }
        if (this.useIntBiomeArray) {
            // ⚠ 必须传**副本**：`intBiomeArray` 是复用字段，下个区块生成时会被覆写。
            // 若 REID 保存的是引用而不是拷贝，网络包稍后构造时就会读到**别的区块**的编号。
            ((INewChunk) chunk).setIntBiomeArray(this.intBiomeArray.clone());
        }
        // 原版 `setBiomeArray` 内部是 `System.arraycopy`（自己会拷），这里不必再 clone。
        chunk.setBiomeArray(this.byteBiomeArray);
        chunk.generateSkylightMap();
        ChunkGenerationProfiler.end(Category.CHUNK_FINALIZE, tFinalize);

        ChunkGenerationProfiler.end(Category.CHUNK_TOTAL, tChunkGen);
        return chunk;
    }

    public void generateTerrain(ChunkPrimer primer, float[] noise) {
        final int seaTop = Math.min(this.settings.seaLevel - 1, 255);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = (int) noise[x * 16 + z];
                int stoneTop = Math.min(height, 255);
                for (int y = 0; y <= stoneTop; y++) {
                    primer.setBlockState(x, y, z, STONE);
                }
                for (int y = Math.max(height + 1, 0); y <= seaTop; y++) {
                    primer.setBlockState(x, y, z, WATER);
                }
            }
        }
    }

    private void replaceBiomeBlocks(int cx, int cz, ChunkPrimer primer, IRealisticBiome[] biomes, Biome[] base, float[] noise, float[] rivers) {
        if (!ForgeEventFactory.onReplaceBiomeBlocks(this, cx, cz, primer, this.world)) {
            return;
        }

        if (this.settings.useSingleBiome) {
            IRealisticBiome singleBiome = getSingleBiomeTarget();
            int worldX = cx * 16;
            int worldZ = cz * 16;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    mpos.setPos(worldX + x, 0, worldZ + z);
                    float river = rivers[x * 16 + z];
                    singleBiome.rReplace(primer, mpos, x, z, -1, rtgWorld, noise, river, base);
                    placeBedrock(primer, x, z);
                }
            }
            return;
        }

        int worldX = cx * 16;
        int worldZ = cz * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.setPos(worldX + x, 0, worldZ + z);
                float river = rivers[x * 16 + z];
                biomes[x * 16 + z].rReplace(primer, mpos, x, z, -1, rtgWorld, noise, river, base);
                placeBedrock(primer, x, z);
            }
        }
    }

    /**
     * 按世界创建界面里的 {@code bedrockLayers}（默认 5，范围 1–10）放置基岩层。
     * <p>
     * 修复了一个既有缺陷：该配置此前**从未被消费**，无论设成几都只放 y=0 一层
     * （见 {@code GeneratorSettings.Factory#bedrockLayers} 与 GUI 滑条）。
     * <p>
     * y=0 恒为基岩；其上各层按 1/(y+1) 的递减概率生成，模拟原版的锯齿状基岩。
     * 使用**无状态位置哈希**而非共享的 {@code this.rand}——后者会因多消耗随机数而
     * 改变洞穴/结构/装饰的生成序列，属于不必要的连带行为变更。
     */
    private void placeBedrock(ChunkPrimer primer, int x, int z) {
        primer.setBlockState(x, 0, z, BEDROCK);
        final int layers = Math.max(1, Math.min(10, this.settings.bedrockLayers));
        for (int y = 1; y < layers; y++) {
            if (bedrockHash(x, z, y) % (y + 1) == 0) {
                primer.setBlockState(x, y, z, BEDROCK);
            }
        }
    }

    /** 与坐标和世界种子绑定的无状态哈希，用于基岩层的伪随机分布。 */
    private int bedrockHash(int x, int z, int y) {
        int h = x * 73856093 ^ z * 19349663 ^ y * 83492791 ^ (int) rtgWorld.seed();
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        h ^= (h >>> 15);
        return h & 0x7fffffff;
    }

    /**
     * 开凿**地下河隧道与交汇洞厅**（WP-3）。
     * <p>
     * 几何取自 RWG（{@code ChunkGeneratorRealistic.carveRiverColumn} 一带）：
     * <ul>
     *   <li>隧道：沿河网（Voronoi 单元边界）延伸，全宽约 13 格；底部到 y=62 填水、以上填空气，
     *       形成"下为暗河、上为气道"的管状结构</li>
     *   <li>洞厅：位于河网交汇点（Voronoi 顶点），强度足够时向上开天窗</li>
     *   <li>洞顶恒被压到 {@code surface - 10} 以下，保证不破地表</li>
     * </ul>
     * <p>
     * <b>与 RWG 的差异</b>：RWG 用"山地链权重"（{@code mountainChainRiverHost > 0.10}）作门控，
     * rtgc 尚无山地链概念（属 WP-4/WP-5），因此改用**海拔门控**——只在 {@code surface ≥ 76}
     * 的地形下方开凿，语义上等价于"山区河道"。
     * <p>
     * <b>必须放在 {@code replaceBiomeBlocks} 之后</b>：地表替换按 {@code depth} 计数涂刷，
     * 若先开凿，隧道内的空气会把 {@code depth} 重置，导致隧道底被误刷上草/沙。
     */
    private void carveRiverTunnels(ChunkPrimer primer, int cx, int cz, ChunkLandscape landscape) {
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;
        }
        final float[] heights = landscape.noise;
        final IBlockState air = Blocks.AIR.getDefaultState();
        final MutableBlockPos pos = this.mpos;
        final int baseX = cx * 16;
        final int baseZ = cz * 16;
        final float[] strengths = this.riverStrengths;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int k = x * 16 + z;
                final int surface = Math.min(255, (int) heights[k]);

                // ⓪ 最便宜的一道前置过滤，**必须放在 Voronoi 计算之前**。
                //
                // 隧道带（距河网边界 < 6.5 格）完全落在「强河流」带内：离线实测该带内
                // riverStrength 的**最小值是 0.637**（5 万样本）。这里取 0.25 作阈值，
                // 留 2.5 倍余量，因此不会把隧道带排除掉，却能挡掉世界上绝大多数列。
                //
                // 教训：修 F-39 时我把 fillRiverStrengths（2 次 simplex + 1 次 cellular）
                // 提到了所有门控之前，等于**让每一列都白算一遍 Voronoi**——而那些列
                // 在旧代码里连噪声都不会碰。这是一次自己造成的性能退化。
                // RWG 的门控就是「在河网带内」= strength < 0，即 landscape.river[k] > 0。
                // 这是**精确**条件而非启发式：隧道带宽 9/1250 ≈ 0.0072 严格小于河网带宽
                // 50/300 ≈ 0.1667，故「隧道带 ⊂ 河网带」。旧实现的 0.25 阈值是为了配旧量纲。
                if (landscape.river[k] <= 0f) {
                    continue;
                }

                // ① 是否落在河网边界 / 顶点附近。
                //    隧道与洞厅共用同一个 warpedRiverVoronoi，故一次求出两者。
                pos.setPos(baseX + x, 0, baseZ + z);
                strengths[0] = layout.getRiverTunnelStrength(baseX + x, baseZ + z);
                strengths[1] = layout.getRiverJunctionStrength(baseX + x, baseZ + z);
                final float tunnel = strengths[0];
                final float junction = strengths[1];
                if (tunnel <= 0f && junction <= 0f) {
                    continue;
                }

                // ② 山地链宿主门控（B5 / RWG {@code ChunkGeneratorRealistic:585-602}）。
                //
                // 原实现用「去掉河流后的**干高度** ≥ 76」当门控 —— 那是在还没有山地链时
                // 用来替代 RWG 门控的权宜之计（F-39）。RWG 的真正门控是**山地链宿主强度**：
                //     mountainChainRiverHost = max(链权重, 邻域链影响)
                // 也就是说：地下河与洞厅是**山地链专有**的地貌，不是"任何够高的地形都会有"。
                final float chainHost = landscape.mountainChainRiverHost[k];
                if (chainHost <= MOUNTAIN_CHAIN_RIVER_HOST_MIN) {
                    continue;
                }

                // RWG:601-602 —— 链宿主与地表高度各做一次 smoothstep：
                // 前者决定"这属于链的哪一部分"，后者保证洞厅上方有足够岩层（天窗只在地表够高时开）。
                final float mountainHost = smoothstep(
                        (chainHost - MOUNTAIN_CHAIN_RIVER_HOST_MIN) / 0.40f);
                final float overheadHost = smoothstep((surface - TUNNEL_MIN_SURFACE) / 24f);

                // ③ 高度带：以 TUNNEL_CENTER_Y 为基准，但整条隧道可**整体下移**，
                //    保证洞顶留在实际地表以下 TUNNEL_ROOF_CLEARANCE 格。
                //    地表 ≥ 84 时不触发下移 ⇒ 与 RWG 原值（58–73）逐位一致。
                final int center = Math.min(TUNNEL_CENTER_Y,
                        surface - TUNNEL_ROOF_CLEARANCE - TUNNEL_ROOF_RISE);

                final float tunnelCurve = (float) Math.sqrt(tunnel);
                int floor = center - Math.round(tunnelCurve * TUNNEL_FLOOR_DROP);
                int ceiling = center + Math.round(tunnelCurve * TUNNEL_ROOF_RISE);

                final float chamberStrength = junction * mountainHost * overheadHost;
                if (chamberStrength > 0f) {
                    final float chamberCurve = (float) Math.sqrt(chamberStrength);
                    floor = Math.min(floor, center + 1 - Math.round(chamberCurve * 23f));
                    ceiling = Math.max(ceiling, center + 1 + Math.round(chamberCurve * 42f));
                    // 注意：必须在 max() **之后**再钳，否则 max() 会把钳制结果顶回去
                    //（原实现的顺序反了，导致洞顶从未真正受 surface − 10 约束）。
                    ceiling = Math.min(ceiling, surface - TUNNEL_ROOF_CLEARANCE);
                    ceiling = Math.max(ceiling, center + Math.round(tunnelCurve * TUNNEL_ROOF_RISE));
                    ceiling = Math.max(ceiling, floor + 1);
                }
                if (ceiling <= floor) {
                    continue;
                }

                final int lavaFreeWaterTop = center;
                for (int y = Math.max(1, floor); y <= Math.min(255, ceiling); y++) {
                    primer.setBlockState(x, y, z, y <= lavaFreeWaterTop ? WATER : air);
                }

                // 记录洞顶，供装饰期挂洞穴藤蔓（RiverCaveVines）——避免在装饰期重算隧道门控
                landscape.riverCaveCeiling[k] = ceiling;

                // 天窗：洞厅足够强时自洞顶向上打通，形成与地表的连通口
                if (chamberStrength > 0.70f && surface > ceiling) {
                    final int openingBottom = Math.max(ceiling + 1, center + 1);
                    for (int y = openingBottom; y <= surface; y++) {
                        final float fraction = (y - openingBottom) / (float) Math.max(1, surface - openingBottom);
                        if (chamberStrength >= 0.70f + fraction * 0.15f) {
                            primer.setBlockState(x, y, z, air);
                        }
                    }
                }
            }
        }
    }

    /** RWG 的 smoothstep 等价物，用于把海拔/强度映射成 0–1 的门控权重。 */
    private static float smoothstep(final float value) {
        if (value <= 0f) {
            return 0f;
        }
        if (value >= 1f) {
            return 1f;
        }
        return value * value * (3f - 2f * value);
    }

    private IRealisticBiome getSingleBiomeTarget() {
        if (!this.settings.useSingleBiome) return null;

        Biome targetBase = Biome.getBiome(this.settings.singleBiomeId);
        if (targetBase == null) {
            Logger.warn("Single biome ID {} not found, fallback to Plains (ID: 1)", this.settings.singleBiomeId);
            targetBase = Biome.getBiome(1);
        }

        IRealisticBiome targetRTG = RTGAPI.getRTGBiome(targetBase);
        if (targetRTG == null) {
            Logger.warn("RTG biome wrapper not found for {}, fallback to Plains", this.settings.singleBiomeId);
            targetRTG = RTGAPI.getRTGBiome(1);
        }
        return targetRTG;
    }

    @Override
    public void populate(int chunkX, int chunkZ) {
        long tPopTotal = ChunkGenerationProfiler.start(Category.POP_TOTAL);
        BlockFalling.fallInstantly = true;
        final BiomeProvider biomeProvider = this.world.getBiomeProvider();
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final BlockPos blockPos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
        final BlockPos offsetPos = blockPos.add(8, 0, 8);

        IRealisticBiome biome;
        if (this.settings.useSingleBiome) {
            biome = getSingleBiomeTarget();
        } else {
            // B1：装饰的群系改从**布局**取（现实主义编号空间），而不是经 MC 群系编号往返。
            //
            // 位置与 RWG 一致：`ChunkGeneratorRealistic:808` 用 `(x + 16, y + 16)`。
            //
            // 为什么必须走布局：山地链与它的备份群系**共用同一个 MC 群系**，经
            // `RTGAPI.getRTGBiome(mcBiome)` 往返只会拿回备份群系，链的
            // `rDecorate`（按平缓点比例缩放装饰）永远不会被调用。
            // 对非链的列两种取法结果完全相同（`layout.getBiomeDataAt` 返回的
            // `IRealisticBiome` 其 `baseBiome()` 正是 `biomeProvider.getBiome` 给的 MC 群系）。
            final IRealisticBiome layoutBiome = RtgLayoutAccess.biomeAt(
                    blockPos.getX() + 16, blockPos.getZ() + 16);
            biome = layoutBiome != null
                    ? layoutBiome
                    : RTGAPI.getRTGBiome(biomeProvider.getBiome(blockPos.add(16, 0, 16)));
        }

        this.rand.setSeed(rtgWorld.getChunkSeed(chunkX, chunkZ));
        boolean hasVillage = false;
        ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, chunkX, chunkZ, false);

        // ---- Pop: structures ----
        long tPopStruct = ChunkGenerationProfiler.start(Category.POP_STRUCTURES);
        if (this.mapFeaturesEnabled) {
            byte flags = 0;
            if (settings.useMineShafts) flags |= 1;
            if (settings.useStrongholds) flags |= 2;
            if (settings.useVillages) flags |= 4;
            if (settings.useTemples) flags |= 8;
            if (settings.useMonuments) flags |= 16;
            if (settings.useMansions) flags |= 32;
            if ((flags & 1) != 0) mineshaftGenerator.generateStructure(world, rand, chunkPos);
            if ((flags & 2) != 0) strongholdGenerator.generateStructure(world, rand, chunkPos);
            if ((flags & 4) != 0) hasVillage = villageGenerator.generateStructure(world, rand, chunkPos);
            if ((flags & 8) != 0) scatteredFeatureGenerator.generateStructure(world, rand, chunkPos);
            if ((flags & 16) != 0) oceanMonumentGenerator.generateStructure(world, rand, chunkPos);
            if ((flags & 32) != 0) woodlandMansionGenerator.generateStructure(world, rand, chunkPos);
        }
        ChunkGenerationProfiler.end(Category.POP_STRUCTURES, tPopStruct);

        // ---- Pop: lakes ----
        long tPopLakes = ChunkGenerationProfiler.start(Category.POP_LAKES);
        if (settings.useWaterLakes && settings.waterLakeChance > 0 && !hasVillage) {
            long nextChance = rand.nextLong();
            int surfaceChance = settings.getSurfaceWaterLakeChance(biome.waterLakeMult());
            BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));
            if (surfaceChance > 0 && nextChance % surfaceChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAKE)) {
                    waterSurfaceLakeGenerator.generate(world, rand, pos.up(rand.nextInt(256)));
                }
            } else if (nextChance % settings.waterLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAKE)) {
                    waterLakeGenerator.generate(world, rand, pos.up(rand.nextInt(50) + 4));
                }
            }
        }

        if (settings.useLavaLakes && settings.lavaLakeChance > 0 && !hasVillage) {
            long nextChance = rand.nextLong();
            int surfaceChance = settings.getSurfaceLavaLakeChance(biome.lavaLakeMult());
            BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));
            if (surfaceChance > 0 && nextChance % surfaceChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAVA)) {
                    lavaSurfaceLakeGenerator.generate(world, rand, pos.up(rand.nextInt(256)));
                }
            } else if (nextChance % settings.lavaLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAVA)) {
                    lavaLakeGenerator.generate(world, rand, pos.up(rand.nextInt(50) + 4));
                }
            }
        }
        ChunkGenerationProfiler.end(Category.POP_LAKES, tPopLakes);

        // ---- Pop: dungeons ----
        long tPopDungeons = ChunkGenerationProfiler.start(Category.POP_DUNGEONS);
        if (settings.useDungeons && TerrainGen.populate(this, world, rand, chunkX, chunkZ,
                hasVillage, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
            for (int i = 0; i < settings.dungeonChance; i++) {
                dungeonGenerator.generate(world, rand,
                        offsetPos.add(rand.nextInt(16), rand.nextInt(256), rand.nextInt(16)));
            }
        }
        ChunkGenerationProfiler.end(Category.POP_DUNGEONS, tPopDungeons);

        // ---- Pop: decoration ----
        long tPopDeco = ChunkGenerationProfiler.start(Category.POP_DECORATION);
        mpos.setPos(blockPos.getX() + 16, 0, blockPos.getZ() + 16);
        // 装饰期的河强：仍用 rtgc 的「1 = 河心」约定（下面与 RIVER_DECORATION_THRESHOLD 比较）。
        final RtgBiomeLayout decoLayout = RtgLayoutAccess.current();
        float river = decoLayout == null ? 0f
                : -decoLayout.getRiverStrength(mpos.getX(), mpos.getZ());
        final ChunkLandscape landscape = getLandscape(biomeProvider, chunkPos);

        // ---- RWG ChunkGeneratorRealistic:971-975 的邻域装饰权重累加 ----
        //
        // 原实现只取 `biomeAt(x + 16, z + 16)`（正北东方向下一区块的原点）这**一个**群系，
        // 然后用它装饰整个区块。后果：处在群系边缘的区块会长满隔壁群系的植被，即
        // "F3 显示 A、地上长的却是 B 的树"。
        //
        // RWG 的做法是：以本区块为中心取样 9×9 个区块（RWG 的 +24 偏移让采样点落在
        // 区块内部而不是角上），每个命中的群系累加 1/81 的权重；该权重就是群系在本区块
        // 的装饰强度。采样与下面的逐 id 顺序都与 RWG 一致。
        //
        // ⚠ 累加必须在 `DecorateBiomeEvent.Pre` **之前**完成（RWG:971 vs RWG:977）。
        final int decoBound = biomeLoopBound();
        java.util.Arrays.fill(this.decoWeights, 0, decoBound, 0f);
        for (int bx = -4; bx <= 4; bx++) {
            for (int bz = -4; bz <= 4; bz++) {
                final IRealisticBiome neighbour = RtgLayoutAccess.biomeAt(
                        blockPos.getX() + 24 + bx * 16, blockPos.getZ() + 24 + bz * 16);
                if (neighbour == null) {
                    continue;
                }
                final int id = RtgRealisticIndex.idFor(neighbour);
                if (id >= 0 && id < decoBound) {
                    this.decoWeights[id] += RWG_DECO_NEIGHBOUR_WEIGHT;
                }
            }
        }

        // ⚠ rtgc 的 populate 取代了原版的 ChunkProviderServer#populate，而后者是
        // `DecorateBiomeEvent.Pre/Post` 的**唯一**发出点。此前这两个事件从未被发出，
        // 于是所有靠它们做装饰的 mod（含 BOP 的一部分植被）在本世界类型下**完全不生效**。
        // RWG 在 ChunkGeneratorRealistic:977/1036 也是自己发的，此处同样处理。
        // ⚠ 单独计时：这个事件里跑的是**别的模组**的装饰处理器（BOP 等），
        // 与 rtgc 自己的 rDecorate 混在一起就分不清"装饰慢"是谁慢。
        long tPreEvent = ChunkGenerationProfiler.start(Category.POP_DECO_PRE_EVENT);
        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(this.world, this.rand, blockPos));
        ChunkGenerationProfiler.end(Category.POP_DECO_PRE_EVENT, tPreEvent);

        // D4 诊断仪表：重置本区块的 deco 调用计数（-Drtg.debugDecorations 时才输出）
        ChunkInfo.resetInvocations();
        // 装饰耗时归因（只在 profiler 打开时累计；见 ChunkInfo 的说明）
        ChunkInfo.resetDecoProfile();

        // 本区块实际被分摊到装饰的群系数（-Drtg.debugDecorations 时打印）
        int decoratedBiomes = 0;
        final int centreId = RtgRealisticIndex.idFor(biome);
        final float centreWeight = centreId >= 0 && centreId < decoBound ? this.decoWeights[centreId] : 0f;

        if (RTG.decorationsDisable() || biome.getConfig().DISABLE_RTG_DECORATIONS.get()) {
            if (river > RTGWorld.RIVER_DECORATION_THRESHOLD) {
                biome.getRiverBiome().baseBiome().decorate(this.world, this.rand, blockPos);
            } else {
                biome.baseBiome().decorate(this.world, this.rand, blockPos);
            }
        } else {
            // ---- RWG ChunkGeneratorRealistic:986-1015：按权重分摊装饰 ----
            //
            // 权重在上面的 9×9 邻域采样里已经算好。RWG 把 `borderNoise[bn]` 作为
            // `strength` 形参传给 `rDecorate(...)`，由各装饰器按 `count * strength`
            // 缩放数量；rtgc 的 `rDecorate` 没有 `strength` 形参，所以这里用
            // **概率等价**：以 w 的概率整份装饰，期望量与 RWG 相同（方差不同）。
            //
            // 典型情形下 9×9 邻域只命中 1 个群系，权重为 81/81 = 1，与改动前
            // "整块用一个群系"的结果完全一致；只有跨群系边界的区块才会分摊。
            for (int id = 0; id < decoBound; id++) {
                float weight = this.decoWeights[id];
                if (weight <= 0f) {
                    continue;
                }
                if (weight > 1f) {
                    weight = 1f;
                }
                final IRealisticBiome neighbour = RtgRealisticIndex.biomeOf(id);
                if (neighbour == null) {
                    continue;
                }
                if (weight < 1f && this.rand.nextFloat() >= weight) {
                    continue;
                }
                decoratedBiomes++;
                if (river > RTGWorld.RIVER_DECORATION_THRESHOLD) {
                    neighbour.getRiverBiome().rDecorate(this.rtgWorld, this.rand, chunkPos, river, hasVillage, landscape.noise);
                } else {
                    neighbour.rDecorate(this.rtgWorld, this.rand, chunkPos, river, hasVillage, landscape.noise);
                }
                // RWG `ChunkGeneratorRealistic:997-998`：海洋群系把"原版装饰"记下来，
                // 等冰雪 pass 之后再跑（它的 rDecorate 里那一段已被跳过）。
                if (neighbour.defersVanillaDecorateUntilAfterIce()) {
                    this.deferredOceanDecorations[id] = weight;
                }
            }
        }

        // ---- D4 诊断（需 -Drtg.debugDecorations）：区块中心群系 + 该群系的 deco 数 + 实际被调用数 ----
        //
        // `有多少 deco` 与 `实际调用了几个` 是两件事：preGenerate(river) 会过滤、
        // 山地链的概率缩放会过滤、关闭装饰的分支会整个跳过。
        // 并排打印才能一眼定位"没有地表装饰"卡在哪一步。
        if (RTG.decoDebug()) {
            final boolean decoOff = RTG.decorationsDisable() || biome.getConfig().DISABLE_RTG_DECORATIONS.get();
            Logger.info("[RTG-DECO] chunk({},{}) biome={} chain={} hasDecos={} invoked={} river={} "
                            + "riverBranch={} rtgDecoOff={} vanillaTrees={} mapFeatures={} decoBiomes={} centreWeight={}",
                    chunkX, chunkZ, biome.baseBiomeResLoc(),
                    biome instanceof RealisticBiomeMountainChain,
                    biome.getDecos().size(), ChunkInfo.chunkInvocations(),
                    String.format(java.util.Locale.ROOT, "%.3f", river),
                    river > RTGWorld.RIVER_DECORATION_THRESHOLD,
                    decoOff, biome.allowVanillaTrees(), this.mapFeaturesEnabled,
                    decoratedBiomes,
                    String.format(java.util.Locale.ROOT, "%.4f", centreWeight));
        }

        long tPostEvent = ChunkGenerationProfiler.start(Category.POP_DECO_POST_EVENT);
        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Post(this.world, this.rand, blockPos));
        ChunkGenerationProfiler.end(Category.POP_DECO_POST_EVENT, tPostEvent);
        ChunkGenerationProfiler.end(Category.POP_DECORATION, tPopDeco);

        // ---- 装饰尖峰的归因 ----
        //
        // 首测显示装饰占了绝大部分耗时，且最慢的区块里 99.8% 都是它。这一行只在
        // **本区块装饰超过阈值**时打印，把"谁的几秒"直接写出来：
        // 被装饰到的群系、rtgc 自己的 deco 总耗时、原版 Biome.decorate 总耗时、最慢的前 5 个装饰器。
        // 阈值 200ms 远高于平均值（~17ms），所以正常游玩不会刷屏。
        if (RTG.decoDebug() || (System.nanoTime() - tPopDeco) > DECO_REPORT_THRESHOLD_NS) {
            Logger.info("[RTG-DECOPROF] chunk({},{}) {}ms {}",
                    chunkX, chunkZ, (System.nanoTime() - tPopDeco) / 1_000_000L, ChunkInfo.decoReport());
        }

        // ---- 洞穴藤蔓 ----
        // 位置与 RWG 一致（ChunkGeneratorRealistic:1017-1019）：群系装饰之后、原版装饰事件之前。
        // RWG 把它绑在 mapFeatures 上（ChunkGeneratorRealistic:154），这里同样处理。
        if (this.mapFeaturesEnabled) {
            RiverCaveVines.decorate(this.world, this.rand, this.rtgWorld, landscape,
                    blockPos.getX(), blockPos.getZ());
        }

        // ---- Pop: animals ----
        long tPopAnimals = ChunkGenerationProfiler.start(Category.POP_ANIMALS);
        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            WorldEntitySpawner.performWorldGenSpawning(this.world, biome.baseBiome(),
                    blockPos.getX() + 8, blockPos.getZ() + 8, 16, 16, this.rand);
        }
        ChunkGenerationProfiler.end(Category.POP_ANIMALS, tPopAnimals);

        // ---- Pop: snow & ice ----
        long tPopSnow = ChunkGenerationProfiler.start(Category.POP_SNOW_ICE);
        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ICE)) {
            float snowTempThreshold = settings.getClampedSnowLayerTemp();
            MutableBlockPos mutablePos = new MutableBlockPos();

            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    mutablePos.setPos(offsetPos.getX() + x, 0, offsetPos.getZ() + z);
                    int freezeY = world.getPrecipitationHeight(mutablePos).getY() - 1;
                    snowCheckPos.setPos(mutablePos.getX(), freezeY, mutablePos.getZ());
                    if (this.world.canBlockFreezeWater(snowCheckPos)) {
                        this.world.setBlockState(snowCheckPos, ICE, 2);
                    }
                    if (settings.useSnowLayers) {
                        BlockPos surfacePos = world.getTopSolidOrLiquidBlock(mutablePos);
                        int baseY = surfacePos.getY();
                        if (biomeProvider.getBiome(surfacePos).getTemperature(surfacePos) <= snowTempThreshold) {
                            for (int y = baseY + 32; y >= baseY; y--) {
                                snowCheckPos.setPos(surfacePos.getX(), y, surfacePos.getZ());
                                if (world.getBlockState(snowCheckPos).getMaterial() == Material.AIR
                                        && Blocks.SNOW_LAYER.canPlaceBlockAt(world, snowCheckPos)) {
                                    this.world.setBlockState(snowCheckPos, SNOW_LAYER, 2);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        ChunkGenerationProfiler.end(Category.POP_SNOW_ICE, tPopSnow);

        // ---- RWG `ChunkGeneratorRealistic:1115-1120`：冰雪之后才补海洋的原版装饰 ----
        //
        // 推迟的意义就在这里：冰/雪已经铺完，此时再放珊瑚/海草就不会被冰压掉；
        // 随后 `rDecorateAfterIce` 还会对 BOP 的水下装饰做一遍"站不住就换回水"的清理
        //（RWG 的 `RealisticBiomeBOPOcean.sanitizeColumn`）。
        for (int id = 0; id < decoBound; id++) {
            final float deferred = this.deferredOceanDecorations[id];
            if (deferred <= 0f) {
                continue;
            }
            this.deferredOceanDecorations[id] = 0f;      // 用完即清（生成器实例会被复用）
            final IRealisticBiome oceanBiome = RtgRealisticIndex.biomeOf(id);
            if (oceanBiome != null) {
                oceanBiome.rDecorateAfterIce(this.rtgWorld, this.rand,
                        blockPos.getX(), blockPos.getZ(), deferred);
            }
        }

        ForgeEventFactory.onChunkPopulate(false, this, this.world, this.rand, chunkX, chunkZ, hasVillage);
        BlockFalling.fallInstantly = false;

        ChunkGenerationProfiler.end(Category.POP_TOTAL, tPopTotal);
        ChunkGenerationProfiler.endChunk(chunkX, chunkZ);
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        return settings.useMonuments && this.mapFeaturesEnabled && chunkIn.getInhabitedTime() < 3600L &&
                this.oceanMonumentGenerator.generateStructure(this.world, this.rand, new ChunkPos(x, z));
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        Biome biome = this.world.getBiome(pos);
        if (this.mapFeaturesEnabled) {
            if (creatureType == EnumCreatureType.MONSTER) {
                if (this.scatteredFeatureGenerator.isSwampHut(pos)) return this.scatteredFeatureGenerator.getMonsters();
                if (settings.useMonuments && this.oceanMonumentGenerator.isPositionInStructure(this.world, pos)) {
                    return this.oceanMonumentGenerator.getMonsters();
                }
            }
        }
        return biome.getSpawnableList(creatureType);
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        if (!this.mapFeaturesEnabled) return null;
        switch (structureName) {
            case "Stronghold":
                return this.strongholdGenerator != null ?
                        this.strongholdGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            case "Mansion":
                return this.woodlandMansionGenerator != null ?
                        this.woodlandMansionGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            case "Monument":
                return this.oceanMonumentGenerator != null ?
                        this.oceanMonumentGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            case "Village":
                return this.villageGenerator != null ?
                        this.villageGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            case "Mineshaft":
                return this.mineshaftGenerator != null ?
                        this.mineshaftGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            case "Temple":
                return this.scatteredFeatureGenerator != null ?
                        this.scatteredFeatureGenerator.getNearestStructurePos(worldIn, position, findUnexplored) : null;
            default:
                return null;
        }
    }

    @Override
    public void recreateStructures(Chunk chunk, int cx, int cz) {
        if (!this.mapFeaturesEnabled) return;
        byte flags = 0;
        if (settings.useMineShafts) flags |= 1;
        if (settings.useVillages) flags |= 2;
        if (settings.useStrongholds) flags |= 4;
        if (settings.useTemples) flags |= 8;
        if (settings.useMonuments) flags |= 16;
        if (settings.useMansions) flags |= 32;
        if ((flags & 1) != 0) this.mineshaftGenerator.generate(this.world, cx, cz, null);
        if ((flags & 2) != 0) this.villageGenerator.generate(this.world, cx, cz, null);
        if ((flags & 4) != 0) this.strongholdGenerator.generate(this.world, cx, cz, null);
        if ((flags & 8) != 0) this.scatteredFeatureGenerator.generate(this.world, cx, cz, null);
        if ((flags & 16) != 0) this.oceanMonumentGenerator.generate(this.world, cx, cz, null);
        if ((flags & 32) != 0) this.woodlandMansionGenerator.generate(this.world, cx, cz, null);
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        if (!this.mapFeaturesEnabled) return false;
        switch (structureName) {
            case "Stronghold":
                return this.strongholdGenerator != null && this.strongholdGenerator.isInsideStructure(pos);
            case "Mansion":
                return this.woodlandMansionGenerator != null && this.woodlandMansionGenerator.isInsideStructure(pos);
            case "Monument":
                return this.oceanMonumentGenerator != null && this.oceanMonumentGenerator.isInsideStructure(pos);
            case "Village":
                return this.villageGenerator != null && this.villageGenerator.isInsideStructure(pos);
            case "Mineshaft":
                return this.mineshaftGenerator != null && this.mineshaftGenerator.isInsideStructure(pos);
            case "Temple":
                return this.scatteredFeatureGenerator != null && this.scatteredFeatureGenerator.isInsideStructure(pos);
            default:
                return false;
        }
    }

    public ChunkLandscape getLandscape(final BiomeProvider biomeProvider, final ChunkPos chunkPos) {
        ChunkLandscape landscape = landscapeCache.get(chunkPos);
        if (landscape == null) {
            landscape = generateLandscape(biomeProvider, new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16));
            landscapeCache.put(chunkPos, landscape);
        }
        return landscape;
    }

    /**
     * {@code /rtg tunnels} 用：**只读**地取缓存里的 landscape，未缓存返回 {@code null}。
     *
     * <p>与 {@link #getLandscape} 的区别是**绝不新建**：不生成、不插缓存。
     * 于是定位器只能观测"已经走到过的区块"—— 这正是我们要的语义
     *（未生成的区块本来也无从谈起"有没有挖过"），且不会因为一条命令污染 LRU。
     */
    public ChunkLandscape cachedLandscape(final int chunkX, final int chunkZ) {
        return landscapeCache.get(new ChunkPos(chunkX, chunkZ));
    }

    /**
     * {@code /rtg probe} 用：读出该列决定「地下河隧道 / 洞厅」生死的三个值。
     *
     * <p>存在的理由：隧道门控（{@code mountainChainRiverHost > 0.10}）与"是否真的开凿了"
     * 都只存在于 {@link ChunkLandscape} 里，此前**没有任何办法在游戏内观察**，
     * 于是"地下河没看见"永远只能靠猜。本方法把它变成一次右键级的读数。
     *
     * <p><b>不写缓存</b>：命中失败时按同一套确定性算法重算一份临时对象，绝不插回
     * {@code landscapeCache}（否则一条诊断命令会顶掉别的区块的缓存项）。
     *
     * @return {@code [0]=山地链权重 [1]=山地链宿主 [2]=洞顶 y（0 = 该列没被开凿）
     *         [3]=1 表示布局来自缓存（此时 [2] 可信）}
     */
    public float[] probeTunnelColumn(final BiomeProvider biomeProvider, final int x, final int z) {

        final ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        final ChunkLandscape cached = landscapeCache.get(chunkPos);
        final ChunkLandscape landscape = cached != null
                ? cached
                : generateLandscape(biomeProvider, new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16));
        final int k = (x & 15) * 16 + (z & 15);
        return new float[] {
                landscape.mountainChainWeight[k],
                landscape.mountainChainRiverHost[k],
                landscape.riverCaveCeiling[k],
                cached != null ? 1f : 0f };
    }

    private ChunkLandscape generateLandscape(BiomeProvider biomeProvider, BlockPos blockPos) {
        final ChunkLandscape landscape = new ChunkLandscape();

        if (this.settings.useSingleBiome) {
            IRealisticBiome singleBiome = getSingleBiomeTarget();
            int biomeId = Biome.getIdForBiome(singleBiome.baseBiome());
            Arrays.fill(this.biomeData, biomeId);
            getNewerNoiseSingleBiome(blockPos.getX(), blockPos.getZ(), landscape, singleBiome);
            Arrays.fill(landscape.biome, singleBiome);
            Arrays.fill(landscape.surfaceBiome, singleBiome);
            return landscape;
        }

        getNewerNoise(biomeProvider, blockPos.getX(), blockPos.getZ(), landscape);
        Biome[] biomes = this.rawBiomes;
        MutableBlockPos biomePos = new MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                biomePos.setPos(blockPos.getX() + x, 0, blockPos.getZ() + z);
                biomes[x * 16 + z] = biomeProvider.getBiome(biomePos);
            }
        }
        analyzer.newRepair(biomes, this.biomeData, landscape);
        return landscape;
    }

    private void getNewerNoiseSingleBiome(final int worldX,
                                          final int worldZ, ChunkLandscape landscape, IRealisticBiome singleBiome) {
        // D1：原先这里走的是 TerrainBase 里**与布局重复的一整套旧河道族**
        //（getRiverStrength(BlockPos…) + rwgCalculateRiver + borderDistance 标定）。
        // 那套是为 rtgc 自造的 SpacedCellularNoise 写的；布局用的是**逐行移植的 RWG CellNoise**
        //（RwgCellNoise），RWG 的原始宽度字面量才是对的。现统一走布局，与主路径完全同源。
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;
        }

        float[] riverValues = this.riverValues;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                riverValues[i * 16 + j] = layout.getRiverStrength(worldX + i, worldZ + j);
            }
        }

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                int x = worldX + i;
                int z = worldZ + j;
                // 与主路径同约定：RWG 的 river 是 `getRiverStrength` 的结果 + 1f
                //（0 = 河心，1 = 内陆）。
                float height = singleBiome.rNoise(rtgWorld, x, z, 1.0f, riverValues[k] + 1f);
                landscape.noise[k] = height;
            }
        }

        for (int k = 0; k < 256; k++) {
            landscape.river[k] = -riverValues[k];   // 转回 rtgc 的「1 = 河心」约定
        }

        // 与主路径一致：RWG 的河道雕刻对高度执行一次。
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                landscape.noise[k] = layout.calculateRiver(
                        worldX + i, worldZ + j,
                        riverValues[k], landscape.noise[k]);
            }
        }
    }
    private void getNewerNoise(BiomeProvider biomeProvider,
                               int chunkWorldX,
                               int chunkWorldZ,
                               ChunkLandscape landscape) {

        final int hugeWidth = 9;
        final int smallWidth = 25;
        final int totalSampleSize = 2 * sampleSize + 5;
        final int baseOffsetX = chunkWorldX - 8;
        final int baseOffsetZ = chunkWorldZ - 8;

        MutableBlockPos tempPos = new MutableBlockPos();

        // 采样编号空间 = **现实主义群系编号**，不是 MC 群系编号。
        // 这正是 RWG 的做法（ChunkManagerRealistic.getBiomeDataAt 返回现实主义编号），
        // 也是山地链能存在的前提：链与它的备份群系共用同一个 MC 群系，
        // 只有在这层编号空间里才能被区分开（见 RtgRealisticIndex）。
        //
        // ⚠ 注意：采样编号空间里**没有** `bound`/`0..256` 的遍历了。
        // 金字塔（mix4）与高度求和一律走 activeBiomeIds（RWG 的设计），
        // 见本方法下方的 active 表构建。

        for (int i = 0; i < totalSampleSize; i++) {
            int xOffset = ((i - sampleSize) * 8) - 8;
            int rowOffset = i * sampleArraySize;
            for (int j = 0; j < totalSampleSize; j++) {
                int zOffset = ((j - sampleSize) * 8) - 8;
                final int sampleX = baseOffsetX + xOffset;
                final int sampleZ = baseOffsetZ + zOffset;
                final IRealisticBiome layoutBiome = RtgLayoutAccess.biomeAt(sampleX, sampleZ);
                if (layoutBiome != null) {
                    biomeData[rowOffset + j] = RtgRealisticIndex.idFor(layoutBiome);
                } else {
                    // 布局未覆盖该列（fail-soft）：退回 MC 编号，与引入本类之前的行为一致
                    tempPos.setPos(sampleX, 0, sampleZ);
                    biomeData[rowOffset + j] = Biome.getIdForBiome(biomeProvider.getBiome(tempPos));
                }
            }
        }

        // 采样网格建好后**一次性**解析出「哪些点是山地链」，供 256 列的邻域扫描复用。
        for (int i = 0; i < biomeData.length; i++) {
            sampleIsChain[i] = RtgRealisticIndex.biomeOf(biomeData[i]) instanceof RealisticBiomeMountainChain;
        }

        // ---- 本次区块的 active 群系表（RWG ChunkGeneratorRealistic:320-340）----
        // 升序构建：RWG 在 :337 注明这是浮点累加顺序的契约，必须保持。
        Arrays.fill(this.activeBiomeFlags, false);
        for (int i = 0; i < biomeData.length; i++) {
            final int id = biomeData[i];
            if (id >= 0 && id < this.activeBiomeFlags.length) {
                this.activeBiomeFlags[id] = true;
            }
        }
        this.activeBiomeCount = 0;
        for (int id = 0; id < this.activeBiomeFlags.length; id++) {
            if (this.activeBiomeFlags[id]) {
                this.activeBiomeIds[this.activeBiomeCount++] = id;
            }
        }

        for (int i = -1; i < 4; i++) {
            int iBase = (i * 2 + 2) * hugeWidth;
            int iSample = i + sampleSize + 1;
            for (int j = -1; j < 4; j++) {
                int idx = iBase + (j * 2 + 2);
                float[] target = hugeRender[idx];
                clearActiveBiomes(target);
                int jSample = j + sampleSize + 1;
                for (int k = -parabolicSize; k <= parabolicSize; k++) {
                    int rowBiome = (iSample + k) * sampleArraySize;
                    int rowWeight = (k + parabolicSize) * parabolicArraySize;
                    for (int l = -parabolicSize; l <= parabolicSize; l++) {
                        int biomeId = biomeData[rowBiome + (jSample + l)];
                        float weight = parabolicField[rowWeight + (l + parabolicSize)] * parabolicFieldTotalInv;
                        target[biomeId] += weight;
                    }
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            int i2 = i * 2, i2p2 = i2 + 2, rowCenter = i2 + 1;
            int rowTop = i2 * hugeWidth, rowBottom = i2p2 * hugeWidth;
            for (int j = 0; j < 4; j++) {
                int j2 = j * 2, j2p2 = j2 + 2, colCenter = j2 + 1;
                int idx = rowCenter * hugeWidth + colCenter;
                clearActiveBiomes(hugeRender[idx]);
                mix4(hugeRender[rowTop + j2], hugeRender[rowBottom + j2],
                        hugeRender[rowTop + j2p2], hugeRender[rowBottom + j2p2],
                        hugeRender[idx]);
            }
        }

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                int i4 = i * 4, j4 = j * 4, smallIdx = i4 * smallWidth + j4;
                clearActiveBiomes(smallRender[smallIdx]);
                if (((i ^ j) & 1) != 0) {
                    mix4(hugeRender[i * hugeWidth + (j + 1)], hugeRender[(i + 1) * hugeWidth + j],
                            hugeRender[(i + 1) * hugeWidth + (j + 2)], hugeRender[(i + 2) * hugeWidth + (j + 1)],
                            smallRender[smallIdx]);
                } else {
                    copyActiveBiomes(hugeRender[(i + 1) * hugeWidth + (j + 1)], smallRender[smallIdx]);
                }
            }
        }

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                int i4 = i * 4, j4 = j * 4;
                int targetIdx = (i4 + 2) * smallWidth + (j4 + 2);
                clearActiveBiomes(smallRender[targetIdx]);
                mix4(smallRender[i4 * smallWidth + j4], smallRender[(i4 + 4) * smallWidth + j4],
                        smallRender[i4 * smallWidth + (j4 + 4)], smallRender[(i4 + 4) * smallWidth + (j4 + 4)],
                        smallRender[targetIdx]);
            }
        }

        for (int i = 0; i < 11; i++) {
            int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4;
            for (int j = 0; j < 11; j++) {
                if (((i ^ j) & 1) != 0) {
                    int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4;
                    int targetIdx = i2p2 * smallWidth + j2p2;
                    clearActiveBiomes(smallRender[targetIdx]);
                    mix4(smallRender[i2 * smallWidth + j2p2], smallRender[i2p2 * smallWidth + j2],
                            smallRender[i2p2 * smallWidth + j2p4], smallRender[i2p4 * smallWidth + j2p2],
                            smallRender[targetIdx]);
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4;
                int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4;
                int targetIdx = (i2 + 3) * smallWidth + (j2 + 3);
                clearActiveBiomes(smallRender[targetIdx]);
                mix4(smallRender[i2p2 * smallWidth + j2p2], smallRender[i2p4 * smallWidth + j2p2],
                        smallRender[i2p2 * smallWidth + j2p4], smallRender[i2p4 * smallWidth + j2p4],
                        smallRender[targetIdx]);
            }
        }

        for (int i = 0; i < 16; i++) {
            int i3 = i + 3, i4 = i + 4, i5 = i + 5;
            for (int j = 0; j < 16; j++) {
                if (((i ^ j) & 1) != 0) {
                    int j4 = j + 4, targetIdx = i4 * smallWidth + j4;
                    clearActiveBiomes(smallRender[targetIdx]);
                    mix4(smallRender[i3 * smallWidth + j4], smallRender[i4 * smallWidth + (j4 - 1)],
                            smallRender[i4 * smallWidth + (j4 + 1)], smallRender[i5 * smallWidth + j4],
                            smallRender[targetIdx]);
                }
            }
        }

        // ---- ⚠ 这里**没有**"中心群系短路"，这是刻意的 ----
        //
        // 历史：本类曾用 `hugeRender[40]` 判定"该区块由单一群系主导"，命中后整块 256 列
        // 只算 `h_dom(1.0)`，跳过下面这条加权求和。那个判定是**错的**，理由是几何上的：
        //
        //   · `hugeRender[40]` 只是**中心那一个 HUGE 节点**，它的采样窗是 21×21 网格的
        //     行/列 2..18（即 ±64 格内的一块 17×17 窗口）；
        //   · 而每列真正用的是 `smallRender[(i+4)*25 + (j+4)]`，它由 HUGE→SMALL 四层
        //     mix4 从**整张 9×9 HUGE 格**平均而来，其覆盖范围包含行/列 0..12 与 4..20
        //     的节点 —— 比中心节点的窗口**更宽**。
        //
        // 所以"中心节点只有一个非零项"**并不等于**"该列的混合求和只有一项"：
        // 区块边缘的列完全可能含第二个群系的真实权重。短路一刀切掉这些项，于是
        // 均匀区块变成纯 `h_dom(1.0)`、相邻区块却是混合值，断差正好落在**区块边界**上
        // （16 格长的直线台阶）。这就是"过渡生硬"的来源。
        //
        // 参照 RWG：`ChunkGeneratorRealistic:356-363` 的确也有一个 `b != null` 判定，但
        // 它**只用于覆盖地表 `biomes[]` 数组**（`:443-451`）并关掉噪声抖动；
        // 高度求和（`:485-510`）**永远无条件执行**。本类此前把这个判定挪用到了高度上，
        // 属于实现偏离，现已删除 —— 四层 HUGE→SMALL 混合对每一列都完整生效。
        //
        // 性能由 activeBiomeIds 补回（RWG 自己的设计）：求和只遍历本区块实际出现的群系
        // （通常 1–8 个），而不是 0..256。

        // RWG 河道：strength ∈ [-1, 0]，**-1 在河心**（ChunkManagerRealistic.getRiverStrength）。
        // 两套约定在此转换：
        //   · 传给 rNoise 的是 RWG 的 `river + 1f`（[0,1]，0 = 河心）—— rNoise 内部正是按此约定；
        //   · 写进 landscape.river[] 的是 rtgc 旧约定 `-strength`（[0,1]，1 = 河心），
        //     因为 Surface* / Deco* 全都按「1 = 最强河流」读它。
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout == null) {
            return;     // 布局未就绪：本区块走父类的群系，河道留空（fail-soft）
        }
        float[] riverValues = this.riverValues;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                riverValues[x * 16 + z] = layout.getRiverStrength(chunkWorldX + x, chunkWorldZ + z);
            }
        }

        float[] baseHeights = this.baseHeights;
        // 链权重/宿主强度随**区块**保存（landscape 可能来自缓存），不能放生成器字段
        final float[] chainWeight = landscape.mountainChainWeight;
        final float[] chainHost = landscape.mountainChainRiverHost;
        // 地表侧的群系边界抖动（RWG `randBiome`）产物，同样随区块保存
        final IRealisticBiome[] surfaceBiome = landscape.surfaceBiome;
        // RWG 用的是它的 `perlin`（= NoiseSelector.createNoiseGenerator(seed)），
        // 在 rtgc 里就是 simplexInstance(0)（同一算法、同一种子偏移）。循环外取一次。
        final SimplexNoise bRandNoise = rtgWorld.simplexInstance(0);

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                int smallIdx = (i + 4) * smallWidth + (j + 4);
                int worldX = chunkWorldX + i;
                int worldZ = chunkWorldZ + j;

                // ---- RWG ChunkGeneratorRealistic:456-460 + 488-495：地表群系的噪声抖动 ----
                //
                // RWG 原文：
                //     bRand = 0.5f + perlin.noise2((x + i) / 15f, (y + j) / 15f);
                //     bRand = clamp(bRand, 0f, 0.99999f);
                //     ...
                //     if (bCount <= 1f) { bCount += smallRender[l][k];
                //                         if (bCount > bRand) { biomes[j*16+i] = getBiome(k); bCount = 2f; } }
                //
                // 即用一张 **15 格尺度**的噪声去扫**累积权重区间**：先越过噪声的那个群系赢得这一列的地表。
                // 这是"群系边界呈噪声状而不是直线"的**另一半**（第一半是四层 HUGE→SMALL 混合，
                // 已在本方法开头无条件恢复）。单群系列（权重 1.0）必然被自己跨过，不可能改选别人 ——
                // 所以"森林里冒出蘑菇岛"不会发生，抖动只作用在**权重本来就混合**的过渡带。
                final float bRand = Math.max(0f, Math.min(0.99999f,
                        0.5f + bRandNoise.noise2f(worldX / 15f, worldZ / 15f)));
                float bCount = 0f;
                IRealisticBiome surfacePick = null;
                int firstNonZero = -1;

                // RWG ChunkGeneratorRealistic:474-512 —— 链权重只累加「包成山地链」的那些贡献项。
                float chain = 0f;

                // 无条件执行四层 HUGE→SMALL 混合的加权求和（RWG:485-510 无任何短路）。
                // 遍历 activeBiomeIds（升序，RWG:337 的顺序契约），不是 0..256。
                float totalHeight = 0f;
                final float[] weights = smallRender[smallIdx];
                for (int ai = 0; ai < this.activeBiomeCount; ai++) {
                    final int bid = this.activeBiomeIds[ai];
                    final float weight = weights[bid];
                    if (weight <= 0f) {
                        continue;
                    }
                    // ⚠ 累积必须放在高度求和**之前**，与 RWG 同一段代码内的先后一致
                    //（RWG:488-495 的 randBiome 块就在 :503-505 的高度累加之前）。
                    if (firstNonZero < 0) {
                        firstNonZero = bid;
                    }
                    if (bCount <= 1f) {
                        bCount += weight;
                        if (bCount > bRand) {
                            surfacePick = RtgRealisticIndex.biomeOf(bid);
                            bCount = 2f;        // 与 RWG 一样：越过一次就停
                        }
                    }
                    final IRealisticBiome biome = RtgRealisticIndex.biomeOf(bid);
                    if (biome != null) {
                        totalHeight += biome.rNoise(rtgWorld, worldX, worldZ, weight, riverValues[k] + 1f) * weight;
                        if (biome instanceof RealisticBiomeMountainChain) {
                            chain += weight;
                        }
                    }
                }
                baseHeights[k] = totalHeight;

                // ⚠ 与 RWG 的一处**有意差异**：RWG 扫不过去时 `biomes[]` 保留的是
                // **上一个区块**留在复用数组里的值（它的 `biomes` 是成员字段），那是个隐患。
                // 这里明确回落到「第一个非零权重的群系」，结果确定、不依赖调用顺序。
                surfaceBiome[k] = surfacePick != null
                        ? surfacePick
                        : RtgRealisticIndex.biomeOf(firstNonZero);

                chainWeight[k] = chain;
                // RWG:511-512 —— 宿主强度还要算上"附近有链"的影响，否则链边缘的隧道会断掉。
                chainHost[k] = Math.max(chain, nearbyMountainChainInfluence(i, j));
            }
        }

        for (int k = 0; k < 256; k++) {
            landscape.noise[k] = baseHeights[k];
            landscape.river[k] = -riverValues[k];       // 转回 rtgc 的「1 = 河心」约定
        }

        // RWG 的河道雕刻：**对混合后的高度执行一次**（对应 ChunkGeneratorRealistic:551
        //     carvedHeight = cmr.calculateRiver(x, y, river, uncarvedHeight, riverSample);
        // 这里用 4 参重载，内部自行取扭曲坐标与噪声河床，结果与 5 参版逐位相同）。
        //
        // B3：山地链内部**不做河道雕刻**（ChunkGeneratorRealistic:550-554）——
        //     fade = smoothstep((mountainChainWeight − 0.35) / 0.65)
        //     testHeight = carved + (uncarved − carved) × fade
        // 链内的河道因此在**地表**上消失，改以地下隧道＋洞厅的形式出现
        // （见 carveRiverTunnels 的 mountainChainRiverHost 门控）。
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                final int k = i * 16 + j;
                final float uncarved = landscape.noise[k];
                final float carved = layout.calculateRiver(
                        chunkWorldX + i, chunkWorldZ + j,
                        riverValues[k], uncarved);

                float fade = (chainWeight[k] - MOUNTAIN_CHAIN_FADE_START) / MOUNTAIN_CHAIN_FADE_WIDTH;
                if (fade <= 0f) {
                    landscape.noise[k] = carved;
                } else if (fade >= 1f) {
                    landscape.noise[k] = uncarved;
                } else {
                    fade = fade * fade * (3f - 2f * fade);
                    landscape.noise[k] = carved + (uncarved - carved) * fade;
                }
            }
        }
    }

    /**
     * RWG {@code ChunkGeneratorRealistic#nearbyMountainChainInfluence} 的移植：
     * 在 ±48 格的采样网格里找最近的山地链列，返回线性衰减的 0–1 影响值。
     *
     * <p>为什么要它：链与链之间、链与普通群系之间的过渡带上
     * {@code mountainChainWeight} 会掉到 0，但那里的地形已经在链的高度上了。
     * 只按自身权重门控会让隧道在这些边缘处**断开**。RWG 用这个邻域影响补上。
     *
     * <p>实现上读 {@link #sampleIsChain}（每区块一次性解析好），内层循环只有数组读取 ——
     * 本方法对每一列都要扫 441 个采样点，不能在里面做编号解析。
     *
     * @param localX 区块内 x（0–15）
     * @param localZ 区块内 z（0–15）
     */
    private float nearbyMountainChainInfluence(final int localX, final int localZ) {

        final int radius = MOUNTAIN_CHAIN_INFLUENCE_RADIUS;
        final int radiusSquared = radius * radius;
        float maximum = 0f;

        for (int sampleX = 0; sampleX < sampleArraySize; sampleX++) {
            final int sampleLocalX = (sampleX - sampleSize) * 8 - 8;
            final float distanceX = sampleLocalX - localX;
            final int rowBiome = sampleX * sampleArraySize;
            for (int sampleZ = 0; sampleZ < sampleArraySize; sampleZ++) {
                if (!sampleIsChain[rowBiome + sampleZ]) {
                    continue;
                }
                final int sampleLocalZ = (sampleZ - sampleSize) * 8 - 8;
                final float distanceZ = sampleLocalZ - localZ;
                final int distanceSquared = (int) (distanceX * distanceX + distanceZ * distanceZ);
                if (distanceSquared >= radiusSquared) {
                    continue;
                }
                maximum = Math.max(maximum, 1f - (float) Math.sqrt(distanceSquared) / radius);
            }
        }
        return maximum;
    }

    /**
     * 采样数组的**实际遍历宽度**在每区块开始时取一次（见 {@link RtgRealisticIndex#usedBound()}）。
     *
     * <p><b>只用于兜底路径</b>：金字塔与高度求和都改走 {@code activeBiomeIds}（RWG 的设计），
     * 本方法现在只服务于"布局返回 null 时按 MC 编号采样"的那条 fail-soft 分支与
     * {@code decoWeights} 的清零范围。
     */
    private static int biomeLoopBound() {
        return RtgRealisticIndex.usedBound();
    }

    /**
     * RWG {@code ChunkGeneratorRealistic:657-666} 的 {@code mix4}：四个权重向量的算术平均。
     *
     * <p><b>只遍历 active 群系</b>，这是 RWG 的性能设计（一张 21×21 采样网通常只命中
     * 1–8 个群系）。数学上与遍历全 256 项**完全等价**：非 active 编号在整张
     * {@code hugeRender}/{@code smallRender} 里恒为 0（数组初始值），且从不被写入 ——
     * 因为每个被写入的格子都会先对当前 active 表清零。
     */
    private void mix4(float[] a, float[] b, float[] c, float[] d, float[] out) {
        for (int i = 0; i < this.activeBiomeCount; i++) {
            final int id = this.activeBiomeIds[i];
            out[id] = (a[id] + b[id] + c[id] + d[id]) * 0.25f;
        }
    }

    /** RWG {@code ChunkGeneratorRealistic:646-648}。 */
    private void clearActiveBiomes(final float[] weights) {
        for (int i = 0; i < this.activeBiomeCount; i++) {
            weights[this.activeBiomeIds[i]] = 0f;
        }
    }

    /** RWG {@code ChunkGeneratorRealistic:650-655}。 */
    private void copyActiveBiomes(final float[] source, final float[] result) {
        for (int i = 0; i < this.activeBiomeCount; i++) {
            final int id = this.activeBiomeIds[i];
            result[id] = source[id];
        }
    }

    private enum StructureType {
        MINESHAFT, MONUMENT, STRONGHOLD, TEMPLE, VILLAGE, MANSION;

        Map<String, String> getSettings(RTGChunkGenSettings settings) {
            Map<String, String> ret = new HashMap<>();
            switch (this) {
                case MINESHAFT:
                    ret.put("chance", String.valueOf(settings.mineShaftChance));
                    break;
                case MONUMENT:
                    ret.put("separation", String.valueOf(settings.monumentSeparation));
                    ret.put("spacing", String.valueOf(settings.monumentSpacing));
                    break;
                case STRONGHOLD:
                    ret.put("count", String.valueOf(settings.strongholdCount));
                    ret.put("distance", String.valueOf(settings.strongholdDistance));
                    ret.put("spread", String.valueOf(settings.strongholdSpread));
                    break;
                case TEMPLE:
                    ret.put("distance", String.valueOf(settings.templeDistance));
                    break;
                case VILLAGE:
                    ret.put("distance", String.valueOf(settings.villageDistance));
                    ret.put("size", String.valueOf(settings.villageSize));
                    break;
                case MANSION:
                    ret.put("spacing", String.valueOf(settings.mansionSpacing));
                    ret.put("separation", String.valueOf(settings.mansionSeparation));
                    break;
            }
            return ret;
        }
    }
}
