package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionExtremeHills;
import rtg.api.world.deco.collection.DecoCollectionExtremeHillsCommon;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceTundra;


public class RealisticBiomeVanillaExtremeHills extends RealisticBiomeBase {

    public static Biome biome = Biomes.EXTREME_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaExtremeHills() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {
        return new GrandMountain();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceTundra(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionExtremeHills(this.getConfig()));
        this.addDecoCollection(new DecoCollectionExtremeHillsCommon(this.getConfig()));
    }

    /**
     * RWG-style Grand Mountain terrain — produces sharp, towering peaks
     * with dramatic height contrast. Uses quadratic amplification (h²/32)
     * for authentic RWG mountain feel with peaks reaching ~200+.
     */
    public static class GrandMountain extends TerrainBase {

        public GrandMountain() {
            this.base = 67f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountain(x, y, rtgWorld, river);
        }
    }

}
