package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgJungleDecos;


/**
 * RWG {@code land/RealisticBiomeHighRainforest} 的独立群系外壳（A 组）。
 *
 * <p>基底 Jungle（0.9 / 0.9，Ocelot）；地形 {@code TerrainHighland(0,140,68,200)}；
 * 地表 {@code SurfaceGrassland(grass, dirt, stone, cobble)}；装饰 {@code addHighRainforest}
 * （苔石 + 泊松点大树 + 13 组草/蕨 + 花）。
 */
public class RealisticBiomeRtgHighRainforest extends RealisticBiomeBase {

    public RealisticBiomeRtgHighRainforest(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgHighRainforest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {
        RtgJungleDecos.addHighRainforest(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgHighRainforest extends TerrainBase {

        public TerrainRtgHighRainforest() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeHighRainforest`：terrain = new TerrainHighland(0f, 140f, 68f, 200f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }
}
