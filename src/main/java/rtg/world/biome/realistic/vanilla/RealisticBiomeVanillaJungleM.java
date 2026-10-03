package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionJungle;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeVanillaJungleM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_JUNGLE;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaJungleM() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.5f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().ALLOW_CACTUS).set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaJungleM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.6f);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionJungle(this.getConfig()));
    }

    @Override
    public void overrideDecorations() {
        baseBiome().decorator.grassPerChunk = 30; // Vanilla = 25
        baseBiome().decorator.flowersPerChunk = -999; // Vanilla = 4
        baseBiome().decorator.treesPerChunk = 30; // Vanilla = 50
    }

    public static class TerrainVanillaJungleM extends TerrainBase {

        public TerrainVanillaJungleM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandMountains(x, y, rtgWorld, river);
        }
    }

}
