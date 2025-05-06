package rtg.world.biome;

import biomesoplenty.api.generation.Generators;
import biomesoplenty.common.world.BOPWorldSettings;
import biomesoplenty.common.world.layer.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.layer.*;

import static rtg.RTGConfig.*;

public class BiomeProviderRTG extends BiomeProvider {
    public BiomeProviderRTG(long seed, WorldType worldType, String chunkProviderSettings) {
        super();


        // load the settings object
        // note on the client side, chunkProviderSettings is an empty string
        // I'm not sure if this is a bug or deliberate, but it might have some consequences when the biomes/genlayers are different between client and server
        // The same thing happens in vanilla minecraft
        System.out.println("settings for world: " + chunkProviderSettings);
        BOPWorldSettings settings = new BOPWorldSettings(chunkProviderSettings);

        // set up all the gen layers
        GenLayer[] agenlayer = setupRTGGenLayers(seed, settings);
        agenlayer = getModdedBiomeGenerators(worldType, seed, agenlayer);
        this.genBiomes = Generators.biomeGenLayer = agenlayer[0];
        this.biomeIndexLayer = Generators.biomeIndexLayer = agenlayer[1];
    }

    public BiomeProviderRTG(World world) {
        this(world.getSeed(), world.getWorldInfo().getTerrainType(), world.getWorldInfo().getGeneratorOptions());
    }

    // generate the regions of land and sea
    public static GenLayer initialLandAndSeaLayer() {
        GenLayer stack;

        switch (getLandScheme()) {
            case 2:
                stack = new GenLayerIslandBOP(1L, 4);
                stack = new GenLayerFuzzyZoom(2000L, stack);
                stack = new GenLayerZoom(2001L, stack);
                stack = new GenLayerIslandBOP(3L, 12, stack);
                stack = new GenLayerZoom(2002L, stack);
                stack = new GenLayerRaggedEdges(4L, stack);
                break;

            case 3:
                stack = new GenLayerIslandBOP(1L, 5);
                break;

            case 1:
            default:
                stack = new GenLayerIsland(1L);
                stack = new GenLayerFuzzyZoom(2000L, stack);
                stack = new GenLayerRaggedEdges(1L, stack);
                stack = new GenLayerZoom(2001L, stack);
                stack = new GenLayerRaggedEdges(2L, stack);
                stack = new GenLayerRaggedEdges(50L, stack);
                stack = new GenLayerRaggedEdges(70L, stack);
                stack = new GenLayerRemoveTooMuchOcean(2L, stack); // <--- this is the layer which does 90% of the work, the ones before it are almost pointless
                stack = new GenLayerRaggedEdges(3L, stack);
                stack = new GenLayerZoom(2002L, stack);
                stack = new GenLayerZoom(2003L, stack);
                stack = new GenLayerRaggedEdges(4L, stack);
                break;
        }

        return stack;
    }

    // superimpose hot and cold regions an a land and sea layer
    public static GenLayerClimate climateLayer(long worldSeed) {
        GenLayer temperature;
        switch (getTempScheme()) {
            case 1:
            default:
                temperature = new GenLayerTemperatureLatitude(2L, 16, worldSeed);
                break;
            case 2:
                temperature = new GenLayerTemperatureNoise(3L, worldSeed, 0.14D);
                break;
            case 3:
                temperature = new GenLayerTemperatureNoise(4L, worldSeed, 0.08D);
                break;
            case 4:
                temperature = new GenLayerTemperatureNoise(5L, worldSeed, 0.04D);
                break;
            case 5:
                temperature = new GenLayerTemperatureRandom(6L);
                break;
        }

        GenLayer rainfall;
        switch (getRainScheme()) {
            case 1:
                rainfall = new GenLayerRainfallNoise(7L, worldSeed, 0.14D);
                break;
            case 2:
            default:
                rainfall = new GenLayerRainfallNoise(8L, worldSeed, 0.08D);
                break;
            case 3:
                rainfall = new GenLayerRainfallNoise(9L, worldSeed, 0.04D);
                break;
            case 4:
                rainfall = new GenLayerRainfallRandom(10L);
                break;
        }

        GenLayerClimate climate = new GenLayerClimate(103L, temperature, rainfall);
        // stack = new GenLayerEdge(3L, stack, GenLayerEdge.Mode.SPECIAL);
        return climate;
    }

    public static GenLayer allocateBiomes(BOPWorldSettings settings, GenLayer mainBranch, GenLayer subBiomesInit, GenLayerClimate climateLayer) {
        // allocate the basic biomes
        GenLayer biomesLayer = new GenLayerBiomeBOP(200L, mainBranch, climateLayer, settings);

        // magnify everything (using the same seed)
        biomesLayer = new GenLayerZoom(1000L, biomesLayer);
        subBiomesInit = new GenLayerZoom(1000L, subBiomesInit);
        GenLayer climateLayerZoomed = new GenLayerZoom(1000L, climateLayer);

        // add medium islands
        switch (getLandScheme()) {
            case 3:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 4);
                break;
            case 2:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 60);
                break;
            case 1:
            default:
                break;
        }

        // magnify everything again (using the same seed)
        biomesLayer = new GenLayerZoom(1000L, biomesLayer);
        subBiomesInit = new GenLayerZoom(1000L, subBiomesInit);
        climateLayerZoomed = new GenLayerZoom(1000L, climateLayerZoomed);

        // add edge biomes
        biomesLayer = new GenLayerBiomeEdgeBOP(1000L, biomesLayer);

        // add sub-biomes (like hills or rare mutated variants) seeded with subBiomesInit
        biomesLayer = new GenLayerSubBiomesBOP(1000L, biomesLayer, subBiomesInit);

        // add tiny islands
        switch (getIslandScheme()) {
            case 3:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 8);
                break;
            case 2:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 60);
                break;
            case 1:
            default:
                biomesLayer = new GenLayerBiomeIslands(15L, biomesLayer, climateLayerZoomed, 12);
                break;
        }

        return biomesLayer;
    }

    public static GenLayer[] setupRTGGenLayers(long worldSeed, BOPWorldSettings settings) {

        int biomeSize = getBiomeSize();
        int riverSize = getRiverSize();

        // first few layers just create areas of land and sea, continents and islands
        GenLayer mainBranch = initialLandAndSeaLayer();

        // add mushroom islands and deep oceans
        mainBranch = new GenLayerAddMushroomIsland(5L, mainBranch);
        mainBranch = new GenLayerLargeIsland(5L, mainBranch);
        mainBranch = new GenLayerDeepOcean(4L, mainBranch);

        // fork off a new branch as a seed for rivers and sub biomes
        GenLayer riversAndSubBiomesInit = new GenLayerRiverInit(100L, mainBranch);

        // create climate layer
        GenLayerClimate climateLayer = climateLayer(worldSeed);

        // allocate the biomes
        mainBranch = allocateBiomes(settings, mainBranch, riversAndSubBiomesInit, climateLayer);

        // do a bit more zooming, depending on biomeSize
        //mainBranch = new GenLayerRareBiome(1001L, mainBranch); - sunflower plains I think
        for (int i = 0; i < biomeSize; ++i) {
            mainBranch = new GenLayerZoom(1000 + i, mainBranch);
            if (i == 0) {
                mainBranch = new GenLayerRaggedEdges(3L, mainBranch);
            }
            if (i == 1 || biomeSize == 1) {
                mainBranch = new GenLayerShoreBOP(1000L, mainBranch);
            }
        }
        mainBranch = new GenLayerSmooth(1000L, mainBranch);

        // develop the rivers branch
        GenLayer riversBranch = GenLayerZoom.magnify(1000L, riversAndSubBiomesInit, 2);
        riversBranch = GenLayerZoom.magnify(1000L, riversBranch, riverSize);
        riversBranch = new GenLayerRiver(1L, riversBranch);
        riversBranch = new GenLayerSmooth(1000L, riversBranch);

        // mix rivers into main branch
        GenLayer riverMixFinal = new GenLayerRiverMixBOP(100L, mainBranch, riversBranch);

        // finish biomes with Voronoi zoom
        GenLayer biomesFinal = new GenLayerVoronoiZoom(10L, riverMixFinal);

        riverMixFinal.initWorldGenSeed(worldSeed);
        biomesFinal.initWorldGenSeed(worldSeed);
        return new GenLayer[]{riverMixFinal, biomesFinal, riverMixFinal};

    }
}