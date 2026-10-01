package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPSacredSprings extends RealisticBiomeBase {

    public RealisticBiomeBOPSacredSprings(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().ALLOW_RIVERS.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPSacredSprings(150f, 30f, 68f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPSacredSprings extends TerrainBase {

        private float width;
        private float strength;
        private float lakeDepth;
        private float lakeWidth;
        private float terrainHeight;

        public TerrainBOPSacredSprings(float mountainWidth, float mountainStrength, float height) {

            width = mountainWidth;
            strength = mountainStrength;
            terrainHeight = height;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:680-684  sacredSprings -> TerrainHighland(0f, 120f, 68f, 200f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 120f, 68f, 200f);
        }
    }

}
