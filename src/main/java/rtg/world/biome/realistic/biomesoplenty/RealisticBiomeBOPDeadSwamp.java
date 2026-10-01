package rtg.world.biome.realistic.biomesoplenty;



import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPDeadSwamp extends RealisticBiomeBase {

    public RealisticBiomeBOPDeadSwamp(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPDeadSwamp();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPDeadSwamp extends TerrainBase {

        public TerrainBOPDeadSwamp() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:253-257  deadSwamp -> TerrainMarsh()
            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
