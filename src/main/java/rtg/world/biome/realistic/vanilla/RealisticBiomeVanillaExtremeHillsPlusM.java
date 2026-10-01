package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.deco.collection.DecoCollectionExtremeHillsCommon;
import rtg.api.world.deco.collection.DecoCollectionExtremeHillsPlusM;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaExtremeHillsPlusM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_EXTREME_HILLS_WITH_TREES;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaExtremeHillsPlusM() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new RealisticBiomeVanillaExtremeHills.GrandMountain();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionExtremeHillsPlusM(this.getConfig()));
        this.addDecoCollection(new DecoCollectionExtremeHillsCommon(this.getConfig()));
    }

}
