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

public class ChunkGeneratorRTG implements IChunkGenerator {

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
    private final float[][] hugeRender = new float[81][256];
    private final float[][] smallRender = new float[625][256];
    private final MutableBlockPos[] posCache = new MutableBlockPos[4];
    private final float parabolicFieldTotalInv;
    private final Map<ChunkPos, ChunkLandscape> landscapeCache;
    private final int sampleSize = 8;
    private final int sampleArraySize = sampleSize * 2 + 5;
    private final int[] biomeData = new int[sampleArraySize * sampleArraySize];
    private final BiomeAnalyzer analyzer = new BiomeAnalyzer();
    private final int[] xyinverted = analyzer.xyinverted();
    private final boolean mapFeaturesEnabled;
    private final Random rand;
    private final Biome[] baseBiomesList;
    private final int parabolicSize;
    private final int parabolicArraySize;
    private final float[] parabolicField;
    private final MutableBlockPos mpos = new MutableBlockPos();
    private int posCacheIndex = 0;

    public ChunkGeneratorRTG(RTGWorld rtgWorld) {
        Logger.debug("Instantiating CPRTG using generator settings: {}", rtgWorld.world().getWorldInfo().getGeneratorOptions());
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

        this.baseBiomesList = new Biome[256];
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

        // 初始化位置缓存池
        for (int i = 0; i < posCache.length; i++) {
            posCache[i] = new MutableBlockPos();
        }

        this.landscapeCache = Collections.synchronizedMap(new LinkedHashMap<ChunkPos, ChunkLandscape>(RTGConfig.landscapeCacheSize(), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkPos, ChunkLandscape> eldest) {
                return size() > RTGConfig.landscapeCacheSize();
            }
        });
        Logger.debug("FINISHED instantiating CPRTG.");
    }

    /**
     * 单线程安全的位置借用：循环覆盖，无需归还
     */
    private MutableBlockPos borrowPos() {
        MutableBlockPos pos = posCache[posCacheIndex];
        posCacheIndex = (posCacheIndex + 1) % posCache.length;
        return pos;
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
        if (this.settings.useSingleBiome) {
            Biome singleBaseBiome = getSingleBiomeTarget().baseBiome();
            Arrays.fill(this.baseBiomesList, singleBaseBiome);
        } else {
            for (int i = 0; i < 256; i++) {
                this.baseBiomesList[i] = landscape.biome[i].baseBiome();
            }
        }

        ISimplexData2D jitterData = SimplexData2D.newDisk();
        IRealisticBiome[] jitteredBiomes = new IRealisticBiome[256];
        IRealisticBiome jitterbiome, actualbiome;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int x = blockPos.getX() + i;
                int z = blockPos.getZ() + j;
                this.rtgWorld.simplexInstance(0).multiEval2D(x, z, jitterData);
                int pX = (int) Math.round(x + jitterData.getDeltaX() * RTGConfig.surfaceBlendRadius());
                int pZ = (int) Math.round(z + jitterData.getDeltaY() * RTGConfig.surfaceBlendRadius());
                actualbiome = landscape.biome[(x & 15) * 16 + (z & 15)];
                jitterbiome = landscape.biome[(pX & 15) * 16 + (pZ & 15)];
                jitteredBiomes[i * 16 + j] = (actualbiome.getConfig().SURFACE_BLEED_IN.get() && jitterbiome.getConfig().SURFACE_BLEED_OUT.get()) ? jitterbiome : actualbiome;
            }
        }
        replaceBiomeBlocks(cx, cz, primer, jitteredBiomes, this.baseBiomesList, landscape.noise);

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

        Chunk chunk = new Chunk(this.world, primer, cx, cz);
        int[] intBiomeArray = new int[256];
        Arrays.fill(intBiomeArray, -1);
        byte[] byteBiomeArray = new byte[256];
        for (int i = 0; i < 256; ++i) {
            intBiomeArray[i] = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
            byteBiomeArray[i] = (byte) intBiomeArray[i];
        }
        if (Loader.isModLoaded("jeid") || Loader.isModLoaded("neid") || Loader.isModLoaded("reid")) {
            ((INewChunk) chunk).setIntBiomeArray(intBiomeArray);
        } else {
            chunk.setBiomeArray(byteBiomeArray);
        }
        chunk.generateSkylightMap();
        return chunk;
    }

    public void generateTerrain(ChunkPrimer primer, float[] noise) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = (int) noise[x * 16 + z];
                for (int y = 0; y < 256; y++) {
                    if (y > height) {
                        primer.setBlockState(x, y, z, y < this.settings.seaLevel ?
                                Blocks.WATER.getDefaultState() :
                                Blocks.AIR.getDefaultState()
                        );
                    } else {
                        primer.setBlockState(x, y, z, Blocks.STONE.getDefaultState());
                    }
                }
            }
        }
    }

    private void replaceBiomeBlocks(int cx, int cz, ChunkPrimer primer, IRealisticBiome[] biomes, Biome[] base, float[] noise) {
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
                    float river = -TerrainBase.getRiverStrength(mpos, rtgWorld);
                    singleBiome.rReplace(primer, mpos, x, z, -1, rtgWorld, noise, river, base);
                    primer.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState());
                }
            }
            return;
        }

        int worldX = cx * 16;
        int worldZ = cz * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.setPos(worldX + x, 0, worldZ + z);
                float river = -TerrainBase.getRiverStrength(mpos, rtgWorld);
                biomes[x * 16 + z].rReplace(primer, mpos, x, z, -1, rtgWorld, noise, river, base);
                primer.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState());
            }
        }
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
        BlockFalling.fallInstantly = true;
        final BiomeProvider biomeProvider = this.world.getBiomeProvider();
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final BlockPos blockPos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
        final BlockPos offsetPos = blockPos.add(8, 0, 8);

        IRealisticBiome biome;
        if (this.settings.useSingleBiome) {
            biome = getSingleBiomeTarget();
        } else {
            biome = RTGAPI.getRTGBiome(
                    biomeProvider.getBiome(blockPos.add(16, 0, 16)));
        }

        this.rand.setSeed(rtgWorld.getChunkSeed(chunkX, chunkZ));
        boolean hasVillage = false;
        ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, chunkX, chunkZ, false);

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

        if (settings.useDungeons && TerrainGen.populate(this, world, rand, chunkX, chunkZ,
                hasVillage, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
            for (int i = 0; i < settings.dungeonChance; i++) {
                new WorldGenDungeons().generate(world, rand,
                        offsetPos.add(rand.nextInt(16), rand.nextInt(256), rand.nextInt(16)));
            }
        }

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

        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            WorldEntitySpawner.performWorldGenSpawning(this.world, biome.baseBiome(),
                    blockPos.getX() + 8, blockPos.getZ() + 8, 16, 16, this.rand);
        }

        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage,
                PopulateChunkEvent.Populate.EventType.ICE)) {
            float snowTempThreshold = settings.getClampedSnowLayerTemp();
            MutableBlockPos mutablePos = new MutableBlockPos();

            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    mutablePos.setPos(offsetPos.getX() + x, 0, offsetPos.getZ() + z);
                    BlockPos freezePos = world.getPrecipitationHeight(mutablePos).down();
                    if (this.world.canBlockFreezeWater(freezePos)) {
                        this.world.setBlockState(freezePos, Blocks.ICE.getDefaultState(), 2);
                    }
                    if (settings.useSnowLayers) {
                        BlockPos surfacePos = world.getTopSolidOrLiquidBlock(mutablePos);
                        if (biomeProvider.getBiome(surfacePos).getTemperature(surfacePos) <= snowTempThreshold) {
                            for (BlockPos checkPos = surfacePos.up(32);
                                 checkPos.getY() >= surfacePos.getY(); checkPos = checkPos.down()) {
                                if (world.getBlockState(checkPos).getMaterial() == Material.AIR
                                        && Blocks.SNOW_LAYER.canPlaceBlockAt(world, checkPos)) {
                                    this.world.setBlockState(checkPos, Blocks.SNOW_LAYER.getDefaultState(), 2);
                                    break;
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

    private ChunkLandscape generateLandscape(BiomeProvider biomeProvider, BlockPos blockPos) {
        final ChunkLandscape landscape = new ChunkLandscape();

        if (this.settings.useSingleBiome) {
            IRealisticBiome singleBiome = getSingleBiomeTarget();
            int biomeId = Biome.getIdForBiome(singleBiome.baseBiome());
            Arrays.fill(this.biomeData, biomeId);
            getNewerNoiseSingleBiome(biomeProvider, blockPos.getX(), blockPos.getZ(), landscape, singleBiome);
            Arrays.fill(landscape.biome, singleBiome);
            return landscape;
        }

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

    private void getNewerNoiseSingleBiome(final BiomeProvider biomeProvider, final int worldX,
                                          final int worldZ, ChunkLandscape landscape, IRealisticBiome singleBiome) {
        float[] riverValues = new float[256];
        MutableBlockPos riverPos = borrowPos();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                riverPos.setPos(worldX + i, 0, worldZ + j);
                riverValues[i * 16 + j] = TerrainBase.getRiverStrength(riverPos, rtgWorld);
            }
        }

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                int x = worldX + i;
                int z = worldZ + j;
                float height = singleBiome.rNoise(rtgWorld, x, z, 1.0f, riverValues[k] + 1f);
                landscape.noise[k] = height;
            }
        }

        for (int k = 0; k < 256; k++) {
            float river = riverValues[k];
            float baseHeight = landscape.noise[k];

            if (river > 0.5f) {
                float erosion = 8f * Math.min(1f, (river - 0.5f) * 2f);
                float riverHeight = baseHeight - erosion;
                int i = k >> 4;
                int j = k & 15;

                if (i >= 1 && i <= 14 && j >= 1 && j <= 14) {
                    float avg = 0.25f * (
                            landscape.noise[k - 16] + landscape.noise[k + 16] +
                                    landscape.noise[k - 1] + landscape.noise[k + 1]
                    );
                    float blend = Math.min(1f, (river - 0.5f) * 2f);
                    landscape.noise[k] = avg + (riverHeight - avg) * blend;
                } else {
                    landscape.noise[k] = riverHeight;
                }
            } else {
                landscape.noise[k] = baseHeight;
            }
            landscape.river[k] = river;
        }
    }

    private void getNewerNoise(final BiomeProvider biomeProvider, final int worldX,
                               final int worldZ, ChunkLandscape landscape) {
        final int hugeWidth = 9;
        final int smallWidth = 25;

        // ==================== 步骤 1: 采样生物群系数据 ====================
        final int baseOffsetX = worldX - 8;
        final int baseOffsetZ = worldZ - 8;
        final int totalSampleSize = 2 * sampleSize + 5;
        MutableBlockPos tempPos = borrowPos();

        for (int i = 0; i < totalSampleSize; i++) {
            final int xOffset = ((i - sampleSize) * 8) - 8;
            final int dataRow = i * sampleArraySize;
            for (int j = 0; j < totalSampleSize; j++) {
                final int zOffset = ((j - sampleSize) * 8) - 8;
                tempPos.setPos(baseOffsetX + xOffset, 0, baseOffsetZ + zOffset);
                biomeData[dataRow + j] = Biome.getIdForBiome(biomeProvider.getBiome(tempPos));
            }
        }

        // ==================== 步骤 2: HUGE 渲染层 (9×9) ====================
        for (int i = -1; i < 4; i++) {
            for (int j = -1; j < 4; j++) {
                int index = (i * 2 + 2) * 9 + (j * 2 + 2);
                float[] weights = hugeRender[index];
                Arrays.fill(weights, 0f); // 复用数组需清空历史数据
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

        // ==================== 步骤 3: HUGE 层混合 ====================
        for (int i = 0; i < 4; i++) {
            int i2 = i * 2, i2p2 = i2 + 2, rowCenter = i2 + 1;
            for (int j = 0; j < 4; j++) {
                int j2 = j * 2, j2p2 = j2 + 2, colCenter = j2 + 1;
                int index = rowCenter * hugeWidth + colCenter;
                Arrays.fill(hugeRender[index], 0f);
                mix4(
                        hugeRender[i2 * hugeWidth + j2],
                        hugeRender[i2p2 * hugeWidth + j2],
                        hugeRender[i2 * hugeWidth + j2p2],
                        hugeRender[i2p2 * hugeWidth + j2p2],
                        hugeRender[index]
                );
            }
        }

        // ==================== 步骤 4: SMALL 渲染层 (25×25) ====================
        // 4.1 初始化关键点
        for (int idx = 0; idx < 49; idx++) {
            int i = idx / 7;
            int j = idx % 7;
            int i1 = i + 1, i2 = i + 2, i4 = i * 4;
            int j1 = j + 1, j2 = j + 2, j4 = j * 4;
            int smallIndex = i4 * smallWidth + j4;
            Arrays.fill(smallRender[smallIndex], 0f);

            if (((i ^ j) & 1) != 0) {
                mix4(
                        hugeRender[i * hugeWidth + j1],
                        hugeRender[i1 * hugeWidth + j],
                        hugeRender[i1 * hugeWidth + j2],
                        hugeRender[i2 * hugeWidth + j1],
                        smallRender[smallIndex]
                );
            } else {
                System.arraycopy(hugeRender[i1 * hugeWidth + j1], 0, smallRender[smallIndex], 0, 256);
            }
        }

        // 4.2 SMALL 层混合 1
        for (int idx = 0; idx < 36; idx++) {
            int i = idx / 6;
            int j = idx % 6;
            int i4 = i * 4, i4p4 = i4 + 4, rowCenter = i4 + 2;
            int j4 = j * 4, j4p4 = j4 + 4, colCenter = j4 + 2;
            int index = rowCenter * smallWidth + colCenter;
            Arrays.fill(smallRender[index], 0f);
            mix4(
                    smallRender[i4 * smallWidth + j4],
                    smallRender[i4p4 * smallWidth + j4],
                    smallRender[i4 * smallWidth + j4p4],
                    smallRender[i4p4 * smallWidth + j4p4],
                    smallRender[index]
            );
        }

        // 4.3 SMALL 层混合 2
        for (int i = 0; i < 11; i++) {
            int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4;
            for (int j = (i & 1) ^ 1; j < 11; j += 2) {
                int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4;
                int index = i2p2 * smallWidth + j2p2;
                Arrays.fill(smallRender[index], 0f);
                mix4(
                        smallRender[i2 * smallWidth + j2p2],
                        smallRender[i2p2 * smallWidth + j2],
                        smallRender[i2p2 * smallWidth + j2p4],
                        smallRender[i2p4 * smallWidth + j2p2],
                        smallRender[index]
                );
            }
        }

        // 4.4 SMALL 层混合 3
        for (int idx = 0; idx < 81; idx++) {
            int i = idx / 9;
            int j = idx % 9;
            int i2 = i * 2, i2p2 = i2 + 2, i2p4 = i2 + 4, rowCenter = i2 + 3;
            int j2 = j * 2, j2p2 = j2 + 2, j2p4 = j2 + 4, colCenter = j2 + 3;
            int index = rowCenter * smallWidth + colCenter;
            Arrays.fill(smallRender[index], 0f);
            mix4(
                    smallRender[i2p2 * smallWidth + j2p2],
                    smallRender[i2p4 * smallWidth + j2p2],
                    smallRender[i2p2 * smallWidth + j2p4],
                    smallRender[i2p4 * smallWidth + j2p4],
                    smallRender[index]
            );
        }

        // 4.5 SMALL 层混合 4
        for (int idx = 0; idx < 128; idx++) {
            int i = idx / 8;
            int j = (idx % 8) * 2 + ((i & 1) ^ 1);
            int i3 = i + 3, i4 = i + 4, i5 = i + 5;
            int j4 = j + 4;
            int index = i4 * smallWidth + j4;
            Arrays.fill(smallRender[index], 0f);
            mix4(
                    smallRender[i3 * smallWidth + j4],
                    smallRender[i4 * smallWidth + (j4 - 1)],
                    smallRender[i4 * smallWidth + (j4 + 1)],
                    smallRender[i5 * smallWidth + j4],
                    smallRender[index]
            );
        }

        // ==================== 步骤 5: 河流强度 ====================
        float[] riverValues = new float[256];
        MutableBlockPos riverPos = borrowPos();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                riverPos.setPos(worldX + i, 0, worldZ + j);
                riverValues[i * 16 + j] = TerrainBase.getRiverStrength(riverPos, rtgWorld);
            }
        }

        // ==================== 步骤 6: 基础高度 ====================
        float[] baseHeights = new float[256];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int k = i * 16 + j;
                int l = (i + 4) * smallWidth + (j + 4);
                int x = worldX + i;
                int z = worldZ + j;
                float totalHeight = 0f;
                for (int biomeId = 0; biomeId < 256; biomeId++) {
                    float weight = smallRender[l][biomeId];
                    if (weight <= 0) continue;
                    IRealisticBiome biome = RTGAPI.getRTGBiome(biomeId);
                    if (biome == null) continue;
                    totalHeight += biome.rNoise(rtgWorld, x, z, weight, riverValues[k] + 1f) * weight;
                }
                baseHeights[k] = totalHeight;
            }
        }

        // ==================== 步骤 7: 河流侵蚀 ====================
        for (int k = 0; k < 256; k++) {
            landscape.noise[k] = baseHeights[k];
            landscape.river[k] = riverValues[k];
        }

        // ==================== 步骤 8: 生物群系数据 ====================
        MutableBlockPos biomePos = borrowPos();
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

    private void mix4(float[] a, float[] b, float[] c, float[] d, float[] out) {
        for (int i = 0; i < 256; i++) {
            out[i] = (a[i] + b[i] + c[i] + d[i]) * 0.25f;
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