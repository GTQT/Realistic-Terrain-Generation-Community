package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeBOPHighland extends RealisticBiomeBase {

    public RealisticBiomeBOPHighland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPHighland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1f, 1.5f, 85f, 20f, 4f);
    }

    /**
     * RWG-style grassland mountains — wide, rolling peaks with carved valleys.
     */
    public static class TerrainBOPHighland extends TerrainBase {

        public TerrainBOPHighland() {
            base = 120f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:414-418  highland -> TerrainHighland(0f, 140f, 68f, 150f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 150f);
        }
    }

}
