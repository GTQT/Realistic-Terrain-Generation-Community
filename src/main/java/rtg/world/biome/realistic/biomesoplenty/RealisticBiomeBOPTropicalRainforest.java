package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPTropicalRainforest extends RealisticBiomeBase {

    public RealisticBiomeBOPTropicalRainforest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTropicalRainforest(0f, 60f, 68f, 200f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPTropicalRainforest extends TerrainBase {

        private float start;
        private float height;
        private float width;

        public TerrainBOPTropicalRainforest(float hillStart, float landHeight, float baseHeight, float hillWidth) {

            start = hillStart;
            height = landHeight;
            base = baseHeight;
            width = hillWidth;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  tropicalRainforest -> TerrainHighland(0f, 140f, 68f, 200f)  [由偏离版 terrainHighlandLegacy 拨回忠实版]
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
