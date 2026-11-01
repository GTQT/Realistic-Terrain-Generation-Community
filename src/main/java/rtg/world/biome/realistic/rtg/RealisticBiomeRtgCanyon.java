package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCanyon;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgDesertDecos;


/**
 * RWG {@code savanna/RealisticBiomeCanyon} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot plains；地形 {@code TerrainCanyon(true, 35, 160, 60, 40, 69)}；
 * 地表 {@code SurfaceCanyon(sand, sand, 1, 0)}；装饰 {@code addCanyon}。
 */
public class RealisticBiomeRtgCanyon extends RealisticBiomeBase {

    /** RWG {@code TerrainCanyon} 的 height[]（每两个一层：步高 + 触发阈值）。 */
    private static final float[] HEIGHTS = new float[] { 5.0f, 0.5f, 12.5f, 0.5f, 18.0f, 0.5f };

    public RealisticBiomeRtgCanyon(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgCanyon();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCanyon(getConfig(), Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(),
                (byte) 1, 0);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addCanyon(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgCanyon extends TerrainBase {

        public TerrainRtgCanyon() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeCanyon`：terrain = new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)
            return terrainCanyon(x, y, rtgWorld, river, HEIGHTS, border, 35f, HEIGHTS.length, true, 69f);
        }
    }
}
