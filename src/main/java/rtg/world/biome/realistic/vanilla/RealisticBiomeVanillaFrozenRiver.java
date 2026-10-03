package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeVanillaFrozenRiver extends RealisticBiomeBase {

    public static Biome biome = Biomes.FROZEN_RIVER;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaFrozenRiver() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaFrozenRiver();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaFrozenRiver extends TerrainBase {

        public TerrainVanillaFrozenRiver() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG land\RealisticBiomeSnowRivers.java -> TerrainMountainRiver()（RWG 的河流用山间河地形）
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
