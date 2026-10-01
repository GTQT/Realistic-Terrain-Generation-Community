package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceCanyon;


public class RealisticBiomeBOPCrag extends RealisticBiomeBase {

    public RealisticBiomeBOPCrag(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_2_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {
        // 依据：RWG SupportBOP.java:231-237 的 **crag** 条目（该作者以 /* */ 注释掉，属"作者原意"）：
        //   new TerrainCanyon(false, new float[]{2.0f, 0.5f, 6.5f, 0.5f, 14.0f, 0.5f, 19.0f, 0.5f},
        //                     35f, 80f, 60f, 40f, 69f)
        // 注意不要与 L164 那条**未注释**的 `new TerrainCanyon(true, 35f, 160f, 60f, 40f, 69f)` 混淆
        // —— 那是 `BOPCBiomes.canyon`（峡谷），而 rtgc 没有 canyon 群系。
        //
        // 原先的 height 数组多了一对 `23.0f, 0.5f`（RTG 时代添加的第 5 级阶地），
        // RWG 的参照只有 4 级，已按参照去掉。
        return new TerrainBOPCrag(false, new float[] {2.0f, 0.5f, 6.5f, 0.5f, 14.0f, 0.5f, 19.0f, 0.5f}, 35f, 80f, 60f, 40f, 69f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCanyon(getConfig(), Blocks.SAND.getDefaultState(), Blocks.SAND.getDefaultState(), (byte) 1, 0);
    }

    public static class TerrainBOPCrag extends TerrainBase {

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
        public TerrainBOPCrag(boolean riverGen, float[] heightArryay, float heightStrength, float canyonWidth, float canyonHeight, float canyonStrength, float baseHeight) {
            booRiver = riverGen;
            height = heightArryay;
            strength = heightStrength;
            heightLength = height.length;
            cWidth = canyonWidth;
            cHeigth = canyonHeight;
            cStrength = canyonStrength;
            base = baseHeight;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 原先这里内联了一整份 RWG 的 TerrainCanyon.generateNoise（约 50 行）。
            // 那段内联代码是**忠实**的，而 TerrainBase.terrainCanyon 当时是一份有偏差的死副本
            //（缺 river*=1.3 钳制、误给 r/sb 乘 river、基高用 getTerrainBase(river) 而非常量 69）。
            // 现已把 TerrainBase.terrainCanyon 修成与 RWG 逐行一致，并让本群系复用它：
            // 重复代码消除，且**世界生成结果与内联版完全相同**。
            //
            // 本方法的实际数值全部来自构造函数（见 `initTerrain()`）：**RWG 注释掉的 crag 条目原参数**
            //（`false` + `{2.0,0.5,6.5,0.5,14.0,0.5,19.0,0.5}` + 35f/80f/60f/40f/69f）。
            //
            // ⚠ 这里与 `initSurface()` 是**有意混用两条 RWG 条目**，属选择性推断（不是照抄）：
            //   · 地形：crag 注释条目（`SupportBOP.java:231-237`）；
            //   · 地表：canyon 的 active 条目（`:157-165` → `SurfaceCanyon(sand, sand, 1, 0)`），
            //     而 crag 注释行给的是 `SurfaceGrassland(crag.topBlock, crag.fillerBlock, stone, cobble)`。
            //   理由：BOP 的 crag 是石质峭壁，峡谷地表比草原地表更接近它的观感。
            return terrainCanyon(x, y, rtgWorld, river, height, border, strength, heightLength, booRiver, base);
        }
    }


}
