package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeBOPGlacier extends RealisticBiomeBase {

    public RealisticBiomeBOPGlacier(final Biome biome) { super(biome, RiverType.FROZEN); }

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

        return new TerrainBOPGlacier(230f, 40f, 68f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.2f);
    }

    /**
     * RWG-style mountain spikes — jagged, dramatic ice peaks for glacial terrain.
     */
    public static class TerrainBOPGlacier extends TerrainBase {

        public TerrainBOPGlacier(float mountainWidth, float mountainStrength, float height) {
            base = height;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
