package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceOcean;
import rtg.api.world.terrain.TerrainBase;


/**
 * BOP 的珊瑚礁 —— RWG 侧对应 {@code SupportBOP.java:44-48} 的
 * {@code new RealisticBiomeBOPOcean(BOPCBiomes.coralReef, …)}。
 *
 * <p><b>它不是海洋槽位群系</b>：RWG 把它赋给 {@code Support.oceanShallowCoral} 这个
 * **patch 钩子**（热带浅海里 {@code -150 < continent < -20 && patch > .07} 的那些斑块），
 * 而不是任何 {@code oceanShallow*}/{@code oceanDeep*} 槽位。
 * rtgc 侧对应 {@code RtgBiomeCategorizer.oceanPatchFor}。
 */
public class RealisticBiomeBOPCoralReef extends RealisticBiomeBase {

    public RealisticBiomeBOPCoralReef(final Biome biome) { super(biome); }

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

        return new TerrainBOPCoralReef(false, -10f, 0f, 0f, 0f, 30f);
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG 的 `RealisticBiomeBOPOcean extends RealisticBiomeOcean` 没有覆写 `rReplace`
        // ⇒ 海底 = 海洋那份（shallow → sand，6 格厚），此前用的是陆地版 SurfaceMountainSnow。
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
     * RWG {@code RealisticBiomeBOPOcean:23-33}：
     * 先按原样跑 {@code Biome.decorate}（BOP 自己的装饰器，放珊瑚/海草），
     * 再对 34×34 范围做一遍"站不住就换回水"的清理。
     *
     * <p>门控 {@code strength > 0.3f} 与 RWG 一致 —— 只有该海洋群系在本区块**占主导**时才动手，
     * 免得一个边缘群系跑过来把主导群系的水下装饰删掉。
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

    public static class TerrainBOPCoralReef extends TerrainBase {

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
        public TerrainBOPCoralReef(boolean riverGen, float heightStrength, float canyonWidth, float canyonHeight, float canyonStrength, float baseHeight) {

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

            // RWG `SupportBOP.java:44-48`：coralReef 走的是 `RealisticBiomeBOPOcean`
            // ⇒ 地形 = `RealisticBiomeOcean.rNoise`（浅海海底 y≈52），RWG 没有为它写陆地地形。
            return terrainOcean(x, y, rtgWorld, true);
        }
    }

}
