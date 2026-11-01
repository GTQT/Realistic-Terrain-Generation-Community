package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgSnowDecos;


/**
 * RWG {@code land/RealisticBiomeSnowLakes} 的独立群系外壳（A 组）。
 *
 * <p>基底 snow forest（0.0 / 0.1）；地形 {@code TerrainFlatLakes}；地表
 * {@code SurfaceMountainSnow(grass, dirt, true, sand, 0.2f)}；装饰 {@code addSnowLakes}
 * （湖泊/苔石/南瓜/花/草/小松-小云杉/灌木）。
 */
public class RealisticBiomeRtgSnowLakes extends RealisticBiomeBase {

    public RealisticBiomeRtgSnowLakes(final Biome biome) {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgSnowLakes();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                true, Blocks.SAND.getDefaultState(), 0.2f);
    }

    @Override
    public void initDecos() {
        RtgSnowDecos.addSnowLakes(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgSnowLakes extends TerrainBase {

        public TerrainRtgSnowLakes() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeSnowLakes`：terrain = new TerrainFlatLakes()
            return terrainFlatLakes(x, y, rtgWorld, river);
        }
    }
}
