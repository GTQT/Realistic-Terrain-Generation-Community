package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPXericShrubland extends RealisticBiomeBase {

    public RealisticBiomeBOPXericShrubland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPXericShrubland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    /**
     * **推断**：RWG 没有 {@code xericShrubland}（只有 {@code shrubland}）。
     * {@code SupportBOP.java:718-723} 给 {@code shrubland} 的地形是
     * {@code new TerrainGrasslandHills(90f, 180f, 13f, 100f, 38f, 260f, 71f)}，
     * 而同族的 {@code RealisticBiomeBOPShrubland} 早已用同一组参数 —— 本群系取同值。
     */
    public static class TerrainBOPXericShrubland extends TerrainBase {

        public TerrainBOPXericShrubland() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
