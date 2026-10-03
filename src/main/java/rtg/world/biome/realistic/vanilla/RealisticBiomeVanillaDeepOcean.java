package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.collection.DecoCollectionOcean;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceOcean;


public class RealisticBiomeVanillaDeepOcean extends RealisticBiomeBase {

    public static Biome biome = Biomes.DEEP_OCEAN;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaDeepOcean() {

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

        return new TerrainVanillaDeepOcean();
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `Support.java:172`：`oceanDeepCold = new RealisticBiomeOcean(BiomeGenBase.deepOcean, false, …)`
        // ⇒ 海底 = **砾石** 6 格（`RealisticBiomeOcean.rReplace`：shallow ? sand : gravel）。
        // 此前这里是陆地用的 `SurfaceMountainSnow(topBlock, fillerBlock, true, SAND, 0.2f)`：
        // `deep_ocean` 的 topBlock 在 1.12 是沙 ⇒ 冷带深海的海底变成沙，与 RWG 不符
        //（冷带面积最大，约 30%，所以这片海会整片看错）。
        return new SurfaceOcean(this.getConfig(), false);
    }

    @Override
    public void initDecos() {
        this.addDecoCollection(new DecoCollectionOcean(this.getConfig()));
    }

    public static class TerrainVanillaDeepOcean extends TerrainBase {

        public TerrainVanillaDeepOcean() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 依据：RWG biomes/realistic/ocean/RealisticBiomeOcean.rNoise —— 深海海底 y≈34
            // （rtgc 此前接的是 land 用的 terrainFlatLakes / BOP 陆地版的 swamp mountain，
            //  导致海洋只有 1 格水；kelp 更是把山地地形带进了海里。）
            return terrainOcean(x, y, rtgWorld, false);
        }
    }

}
