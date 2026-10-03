package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionBirchForest;
import rtg.api.world.deco.collection.trees.DecoCollectionBirchForestTrees;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaBirchForest extends RealisticBiomeBase {

    public static Biome biome = Biomes.BIRCH_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaBirchForest() {

        super(biome);
    }
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaBirchForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionBirchForest(this.getConfig()));
        this.treeGenerator = new DecoCollectionBirchForestTrees(this.getConfig());
    }

//    @Override
//    public void overrideDecorations() {
//        baseBiome().decorator.grassPerChunk = -999;
//        baseBiome().decorator.flowersPerChunk = -999;
//    }

    public static class TerrainVanillaBirchForest extends TerrainBase {

        public TerrainVanillaBirchForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG 温带 WoodHills -> TerrainHilly(230f,120f,0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
