package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import biomesoplenty.api.block.BOPBlocks;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.helper.DecoHelperRandomSplit;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;

public class RealisticBiomeBOPDeadForest extends RealisticBiomeBase {

    public RealisticBiomeBOPDeadForest(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {
        return BOPBiomes.gravel_beach.orNull();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPDeadForest(58f, 80f, 30f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setChance(16);
        decoBoulder.setMaxY(95);
        decoBoulder.setStrengthFactor(1f);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree1 = new DecoFallenTree();
        decoFallenTree1.setLoops(1);
        decoFallenTree1.getDistribution().setNoiseDivisor(100f);
        decoFallenTree1.getDistribution().setNoiseFactor(6f);
        decoFallenTree1.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree1.setLogConditionChance(10);
        decoFallenTree1.setMaxY(100);
        decoFallenTree1.setLogBlock(BOPBlocks.log_3.getStateFromMeta(2));
        decoFallenTree1.setLeavesBlock(Blocks.LEAVES.getDefaultState());
        decoFallenTree1.setMinSize(3);
        decoFallenTree1.setMaxSize(5);

        DecoFallenTree decoFallenTree2 = new DecoFallenTree();
        decoFallenTree2.setLoops(1);
        decoFallenTree2.getDistribution().setNoiseDivisor(100f);
        decoFallenTree2.getDistribution().setNoiseFactor(6f);
        decoFallenTree2.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree2.setLogConditionChance(10);
        decoFallenTree2.setMaxY(100);
        decoFallenTree2.setLogBlock(BlockUtil.getStateLog(EnumType.SPRUCE));
        decoFallenTree2.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.SPRUCE));
        decoFallenTree2.setMinSize(3);
        decoFallenTree2.setMaxSize(5);

        DecoHelperRandomSplit decoHelperRandomSplit = new DecoHelperRandomSplit();
        decoHelperRandomSplit.decos = new DecoBase[]{decoFallenTree2, decoFallenTree1};
        decoHelperRandomSplit.chances = new int[]{12, 1};
        this.addDeco(decoHelperRandomSplit, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPDeadForest extends TerrainBase {

        private float minHeight = 58f;
        private float maxHeight = 120f;
        private float hillStrength = 30f;
        private float deadForestGroundAmplitude = 10f;

        public TerrainBOPDeadForest() {

        }

        public TerrainBOPDeadForest(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:239-244  deadForest -> TerrainGrasslandHills(50f, 180f, 13f, 100f, 28f, 260f, 70f)
            return terrainGrasslandHills(x, y, rtgWorld, river, 50f, 180f, 13f, 100f, 28f, 260f, 70f);
        }
    }

}
