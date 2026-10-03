package rtg.world.gen;

import net.minecraft.block.Block;
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
import rtg.api.util.BlockUtil;
import rtg.api.util.ChunkGenerationProfiler;
import rtg.api.util.ChunkGenerationProfiler.Category;
import rtg.api.util.ChunkInfo;
import rtg.api.util.Logger;
import rtg.api.util.noise.*;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.api.world.gen.feature.WorldGenPond;
import rtg.event.EventHandlerCommon;
import rtg.world.biome.BiomeAnalyzer;
import rtg.world.biome.RtgBiomeLayout;
import rtg.world.biome.RtgLayoutAccess;
import rtg.world.biome.RtgRealisticIndex;
import rtg.world.biome.realistic.land.RealisticBiomeIslandVolcano;
import rtg.world.biome.realistic.land.RealisticBiomeMountainChain;
import rtg.world.gen.structure.WoodlandMansionRTG;

import javax.annotation.Nullable;
import java.util.*;

public class ChunkGeneratorRTG implements IChunkGenerator {

    private static final IBlockState STONE = Blocks.STONE.getDefaultState();
    private static final IBlockState WATER = Blocks.WATER.getDefaultState();
    private static final IBlockState BEDROCK = Blocks.BEDROCK.getDefaultState();

    /**
     * 山地链邻域影响的半径（格）。RWG {@code MOUNTAIN_CHAIN_INFLUENCE_RADIUS = 48f}；**rtgc 收到 32**
     *（用户实机反馈"两山交接处很容易就生成地下河，出现次数过多了"）。
     *
     * <p>它只用于一处：{@code chainHost[k] = max(chain, nearbyMountainChainInfluence(i, j))}
     * —— 也就是**暗河隧道门控**（不是地形、也不是山地链 fade）。±48 的线性衰减会把两条链**交界**
     * 那一带整片抬起来（两边影响叠加）⇒"两山交接处"到处过门控；±32 把这片肥区收窄。
     */
    private static final int MOUNTAIN_CHAIN_INFLUENCE_RADIUS = 32;

    /**
     * 山地链 fade 的起点与宽度：{@code fade = clamp((mountainChainWeight − 0.35) / 0.65)}，再做 smoothstep。
     * {@code fade = 1} 时该列完全不接受河道雕刻。
     */
    private static final float MOUNTAIN_CHAIN_FADE_START = 0.35f;
    private static final float MOUNTAIN_CHAIN_FADE_WIDTH = 0.65f;
    /** 装饰阶段超过该值就打印一行归因（{@code [RTG-DECOPROF]}）。 */
    private static final long DECO_REPORT_THRESHOLD_NS = 200_000_000L;

    /** 9×9 区块邻域中每个命中群系的装饰权重（{@code 1f / 81f}）。 */
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
    private final int biomeCount;
    private final float[][] hugeRender;
    private final float[][] smallRender;
    private final float parabolicFieldTotalInv;
    private final Map<ChunkPos, ChunkLandscape> landscapeCache;
    private float[] deferredOceanDecorations;
    private final int sampleSize = 8;
    private final int sampleArraySize = sampleSize * 2 + 5;
    private final int[] biomeData = new int[sampleArraySize * sampleArraySize];
    private final boolean[] sampleIsChain = new boolean[sampleArraySize * sampleArraySize];
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
    /** WP-5：与 {@link #riverValues} 同一次 Voronoi 求值带出的 {@code borderDistance()}。 */
    private final float[] riverBorders = new float[256];
    private final float[] baseHeights = new float[256];
    /**
     * 该列火山地表的火山灰层厚度。
     * <p>写于高度混合循环，读于 {@code replaceBiomeBlocks}。每个区块的每一列都要清零 ——
     * 本字段是生成器级的复用数组，不清就会把上一个区块的厚度漏进本区块的地表。
     */
    private final byte[] volcanoSurfaceDepth = new byte[256];
    /**
     * 熔岩洞地标用的 cell 噪声。只用 {@code sampleTwo2D}，不读 {@code distanceMethod}，
     * 故这里自建一个与布局内部那个逐位相同的实例。
     */
    private final RwgCellNoise landmarkCell;
    /**
     * 熔岩洞岸边那种"冒烟的草"。装了 BOP 时取 {@code biomesoplenty:grass}，否则为 null。
     */
    private final Block smolderingGrass;
    /**
     * 给 {@code generateMapGen}（MapVolcano 等）用的生成器自有随机源。
     * <p>复用一个实例不会串状态（每次调用都 setSeed）；借用别处的随机源会污染其序列。
     */
    private final Random mapRand;
    /**
     * {@code smallRender} 的中心列下标：金字塔是 25×25 个小格，区块中心 i=j=8 ⇒
     * {@code (8 + 4) * 25 + (8 + 4) = 312}。
     */
    private static final int SMALL_RENDER_CENTER_INDEX = 312;
    /**
     * 装饰按 9×9 区块邻域分摊到各真实群系。
     * <p>rtgc 的 {@code rDecorate} 没有 {@code strength} 形参，所以这里用概率等价：
     * 期望装饰量 = Σ w·deco(群系) 与原版相同。
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
        RtgLayoutAccess.setTerrainWorld(rtgWorld);
        this.settings = rtgWorld.getGeneratorSettings();
        this.world.setSeaLevel(this.settings.seaLevel);
        WaterLevel.setCurrent(this.settings.seaLevel);
        this.rand = new Random(rtgWorld.seed());
        this.rtgWorld.setRandom(this.rand);
        this.mapFeaturesEnabled = world.getWorldInfo().isMapFeaturesEnabled();
        this.landmarkCell = new RwgCellNoise(rtgWorld.seed(), (short) 0);
        this.smolderingGrass = BlockUtil.getBlock("biomesoplenty:grass", null);
        this.mapRand = new Random(rtgWorld.seed());
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

        final float[] mapGenColumn = this.smallRender[SMALL_RENDER_CENTER_INDEX];
        for (int activeIndex = 0; activeIndex < this.activeBiomeCount; activeIndex++) {
            final int biomeId = this.activeBiomeIds[activeIndex];
            if (biomeId < 0 || biomeId >= mapGenColumn.length || mapGenColumn[biomeId] <= 0f) {
                continue;
            }
            final IRealisticBiome mapGenBiome = RtgRealisticIndex.biomeOf(biomeId);
            if (mapGenBiome == null) {
                continue;
            }
            mapGenBiome.generateMapGen(this.rtgWorld, primer, this.mapRand, this.world.getSeed(), cx, cz,
                    landscape.noise);
        }

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

                // ---- 「地表渗透」----
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
        UndergroundRiver.carve(primer, cx, cz, landscape);
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

        // ---- 岩浆房 ----
        //
        // 位置：洞穴/峡谷与结构之后、new Chunk(...) 之前。黑曜石外壳要能封住洞穴与结构挖开的口子。
        // 门控：布局恒为大陆模式，所以这里用地标子系统的唯一开关代替；
        // 该判据 + 生成器内部的逐列坐标判定 ⇒ 配置为 0 时本块零副作用。
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        if (layout != null && RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f
                && RealisticBiomeIslandVolcano.volcanoIsland instanceof RealisticBiomeIslandVolcano) {
            RealisticBiomeIslandVolcano.volcanoIsland.generateMagmaChamber(primer, cx, cz, layout);
        }

        // ---- 熔岩洞地标本体 ----
        // 紧随岩浆房之后（它也要在洞穴/结构之后，因为用黑曜石/岩浆把洞穴壁改造成地标）。
        if (layout != null && RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f) {
            LavaCaveLandmark.generate(primer, cx, cz, layout, this.rtgWorld.simplexInstance(0), this.landmarkCell);
        }

        // ---- Chunk finalize ----
        long tFinalize = ChunkGenerationProfiler.start(Category.CHUNK_FINALIZE);
        Chunk chunk = new Chunk(this.world, primer, cx, cz);
        for (int i = 0; i < 256; ++i) {
            int value = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
            this.intBiomeArray[i] = value;
            this.byteBiomeArray[i] = (byte) value;
        }
        if (this.useIntBiomeArray) {
            // 必须传副本：intBiomeArray 是复用字段，下个区块生成时会被覆写。
            ((INewChunk) chunk).setIntBiomeArray(this.intBiomeArray.clone());
        }
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
        // 火山地表的查询入口（世界级布局单例）。
        final RtgBiomeLayout layout = RtgLayoutAccess.current();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.setPos(worldX + x, 0, worldZ + z);
                float river = rivers[x * 16 + z];
                final IRealisticBiome columnBiome = biomes[x * 16 + z];

                // ---- 火山地表 ----
                if (columnBiome instanceof RealisticBiomeIslandVolcano && layout != null
                        && RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f) {
                    final int blockX = worldX + x;
                    final int blockZ = worldZ + z;
                    final long coordinates = layout.getVolcanoCoordinates(blockX, blockZ);
                    if (coordinates != Long.MIN_VALUE) {
                        final IRealisticBiome underlyingBiome = layout.getVolcanoUnderlyingBiome(blockX, blockZ);
                        final float localX = ContinentalNoise.unpackVolcanoX(coordinates);
                        final float localZ = ContinentalNoise.unpackVolcanoY(coordinates);
                        final float baseHeight = layout.getVolcanoBaseHeight(blockX, blockZ);
                        final int addedBlocks = this.volcanoSurfaceDepth[x * 16 + z] & 255;
                        if (addedBlocks > 0 || underlyingBiome == null) {
                            ((RealisticBiomeIslandVolcano) columnBiome).rReplaceAt(primer, blockX, blockZ, x, z, -1,
                                    rtgWorld, noise, river, base, localX, localZ, baseHeight,
                                    layout.getVolcanoUnderlyingHeight(blockX, blockZ), Math.max(1, addedBlocks));
                        } else {
                            base[x * 16 + z] = underlyingBiome.baseBiome();
                            underlyingBiome.rReplace(primer, blockX, blockZ, x, z, -1, rtgWorld, noise, river, base);
                        }
                    } else {
                        IRealisticBiome actualBiome = layout.getBiomeDataAt(blockX, blockZ);
                        if (actualBiome == columnBiome) {
                            actualBiome = layout.getVolcanoUnderlyingBiome(blockX, blockZ);
                        }
                        if (actualBiome == null) {
                            actualBiome = columnBiome;
                        }
                        base[x * 16 + z] = actualBiome.baseBiome();
                        actualBiome.rReplace(primer, blockX, blockZ, x, z, -1, rtgWorld, noise, river, base);
                    }
                } else {
                    columnBiome.rReplace(primer, mpos, x, z, -1, rtgWorld, noise, river, base);
                }
                placeBedrock(primer, x, z);
            }
        }
    }

    /**
     * 按世界创建界面里的 {@code bedrockLayers}（默认 5，范围 1–10）放置基岩层。
     * <p>y=0 恒为基岩；其上各层按 1/(y+1) 的递减概率生成，模拟原版的锯齿状基岩。
     * 使用无状态位置哈希而非共享的 this.rand —— 后者会因多消耗随机数而改变洞穴/结构/装饰的序列。
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
            // 装饰的群系改从布局取（现实主义编号空间），而不是经 MC 群系编号往返。
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
        // 装饰期的河强：用 rtgc 的「1 = 河心」约定。
        final RtgBiomeLayout decoLayout = RtgLayoutAccess.current();
        float river = decoLayout == null ? 0f
                : -decoLayout.getRiverStrength(mpos.getX(), mpos.getZ());
        final ChunkLandscape landscape = getLandscape(biomeProvider, chunkPos);

        // ---- 邻域装饰权重累加 ----
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

        // rtgc 的 populate 取代了原版的 ChunkProviderServer#populate，而后者是
        // DecorateBiomeEvent.Pre/Post 的唯一发出点。此前这两个事件从未被发出，
        // 于是所有靠它们做装饰的 mod（含 BOP 的一部分植被）在本世界类型下完全不生效。
        // 单独计时：这个事件里跑的是别的模组的装饰处理器。
        long tPreEvent = ChunkGenerationProfiler.start(Category.POP_DECO_PRE_EVENT);
        MinecraftForge.EVENT_BUS.post(new DecorateBiomeEvent.Pre(this.world, this.rand, blockPos));
        ChunkGenerationProfiler.end(Category.POP_DECO_PRE_EVENT, tPreEvent);

        ChunkInfo.resetInvocations();
        ChunkInfo.resetDecoProfile();

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
            // ---- 按权重分摊装饰 ----
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
                // 海洋群系把"原版装饰"记下来，等冰雪 pass 之后再跑（它的 rDecorate 里那一段已被跳过）。
                if (neighbour.defersVanillaDecorateUntilAfterIce()) {
                    this.deferredOceanDecorations[id] = weight;
                }
            }
        }

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

        if (RTG.decoDebug() || (ChunkGenerationProfiler.isEnabled()
                && (System.nanoTime() - tPopDeco) > DECO_REPORT_THRESHOLD_NS)) {
            Logger.info("[RTG-DECOPROF] chunk({},{}) {}ms {}",
                    chunkX, chunkZ, (System.nanoTime() - tPopDeco) / 1_000_000L, ChunkInfo.decoReport());
        }

        // ---- 洞穴藤蔓 ----
        if (this.mapFeaturesEnabled) {
            RiverCaveVines.decorate(this.world, this.rand, this.rtgWorld, landscape,
                    blockPos.getX(), blockPos.getZ());
        }

        // ---- 熔岩洞地标的装饰 ----
        if (RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f) {
            final RtgBiomeLayout landmarkLayout = RtgLayoutAccess.current();
            if (landmarkLayout != null) {
                LavaCaveLandmark.decorateSurface(this.world, landmarkLayout,
                        this.rtgWorld.simplexInstance(0), blockPos.getX(), blockPos.getZ(), this.smolderingGrass);
            }
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

        // ---- 冰雪之后才补海洋的原版装饰 ----
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
        // 上游 74cf4fd「Lighting bug reduction」：填充完成后修补四个角邻区块的天光。
        // 放在 onChunkPopulate(false) 之后、与上游同一位置。
        EventHandlerCommon.fixLightingAround(world, chunkPos);
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
     * {@code /rtg probe} 用：读出该列决定「地下河隧道 / 洞厅」生死的各个值。
     *
     * <p>不写缓存：命中失败时按同一套确定性算法重算一份临时对象，绝不插回
     * landscapeCache（否则一条诊断命令会顶掉别的区块的缓存项）。
     *
     * @return {@code [0]=山地链权重 [1]=山地链宿主 [2]=洞顶 y（0 = 该列没被开凿）
     *         [3]=1 表示布局来自缓存（此时 [2] 可信） [4]=干高度 [5]=山体门控权重（链与海拔取大）}
     */
    public float[] probeTunnelColumn(final BiomeProvider biomeProvider, final int x, final int z) {

        final ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        final ChunkLandscape cached = landscapeCache.get(chunkPos);
        final ChunkLandscape landscape = cached != null
                ? cached
                : generateLandscape(biomeProvider, new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16));
        final int k = (x & 15) * 16 + (z & 15);
        final float dry = landscape.dryHeight[k] > 0f ? landscape.dryHeight[k] : landscape.noise[k];
        final float chainHost = landscape.mountainChainRiverHost[k];
        final float host = UndergroundRiver.mountainHostAt(landscape, k);   // 与开凿共用同一函数
        return new float[] {
                landscape.mountainChainWeight[k],
                chainHost,
                landscape.riverCaveCeiling[k],
                cached != null ? 1f : 0f,
                dry,
                host };
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
        // 河道统一走布局：与主路径完全同源（避免与布局里重复的旧河道族漂移）。
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
                // 与主路径同约定：RWG 的 river 是 getRiverStrength 的结果 + 1f（0 = 河心，1 = 内陆）。
                float height = singleBiome.rNoise(rtgWorld, x, z, 1.0f, riverValues[k] + 1f);
                landscape.noise[k] = height;
                landscape.dryHeight[k] = height;        // 与主路径同源：雕刻前的"干高度"
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

        for (int i = 0; i < totalSampleSize; i++) {
            int xOffset = ((i - sampleSize) * 8) - 8;
            int rowOffset = i * sampleArraySize;
            for (int j = 0; j < totalSampleSize; j++) {
                int zOffset = ((j - sampleSize) * 8) - 8;
                final int sampleX = baseOffsetX + xOffset;
                final int sampleZ = baseOffsetZ + zOffset;
                IRealisticBiome layoutBiome = RtgLayoutAccess.biomeAt(sampleX, sampleZ);
                // ---- 采样时把火山群系换成它的「底层群系」----
                if (layoutBiome instanceof RealisticBiomeIslandVolcano) {
                    final RtgBiomeLayout volcanoLayout = RtgLayoutAccess.current();
                    final IRealisticBiome underlyingBiome = volcanoLayout == null
                            ? null
                            : volcanoLayout.getVolcanoUnderlyingBiome(sampleX, sampleZ);
                    if (underlyingBiome != null) {
                        layoutBiome = underlyingBiome;
                    }
                }
                if (layoutBiome != null) {
                    biomeData[rowOffset + j] = RtgRealisticIndex.idFor(layoutBiome);
                } else {
                    // 布局未覆盖该列（fail-soft）：退回 MC 编号，与引入本类之前的行为一致
                    tempPos.setPos(sampleX, 0, sampleZ);
                    biomeData[rowOffset + j] = Biome.getIdForBiome(biomeProvider.getBiome(tempPos));
                }
            }
        }

        // 采样网格建好后一次性解析出「哪些点是山地链」，供 256 列的邻域扫描复用。
        for (int i = 0; i < biomeData.length; i++) {
            sampleIsChain[i] = RtgRealisticIndex.biomeOf(biomeData[i]) instanceof RealisticBiomeMountainChain;
        }

        // ---- 本次区块的 active 群系表 ----
        Arrays.fill(this.activeBiomeFlags, false);
        for (final int id : biomeData) {
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
        // 链权重/宿主强度随区块保存（landscape 可能来自缓存），不能放生成器字段
        final float[] chainWeight = landscape.mountainChainWeight;
        final float[] chainHost = landscape.mountainChainRiverHost;
        // 地表侧的群系边界抖动产物，同样随区块保存
        final IRealisticBiome[] surfaceBiome = landscape.surfaceBiome;
        final SimplexNoise bRandNoise = rtgWorld.simplexInstance(0);

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                int smallIdx = (i + 4) * smallWidth + (j + 4);
                int worldX = chunkWorldX + i;
                int worldZ = chunkWorldZ + j;
                // 火山灰厚度逐列清零（复用字段，不清会把上一个区块的厚度漏进本区块的地表）。
                this.volcanoSurfaceDepth[k] = 0;

                // ---- 地表群系的噪声抖动 ----
                //
                // 用一张 15 格尺度的噪声去扫累积权重区间：先越过噪声的那个群系赢得这一列的地表。
                // 这是"群系边界呈噪声状而不是直线"的另一半（第一半是四层 HUGE→SMALL 混合）。
                // 单群系列（权重 1.0）必然被自己跨过，不可能改选别人 —— 抖动只作用在权重混合的过渡带。
                final float bRand = Math.max(0f, Math.min(0.99999f,
                        0.5f + bRandNoise.noise2f(worldX / 15f, worldZ / 15f)));
                float bCount = 0f;
                IRealisticBiome surfacePick = null;
                int firstNonZero = -1;

                // 链权重只累加「包成山地链」的那些贡献项。
                float chain = 0f;

                // 无条件执行四层 HUGE→SMALL 混合的加权求和。
                // 遍历 activeBiomeIds（升序，顺序契约），不是 0..256。
                float totalHeight = 0f;
                final float[] weights = smallRender[smallIdx];
                for (int ai = 0; ai < this.activeBiomeCount; ai++) {
                    final int bid = this.activeBiomeIds[ai];
                    final float weight = weights[bid];
                    if (weight <= 0f) {
                        continue;
                    }
                    // 累积必须放在高度求和之前，与 RWG 同一段代码内的先后一致。
                    if (firstNonZero < 0) {
                        firstNonZero = bid;
                    }
                    if (bCount <= 1f) {
                        bCount += weight;
                        if (bCount > bRand) {
                            surfacePick = RtgRealisticIndex.biomeOf(bid);
                            bCount = 2f;        // 越过一次就停
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

                // 扫不过去时明确回落到「第一个非零权重的群系」，结果确定、不依赖调用顺序。
                surfaceBiome[k] = surfacePick != null
                        ? surfacePick
                        : RtgRealisticIndex.biomeOf(firstNonZero);

                chainWeight[k] = chain;
                // 宿主强度还要算上"附近有链"的影响，否则链边缘的隧道会断掉。
                chainHost[k] = Math.max(chain, nearbyMountainChainInfluence(i, j));

                // ---- 火山锥的高度叠加 ----
                if (RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f
                        && RealisticBiomeIslandVolcano.volcanoIsland instanceof RealisticBiomeIslandVolcano) {
                    final long coordinates = layout.getVolcanoVicinityCoordinates(worldX, worldZ);
                    if (coordinates != Long.MIN_VALUE) {
                        final RealisticBiomeIslandVolcano volcano = RealisticBiomeIslandVolcano.volcanoIsland;
                        final float localX = ContinentalNoise.unpackVolcanoX(coordinates);
                        final float localZ = ContinentalNoise.unpackVolcanoY(coordinates);
                        final float underlyingHeight = baseHeights[k];
                        final float volcanoHeight = volcano.rNoiseAt(rtgWorld, localX, localZ,
                                layout.getVolcanoBaseHeight(worldX, worldZ), underlyingHeight);
                        baseHeights[k] = volcanoHeight;
                        final int addedBlocks = (int) volcanoHeight - (int) underlyingHeight;
                        final boolean lavaBasin = volcano.isInsideLavaFill(rtgWorld, localX, localZ);
                        final float ashBlend = Math.max(0f, Math.min(1f, (addedBlocks - 1f) / 4f));
                        final float ashNoise = Math.max(0f, Math.min(1f,
                                0.5f + bRandNoise.noise2f(worldX / 12f, worldZ / 12f) * 0.5f));
                        final boolean volcanicSurface = addedBlocks >= 5 || addedBlocks > 0 && ashBlend > ashNoise;
                        if ((volcanicSurface || lavaBasin)
                                && layout.getVolcanoCoordinates(worldX, worldZ) != Long.MIN_VALUE) {
                            surfaceBiome[k] = volcano;
                            this.volcanoSurfaceDepth[k] = (byte) (lavaBasin ? 6 : Math.min(127, addedBlocks));
                        }
                    }
                }

                // ---- 熔岩洞地标的通风口锥体 ----
                if (RwgLayoutConfig.averageLandmarksPerTypeAndContinent > 0f
                        && layout.getVolcanoVicinityCoordinates(worldX, worldZ) == Long.MIN_VALUE) {
                    final long caveCoordinates = layout.getLavaCaveCoordinates(worldX, worldZ);
                    if (caveCoordinates != Long.MIN_VALUE) {
                        baseHeights[k] = LavaCaveLandmark.surfaceHeight(bRandNoise,
                                ContinentalNoise.unpackVolcanoX(caveCoordinates),
                                ContinentalNoise.unpackVolcanoY(caveCoordinates),
                                baseHeights[k]);
                    }
                }
            }
        }

        for (int k = 0; k < 256; k++) {
            landscape.noise[k] = baseHeights[k];
            landscape.dryHeight[k] = baseHeights[k];   // 河道雕刻之前的"干高度"（见 ChunkLandscape）
            landscape.river[k] = -riverValues[k];       // 转回 rtgc 的「1 = 河心」约定
        }

        // RWG 的河道雕刻：对混合后的高度执行一次。
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
     * 在 ±48 格的采样网格里找最近的山地链列，返回线性衰减的 0–1 影响值。
     *
     * <p>链与链之间、链与普通群系之间的过渡带上 mountainChainWeight 会掉到 0，
     * 但那里的地形已经在链的高度上了。只按自身权重门控会让隧道在这些边缘处断开。
     *
     * <p>读 {@link #sampleIsChain}（每区块一次性解析好），内层循环只有数组读取。
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
     * 采样数组的实际遍历宽度在每区块开始时取一次。
     *
     * <p>只用于兜底路径：金字塔与高度求和都改走 {@code activeBiomeIds}，
     * 本方法现在只服务于"布局返回 null 时按 MC 编号采样"的那条 fail-soft 分支与
     * {@code decoWeights} 的清零范围。
     */
    private static int biomeLoopBound() {
        return RtgRealisticIndex.usedBound();
    }

    /**
     * mix4：四个权重向量的算术平均。
     *
     * <p>只遍历 active 群系。数学上与遍历全 256 项完全等价：非 active 编号在整张
     * hugeRender/smallRender 里恒为 0（数组初始值），且从不被写入 ——
     * 因为每个被写入的格子都会先对当前 active 表清零。
     */
    private void mix4(float[] a, float[] b, float[] c, float[] d, float[] out) {
        for (int i = 0; i < this.activeBiomeCount; i++) {
            final int id = this.activeBiomeIds[i];
            out[id] = (a[id] + b[id] + c[id] + d[id]) * 0.25f;
        }
    }

    private void clearActiveBiomes(final float[] weights) {
        for (int i = 0; i < this.activeBiomeCount; i++) {
            weights[this.activeBiomeIds[i]] = 0f;
        }
    }

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