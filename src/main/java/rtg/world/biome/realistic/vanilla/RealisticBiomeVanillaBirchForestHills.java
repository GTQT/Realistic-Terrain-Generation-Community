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


public class RealisticBiomeVanillaBirchForestHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.BIRCH_FOREST_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaBirchForestHills() {

        super(biome);
    }
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaBirchForestHills();
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

    public static class TerrainVanillaBirchForestHills extends TerrainBase {

        private float hillStrength = 50f;

        public TerrainVanillaBirchForestHills() {

        }

        public TerrainVanillaBirchForestHills(float bh, float hs) {

            base = bh;
            hillStrength = hs;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG forest\RealisticBiomeWoodHills -> TerrainHilly(230f, 120f, 0f)；与基准群系 VanillaBirchForest 同值
            // 原先的 GrasslandHills(70f, 180f, 7f, ...) 在 RWG 全仓查无此文，且比基准群系**更平**（方向反了）。
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
