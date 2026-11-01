package rtg.world.biome.realistic.rtg;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceDesertOasis;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgDesertDecos;


/**
 * RWG {@code desert/RealisticBiomeOasis} 的独立群系外壳（A 组）。
 *
 * <p>基底 Oasis（0.9 / 0.9，Ocelot）；地形 {@code TerrainHilly(230,120,20,60,63)}；
 * 地表 {@code SurfaceDesertOasis(grass, dirt, stone, cobble, 0, 0)}；装饰
 * {@code addOasis}（巨石/灌木/棕榈/稀树树/仙人掌/芦苇/南瓜/花/草/枯木）。
 * 河岸走 {@code rReplaceWithRiver}（RWG 的 {@code SurfaceRiverOasis}）。
 */
public class RealisticBiomeRtgOasis extends RealisticBiomeBase {

    public RealisticBiomeRtgOasis(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgOasis();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDesertOasis(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), (byte) 0, 0);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addOasis(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgOasis extends TerrainBase {

        public TerrainRtgOasis() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeOasis`：terrain = new TerrainHilly(230f, 120f, 20f, 60f, 63f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 20f, 60f, 63f);
        }
    }
}
