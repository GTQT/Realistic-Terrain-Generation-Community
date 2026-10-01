package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;

import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceMountainStone;


public class RealisticBiomeBOPMountainPeaks extends RealisticBiomeBase {

    public RealisticBiomeBOPMountainPeaks(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMountainPeaks(120f, 100f);
    }

    @Override
    public SurfaceBase initSurface() {
        // RWG `SupportBOP.java:576-588`：SurfaceMountainStone(top, filler, true, Blocks.sand, 0.75f)
        // （此前是 `SurfaceTundra(top, filler)`，与 RWG 的 active 条目不符）
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock,
                true, Blocks.SAND.getDefaultState(), 0.75f);
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setMaxY(90);
        decoBoulder.setChance(16);
        decoBoulder.setStrengthFactor(3f);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(100f);
        decoFallenTree.getDistribution().setNoiseFactor(6f);
        decoFallenTree.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree.setLogConditionChance(6);
        decoFallenTree.setLogBlock(BOPBlocks.log_2.getStateFromMeta(6));
        decoFallenTree.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setLoopMultiplier(1f);
        decoShrub.setChance(10);
        this.addDeco(decoShrub);
    }

    /**
     * RWG-style grand mountain peaks — towering terrain with dramatic height.
     */
    public static class TerrainBOPMountainPeaks extends TerrainBase {

        private float terrainHeight;

        public TerrainBOPMountainPeaks(float mountainWidth, float mountainStrength) {
            this(mountainWidth, mountainStrength, 90f);
        }

        public TerrainBOPMountainPeaks(float mountainWidth, float mountainStrength, float height) {
            terrainHeight = height;
            base = 67f;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG `SupportBOP.java:576-588`（// MOUNTAIN）：
            //     new RealisticBiomeSupport(BOPCBiomes.mountain, RWGBiomes.baseRiverHot,
            //                               new TerrainMountainRiver(),
            //                               new SurfaceMountainStone(mountain.topBlock, mountain.fillerBlock,
            //                                                        true, Blocks.sand, 0.75f))
            // ⚠ 此前是 `terrainMountain(...)` + `SurfaceTundra(top, filler)` —— 两半都与 RWG 的
            // active 条目不符（`TerrainMountain` 在 RWG 里不属于 BOP mountain 这一条）。
            return terrainMountainRiver(x, y, rtgWorld, river);
        }
    }

}
