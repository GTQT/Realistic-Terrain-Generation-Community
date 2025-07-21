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
import rtg.api.util.LimitedArrayCacheMap;
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
import java.util.stream.IntStream;

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
    private final LimitedArrayCacheMap<ChunkPos, ChunkLandscape> landscapeCache = new LimitedArrayCacheMap<>(1024);// cache ChunkLandscape objects
    private final int sampleSize = 8;
    private final int sampleArraySize = sampleSize * 2 + 5;
    private final int[] biomeData = new int[sampleArraySize * sampleArraySize];
    private final BiomeAnalyzer analyzer = new BiomeAnalyzer();
    private final int[] xyinverted = analyzer.xyinverted();
    private final boolean mapFeaturesEnabled;
    private final Random rand;
    private final Biome[] baseBiomesList;
    // 添加RWG噪声参数
    private final int parabolicSize;
    private final int parabolicArraySize;
    private final float[] parabolicField;
    private float parabolicFieldTotal;

    public ChunkGeneratorRTG(RTGWorld rtgWorld) {

        Logger.debug("Instantiating CPRTG using generator settings: {}", rtgWorld.world().getWorldInfo().getGeneratorOptions());

        this.world = rtgWorld.world();
        this.rtgWorld = rtgWorld;
        this.settings = rtgWorld.getGeneratorSettings();

// TODO: [1.12] seaLevel will be removed as terrain noise values are all hardcoded and will not variate properly.
        this.world.setSeaLevel(this.settings.seaLevel);
        this.rand = new Random(rtgWorld.seed());
        this.rtgWorld.setRandom(this.rand);
        this.mapFeaturesEnabled = world.getWorldInfo().isMapFeaturesEnabled();

        this.caveGenerator = TerrainGen.getModdedMapGen(new MapGenCavesRTG(this.settings.caveChance, this.settings.caveDensity), EventType.CAVE);
        this.ravineGenerator = TerrainGen.getModdedMapGen(new MapGenRavineRTG(this.settings.ravineChance), EventType.RAVINE);
        this.villageGenerator = (MapGenVillage) TerrainGen.getModdedMapGen(new MapGenVillage(StructureType.VILLAGE.getSettings(this.settings)), EventType.VILLAGE);
        this.strongholdGenerator = (MapGenStronghold) TerrainGen.getModdedMapGen(new MapGenStronghold(StructureType.STRONGHOLD.getSettings(this.settings)), EventType.STRONGHOLD);
        this.woodlandMansionGenerator = new WoodlandMansionRTG(this, StructureType.MANSION.getSettings(this.settings));//don't allow mods to override our generator.
        this.mineshaftGenerator = (MapGenMineshaft) TerrainGen.getModdedMapGen(new MapGenMineshaft(StructureType.MINESHAFT.getSettings(this.settings)), EventType.MINESHAFT);
        this.scatteredFeatureGenerator = (MapGenScatteredFeature) TerrainGen.getModdedMapGen(new MapGenScatteredFeature(StructureType.TEMPLE.getSettings(this.settings)), EventType.SCATTERED_FEATURE);
        this.oceanMonumentGenerator = (StructureOceanMonument) TerrainGen.getModdedMapGen(new StructureOceanMonument(StructureType.MONUMENT.getSettings(this.settings)), EventType.OCEAN_MONUMENT);

        this.baseBiomesList = new Biome[256];

        // 初始化RWG抛物线场
        parabolicSize = sampleSize;
        parabolicArraySize = parabolicSize * 2 + 1;
        parabolicField = new float[parabolicArraySize * parabolicArraySize];
        parabolicFieldTotal = 0;
        for (int j = -parabolicSize; j <= parabolicSize; ++j) {
            for (int k = -parabolicSize; k <= parabolicSize; ++k) {
                float f = 0.445f / (float) Math.sqrt(j * j + k * k + 0.3F);
                parabolicField[(j + parabolicSize) + (k + parabolicSize) * parabolicArraySize] = f;
                parabolicFieldTotal += f;
            }
        }

        Logger.debug("FINISHED instantiating CPRTG.");
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

        //get standard biome Data
        for (int i = 0; i < 256; i++) {
            this.baseBiomesList[i] = landscape.biome[i].baseBiome();
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
            if (settings.useMineShafts) {
                this.mineshaftGenerator.generate(this.world, cx, cz, primer);
            }
            if (settings.useStrongholds) {
                this.strongholdGenerator.generate(this.world, cx, cz, primer);
            }
            if (settings.useVillages) {
                this.villageGenerator.generate(this.world, cx, cz, primer);
            }
            if (settings.useTemples) {
                this.scatteredFeatureGenerator.generate(this.world, cx, cz, primer);
            }
            if (settings.useMonuments) {
                this.oceanMonumentGenerator.generate(this.world, cx, cz, primer);
            }
            if (settings.useMansions) {
                this.woodlandMansionGenerator.generate(this.world, cx, cz, primer);
            }
        }


        // store in the in process pile
        Chunk chunk = new Chunk(this.world, primer, cx, cz);

        int[] intBiomeArray = new int[256];
        Arrays.fill(intBiomeArray, -1);

        byte[] byteBiomeArray = new byte[256];

        for (int i = 0; i < intBiomeArray.length; ++i) {
            // Biomes are y-first and terrain x-first
            intBiomeArray[i] = Biome.getIdForBiome(this.baseBiomesList[this.xyinverted[i]]);
            byteBiomeArray[i] = (byte) intBiomeArray[i];
        }

        if (Loader.isModLoaded("jeid")) {
            //noinspection ConstantConditions
            ((INewChunk) chunk).setIntBiomeArray(intBiomeArray);
        } else {
            chunk.setBiomeArray(byteBiomeArray);
        }

        chunk.generateSkylightMap();

        return chunk;
    }

    public void generateTerrain(ChunkPrimer primer, float[] noise) {

        int height;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                height = (int) noise[x * 16 + z];
                for (int y = 0; y < 256; y++) {
                    if (y > height) {
                        if (y < this.settings.seaLevel) {
                            primer.setBlockState(x, y, z, Blocks.WATER.getDefaultState());
                        } else {
                            primer.setBlockState(x, y, z, Blocks.AIR.getDefaultState());
                        }
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

        int worldX = cx * 16;
        int worldZ = cz * 16;

        MutableBlockPos mpos = new MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mpos.setPos(worldX + x, 0, worldZ + z);

                float river = -TerrainBase.getRiverStrength(mpos, rtgWorld);
                int depth = -1;
                biomes[x * 16 + z].rReplace(primer, mpos, x, z, depth, rtgWorld, noise, river, base);

                // sparse bedrock layers above y=0
                if (this.settings.bedrockLayers > 1) {
                    for (int bl = 9; bl >= 0; --bl) {
                        if (bl <= this.rand.nextInt(this.settings.bedrockLayers)) {
                            primer.setBlockState(x, bl, z, Blocks.BEDROCK.getDefaultState());
                        }
                    }
                } else {
                    primer.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState());
                }
            }
        }
    }

    @SuppressWarnings("ConstantConditions") //false-positive on `hasVillage`, IDEA is probably not looking deep enough.
    @Override
    public void populate(int chunkX, int chunkZ) {

        BlockFalling.fallInstantly = true;

        final BiomeProvider biomeProvider = this.world.getBiomeProvider();
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        final BlockPos blockPos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
        final BlockPos offsetpos = blockPos.add(8, 0, 8);

        IRealisticBiome biome = RTGAPI.getRTGBiome(biomeProvider.getBiome(blockPos.add(16, 0, 16)));

        this.rand.setSeed(rtgWorld.getChunkSeed(chunkX, chunkZ));

        boolean hasVillage = false;

        ForgeEventFactory.onChunkPopulate(true, this, this.world, this.rand, chunkX, chunkZ, false);

        if (this.mapFeaturesEnabled) {
            if (settings.useMineShafts) {
                mineshaftGenerator.generateStructure(world, rand, chunkPos);
            }
            if (settings.useStrongholds) {
                strongholdGenerator.generateStructure(world, rand, chunkPos);
            }
            if (settings.useVillages) {
                hasVillage = villageGenerator.generateStructure(world, rand, chunkPos);
            }
            if (settings.useTemples) {
                scatteredFeatureGenerator.generateStructure(world, rand, chunkPos);
            }
            if (settings.useMonuments) {
                oceanMonumentGenerator.generateStructure(this.world, rand, chunkPos);
            }
            if (settings.useMansions) {
                woodlandMansionGenerator.generateStructure(world, rand, chunkPos);
            }
        }

        // water lakes.
        if (settings.useWaterLakes && settings.waterLakeChance > 0 && !hasVillage) {

            final long nextchance = rand.nextLong();
            final int surfacechance = settings.getSurfaceWaterLakeChance(biome.waterLakeMult());
            final BlockPos pos = offsetpos.add(rand.nextInt(16), 0, rand.nextInt(16));

            // possibly reduced chance to generate anywhere, including on surface
            if (surfacechance > 0 && nextchance % surfacechance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.LAKE)) {
                    (new WorldGenPond(Blocks.WATER.getDefaultState())).generate(world, rand, pos.up(rand.nextInt(256)));
                }
            }
            // normal chance to generate underground
            else if (nextchance % settings.waterLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.LAKE)) {
                    (new WorldGenLakes(Blocks.WATER)).generate(world, rand, pos.up(rand.nextInt(50) + 4));//make sure that underground lakes are sufficiently underground
                }
            }
        }

        // lava lakes.
        if (settings.useLavaLakes && settings.lavaLakeChance > 0 && !hasVillage) {

            final long nextchance = rand.nextLong();
            final int surfacechance = settings.getSurfaceLavaLakeChance(biome.lavaLakeMult());
            final BlockPos pos = offsetpos.add(rand.nextInt(16), 0, rand.nextInt(16));

            // possibly reduced chance to generate anywhere, including on surface
            if (surfacechance > 0 && nextchance % surfacechance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.LAVA)) {
                    (new WorldGenPond(Blocks.LAVA.getDefaultState())).generate(world, rand, pos.up(rand.nextInt(256)));
                }
            }
            // normal chance to generate underground
            else if (nextchance % settings.lavaLakeChance == 0) {
                if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.LAVA)) {
                    (new WorldGenLakes(Blocks.LAVA)).generate(world, rand, pos.up(rand.nextInt(50) + 4));//make sure that underground lakes are sufficiently underground
                }
            }
        }

        if (settings.useDungeons) {
            if (TerrainGen.populate(this, world, rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
                for (int i = 0; i < settings.dungeonChance; i++) {
                    (new WorldGenDungeons()).generate(world, rand, offsetpos.add(rand.nextInt(16), rand.nextInt(256), rand.nextInt(16)));
                }
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

        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            WorldEntitySpawner.performWorldGenSpawning(this.world, biome.baseBiome(), blockPos.getX() + 8, blockPos.getZ() + 8, 16, 16, this.rand);
        }

        if (TerrainGen.populate(this, this.world, this.rand, chunkX, chunkZ, hasVillage, PopulateChunkEvent.Populate.EventType.ICE)) {

            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {

                    // Ice.
                    final BlockPos freezePos = world.getPrecipitationHeight(offsetpos.add(x, 0, z)).down();
                    if (this.world.canBlockFreezeWater(freezePos)) {
                        this.world.setBlockState(freezePos, Blocks.ICE.getDefaultState(), 2);
                    }

                    // Snow layers.
                    final BlockPos surfacePos = world.getTopSolidOrLiquidBlock(offsetpos.add(x, 0, z));
                    if (settings.useSnowLayers) {
                        // start at 32 blocks above the surface (should be above any tree leaves), and move down placing
                        // snow layers on any leaves, or the surface block, if the temperature permits it.
                        for (BlockPos checkPos = surfacePos.up(32); checkPos.getY() >= surfacePos.getY(); checkPos = checkPos.down()) {
                            if (world.getBlockState(checkPos).getMaterial() == Material.AIR) {
                                final float temp = biomeProvider.getBiome(surfacePos).getTemperature(checkPos);
                                if (temp <= settings.getClampedSnowLayerTemp()) {
                                    if (Blocks.SNOW_LAYER.canPlaceBlockAt(world, checkPos)) {
                                        this.world.setBlockState(checkPos, Blocks.SNOW_LAYER.getDefaultState(), 2);
                                        // we already know the next check block is not air, so skip ahead.
                                        checkPos = checkPos.down();
                                    }
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
        boolean flag = false;
        if (settings.useMonuments && this.mapFeaturesEnabled && chunkIn.getInhabitedTime() < 3600L) {
            flag = this.oceanMonumentGenerator.generateStructure(this.world, this.rand, new ChunkPos(x, z));
        }
        return flag;
    }

    @Override
    public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
        Biome biome = this.world.getBiome(pos);
        if (this.mapFeaturesEnabled) {
            if (creatureType == EnumCreatureType.MONSTER && this.scatteredFeatureGenerator.isSwampHut(pos)) {
                return this.scatteredFeatureGenerator.getMonsters();
            }
            if (creatureType == EnumCreatureType.MONSTER && settings.useMonuments && this.oceanMonumentGenerator.isPositionInStructure(this.world, pos)) {
                return this.oceanMonumentGenerator.getMonsters();
            }
        }
        return biome.getSpawnableList(creatureType);
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {
        if (!this.mapFeaturesEnabled) {
            return null;
        }
        if ("Stronghold".equals(structureName) && this.strongholdGenerator != null) {
            return this.strongholdGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        if ("Mansion".equals(structureName) && this.woodlandMansionGenerator != null) {
            return this.woodlandMansionGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        if ("Monument".equals(structureName) && this.oceanMonumentGenerator != null) {
            return this.oceanMonumentGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        if ("Village".equals(structureName) && this.villageGenerator != null) {
            return this.villageGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        if ("Mineshaft".equals(structureName) && this.mineshaftGenerator != null) {
            return this.mineshaftGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        if ("Temple".equals(structureName) && this.scatteredFeatureGenerator != null) {
            return this.scatteredFeatureGenerator.getNearestStructurePos(worldIn, position, findUnexplored);
        }
        return null;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void recreateStructures(Chunk chunk, int cx, int cz) {
        if (this.mapFeaturesEnabled) {
            if (this.settings.useMineShafts) {
                this.mineshaftGenerator.generate(this.world, cx, cz, null);
            }
            if (this.settings.useVillages) {
                this.villageGenerator.generate(this.world, cx, cz, null);
            }
            if (this.settings.useStrongholds) {
                this.strongholdGenerator.generate(this.world, cx, cz, null);
            }
            if (this.settings.useTemples) {
                this.scatteredFeatureGenerator.generate(this.world, cx, cz, null);
            }
            if (this.settings.useMonuments) {
                this.oceanMonumentGenerator.generate(this.world, cx, cz, null);
            }
            if (this.settings.useMansions) {
                this.woodlandMansionGenerator.generate(this.world, cx, cz, null);
            }
        }
    }

    @Override
    public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {
        if (!this.mapFeaturesEnabled) {
            return false;
        }
        if ("Stronghold".equals(structureName) && this.strongholdGenerator != null) {
            return this.strongholdGenerator.isInsideStructure(pos);
        }
        if ("Mansion".equals(structureName) && this.woodlandMansionGenerator != null) {
            return this.woodlandMansionGenerator.isInsideStructure(pos);
        }
        if ("Monument".equals(structureName) && this.oceanMonumentGenerator != null) {
            return this.oceanMonumentGenerator.isInsideStructure(pos);
        }
        if ("Village".equals(structureName) && this.villageGenerator != null) {
            return this.villageGenerator.isInsideStructure(pos);
        }
        if ("Mineshaft".equals(structureName) && this.mineshaftGenerator != null) {
            return this.mineshaftGenerator.isInsideStructure(pos);
        }
        return ("Temple".equals(structureName) && this.scatteredFeatureGenerator != null) && this.scatteredFeatureGenerator.isInsideStructure(pos);
    }

    public ChunkLandscape getLandscape(final BiomeProvider biomeProvider, final ChunkPos chunkPos) {
        final BlockPos blockPos = new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16);
        ChunkLandscape landscape = landscapeCache.get(chunkPos);
        if (landscape == null) {
            landscape = generateLandscape(biomeProvider, blockPos);
            landscapeCache.put(chunkPos, landscape);
        }
        return landscape;
    }

    private synchronized ChunkLandscape generateLandscape(BiomeProvider biomeProvider, BlockPos blockPos) {
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

    private synchronized void getNewerNoise(final BiomeProvider biomeProvider, final int worldX, final int worldZ, ChunkLandscape landscape) {
        // 步骤1: 采样生物群系数据
        final int baseOffsetX = worldX - 8;
        final int baseOffsetZ = worldZ - 8;
        for (int i = -sampleSize; i < sampleSize + 5; i++) {
            final int xOffset = (i * 8) - 8;
            final int dataRow = (i + sampleSize) * sampleArraySize;

            for (int j = -sampleSize; j < sampleSize + 5; j++) {
                final int zOffset = (j * 8) - 8;
                BlockPos pos = new BlockPos(baseOffsetX + xOffset, 0, baseOffsetZ + zOffset);
                biomeData[dataRow + (j + sampleSize)] = Biome.getIdForBiome(biomeProvider.getBiome(pos));
            }
        }


        // 步骤2: 创建HUGE渲染层 (9x9网格)
        float[][] hugeRender = new float[81][256];
        for (int i = -1; i < 4; i++) {
            for (int j = -1; j < 4; j++) {
                int index = (i * 2 + 2) * 9 + (j * 2 + 2);
                hugeRender[index] = new float[256];
                for (int k = -parabolicSize; k <= parabolicSize; k++) {
                    for (int l = -parabolicSize; l <= parabolicSize; l++) {
                        int biomeId = biomeData[(i + k + sampleSize + 1) * sampleArraySize + (j + l + sampleSize + 1)];
                        float weight = parabolicField[(k + parabolicSize) + (l + parabolicSize) * parabolicArraySize] / parabolicFieldTotal;
                        hugeRender[index][biomeId] += weight;
                    }
                }
            }
        }

        // 步骤3: HUGE层混合
        final int hugeWidth = 9; // HUGE层网格宽度

        // HUGE 1: 混合4个角点 (4x4)
        for (int i = 0; i < 4; i++) {
            int i2 = i * 2;
            int i2p2 = i2 + 2;
            int rowCenter = i2 + 1;

            for (int j = 0; j < 4; j++) {
                int j2 = j * 2;
                int j2p2 = j2 + 2;
                int colCenter = j2 + 1;

                int index = rowCenter * hugeWidth + colCenter;
                hugeRender[index] = mix4(
                        hugeRender[i2 * hugeWidth + j2],
                        hugeRender[i2p2 * hugeWidth + j2],
                        hugeRender[i2 * hugeWidth + j2p2],
                        hugeRender[i2p2 * hugeWidth + j2p2]
                );
            }
        }

        // 步骤4: 创建SMALL渲染层 (25x25网格) (优化版)
        final int smallWidth = 25; // SMALL层网格宽度
        float[][] smallRender = new float[625][256];

        // 初始化SMALL层的关键点 (7x7)
        for (int i = 0; i < 7; i++) {
            int i1 = i + 1;
            int i2 = i + 2;
            int i4 = i * 4;

            for (int j = 0; j < 7; j++) {
                int j1 = j + 1;
                int j2 = j + 2;
                int j4 = j * 4;

                int smallIndex = i4 * smallWidth + j4;

                // 使用位运算优化奇偶判断 (i和j奇偶性不同)
                if (((i ^ j) & 1) != 0) {
                    smallRender[smallIndex] = mix4(
                            hugeRender[i * hugeWidth + j1],
                            hugeRender[i1 * hugeWidth + j],
                            hugeRender[i1 * hugeWidth + j2],
                            hugeRender[i2 * hugeWidth + j1]
                    );
                } else {
                    smallRender[smallIndex] = hugeRender[i1 * hugeWidth + j1];
                }
            }
        }

        // 步骤5: SMALL层混合
        // 优化1: 预先计算常用值
        final int width = 25; // 网格宽度

        // SMALL 1: 混合4个角点 (6x6)
        for (int i = 0; i < 6; i++) {
            int i4 = i * 4;
            int i4p4 = i4 + 4;
            int rowCenter = i4 + 2;

            for (int j = 0; j < 6; j++) {
                int j4 = j * 4;
                int j4p4 = j4 + 4;
                int colCenter = j4 + 2;

                int index = rowCenter * width + colCenter;
                smallRender[index] = mix4(
                        smallRender[i4 * width + j4],
                        smallRender[i4p4 * width + j4],
                        smallRender[i4 * width + j4p4],
                        smallRender[i4p4 * width + j4p4]
                );
            }
        }

        // SMALL 2: 混合交叉点
        for (int i = 0; i < 11; i++) {
            int i2 = i * 2;
            int i2p2 = i2 + 2;
            int i2p4 = i2 + 4;

            for (int j = (i & 1) ^ 1; j < 11; j += 2) {  // 优化分支预测
                int j2 = j * 2;
                int j2p2 = j2 + 2;
                int j2p4 = j2 + 4;

                int index = i2p2 * width + j2p2;
                smallRender[index] = mix4(
                        smallRender[i2 * width + j2p2],
                        smallRender[i2p2 * width + j2],
                        smallRender[i2p2 * width + j2p4],
                        smallRender[i2p4 * width + j2p2]
                );
            }
        }

        // SMALL 3: 混合中间点 (9x9)
        for (int i = 0; i < 9; i++) {
            int i2 = i * 2;
            int i2p2 = i2 + 2;
            int i2p4 = i2 + 4;
            int rowCenter = i2 + 3;

            for (int j = 0; j < 9; j++) {
                int j2 = j * 2;
                int j2p2 = j2 + 2;
                int j2p4 = j2 + 4;
                int colCenter = j2 + 3;

                int index = rowCenter * width + colCenter;
                smallRender[index] = mix4(
                        smallRender[i2p2 * width + j2p2],
                        smallRender[i2p4 * width + j2p2],
                        smallRender[i2p2 * width + j2p4],
                        smallRender[i2p4 * width + j2p4]
                );
            }
        }

        // SMALL 4: 填充剩余点
        for (int i = 0; i < 16; i++) {
            int i3 = i + 3;
            int i4 = i + 4;
            int i5 = i + 5;

            for (int j = (i & 1) ^ 1; j < 16; j += 2) {  // 优化分支预测
                int j4 = j + 4;

                int index = i4 * width + j4;
                smallRender[index] = mix4(
                        smallRender[i3 * width + j4],
                        smallRender[i4 * width + (j4 - 1)],
                        smallRender[i4 * width + (j4 + 1)],
                        smallRender[i5 * width + j4]
                );
            }
        }


        // 步骤6: 计算高度 - 兼容版优化
        float[] riverValues = new float[256]; // 16x16

        // 1. 并行计算河流强度 (使用简单位置计算)
        MutableBlockPos mpos = new MutableBlockPos();
        IntStream.range(0, 256).parallel().forEach(k -> {
            int x = worldX + (k / 16);
            int z = worldZ + (k % 16);
            mpos.setPos(x, 0, z);
            riverValues[k] = TerrainBase.getRiverStrength(mpos, rtgWorld);
        });

        // 2. 预计算非零权重生物群系索引
        List<int[]> nonZeroBiomes = new ArrayList<>(1024); // [l, biomeId, k]
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int l = (i + 4) * 25 + (j + 4);
                int k = i * 16 + j;
                for (int biomeId = 0; biomeId < 256; biomeId++) {
                    if (smallRender[l][biomeId] > 0) {
                        nonZeroBiomes.add(new int[]{l, biomeId, k});
                    }
                }
            }
        }

        // 3. 计算基础高度
        float[] baseHeights = new float[256];
        for (int[] entry : nonZeroBiomes) {
            int l = entry[0];
            int biomeId = entry[1];
            int k = entry[2];
            float weight = smallRender[l][biomeId];

            int i = k / 16;
            int j = k % 16;
            int x = worldX + i;
            int z = worldZ + j;

            IRealisticBiome biome = RTGAPI.getRTGBiome(biomeId);
            if (biome != null) {
                baseHeights[k] += biome.rNoise(
                        rtgWorld,
                        x,
                        z,
                        weight,
                        riverValues[k] + 1f
                ) * weight;
            }
        }

        // 4. 应用河流侵蚀和平滑
        for (int k = 0; k < 256; k++) {
            float river = riverValues[k];
            int i = k / 16;
            int j = k % 16;

            if (river > 0.5f) {
                // 优化侵蚀计算
                float erosion = 8f * Math.min(1f, Math.max(0f, (river - 0.5f) * 2f));
                float riverHeight = baseHeights[k] - erosion;

                // 边界安全检测
                boolean canSmooth = (i > 0) && (i < 15) && (j > 0) && (j < 15);

                if (canSmooth) {
                    // 使用周围4点平均值
                    float avg = 0.25f * (
                            baseHeights[k - 16] + // north (i-1,j)
                                    baseHeights[k + 16] + // south (i+1,j)
                                    baseHeights[k - 1]  + // west (i,j-1)
                                    baseHeights[k + 1]    // east (i,j+1)
                    );

                    // 优化混合曲线
                    float blend = river * 2f - 1f; // 映射[0.5,1]到[0,1]
                    blend = blend * blend * (3 - 2 * blend); // smoothstep

                    landscape.noise[k] = avg * (1 - blend) + riverHeight * blend;
                } else {
                    landscape.noise[k] = riverHeight;
                }
            } else {
                landscape.noise[k] = baseHeights[k];
            }

            landscape.river[k] = river;
        }

        // 填充生物群系数据
        MutableBlockPos biomePos = new MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            int wx = worldX + (x - 7) * 8 + 4;
            for (int z = 0; z < 16; z++) {
                biomePos.setPos(wx, 0, worldZ + (z - 7) * 8 + 4);
                landscape.biome[x*16+z] = RTGAPI.getRTGBiome(biomeProvider.getBiome(biomePos));
            }
        }
    }

    // RWG混合函数
    private float[] mix4(float[] a, float[] b, float[] c, float[] d) {
        float[] result = new float[256];
        // 使用局部变量减少数组访问开销
        float aVal, bVal, cVal, dVal;

        for (int i = 0; i < 256; ) {
            // 手动循环展开4次
            aVal = a[i]; bVal = b[i]; cVal = c[i]; dVal = d[i];
            result[i++] = (aVal + bVal + cVal + dVal) * 0.25f;

            aVal = a[i]; bVal = b[i]; cVal = c[i]; dVal = d[i];
            result[i++] = (aVal + bVal + cVal + dVal) * 0.25f;

            aVal = a[i]; bVal = b[i]; cVal = c[i]; dVal = d[i];
            result[i++] = (aVal + bVal + cVal + dVal) * 0.25f;

            aVal = a[i]; bVal = b[i]; cVal = c[i]; dVal = d[i];
            result[i++] = (aVal + bVal + cVal + dVal) * 0.25f;
        }
        return result;
    }
    // A helper class to generate settings maps to configure the vanilla structure classes
    private enum StructureType {

        MINESHAFT,
        MONUMENT,
        STRONGHOLD,
        TEMPLE,
        VILLAGE,
        MANSION;

        Map<String, String> getSettings(RTGChunkGenSettings settings) {

            Map<String, String> ret = new HashMap<>();

            if (this == MINESHAFT) {
                ret.put("chance", String.valueOf(settings.mineShaftChance));
                return ret;
            }

            if (this == MONUMENT) {
                ret.put("separation", String.valueOf(settings.monumentSeparation));
                ret.put("spacing", String.valueOf(settings.monumentSpacing));
                return ret;
            }

            if (this == STRONGHOLD) {
                ret.put("count", String.valueOf(settings.strongholdCount));
                ret.put("distance", String.valueOf(settings.strongholdDistance));
                ret.put("spread", String.valueOf(settings.strongholdSpread));
                return ret;
            }

            if (this == TEMPLE) {
                ret.put("distance", String.valueOf(settings.templeDistance));
                return ret;
            }

            if (this == VILLAGE) {
                ret.put("distance", String.valueOf(settings.villageDistance));
                ret.put("size", String.valueOf(settings.villageSize));
                return ret;
            }

            if (this == MANSION) {
                ret.put("spacing", String.valueOf(settings.mansionSpacing));
                ret.put("separation", String.valueOf(settings.mansionSeparation));
                return ret;
            }

            return ret;
        }
    }
}
