package rtg.world.gen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
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
    private final float[][] weightings = new float[sampleArraySize * sampleArraySize][256];
    private final MesaBiomeCombiner mesaCombiner = new MesaBiomeCombiner();
    private final BiomeAnalyzer analyzer = new BiomeAnalyzer();
    private final int[] xyinverted = analyzer.xyinverted();
    private final boolean mapFeaturesEnabled;
    private final Random rand;
    private final Biome[] baseBiomesList;
    // 添加RWG噪声参数
    private final int parabolicSize;
    private final int parabolicArraySize;
    private final float[] parabolicField;
    private boolean[] mesaPlateauBiome;
    private float parabolicFieldTotal;
    private final float[] testHeight = new float[256];
    private final float[] mapGenBiomes = new float[258];
    private final float[] borderNoise = new float[256];

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

        setWeightings();// landscape generator init
        setMesaPlauteauBiomes();//mark plateau biomes to combine mesas

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
            // 在populate方法中添加
            if (river > 0.5f) {
                generateRiverVegetation(world, rand, offsetpos, 65, river);
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


    // 河岸植被生成方法
    private void generateRiverVegetation(World world, Random rand, BlockPos pos, int seaLevel, float riverStrength) {
        int plants = 15 + (int)(riverStrength * 30); // 基于河流强度决定植被密度

        for (int i = 0; i < plants; i++) {
            BlockPos plantPos = pos.add(
                    rand.nextInt(16),
                    seaLevel + 1,
                    rand.nextInt(16)
            );

            // 睡莲（水面）
            if (rand.nextFloat() < 0.3f && world.getBlockState(plantPos).getBlock() == Blocks.WATER) {
                world.setBlockState(plantPos.up(), Blocks.WATERLILY.getDefaultState());
            }
            // 芦苇（浅水区）
            else if (world.getBlockState(plantPos.down()).getMaterial() == Material.WATER) {
                int height = 1 + rand.nextInt(2 + (int)(riverStrength * 3));
                for (int h = 0; h < height; h++) {
                    world.setBlockState(plantPos.up(h), Blocks.REEDS.getDefaultState());
                }
            }
            // 河岸草丛
            else if (world.getBlockState(plantPos.down()).isOpaqueCube()) {
                if (rand.nextFloat() < 0.7f) {
                    world.setBlockState(plantPos, Blocks.TALLGRASS.getStateFromMeta(1));
                } else {
                    // 河岸花朵
                    Block flower = rand.nextBoolean() ? Blocks.RED_FLOWER : Blocks.YELLOW_FLOWER;
                    world.setBlockState(plantPos, flower.getDefaultState());
                }
            }
        }
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


    /* Landscape Geneator */

    private void setWeightings() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                float limit = (float) Math.pow((56f * 56f), 0.7D);
                for (int mapX = 0; mapX < sampleArraySize; mapX++) {
                    for (int mapZ = 0; mapZ < sampleArraySize; mapZ++) {
                        float xDist = (x - (mapX - sampleSize) * 8);
                        float zDist = (z - (mapZ - sampleSize) * 8);
                        float distanceSquared = xDist * xDist + zDist * zDist;
                        float distance = (float) Math.pow(distanceSquared, 0.7D);
                        float weight = 1f - distance / limit;
                        if (weight < 0) {
                            weight = 0;
                        }
                        weightings[mapX * sampleArraySize + mapZ][x * 16 + z] = weight;
                    }
                }
            }
        }
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
        for (int i = -sampleSize; i < sampleSize + 5; i++) {
            for (int j = -sampleSize; j < sampleSize + 5; j++) {
                biomeData[(i + sampleSize) * sampleArraySize + (j + sampleSize)] =
                        Biome.getIdForBiome(biomeProvider.getBiome(new BlockPos(
                                worldX + ((i * 8) - 8),
                                0,
                                worldZ + ((j * 8) - 8)
                        )));
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
        // HUGE 1: 混合4个角点
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int index = (i * 2 + 1) * 9 + (j * 2 + 1);
                hugeRender[index] = mix4(new float[][]{
                        hugeRender[(i * 2) * 9 + (j * 2)],
                        hugeRender[(i * 2 + 2) * 9 + (j * 2)],
                        hugeRender[(i * 2) * 9 + (j * 2 + 2)],
                        hugeRender[(i * 2 + 2) * 9 + (j * 2 + 2)]
                });
            }
        }

        // 步骤4: 创建SMALL渲染层 (25x25网格)
        float[][] smallRender = new float[625][256];

        // 初始化SMALL层的关键点
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                int smallIndex = (i * 4) * 25 + (j * 4);
                if (!(i % 2 == 0 && j % 2 == 0) && !(i % 2 != 0 && j % 2 != 0)) {
                    smallRender[smallIndex] = mix4(new float[][]{
                            hugeRender[(i) * 9 + (j + 1)],
                            hugeRender[(i + 1) * 9 + (j)],
                            hugeRender[(i + 1) * 9 + (j + 2)],
                            hugeRender[(i + 2) * 9 + (j + 1)]
                    });
                } else {
                    smallRender[smallIndex] = hugeRender[(i + 1) * 9 + (j + 1)];
                }
            }
        }

        // 步骤5: SMALL层混合
        // SMALL 1: 混合4个角点
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                int index = (i * 4 + 2) * 25 + (j * 4 + 2);
                smallRender[index] = mix4(new float[][]{
                        smallRender[(i * 4) * 25 + (j * 4)],
                        smallRender[(i * 4 + 4) * 25 + (j * 4)],
                        smallRender[(i * 4) * 25 + (j * 4 + 4)],
                        smallRender[(i * 4 + 4) * 25 + (j * 4 + 4)]
                });
            }
        }

        // SMALL 2: 混合交叉点
        for (int i = 0; i < 11; i++) {
            for (int j = 0; j < 11; j++) {
                if (!(i % 2 == 0 && j % 2 == 0) && !(i % 2 != 0 && j % 2 != 0)) {
                    int index = (i * 2 + 2) * 25 + (j * 2 + 2);
                    smallRender[index] = mix4(new float[][]{
                            smallRender[(i * 2) * 25 + (j * 2 + 2)],
                            smallRender[(i * 2 + 2) * 25 + (j * 2)],
                            smallRender[(i * 2 + 2) * 25 + (j * 2 + 4)],
                            smallRender[(i * 2 + 4) * 25 + (j * 2 + 2)]
                    });
                }
            }
        }

        // SMALL 3: 混合中间点
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                int index = (i * 2 + 3) * 25 + (j * 2 + 3);
                smallRender[index] = mix4(new float[][]{
                        smallRender[(i * 2 + 2) * 25 + (j * 2 + 2)],
                        smallRender[(i * 2 + 4) * 25 + (j * 2 + 2)],
                        smallRender[(i * 2 + 2) * 25 + (j * 2 + 4)],
                        smallRender[(i * 2 + 4) * 25 + (j * 2 + 4)]
                });
            }
        }

        // SMALL 4: 填充剩余点
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int index = (i + 4) * 25 + (j + 4);
                if (!(i % 2 == 0 && j % 2 == 0) && !(i % 2 != 0 && j % 2 != 0)) {
                    smallRender[index] = mix4(new float[][]{
                            smallRender[(i + 3) * 25 + (j + 4)],
                            smallRender[(i + 4) * 25 + (j + 3)],
                            smallRender[(i + 4) * 25 + (j + 5)],
                            smallRender[(i + 5) * 25 + (j + 4)]
                    });
                }
            }
        }

        // 步骤6: 计算高度
        MutableBlockPos mpos = new MutableBlockPos();
        float[] riverValues = new float[16 * 16]; // 存储每个点的河流强度

        // 第一步：预先计算所有点的河流强度
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                mpos.setPos(worldX + i, 0, worldZ + j);
                riverValues[i * 16 + j] = TerrainBase.getRiverStrength(mpos, rtgWorld);
            }
        }

        // 第二步：计算地形高度，应用平滑的河流过渡
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int l = (i + 4) * 25 + (j + 4);
                float river = riverValues[i * 16 + j];

                // 计算基础地形高度
                float baseHeight = 0f;
                for (int biomeId = 0; biomeId < 256; biomeId++) {
                    float weight = smallRender[l][biomeId];
                    if (weight > 0f) {
                        IRealisticBiome biome = RTGAPI.getRTGBiome(biomeId);
                        if (biome != null) {
                            baseHeight += biome.rNoise(
                                    rtgWorld,
                                    worldX + i,
                                    worldZ + j,
                                    weight,
                                    river + 1f
                            ) * weight;
                        }
                    }
                }

                // 应用河流侵蚀效果（平滑过渡）
                if (river > 0.5f) {
                    // 1. 计算侵蚀深度（0-8格）
                    float erosionDepth = (river - 0.5f) * 16f;
                    erosionDepth = Math.min(erosionDepth, 8f);

                    // 2. 应用平滑侵蚀曲线
                    float riverHeight = baseHeight - erosionDepth;

                    // 3. 混合周围地形高度（避免陡峭悬崖）
                    if (i > 0 && j > 0 && i < 15 && j < 15) {
                        float avgHeight = (
                                landscape.noise[(i-1)*16+j] +
                                        landscape.noise[(i+1)*16+j] +
                                        landscape.noise[i*16+(j-1)] +
                                        landscape.noise[i*16+(j+1)]
                        ) * 0.25f;

                        // 混合比例取决于河流强度
                        float blendFactor = MathHelper.clamp(river * 2f, 0f, 1f);
                        landscape.noise[i * 16 + j] = avgHeight * (1f - blendFactor) + riverHeight * blendFactor;
                    } else {
                        landscape.noise[i * 16 + j] = riverHeight;
                    }
                } else {
                    landscape.noise[i * 16 + j] = baseHeight;
                }

                landscape.river[i * 16 + j] = river;
            }
        }

        // 填充生物群系数据
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockPos pos = new BlockPos(worldX + (x - 7) * 8 + 4, 0, worldZ + (z - 7) * 8 + 4);
                landscape.biome[x * 16 + z] = RTGAPI.getRTGBiome(biomeProvider.getBiome(pos));
            }
        }
    }

    // RWG混合函数
    private float[] mix4(float[][] ingredients) {
        float[] result = new float[256];
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 4; j++) {
                if (ingredients[j][i] > 0f) {
                    result[i] += ingredients[j][i] / 4f;
                }
            }
        }
        return result;
    }


    private void setMesaPlauteauBiomes() {
        mesaPlateauBiome = new boolean[256];
        mesaPlateauBiome[Biome.getIdForBiome(Biomes.MESA_CLEAR_ROCK)] = true;
        mesaPlateauBiome[Biome.getIdForBiome(Biomes.MESA_ROCK)] = true;
        mesaPlateauBiome[Biome.getIdForBiome(Biomes.MUTATED_MESA)] = true;
        mesaPlateauBiome[Biome.getIdForBiome(Biomes.MUTATED_MESA_CLEAR_ROCK)] = true;
        mesaPlateauBiome[Biome.getIdForBiome(Biomes.MUTATED_MESA_ROCK)] = true;
    }

    private boolean isMesaPlateau(int Id) {
        if ((Id > 255) | (Id < 0)) return false;
        return (mesaPlateauBiome[Id]);
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
