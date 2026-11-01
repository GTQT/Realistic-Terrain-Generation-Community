package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastDunes;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgCoastDecos;


public class RealisticBiomeBOPOriginBeach extends RealisticBiomeBase {

    public RealisticBiomeBOPOriginBeach(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        // RWG `coast/RealisticBiomeCoastDunes.rDecorate`（水草带）。
        RtgCoastDecos.addCoastDunes(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    @Override
    public void initConfig() {

    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPOriginBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCoastDunes(getConfig());
    }

    public static class TerrainBOPOriginBeach extends TerrainBase {

        public TerrainBOPOriginBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG coast\RealisticBiomeCoastDunes.java:43-63（同上）
            return terrainCoastDunes(x, y, rtgWorld, river);
        }
    }

}
