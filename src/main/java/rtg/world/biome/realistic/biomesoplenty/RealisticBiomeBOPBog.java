package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.WaterLevel;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPBog extends RealisticBiomeBase {

    public RealisticBiomeBOPBog(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {}

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPBog();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPBog extends TerrainBase {

        // 这里原有 `bottom` / `bottomVariation`(HeightVariation) / `smallHills` / `mediumHills`
        // (HillockEffect) 四个字段与一整段构造代码 —— 它们**只被赋值、从未被读取**，
        // 是 RTG 时代高度效应体系的残留（地形早已迁到 `terrainMarsh`）。
        // 已连同整个 `rtg.api.world.terrain.heighteffect` 包一并删除（见 CHANGELOG「死码清理」）。
        // 判据不是肉眼：`tools/reachability.ps1` 报告这些类在包外零调用者，
        // 且本文件内这些字段没有任何读取点。

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:96-100  bog -> TerrainMarsh()
            return terrainMarsh(x, y, rtgWorld, WaterLevel.current().waterSurfaceTop(), river);
        }
    }

}
