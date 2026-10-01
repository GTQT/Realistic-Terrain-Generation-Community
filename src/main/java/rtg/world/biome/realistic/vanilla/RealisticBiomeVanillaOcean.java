package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionOcean;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceOcean;


public class RealisticBiomeVanillaOcean extends RealisticBiomeBase {

    public static Biome biome = Biomes.OCEAN;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaOcean() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_SPONGE).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaOcean();
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `Support.java:161-165`：冷带的浅海是 `RealisticBiomeOcean(baseOceanCold, true, …)`
        // ⇒ 浅海海底 = 沙 6 格。
        //
        // ⚠ 注意本群系**当前不占任何海洋槽位**：rtgc 的 COLD 浅海由
        // `rtgc:shallow_cold_ocean` 占位（`RtgBiomeCategorizer` 的 authShallow 覆盖）。
        // 原版 `minecraft:ocean` 仍在 RTG_BIOMES 里，作为"布局未就绪"时的回落群系。
        // 这里与其余海洋群系统一用 `SurfaceOcean`，免得回落时海底出现第二种配方。
        return new SurfaceOcean(this.getConfig(), true);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionOcean(this.getConfig()));
    }

    public static class TerrainVanillaOcean extends TerrainBase {

        public TerrainVanillaOcean() {

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
