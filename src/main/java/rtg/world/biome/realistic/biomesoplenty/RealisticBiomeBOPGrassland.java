package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPGrassland extends RealisticBiomeBase {

    public RealisticBiomeBOPGrassland(final Biome biome) { super(biome); }

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

        return new TerrainBOPGrassland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPGrassland extends TerrainBase {

        public TerrainBOPGrassland() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 推断（**不是照抄**）：RWG SupportBOP.java:378-383 的 grassland 条目被 /* */ 注释，
            // 且注释行原配方是 (47f,180f,13f,100f,28f,260f,70f)，与本处不同。
            // 此处取该文件中出现最多的配方 (90f,180f,13f,100f,38f,260f,71f)，属主观选择。
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
