package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPMarsh extends RealisticBiomeBase {

    public RealisticBiomeBOPMarsh(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMarsh();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPMarsh extends TerrainBase {

        // 这里原有 `baseHeight` 与 `variation` / `smallVariation`(HeightVariation)
        // 三个字段与构造代码 —— **只被赋值、从未被读取**，属 RTG 高度效应体系的残留。
        // 已连同整个 `rtg.api.world.terrain.heighteffect` 包删除（见 CHANGELOG「死码清理」）。

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 推断（**不是照抄**）：RWG SupportBOP.java:525-530 的 marsh 条目整块被 /* */ 注释，注释行写的是 TerrainMarsh()（作者原意）。
            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
