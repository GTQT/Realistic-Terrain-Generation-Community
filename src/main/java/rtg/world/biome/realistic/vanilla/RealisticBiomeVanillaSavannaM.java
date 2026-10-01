package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.deco.collection.DecoCollectionSavanna;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMesa;


public class RealisticBiomeVanillaSavannaM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_SAVANNA;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaSavannaM() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().ALLOW_RIVERS.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");

    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaSavannaM(true, 35f, 160f, 60f, 40f, 69f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMesa(getConfig(), Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(), (byte) 1);
    }
    
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    

    @Override
    public void initDecos() {

        this.addDecoCollection(new DecoCollectionDesertRiver(this.getConfig()));
        this.addDecoCollection(new DecoCollectionSavanna(this.getConfig()));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.cactiPerChunk = -999;
    }

    public static class TerrainVanillaSavannaM extends TerrainBase {

        private float[] height;
        private int heightLength;
        private float strength;

        /*
         * Example parameters:
         *
         * allowed to generate rivers?
         * riverGen = true
         *
         * canyon jump heights
         * heightArray = new float[]{2.0f, 0.5f, 6.5f, 0.5f, 14.0f, 0.5f, 19.0f, 0.5f}
         *
         * strength of canyon jump heights
         * heightStrength = 35f
         *
         * canyon width (cliff to cliff)
         * canyonWidth = 160f
         *
         * canyon heigth (total heigth)
         * canyonHeight = 60f
         *
         * canyon strength
         * canyonStrength = 40f
         *
         */
        public TerrainVanillaSavannaM(boolean riverGen, float heightStrength, float canyonWidth, float canyonHeight, float canyonStrength, float baseHeight) {
            /*
             * Values come in pairs per layer. First is how high to step up.
             * 	Second is a value between 0 and 1, signifying when to step up.
             */
            height = new float[]{18f, 0.4f, 12f, 0.6f, 8f, 0.8f, 4f, 0.8f};
            strength = 10f;
            heightLength = height.length;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 推断：同上
            return terrainMesa(x, y, rtgWorld, border, river);
        }
    }

}
