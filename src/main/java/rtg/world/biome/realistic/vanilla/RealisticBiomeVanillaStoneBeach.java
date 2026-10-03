package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastDunes;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;


public class RealisticBiomeVanillaStoneBeach extends RealisticBiomeBase {

    public static Biome biome = Biomes.STONE_BEACH;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaStoneBeach() {

        super(biome, BeachType.STONE);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_BLEED_IN.set(true);
        this.getConfig().SURFACE_BLEED_OUT.set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaStoneBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCoastDunes(getConfig());
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaStoneBeach extends TerrainBase {

        public TerrainVanillaStoneBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 照抄 RWG coast\RealisticBiomeCoastDunes.java:43-63（同上）
            return terrainCoastDunes(x, y, rtgWorld, river);
        }
    }

}
