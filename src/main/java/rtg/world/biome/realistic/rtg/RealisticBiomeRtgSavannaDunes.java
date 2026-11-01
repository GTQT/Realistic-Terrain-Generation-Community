package rtg.world.biome.realistic.rtg;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceDuneValley;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.RtgDesertDecos;


/**
 * RWG {@code savanna/RealisticBiomeSavannaDunes} 的独立群系外壳（A 组）。
 *
 * <p>基底 hot plains（0.9 / 0.1，禁雨）；地形 {@code TerrainDuneValley(300f)}；地表
 * {@code SurfaceDuneValley(grass, dirt, 300f, true, true)}；装饰
 * {@code addSavannaDunes}（河岸/干地两套：树带、仙人掌 12/36、芦苇、南瓜、花、草、野麦）。
 */
public class RealisticBiomeRtgSavannaDunes extends RealisticBiomeBase {

    public RealisticBiomeRtgSavannaDunes(final Biome biome) {

        super(biome, RiverType.NORMAL, BeachType.NORMAL);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {
        return new TerrainRtgSavannaDunes();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceDuneValley(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, 300f, true, true);
    }

    @Override
    public void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld,
                         float[] noise, float river, Biome[] base) {

        this.rReplaceWithRiver(primer, i, j, x, y, depth, rtgWorld, noise, river, base);
    }

    @Override
    public void initDecos() {
        RtgDesertDecos.addSavannaDunes(this);
    }

    @Override
    public boolean allowVanillaTrees() {
        return false;
    }

    public static class TerrainRtgSavannaDunes extends TerrainBase {

        public TerrainRtgSavannaDunes() {
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // RWG `RealisticBiomeSavannaDunes`：terrain = new TerrainDuneValley(300f)
            return terrainDuneValley(x, y, rtgWorld, river, 300f);
        }
    }
}
