package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceIslandMountainStone;


public class RealisticBiomeBOPOriginIsland extends RealisticBiomeBase {

    public RealisticBiomeBOPOriginIsland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPOriginIsland(65f, 80f, 38f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceIslandMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 67, Blocks.SAND.getDefaultState(), 0f);
    }

    public static class TerrainBOPOriginIsland extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        public TerrainBOPOriginIsland(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG ocean\RealisticBiomeIslandTundra.java:32-40；温带岛选 tundra 版属**推断**（RWG 按岛屿种子气候在 tropical/tundra 间选）
            return terrainIslandTundra(x, y, rtgWorld, border);
        }
    }

}
