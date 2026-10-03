package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaMushroomIslandShore extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUSHROOM_ISLAND_SHORE;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMushroomIslandShore() {

        super(biome);
    }

    @Override
    public Biome preferredBeach() {
        return biome;
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaMushroomIslandShore();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaMushroomIslandShore extends TerrainBase {

        public TerrainVanillaMushroomIslandShore() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
