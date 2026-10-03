package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeBOPAlpsFoothills extends RealisticBiomeBase {

    public RealisticBiomeBOPAlpsFoothills(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPAlpsFoothills();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    /**
     * RWG-style mountain river terrain — produces mountain peaks with gentler lower slopes
     * for a smoother transition from valley floor to peak, ideal for foothills.
     * The original RTG developer intended this function for this biome (was commented out).
     */
    public static class TerrainBOPAlpsFoothills extends TerrainBase {

        public TerrainBOPAlpsFoothills() {
            base = 90f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
