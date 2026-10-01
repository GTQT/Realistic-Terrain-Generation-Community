package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeBOPLavenderFields extends RealisticBiomeBase {

    public RealisticBiomeBOPLavenderFields(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPLavenderFields();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1.2f);
    }

    @Override
    public void initDecos() {

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setChance(10);
        decoShrub.setLoopMultiplier(3f);
        this.addDeco(decoShrub);
    }

    public static class TerrainBOPLavenderFields extends TerrainBase {

        public TerrainBOPLavenderFields() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  lavenderFields -> TerrainMountainSpikes()
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
