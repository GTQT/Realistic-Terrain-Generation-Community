package rtg.world.biome.realistic.biomesoplenty;



import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeBOPMoor extends RealisticBiomeBase {

    public RealisticBiomeBOPMoor(final Biome biome) { super(biome); }

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

        return new TerrainBOPMoor(68f, 75f, 16f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    public static class TerrainBOPMoor extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;
        private float lift;

        // 63f, 80f, 30f

        public TerrainBOPMoor(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
            lift = minHeight - 62f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG SupportBOP.java:561-566  garden（注释写明「GARDEN (MOOR TERRAIN)」）-> TerrainMountainRiver()
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
