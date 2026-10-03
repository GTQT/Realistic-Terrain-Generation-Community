package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionTaiga;
import rtg.api.world.deco.collection.trees.DecoCollectionTaigaTrees;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;


public class RealisticBiomeVanillaColdTaigaM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_TAIGA_COLD;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaColdTaigaM() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaColdTaigaM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.6f);
    }
    
    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    
    @Override
    public void initDecos() {

    	// unlike the other cold taigas, *not* smaller than ordinary Taiga, to be different
        this.addDecoCollection(new DecoCollectionTaiga(this.getConfig(), 8f));
        this.treeGenerator = new DecoCollectionTaigaTrees(this.getConfig());
    }

    public static class TerrainVanillaColdTaigaM extends TerrainBase {

        public TerrainVanillaColdTaigaM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandMountains(x, y, rtgWorld, river);
        }
    }

}
