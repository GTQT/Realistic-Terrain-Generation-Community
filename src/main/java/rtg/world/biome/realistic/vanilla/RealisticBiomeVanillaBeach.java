package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastDunes;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgCoastDecos;


public class RealisticBiomeVanillaBeach extends RealisticBiomeBase {

    public static Biome biome = Biomes.BEACH;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaBeach() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_PALM_TREES).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCoastDunes(getConfig());
    }

    @Override
    public void initDecos() {

        // RWG `coast/RealisticBiomeCoastDunes.rDecorate`：只有水草带（没有棕榈树）。
        RtgCoastDecos.addCoastDunes(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainVanillaBeach extends TerrainBase {

        public TerrainVanillaBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG coast\RealisticBiomeCoastDunes.java:43-63（手写 rNoise，已提为 TerrainBase.terrainCoastDunes）
            return terrainCoastDunes(x, y, rtgWorld, river);
        }
    }

}
