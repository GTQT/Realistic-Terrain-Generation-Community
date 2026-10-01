package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPSteppe extends RealisticBiomeBase {

    public RealisticBiomeBOPSteppe(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPSteppe(65f, 68f, 30f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPSteppe extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        // 63f, 80f, 30f

        public TerrainBOPSteppe(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 推断（**不是照抄**）：RWG SupportBOP.java:745-750 的 steppe 条目整块被 /* */ 注释，
            // 参数按注释行原文 (70f,180f,13f,100f,38f,260f,71f) 采用。
            return terrainGrasslandHills(x, y, rtgWorld, river, 70f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
