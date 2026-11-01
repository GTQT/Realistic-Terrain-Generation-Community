package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStoneMix1;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgDesertDecos;


/**
 * RWG {@code savanna/RealisticBiomeStoneMountainsCactus} 的独立群系外壳（A 组）。
 *
 * <p>与 {@link RealisticBiomeRtgStoneMountains} 的差别只有 mixSize（0.20 vs 0.08）与
 * 仙人掌密度（干地 70 vs 10，y=64+rand64）；装饰用 {@code addStoneMountains(true)} 复用同一段代码。
 */
public class RealisticBiomeRtgStoneMountainsCactus extends RealisticBiomeBase {

    public RealisticBiomeRtgStoneMountainsCactus(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgStoneMountainsCactus();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStoneMix1(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                false, null, 0f, 1.5f, 60f, 65f, 1.5f, Blocks.STONE.getDefaultState(), 0.20f);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addStoneMountains(this, true);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgStoneMountainsCactus extends TerrainBase {

        public TerrainRtgStoneMountainsCactus() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeStoneMountainsCactus`：terrain = new TerrainHilly(230f, 120f, 0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }
}
