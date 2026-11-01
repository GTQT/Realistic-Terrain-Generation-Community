package rtg.world.biome.realistic.rtg;

import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceTundra;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgTaigaDecos;


/**
 * RWG {@code land/RealisticBiomeTaigaPlains} 的独立群系外壳（A 组）。
 *
 * <p>基底 cold forest（0.5 / 0.4）；地形 {@code TerrainFlatLakes}；地表
 * {@code SurfaceTundra(grass, dirt)}；装饰 {@code addTaigaPlains}
 * （湖泊/苔石/噪声树带/倒木/灌木/蘑菇/南瓜/野麦/花/草）。
 */
public class RealisticBiomeRtgTaigaPlains extends RealisticBiomeBase {

    public RealisticBiomeRtgTaigaPlains(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgTaigaPlains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceTundra(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock);
    }

    @Override
    public void initDecos() {
        RtgTaigaDecos.addTaigaPlains(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgTaigaPlains extends TerrainBase {

        public TerrainRtgTaigaPlains() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeTaigaPlains`：terrain = new TerrainFlatLakes()
            return terrainFlatLakes(x, y, rtgWorld, river);
        }
    }
}
