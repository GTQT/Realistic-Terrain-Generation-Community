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
 * RWG {@code savanna/RealisticBiomeCanyonForest} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot forest；地形与 Canyon 相同 {@code TerrainCanyon(true, 35, 160, 60, 40, 69)}；
 * 地表 {@code SurfaceCanyon(sand, sand, 1, 47)}；装饰 {@code addCanyonForest}
 * （苔石 + 河岸树带 / 干地灌木 + 枯木 + 25×仙人掌）。
 */
public class RealisticBiomeRtgCanyonForest extends RealisticBiomeBase {

    private static final float[] HEIGHTS = new float[] { 5.0f, 0.5f, 12.5f, 0.5f, 18.0f, 0.5f };

    public RealisticBiomeRtgCanyonForest(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgCanyonForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCanyon(getConfig(), Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(),
                (byte) 1, 47);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addCanyonForest(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgCanyonForest extends TerrainBase {

        public TerrainRtgCanyonForest() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeCanyonForest`：terrain = new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)
            return terrainCanyon(x, y, rtgWorld, river, HEIGHTS, border, 35f, HEIGHTS.length, true, 69f);
        }
    }
}
