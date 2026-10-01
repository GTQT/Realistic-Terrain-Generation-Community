package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionForest;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaForest extends RealisticBiomeBase {

    public static Biome biome = Biomes.FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaForest() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_2_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }
    @Override
    public void initDecos() {

        this.addDecoCollection(new DecoCollectionForest(this.getConfig()));
    }

    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    
    public static class TerrainVanillaForest extends TerrainBase {

        private float hillStrength = 20f;// this needs to be linked to the

        public TerrainVanillaForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG 温带 WoodHills -> TerrainHilly(230f,120f,0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
