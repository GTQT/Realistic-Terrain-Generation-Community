package rtg.world.biome.realistic.rtg;

import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassCanyon;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgJungleDecos;


/**
 * RWG {@code land/RealisticBiomeJungleCanyon} 的独立群系外壳（A 组）。
 *
 * <p>基底 Jungle；地形 {@code TerrainCanyon(true,35,160,60,40,69)}；地表
 * {@code SurfaceGrassCanyon(grass, dirt, 0)}；装饰 {@code addJungleCanyon}
 * （苔石 + 5×小丛林树）。
 */
public class RealisticBiomeRtgJungleCanyon extends RealisticBiomeBase {

    private static final float[] HEIGHTS = new float[] { 5.0f, 0.5f, 12.5f, 0.5f, 18.0f, 0.5f };

    public RealisticBiomeRtgJungleCanyon(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgJungleCanyon();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassCanyon(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, (byte) 0);
    }

    @Override
    public void initDecos() {
        RtgJungleDecos.addJungleCanyon(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgJungleCanyon extends TerrainBase {

        public TerrainRtgJungleCanyon() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeJungleCanyon`：terrain = new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)
            return terrainCanyon(x, y, rtgWorld, river, HEIGHTS, border, 35f, HEIGHTS.length, true, 69f);
        }
    }
}
