package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceDesertMountain;


public class RealisticBiomeBOPColdDesert extends RealisticBiomeBase {

    public RealisticBiomeBOPColdDesert(final Biome biome) { super(biome, RiverType.FROZEN, BeachType.COLD); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPColdDesert();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDesertMountain(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    public static class TerrainBOPColdDesert extends TerrainBase {

        private float ruggedness = 3f;
        private float ruggednessWavelength = 100f;
        private float heightPitch = 35f;// the ruggedness parameter will multiply this by 0.2
        private float heightDivisor = 1f;

        public TerrainBOPColdDesert() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 推断：RWG 无 coldDesert；沙漠族的对应物是 desert\RealisticBiomeDesert.java -> TerrainHilly(150f, 50f, 0f)
            float result = terrainHilly(x, y, rtgWorld, river, 150f, 50f, 0f, 260f, 68f);
            // no indentations; cutoff is not noticeable with these low slopes
            return result > base ? result : base;
        }
    }

}
