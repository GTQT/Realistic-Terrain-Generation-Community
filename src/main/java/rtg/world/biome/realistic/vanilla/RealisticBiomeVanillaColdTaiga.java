package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionTaiga;
import rtg.api.world.deco.collection.trees.DecoCollectionTaigaTrees;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaColdTaiga extends RealisticBiomeBase {

    public static Biome biome = Biomes.COLD_TAIGA;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaColdTaiga() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaColdTaiga();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }
    
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    
    @Override
    public void initDecos() {

    	DecoCollectionTaiga decos = new DecoCollectionTaiga(this.getConfig(), 8f);
    	// smaller for cold
    	decos.changeAvgHeightSqrt(-1f);
    	decos.changeHeightVariability(-0.5f);
        this.addDecoCollection(decos);
        DecoCollectionTaigaTrees decoTrees = new DecoCollectionTaigaTrees(this.getConfig());
        this.treeGenerator = decoTrees;
    	decoTrees.changeAvgHeightSqrt(-1f);
    	decoTrees.changeHeightVariability(-0.5f);
    }

    public static class TerrainVanillaColdTaiga extends TerrainBase {

        public TerrainVanillaColdTaiga() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG TaigaHills -> TerrainMountainRiver()
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
