package rtg.world.biome.realistic.vanilla;

import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeVanillaIceMountains extends RealisticBiomeBase {

    public static Biome biome = Biomes.ICE_MOUNTAINS;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaIceMountains() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().addProperty(this.getConfig().USE_ARCTIC_SURFACE).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaIceMountains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.2f);
    }

    @Override
    public void initDecos() {
    }

    /**
     * RWG-style mountain spikes terrain — produces jagged, dramatic snowy peaks.
     */
    public static class TerrainVanillaIceMountains extends TerrainBase {

        public TerrainVanillaIceMountains() {
            this.base = 68f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
