package rtg.world.biome.realistic.rtg;

import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceTundra;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgSnowDecos;


/**
 * RWG {@code land/RealisticBiomeTundraHills} 的独立群系外壳（A 组）。
 *
 * <p>基底 cold plains（0.2 / 0.2，Wolf + Horse）；地形 {@code TerrainMountain}；地表
 * {@code SurfaceTundra(grass, dirt)}；装饰 {@code addTundraHills}
 * （湖泊/圆石/河岸树带 + 干地稀树/灌木/南瓜/花/草）。
 */
public class RealisticBiomeRtgTundraHills extends RealisticBiomeBase {

    public RealisticBiomeRtgTundraHills(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgTundraHills();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceTundra(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    @Override
    public void initDecos() {
        RtgSnowDecos.addTundraHills(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgTundraHills extends TerrainBase {

        public TerrainRtgTundraHills() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeTundraHills`：terrain = new TerrainMountain()
            return terrainMountain(x, y, rtgWorld, river);
        }
    }
}
