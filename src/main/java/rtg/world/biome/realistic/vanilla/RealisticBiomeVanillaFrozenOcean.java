package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceOcean;


public class RealisticBiomeVanillaFrozenOcean extends RealisticBiomeBase {

    public static Biome biome = Biomes.FROZEN_OCEAN;
    public static Biome river = Biomes.FROZEN_RIVER;

    public RealisticBiomeVanillaFrozenOcean() {

        super(biome, RiverType.FROZEN, BeachType.COLD);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaFrozenOcean();
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `Support.java:156-160`：`oceanShallowSnow = new RealisticBiomeOcean(baseOceanCold, true, …)`
        // ⇒ **浅海**海底 = 沙 6 格（`rReplace`：shallow ? sand : gravel）。
        // 此前这里用陆地版 `SurfaceMountainSnow(topBlock, fillerBlock, true, SAND, 0.2f)`：
        // 只有顶层 1 格沙、下面 5 格砾石（`frozen_ocean` 的 fillerBlock 是砾石）。
        return new SurfaceOcean(this.getConfig(), true);
    }

    @Override
    public void initDecos() {
    }

    public static class TerrainVanillaFrozenOcean extends TerrainBase {

        public TerrainVanillaFrozenOcean() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG biomes/realistic/ocean/RealisticBiomeOcean.rNoise —— 浅海海底 y≈52
            // （rtgc 此前接的是 land 用的 terrainFlatLakes / BOP 陆地版的 swamp mountain，
            //  导致海洋只有 1 格水；kelp 更是把山地地形带进了海里。）
            return terrainOcean(x, y, rtgWorld, true);
        }
    }

}
