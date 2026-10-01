package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.deco.collection.DecoCollectionExtremeHillsCommon;
import rtg.api.world.deco.collection.DecoCollectionExtremeHillsM;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceTundra;


public class RealisticBiomeVanillaExtremeHillsM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_EXTREME_HILLS;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaExtremeHillsM() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new RealisticBiomeVanillaExtremeHills.GrandMountain();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceTundra(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionExtremeHillsM(this.getConfig()));
        this.addDecoCollection(new DecoCollectionExtremeHillsCommon(this.getConfig()));
    }

}
