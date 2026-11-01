package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceDesert;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgDesertDecos;


/**
 * RWG {@code desert/RealisticBiomeDuneValley} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot desert（1.0 / 0.0）；地形 {@code TerrainDunes}；地表
 * {@code SurfaceDesert(sand, sand, sandstone, stone, cobblestone)}；装饰
 * {@code addDuneValley}（巨石/仙人掌/枯木 + 河岸树带/芦苇/南瓜/花/草）。
 */
public class RealisticBiomeRtgDuneValley extends RealisticBiomeBase {

    public RealisticBiomeRtgDuneValley(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgDuneValley();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDesert(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                Blocks.SANDSTONE.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addDuneValley(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgDuneValley extends TerrainBase {

        public TerrainRtgDuneValley() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeDuneValley`：terrain = new TerrainDunes()
            return terrainDunes(x, y, rtgWorld, river);
        }
    }
}
