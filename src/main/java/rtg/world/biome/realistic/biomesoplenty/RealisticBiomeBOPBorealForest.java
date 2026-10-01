package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainSnow;


public class RealisticBiomeBOPBorealForest extends RealisticBiomeBase {

    public RealisticBiomeBOPBorealForest(final Biome biome) { super(biome); }

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

        return new TerrainBOPBorealForest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainSnow(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, true, Blocks.SAND.getDefaultState(), 0.45f, 1.5f, 60f, 65f, 0.4f, 130f, 50f, 1.5f);
    }

    public static class TerrainBOPBorealForest extends TerrainBase {

        // 这里原有 `hillEffect`(BumpyHillsEffect) 与 `baseHeight` / `hillStrength` /
        // `hillWidth` / `hillBumpyness` / `hillBumpynessWidth` 共 6 个字段、两个构造器
        // —— **只被赋值、从未被读取**（`generateNoise` 早已改为 `terrainMountainSpikes`），
        // 属 RTG 高度效应体系的残留。已连同整个
        // `rtg.api.world.terrain.heighteffect` 包删除（见 CHANGELOG「死码清理」）。

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  borealForest -> TerrainMountainSpikes()
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
