package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPPrairie extends RealisticBiomeBase {

    public RealisticBiomeBOPPrairie(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPPrairie(65f, 80f, 25f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPPrairie extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        // 63f, 80f, 30f

        public TerrainBOPPrairie(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  prairie -> TerrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
