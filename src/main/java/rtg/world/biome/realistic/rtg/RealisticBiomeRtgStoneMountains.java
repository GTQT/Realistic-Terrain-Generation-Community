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
 * RWG {@code savanna/RealisticBiomeStoneMountains} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot plains；地形 {@code TerrainHilly(230,120,0)}；地表
 * {@code SurfaceMountainStoneMix1(..., mixSize 0.08f)}；装饰 {@code addStoneMountains(false)}。
 */
public class RealisticBiomeRtgStoneMountains extends RealisticBiomeBase {

    public RealisticBiomeRtgStoneMountains(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgStoneMountains();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStoneMix1(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                false, null, 0f, 1.5f, 60f, 65f, 1.5f, Blocks.STONE.getDefaultState(), 0.08f);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addStoneMountains(this, false);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgStoneMountains extends TerrainBase {

        public TerrainRtgStoneMountains() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeStoneMountains`：terrain = new TerrainHilly(230f, 120f, 0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }
}
