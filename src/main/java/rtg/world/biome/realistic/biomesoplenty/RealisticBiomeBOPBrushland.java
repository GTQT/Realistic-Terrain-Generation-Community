package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrasslandMix1;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeBOPBrushland extends RealisticBiomeBase {

    public RealisticBiomeBOPBrushland(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPBrushland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrasslandMix1(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.SAND.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), 13f, 0.27f);
    }

    public static class TerrainBOPBrushland extends TerrainBase {

        private float baseHeight = 76f;
        private float hillStrength = 20f;

        public TerrainBOPBrushland() {

        }

        public TerrainBOPBrushland(float bh, float hs) {

            baseHeight = bh;
            hillStrength = hs;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:135  brushland -> TerrainGrasslandHills(90f,180f,13f,100f,38f,260f,71f)
            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
