package rtg.world.biome.realistic.biomesoplenty;

import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeBOPBambooForest extends RealisticBiomeBase {

    public RealisticBiomeBOPBambooForest(final Biome biome) {
        super(biome);
    }

    @Override
    public void initDecos() {
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPBambooForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.95f);
    }

    public static class TerrainBOPBambooForest extends TerrainBase {

        public TerrainBOPBambooForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:67-72  bambooForest -> TerrainSwampMountain(135f, 300f)
            return terrainSwampMountain(x, y, rtgWorld, river, 135f, 300f);
        }
    }

}
