package rtg.world.gen;

import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
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
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.terraingen.InitMapGenEvent.EventType;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.common.Loader;
import org.dimdev.jeid.INewChunk;
import rtg.RTG;
import rtg.RTGConfig;
import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.util.NoiseArrayPool;
import rtg.api.util.noise.ISimplexData2D;
import rtg.api.util.noise.SimplexData2D;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.gen.RTGChunkGenSettings;
import rtg.api.world.gen.feature.WorldGenPond;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.BiomeAnalyzer;
import rtg.world.gen.structure.WoodlandMansionRTG;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkGeneratorRTG implements IChunkGenerator {
    private static final ThreadLocal<Integer> threadNoiseSlot =
            ThreadLocal.withInitial(() -> ThreadLocalRandom.current().nextInt(NoiseArrayPool.POOL_SIZE));
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
    private final BiomeAnalyzer analyzer = new BiomeAnalyzer();
    private final int[] xyinverted = analyzer.xyinverted();
    private final boolean mapFeaturesEnabled;
    private final Random rand;
    private final Biome[] baseBiomesList = new Biome[256];
    private final int sampleSize = 8;
    private final int sampleArraySize = sampleSize * 2 + 5;  // 21
    private final int[] biomeData = new int[sampleArraySize * sampleArraySize];
    private final int parabolicSize;
    private final int parabolicArraySize;
    private final float[] parabolicField;
    private final float parabolicFieldTotalInv;  // 预计算倒数
    private final NoiseArrayPool noisePool;
    private final MutableBlockPos[] posPool = new MutableBlockPos[4];
    private final AtomicInteger posBorrowedMask = new AtomicInteger(0);
    private final Map<ChunkPos, ChunkLandscape> landscapeCache;


    public ChunkGeneratorRTG(RTGWorld rtgWorld) {
        Logger.debug("Instantiating CPRTG using generator settings: {}",
                rtgWorld.world().getWorldInfo().getGeneratorOptions());

        this.world = rtgWorld.world();
        this.rtgWorld = rtgWorld;
        this.settings = rtgWorld.getGeneratorSettings();
        this.world.setSeaLevel(this.settings.seaLevel);
        this.rand = new Random(rtgWorld.seed());
        this.rtgWorld.setRandom(this.rand);
        this.mapFeaturesEnabled = world.getWorldInfo().isMapFeaturesEnabled();

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

        // 初始化抛物线权重场 (保留 RWG 算法)
        parabolicSize = sampleSize;
        parabolicArraySize = parabolicSize * 2 + 1;
        parabolicField = new float[parabolicArraySize * parabolicArraySize];
        float parabolicFieldTotal = 0f;
        for (int j = -parabolicSize; j <= parabolicSize; ++j) {
            for (int k = -parabolicSize; k <= parabolicSize; ++k) {
                float f = 0.445f / (float) Math.sqrt(j * j + k * k + 0.3F);
                parabolicField[(j + parabolicSize) + (k + parabolicSize) * parabolicArraySize] = f;
                parabolicFieldTotal += f;
            }
        }
        parabolicFieldTotalInv = 1.0f / parabolicFieldTotal;  // 预计算倒数

        this.noisePool = RTGAPI.getNoiseArrayPool();

        // 初始化坐标对象池
        for (int i = 0; i < posPool.length; i++) {
            posPool[i] = new MutableBlockPos();
        }

        // LRU 缓存
        this.landscapeCache = Collections.synchronizedMap(
                new LinkedHashMap<ChunkPos, ChunkLandscape>(
                        RTGConfig.landscapeCacheSize(), 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<ChunkPos, ChunkLandscape> eldest) {
                        return size() > RTGConfig.landscapeCacheSize();
                    }
                });

        Logger.debug("FINISHED instantiating CPRTG with GC optimizations.");
    }

    /**
     * 借用 MutableBlockPos (线程安全，支持嵌套)
     *
     * @return PosHandle 包装对象，必须用 try-with-resources 或手动 close()
     */
    private PosHandle borrowPosHandle() {
        int mask, newMask, bit;
        do {
            mask = posBorrowedMask.get();

            // 池耗尽处理：强制回收 + 警告
            if (mask == 0xF) {  // 0b1111 = 所有4个槽位都被借用
                Logger.warn("PosPool exhausted on thread {}, forcing recycle",
                        Thread.currentThread().getName());
                posBorrowedMask.set(0);
                mask = 0;
            }

            // 找第一个空闲槽位: ~mask 取反后找最低位的0
            bit = 1 << Integer.numberOfTrailingZeros(~mask & 0xF);
            newMask = mask | bit;

        } while (!posBorrowedMask.compareAndSet(mask, newMask));

        int slot = Integer.numberOfTrailingZeros(bit);
        return new PosHandle(posPool[slot], posBorrowedMask, bit);
    }

    @Override
    public Chunk generateChunk(final int cx, final int cz) {
        final ChunkPos chunkPos = new ChunkPos(cx, cz);
        final BlockPos blockPos = new BlockPos(cx * 16, 0, cz * 16);
        final BiomeProvider biomeProvider = this.world.getBiomeProvider();

        this.rand.setSeed(cx * 341873128712L + cz * 132897987541L);

        final ChunkPrimer primer = new ChunkPrimer();
        final ChunkLandscape landscape = getLandscape(biomeProvider, chunkPos);

        generateTerrain(primer, landscape.noise);

        // 获取标准生物群系数据
        for (int i = 0; i < 256; i++) {
            this.baseBiomesList[i] = landscape.biome[i].baseBiome();
        }

        // 表面抖动 (Surface Jitter) 实现群系渗透
        ISimplexData2D jitterData = SimplexData2D.newDisk();
        IRealisticBiome[] jitteredBiomes = new IRealisticBiome[256];

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = blockPos.getX() + i;
                int z = blockPos.getZ() + j;

                this.rtgWorld.simplexInstance(0).multiEval2D(x, z, jitterData);
                int pX = (int) Math.round(x + jitterData.getDeltaX() * RTGConfig.surfaceBlendRadius());
                int pZ = (int) Math.round(z + jitterData.getDeltaY() * RTGConfig.surfaceBlendRadius());

                IRealisticBiome actual = landscape.biome[(x & 15) * 16 + (z & 15)];
                IRealisticBiome jittered = landscape.biome[(pX & 15) * 16 + (pZ & 15)];

                jitteredBiomes[i * 16 + j] = (actual.getConfig().SURFACE_BLEED_IN.get()
                        && jittered.getConfig().SURFACE_BLEED_OUT.get()) ? jittered : actual;
            }
        }

        // 替换表层方块
        replaceBiomeBlocks(cx, cz, primer, jitteredBiomes, this.baseBiomesList, landscape.noise);

        // 生成结构
        if (this.settings.useCaves) {
            this.caveGenerator.generate(this.world, cx, cz, primer);
        }
        if (this.settings.useRavines) {
            this.ravineGenerator.generate(this.world, cx, cz, primer);
        }
        if (this.mapFeaturesEnabled) {
            if (settings.useMineShafts) this.mineshaftGenerator.generate(this.world, cx, cz, primer);
            if (settings.useStrongholds) this.strongholdGenerator.generate(this.world, cx, cz, primer);
            if (settings.useVillages) this.villageGenerator.generate(this.world, cx, cz, primer);
            if (settings.useTemples) this.scatteredFeatureGenerator.generate(this.world, cx, cz, primer);
            if (settings.useMonuments) this.oceanMonumentGenerator.generate(this.world, cx, cz, primer);
            if (settings.useMansions) this.woodlandMansionGenerator.generate(this.world, cx, cz, primer);
        }

        // 创建 Chunk 并设置生物群系
        Chunk chunk = new Chunk(this.world, primer, cx, cz);

        int[] intBiomeArray = new int[256];
        byte[] byteBiomeArray = new byte[256];

        for (int i = 0; i < 256; ++i) {
            intBiomeArray[i] = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
            byteBiomeArray[i] = (byte) intBiomeArray[i];
        }

        if (Loader.isModLoaded("jeid")) {
            ((INewChunk) chunk).setIntBiomeArray(intBiomeArray);
        } else {
            chunk.setBiomeArray(byteBiomeArray);
        }

        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public void populate(int chunkX, int chunkZ) {
        BlockFalling.fallInstantly = true;

        final BiomeProvider biomeProvider = this.world.getBiomeProvider();
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final BlockPos blockPos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
        final BlockPos offsetPos = blockPos.add(8, 0, 8);

        IRealisticBiome biome = RTGAPI.getRTGBiome(
                biomeProvider.getBiome(blockPos.add(16, 0, 16)));

        this.rand.setSeed(rtgWorld.getChunkSeed(chunkX, chunkZ));
        boolean hasVillage = false;

        ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, chunkX, chunkZ, false);

        // 结构生成 (位标志压缩配置)
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

        // 水湖生成 (支持地表/地下双模式)
        if (settings.useWaterLakes && settings.waterLakeChance > 0 && !hasVillage) {
            long nextChance = rand.nextLong();
            int surfaceChance = settings.getSurfaceWaterLakeChance(biome.waterLakeMult());
            BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));

            if (surfaceChance > 0 && nextChance % surfaceChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAKE)) {
                    new WorldGenPond(Blocks.WATER.getDefaultState())
                            .generate(world, rand, pos.up(rand.nextInt(256)));
                }
            } else if (nextChance % settings.waterLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAKE)) {
                    new WorldGenLakes(Blocks.WATER)
                            .generate(world, rand, pos.up(rand.nextInt(50) + 4));
                }
            }
        }

        // 岩浆湖生成
        if (settings.useLavaLakes && settings.lavaLakeChance > 0 && !hasVillage) {
            long nextChance = rand.nextLong();
            int surfaceChance = settings.getSurfaceLavaLakeChance(biome.lavaLakeMult());
            BlockPos pos = offsetPos.add(rand.nextInt(16), 0, rand.nextInt(16));

            if (surfaceChance > 0 && nextChance % surfaceChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAVA)) {
                    new WorldGenPond(Blocks.LAVA.getDefaultState())
                            .generate(world, rand, pos.up(rand.nextInt(256)));
                }
            } else if (nextChance % settings.lavaLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage,
                        PopulateChunkEvent.Populate.EventType.LAVA)) {
                    new WorldGenLakes(Blocks.LAVA)
                            .generate(world, rand, pos.up(rand.nextInt(50) + 4));
                }
            }
        }

        // 地牢生成
        if (settings.useDungeons && TerrainGen.populate(this, world, rand, chunkX, chunkZ,
                hasVillage, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
            for (int i = 0; i < settings.dungeonChance; i++) {
                new WorldGenDungeons().generate(world, rand,
                        offsetPos.add(rand.nextInt(16), rand.nextInt(256), rand.nextInt(16)));
            }
        }

        // 装饰生成
        float river = -TerrainBase.getRiverStrength(blockPos.add(16, 0, 16), rtgWorld);
        if (RTG.decorationsDisable() || biome.getConfig().DISABLE_RTG_DECORATIONS.get()) {
            if (river > 0.8f) {
                biome.getRiverBiome().baseBiome().decorate(this.world, this.rand, blockPos);
            } else {
                biome.baseBiome().decorate(this.world, this.rand, blockPos);
            }
        } else {
            if (river > 0.8f) {
                biome.getRiverBiome().rDecorate(this.rtgWorld, this.rand, chunkPos, river, hasVillage);
            } else {
                biome.rDecorate(this.rtgWorld, this.rand, chunkPos, river, hasVillage);
            }
        }

        // 生物生成
        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            WorldEntitySpawner.performWorldGenSpawning(this.world, biome.baseBiome(),
                    blockPos.getX() + 8, blockPos.getZ() + 8, 16, 16, this.rand);
        }

        // 冰雪生成
        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ICE)) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    BlockPos freezePos = world.getPrecipitationHeight(offsetPos.add(x, 0, z)).down();
                    if (this.world.canBlockFreezeWater(freezePos)) {
                        this.world.setBlockState(freezePos, Blocks.ICE.getDefaultState(), 2);
                    }

                    if (settings.useSnowLayers) {
                        BlockPos surfacePos = world.getTopSolidOrLiquidBlock(offsetPos.add(x, 0, z));
                        if (biomeProvider.getBiome(surfacePos).getTemperature(surfacePos)
                                <= settings.getClampedSnowLayerTemp()) {
                            for (BlockPos checkPos = surfacePos.up(32);
                                 checkPos.getY() >= surfacePos.getY(); checkPos = checkPos.down()) {
                                if (world.getBlockState(checkPos).getMaterial() == Material.AIR
                                        && Blocks.SNOW_LAYER.canPlaceBlockAt(world, checkPos)) {
                                    this.world.setBlockState(checkPos, Blocks.SNOW_LAYER.getDefaultState(), 2);
                                    checkPos = checkPos.down();
                                }
                            }
                        }
                    }
                }
            }
        }

        ForgeEventFactory.onChunkPopulate(false, this, this.world, this.rand, chunkX, chunkZ, hasVillage);
        BlockFalling.fallInstantly = false;
    }

    public void generateTerrain(ChunkPrimer primer, float[] noise) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = (int) noise[x * 16 + z];
                for (int y = 0; y < 256; y++) {
                    if (y > height) {
                        primer.setBlockState(x, y, z, y < this.settings.seaLevel ?
                                Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState());
                    } else {
                        primer.setBlockState(x, y, z, Blocks.STONE.getDefaultState());
                    }
                }
            }
        }
    }

    private void replaceBiomeBlocks(int cx, int cz, ChunkPrimer primer,
                                    IRealisticBiome[] biomes, Biome[] base, float[] noise) {

        if (!ForgeEventFactory.onReplaceBiomeBlocks(this, cx, cz, primer, this.world)) {
            return;
        }

        int worldX = cx * 16;
        int worldZ = cz * 16;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                try (PosHandle posHandle = borrowPosHandle()) {
                    MutableBlockPos pos = posHandle.pos;
                    pos.setPos(worldX + x, 0, worldZ + z);
                    float river = -TerrainBase.getRiverStrength(pos, rtgWorld);
                    biomes[x * 16 + z].rReplace(primer, pos, x, z, -1, rtgWorld, noise, river, base);
                    primer.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState());
                }
            }
        }
    }

    /**
     * Generate noise and biome data for a chunk using pooled arrays and precomputed heights
     * 优化：保留并行，河流公式改回老版本 (阈值0.5, blend从0开始, erosion=8f, 4邻域)
     */
    private void getNewerNoise(final BiomeProvider biomeProvider, final int worldX,
                               final int worldZ, ChunkLandscape landscape) {
        int slot = threadNoiseSlot.get();
        float[][] hugeRender = noisePool.borrowHuge(slot);
        float[][] smallRender = noisePool.borrowSmall(slot);
        try {

            // ==================== 步骤1: 采样生物群系数据 (13×13 网格, 8格间隔) ====================
            final int baseOffsetX = worldX - 8;
            final int baseOffsetZ = worldZ - 8;
            final int totalSampleSize = 2 * sampleSize + 5;
            try (PosHandle tempPosHandle = borrowPosHandle()) {
                MutableBlockPos tempPos = tempPosHandle.pos;
                for (int i = 0; i < totalSampleSize; i++) {
                    final int xOffset = ((i - sampleSize) * 8) - 8;
                    final int dataRow = i * sampleArraySize;
                    for (int j = 0; j < totalSampleSize; j++) {
                        final int zOffset = ((j - sampleSize) * 8) - 8;
                        tempPos.setPos(baseOffsetX + xOffset, 0, baseOffsetZ + zOffset);
                        biomeData[dataRow + j] = Biome.getIdForBiome(biomeProvider.getBiome(tempPos));
                    }
                }
            }

            // ==================== 步骤2: HUGE 渲染层 (9×9) - 抛物线权重聚合 ====================
            for (int i = -1; i < 4; i++) {
                for (int j = -1; j < 4; j++) {
                    int index = (i * 2 + 2) * 9 + (j * 2 + 2);
                    float[] weights = hugeRender[index];
                    for (int k = -parabolicSize; k <= parabolicSize; k++) {
                        int rowIdx = (i + k + sampleSize + 1) * sampleArraySize;
                        int weightRow = (k + parabolicSize) * parabolicArraySize;
                        for (int l = -parabolicSize; l <= parabolicSize; l++) {
                            int biomeId = biomeData[rowIdx + (j + l + sampleSize + 1)];
                            float weight = parabolicField[weightRow + (l + parabolicSize)] * parabolicFieldTotalInv;
                            weights[biomeId] += weight;
                        }
                    }
                }
            }

            // ==================== 步骤3: HUGE 层混合 (4×4 中间点) ====================
            final int hugeWidth = 9;
            for (int i = 0; i < 4; i++) {
                int i2 = i * 2, i2p2 = i2 + 2, rowCenter = i2 + 1;
                for (int j = 0; j < 4; j++) {
                    int j2 = j * 2, j2p2 = j2 + 2, colCenter = j2 + 1;
                    int index = rowCenter * hugeWidth + colCenter;
                    mix4InPlace(
                            hugeRender[i2 * hugeWidth + j2],
                            hugeRender[i2p2 * hugeWidth + j2],
                            hugeRender[i2 * hugeWidth + j2p2],
                            hugeRender[i2p2 * hugeWidth + j2p2],
                            hugeRender[index]
                    );
                }
            }

            // ==================== 步骤4: 创建 SMALL 渲染层 (25×25) ====================
            final int smallWidth = 25;

            // 4.1 初始化关键点 (7×7=49任务)
            java.util.stream.IntStream.range(0, 49).parallel().forEach(idx -> {
                int i = idx / 7;
                int j = idx % 7;
                int i1 = i + 1, i2 = i + 2, i4 = i * 4;
                int j1 = j + 1, j2 = j + 2, j4 = j * 4;
                int smallIndex = i4 * smallWidth + j4;
                if (((i ^ j) & 1) != 0) {
                    mix4InPlace(
                            hugeRender[i * hugeWidth + j1],
                            hugeRender[i1 * hugeWidth + j],
                            hugeRender[i1 * hugeWidth + j2],
                            hugeRender[i2 * hugeWidth + j1],
                            smallRender[smallIndex]
                    );
                } else {
                    System.arraycopy(hugeRender[i1 * hugeWidth + j1], 0,
                            smallRender[smallIndex], 0, 256);
                }
            });

            // 4.2 SMALL 层混合 1: 6×6=36角点
            java.util.stream.IntStream.range(0, 36).parallel().forEach(idx -> {
                int i = idx / 6;
                int j = idx % 6;
                int i4 = i * 4, i4p4 = i4 + 4, rowCenter = i4 + 2;
                int j4 = j * 4, j4p4 = j4 + 4, colCenter = j4 + 2;
                int index = rowCenter * smallWidth + colCenter;
                mix4InPlace(
                        smallRender[i4 * smallWidth + j4],
                        smallRender[i4p4 * smallWidth + j4],
                        smallRender[i4 * smallWidth + j4p4],
                        smallRender[i4p4 * smallWidth + j4p4],
                        smallRender[index]
                );
            });

            // 4.3 SMALL 层混合 2: 11×11交叉点
            java.util.stream.IntStream.range(0, 11).parallel().forEach(i -> {
                int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4;
                for (int j = (i & 1) ^ 1; j < 11; j += 2) {
                    int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4;
                    int index = i2p2 * smallWidth + j2p2;
                    mix4InPlace(
                            smallRender[i2 * smallWidth + j2p2],
                            smallRender[i2p2 * smallWidth + j2],
                            smallRender[i2p2 * smallWidth + j2p4],
                            smallRender[i2p4 * smallWidth + j2p2],
                            smallRender[index]
                    );
                }
            });

            // 4.4 SMALL 层混合 3: 9×9=81中间点
            java.util.stream.IntStream.range(0, 81).parallel().forEach(idx -> {
                int i = idx / 9;
                int j = idx % 9;
                int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4, rowCenter = i2 + 3;
                int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4, colCenter = j2 + 3;
                int index = rowCenter * smallWidth + colCenter;
                mix4InPlace(
                        smallRender[i2p2 * smallWidth + j2p2],
                        smallRender[i2p4 * smallWidth + j2p2],
                        smallRender[i2p2 * smallWidth + j2p4],
                        smallRender[i2p4 * smallWidth + j2p4],
                        smallRender[index]
                );
            });

            // 4.5 SMALL 层混合 4: 填充剩余点到16×16
            java.util.stream.IntStream.range(0, 128).parallel().forEach(idx -> {
                int i = idx / 8;
                int j = (idx % 8) * 2 + ((i & 1) ^ 1);
                int i3 = i + 3, i4 = i + 4, i5 = i + 5;
                int j4 = j + 4;
                int index = i4 * smallWidth + j4;
                mix4InPlace(
                        smallRender[i3 * smallWidth + j4],
                        smallRender[i4 * smallWidth + (j4 - 1)],
                        smallRender[i4 * smallWidth + (j4 + 1)],
                        smallRender[i5 * smallWidth + j4],
                        smallRender[index]
                );
            });

            // ==================== 步骤5: 预计算河流强度 ====================
            float[] riverValues = new float[256];
            try (PosHandle riverPosHandle = borrowPosHandle()) {
                MutableBlockPos riverPos = riverPosHandle.pos;
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        riverPos.setPos(worldX + i, 0, worldZ + j);
                        riverValues[i * 16 + j] = -TerrainBase.getRiverStrength(riverPos, rtgWorld);
                    }
                }
            }

            // ==================== 步骤6: 预计算所有位置的基础高度 ====================
            float[] baseHeights = new float[256];
            java.util.stream.IntStream.range(0, 256).parallel().forEach(idx -> {
                int i = idx >> 4;
                int j = idx & 15;
                int l = (i + 4) * smallWidth + (j + 4);
                int x = worldX + i;
                int z = worldZ + j;
                float totalHeight = 0f;
                for (int biomeId = 0; biomeId < 256; biomeId++) {
                    float weight = smallRender[l][biomeId];
                    if (weight <= 0f) continue;
                    IRealisticBiome biome = RTGAPI.getRTGBiome(biomeId);
                    if (biome == null) continue;
                    totalHeight += biome.rNoise(rtgWorld, x, z, weight, riverValues[idx] + 1f) * weight;
                }
                baseHeights[idx] = totalHeight;
            });

            // ==================== 步骤7: 应用河流侵蚀 ====================
            java.util.stream.IntStream.range(0, 256).parallel().forEach(idx -> {
                float river = riverValues[idx];
                float baseHeight = baseHeights[idx];

                if (river > 0.5f) {
                    float blend = river * 2f - 1f;

                    float erosion = 8f * blend;
                    float riverHeight = baseHeight - erosion;

                    if (idx > 15 && idx < 240 && (idx & 15) > 0 && (idx & 15) < 15) {
                        float avg = 0.25f * (
                                baseHeights[idx - 16] +  // north
                                        baseHeights[idx + 16] +  // south
                                        baseHeights[idx - 1]  +  // west
                                        baseHeights[idx + 1]     // east
                        );

                        blend = blend * blend * (3f - 2f * blend);

                        landscape.noise[idx] = avg * (1f - blend) + riverHeight * blend;
                    } else {
                        landscape.noise[idx] = riverHeight;
                    }
                } else {
                    landscape.noise[idx] = baseHeight;
                }

                landscape.river[idx] = river;
            });

            // ==================== 步骤8: 填充生物群系数据 ====================
            try (PosHandle biomePosHandle = borrowPosHandle()) {
                MutableBlockPos biomePos = biomePosHandle.pos;
                for (int x = 0; x < 16; x++) {
                    int wx = worldX + (x - 7) * 8 + 4;
                    for (int z = 0; z < 16; z++) {
                        int wz = worldZ + (z - 7) * 8 + 4;
                        biomePos.setPos(wx, 0, wz);
                        landscape.biome[x * 16 + z] = RTGAPI.getRTGBiome(
                                biomeProvider.getBiome(biomePos));
                    }
                }
            }

        } finally {
            noisePool.returnHuge(slot);
            noisePool.returnSmall(slot);
        }
    }

    /**
     * 原地四向混合: 结果写入 target 数组，避免 new float[256]
     * 手动展开 4 路循环，帮助 JIT 编译器优化
     */
    private void mix4InPlace(float[] a, float[] b, float[] c, float[] d, float[] target) {
        for (int i = 0; i < 256; i += 4) {
            float s0 = (a[i] + b[i] + c[i] + d[i]) * 0.25f;
            float s1 = (a[i + 1] + b[i + 1] + c[i + 1] + d[i + 1]) * 0.25f;
            float s2 = (a[i + 2] + b[i + 2] + c[i + 2] + d[i + 2]) * 0.25f;
            float s3 = (a[i + 3] + b[i + 3] + c[i + 3] + d[i + 3]) * 0.25f;
            target[i] = s0;
            target[i + 1] = s1;
            target[i + 2] = s2;
            target[i + 3] = s3;
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

    private ChunkLandscape generateLandscape(BiomeProvider biomeProvider, BlockPos blockPos) {
        final ChunkLandscape landscape = new ChunkLandscape();
        getNewerNoise(biomeProvider, blockPos.getX(), blockPos.getZ(), landscape);

        Biome[] biomes = new Biome[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                biomes[x * 16 + z] = biomeProvider.getBiome(blockPos.add(x, 0, z));
            }
        }
        analyzer.newRepair(biomes, this.biomeData, landscape);
        return landscape;
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z) {
        return settings.useMonuments && this.mapFeaturesEnabled
                && chunkIn.getInhabitedTime() < 3600L
                && this.oceanMonumentGenerator.generateStructure(
                this.world, this.rand, new ChunkPos(x, z));
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        Biome biome = this.world.getBiome(pos);
        if (this.mapFeaturesEnabled && creatureType == EnumCreatureType.MONSTER) {
            if (this.scatteredFeatureGenerator.isSwampHut(pos))
                return this.scatteredFeatureGenerator.getMonsters();
            if (settings.useMonuments && this.oceanMonumentGenerator.isPositionInStructure(this.world, pos)) {
                return this.oceanMonumentGenerator.getMonsters();
            }
        }
        return biome.getSpawnableList(creatureType);
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName,
                                           BlockPos position, boolean findUnexplored) {
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

    private static class PosHandle implements AutoCloseable {
        public final MutableBlockPos pos;
        private final AtomicInteger maskRef;
        private final int bit;
        private boolean closed = false;

        private PosHandle(MutableBlockPos pos, AtomicInteger maskRef, int bit) {
            this.pos = pos;
            this.maskRef = maskRef;
            this.bit = bit;
        }

        @Override
        public void close() {
            if (!closed) {
                int oldMask, newMask;
                do {
                    oldMask = maskRef.get();
                    newMask = oldMask & ~bit;
                } while (!maskRef.compareAndSet(oldMask, newMask));

                closed = true;
            }
        }
    }
}