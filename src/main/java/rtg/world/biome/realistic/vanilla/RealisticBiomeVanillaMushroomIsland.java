package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeVanillaMushroomIsland extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUSHROOM_ISLAND;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaMushroomIsland() {

        super(biome);
    }

    @Override
    public Biome preferredBeach() {
        return Biomes.MUSHROOM_ISLAND_SHORE;
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaMushroomIsland();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaMushroomIsland extends TerrainBase {

        private float heigth;
        private float width;

        public TerrainVanillaMushroomIsland() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG Support.java:104-115 —— mushroomIsland 用的是 TerrainSmallIsland（BiomePlacement.SMALL_ISLAND）
            return terrainSmallIsland(x, y, rtgWorld, river);
        }
    }

}
