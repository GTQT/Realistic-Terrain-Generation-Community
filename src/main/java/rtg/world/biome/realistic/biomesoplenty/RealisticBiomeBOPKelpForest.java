package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceOcean;
import rtg.api.world.terrain.TerrainBase;


/**
 * BOP 的海带森林 —— RWG 侧对应 {@code SupportBOP.java:39-43} 的
 * {@code new RealisticBiomeBOPOcean(BOPCBiomes.kelpForest, …)}。
 *
 * <p><b>它不是海洋槽位群系</b>：RWG 把它赋给 {@code Support.oceanShallowKelp} 这个
 * **patch 钩子**（冷带浅海里 {@code continent < -90 && patch > 0} 的那些斑块），
 * 而不是任何 {@code oceanShallow*}/{@code oceanDeep*} 槽位。
 * rtgc 侧对应 {@code RtgBiomeCategorizer.oceanPatchFor}：带 OCEAN 字典标签但**不占槽位**。
 */
public class RealisticBiomeBOPKelpForest extends RealisticBiomeBase {

    public RealisticBiomeBOPKelpForest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public void initConfig() {
        // 与 rtgc 其余海洋群系一致（RWG 的海洋没有河/湖）
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_SPONGE).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPKelpForest(false, -10f, 0f, 0f, 0f, 30f);
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG 的 `RealisticBiomeBOPOcean extends RealisticBiomeOcean` 没有覆写 `rReplace`
        // ⇒ 海底用的就是海洋那份：`shallow ? Blocks.sand : Blocks.gravel`，6 格厚。
        // 此前这里是陆地版的 `SurfaceMountainSnow(topBlock, fillerBlock, …)`（砾石/泥），
        // 与"海底刷沙"不一致，现已换成 {@link SurfaceOcean}（shallow=true）。
        return new SurfaceOcean(getConfig(), true);
    }

    // ==================================================================
    // RWG 的海洋装饰延迟 + BOP 珊瑚/海草清理
    // ==================================================================

    /** 见 {@code IRealisticBiome#defersVanillaDecorateUntilAfterIce}：RWG 里只有 BOP 海洋为 true。 */
    @Override
    public boolean defersVanillaDecorateUntilAfterIce() {
        return true;
    }

    /**
     * RWG {@code RealisticBiomeBOPOcean:23-33}：先跑 {@code Biome.decorate}（BOP 放海草/珊瑚），
     * 再对 34×34 范围做"站不住就换回水"的清理。门控 {@code strength > 0.3f} 与 RWG 一致。
     */
    @Override
    public void rDecorateAfterIce(final RTGWorld rtgWorld, final java.util.Random rand,
                                  final int chunkX, final int chunkZ, final float strength) {
        if (strength <= 0.3f) {
            return;
        }
        vanillaDecorate(rtgWorld, rand, new net.minecraft.util.math.ChunkPos(chunkX >> 4, chunkZ >> 4));
        rtg.api.util.OceanDecorationSanitizer.sanitize(rtgWorld.world(), chunkX, chunkZ, baseBiome());
    }

    public static class TerrainBOPKelpForest extends TerrainBase {

        private boolean booRiver;
        private float[] height;
        private int heightLength;
        private float strength;
        private float cWidth;
        private float cHeigth;
        private float cStrength;
        private float base;

        /*
         * Example parameters:
         *
         * allowed to generate rivers?
         * riverGen = true
         *
         * canyon jump heights
         * heightArray = new float[]{2.0f, 0.5f, 6.5f, 0.5f, 14.0f, 0.5f, 19.0f, 0.5f}
         *
         * strength of canyon jump heights
         * heightStrength = 35f
         *
         * canyon width (cliff to cliff)
         * canyonWidth = 160f
         *
         * canyon heigth (total heigth)
         * canyonHeight = 60f
         *
         * canyon strength
         * canyonStrength = 40f
         *
         */
        public TerrainBOPKelpForest(boolean riverGen, float heightStrength, float canyonWidth, float canyonHeight, float canyonStrength, float baseHeight) {

            booRiver = riverGen;
            height = new float[]{5.0f, 0.5f, 12.5f, 0.5f};
            strength = heightStrength;
            heightLength = height.length;
            cWidth = canyonWidth;
            cHeigth = canyonHeight;
            cStrength = canyonStrength;
            base = baseHeight;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // RWG `SupportBOP.java:39-43`：kelpForest 走的是 `RealisticBiomeBOPOcean`
            // ⇒ 地形就是 `RealisticBiomeOcean.rNoise`：`height = shallow ? 52f : 34f` 加噪声。
            //（⚠ 旧注释写"照抄 kelpForest -> TerrainSwampMountain(135f,300f)"是**错的**：
            //  那个地形属于 bambooForest / eucalyptusForest / fungiForest。）
            return terrainOcean(x, y, rtgWorld, true);
        }
    }

}
