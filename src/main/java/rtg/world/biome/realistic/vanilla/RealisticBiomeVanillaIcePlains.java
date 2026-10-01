package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeVanillaIcePlains extends RealisticBiomeBase {

    public static Biome biome = Biomes.ICE_PLAINS;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaIcePlains() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().USE_ARCTIC_SURFACE).set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        super.initConfig();
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaIcePlains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaIcePlains extends TerrainBase {

        public TerrainVanillaIcePlains() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG land\RealisticBiomeTundraPlains.java -> TerrainFlatLakes()
            return terrainFlatLakes(x, y, rtgWorld, river);
        }
    }

}
