package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceIslandMountainStone;


public class RealisticBiomeBOPFlowerIsland extends RealisticBiomeBase {

    public RealisticBiomeBOPFlowerIsland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPFlowerIsland(65f, 68f, 24f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceIslandMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 67, Blocks.SAND.getDefaultState(), 0f);
    }

    public static class TerrainBOPFlowerIsland extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        // 63f, 80f, 30f

        public TerrainBOPFlowerIsland(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG ocean\RealisticBiomeIslandTropical.java:77-95（手写 rNoise，已提为 TerrainBase.terrainIslandTropical）
            return terrainIslandTropical(x, y, rtgWorld, border);
        }
    }

}
