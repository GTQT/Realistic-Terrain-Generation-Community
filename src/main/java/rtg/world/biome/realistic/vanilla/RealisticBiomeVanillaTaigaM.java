package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionTaiga;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaTaigaM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_TAIGA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaTaigaM() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaTaigaM(70f, 180f, 7f, 100f, 38f, 160f, 68f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        this.addDecoCollection(new DecoCollectionTaiga(this.getConfig(), 10f));
    }

    public boolean allowVanillaTrees() {return false;}
    
    public static class TerrainVanillaTaigaM extends TerrainBase {

        private float hHeight;
        private float hWidth;
        private float vHeight;
        private float vWidth;
        private float lHeight;
        private float lWidth;
        private float bHeight;

        public TerrainVanillaTaigaM(float hillHeight, float hillWidth, float varHeight, float varWidth, float lakeHeight, float lakeWidth, float baseHeight) {

            hHeight = hillHeight;
            hWidth = hillWidth;

            vHeight = varHeight;
            vWidth = varWidth;

            lHeight = lakeHeight;
            lWidth = lakeWidth;

            bHeight = baseHeight;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandHills(x, y, rtgWorld, river, hHeight, hWidth, vHeight, vWidth, lHeight, lWidth, bHeight);
        }
    }

}
