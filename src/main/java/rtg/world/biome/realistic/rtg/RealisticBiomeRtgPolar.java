package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfacePolar;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgSnowDecos;


/**
 * RWG {@code land/RealisticBiomePolar} 的独立群系外壳（A 组）。
 *
 * <p>基底 snow desert（0.0 / 0.1，无动物）；地形 {@code TerrainPolar}；地表
 * {@code SurfacePolar(snow, snow)}；装饰 {@code addPolar}（只在 river>0.86 时：
 * 5×浮冰 + 1/2 小云杉）。
 */
public class RealisticBiomeRtgPolar extends RealisticBiomeBase {

    public RealisticBiomeRtgPolar(final Biome biome) {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgPolar();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfacePolar(getConfig(), Blocks.SNOW.getDefaultState(), Blocks.SNOW.getDefaultState());
    }

    @Override
    public void initDecos() {
        RtgSnowDecos.addPolar(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgPolar extends TerrainBase {

        public TerrainRtgPolar() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomePolar`：terrain = new TerrainPolar()
            return terrainPolar(x, y, rtgWorld, river);
        }
    }
}
