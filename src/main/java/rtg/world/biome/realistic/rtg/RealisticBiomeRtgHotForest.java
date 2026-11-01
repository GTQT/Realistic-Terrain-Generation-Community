package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrasslandMixBig;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgRedwoodDecos;


/**
 * RWG {@code savanna/RealisticBiomeHotForest} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot forest（0.8 / 0.2，禁雨）；地形 {@code TerrainGrasslandFlats}；地表
 * {@code SurfaceGrasslandMixBig(sand, sand, grass, dirt, stone, cobble, 60, -0.14, 14, 0.25)}；
 * 装饰与 {@code HotRedwood} 逐行相同，共用 {@link RtgRedwoodDecos#addHotForest}。
 */
public class RealisticBiomeRtgHotForest extends RealisticBiomeBase {

    public RealisticBiomeRtgHotForest(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgHotForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrasslandMixBig(getConfig(),
                Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(),
                Blocks.GRASS.getDefaultState(), Blocks.DIRT.getDefaultState(),
                Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
                60f, -0.14f, 14f, 0.25f);
    }

    @Override
    public void initDecos() {
        RtgRedwoodDecos.addHotForest(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgHotForest extends TerrainBase {

        public TerrainRtgHotForest() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeHotForest`：terrain = new TerrainGrasslandFlats()
            return terrainGrasslandFlats(x, y, rtgWorld, river);
        }
    }
}
