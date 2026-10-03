package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceDuneValley;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeBOPOutback extends RealisticBiomeBase {

    public RealisticBiomeBOPOutback(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPOutback();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDuneValley(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 300f, false, false);
    }

    public static class TerrainBOPOutback extends TerrainBase {

        public TerrainBOPOutback() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  outback -> TerrainDuneValley(300f)
            return terrainDuneValley(x, y, rtgWorld, river, 300f);
        }
    }

}
