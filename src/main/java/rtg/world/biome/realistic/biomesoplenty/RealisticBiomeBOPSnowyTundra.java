package rtg.world.biome.realistic.biomesoplenty;



import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.biome.RealisticBiomeBase.BeachType;
import rtg.api.world.biome.RealisticBiomeBase.RiverType;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPSnowyTundra extends RealisticBiomeBase {

    public RealisticBiomeBOPSnowyTundra(final Biome biome) { super(biome, RiverType.FROZEN, BeachType.COLD); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTundra();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPTundra extends TerrainBase {

        public TerrainBOPTundra() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG SupportBOP.java  tundra -> TerrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)（snowyTundra 归入 tundra）
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}

