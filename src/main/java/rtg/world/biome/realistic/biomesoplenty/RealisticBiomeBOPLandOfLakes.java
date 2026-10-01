package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.helper.DecoHelper5050;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaPapyrifera;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGPiceaSitchensis;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPLandOfLakes extends RealisticBiomeBase {

    public RealisticBiomeBOPLandOfLakes(final Biome biome) { super(biome); }

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

        return new TerrainBOPLandOfLakes();//(58f, 76f, 36f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        TreeRTG birchTree = new TreeRTGBetulaPapyrifera();
        birchTree.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        birchTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        birchTree.setMinTrunkSize(4);
        birchTree.setMaxTrunkSize(8);
        birchTree.setMinCrownSize(8);
        birchTree.setMaxCrownSize(16);
        this.addTree(birchTree);

        DecoTree birchTrees = new DecoTree(birchTree);
        birchTrees.setStrengthFactorForLoops(5f);
        birchTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        birchTrees.getDistribution().setNoiseDivisor(100f);
        birchTrees.getDistribution().setNoiseFactor(6f);
        birchTrees.getDistribution().setNoiseAddend(0.8f);
        birchTrees.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        birchTrees.setTreeConditionChance(1);
        birchTrees.setTreeConditionNoise(0f);
        birchTrees.setMaxY(120);

        TreeRTG sitchensisTree = new TreeRTGPiceaSitchensis();
        sitchensisTree.setLogBlock(BlockUtil.getStateLog(EnumType.SPRUCE));
        sitchensisTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.SPRUCE));
        sitchensisTree.setMinTrunkSize(4);
        sitchensisTree.setMaxTrunkSize(9);
        sitchensisTree.setMinCrownSize(5);
        sitchensisTree.setMaxCrownSize(14);
        this.addTree(sitchensisTree);

        DecoTree smallPine = new DecoTree(sitchensisTree);
        smallPine.setStrengthFactorForLoops(5f);
        smallPine.setTreeType(DecoTree.TreeType.RTG_TREE);
        smallPine.getDistribution().setNoiseDivisor(100f);
        smallPine.getDistribution().setNoiseFactor(6f);
        smallPine.getDistribution().setNoiseAddend(0.8f);
        smallPine.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        smallPine.setTreeConditionChance(1);
        smallPine.setTreeConditionNoise(0f);
        smallPine.setMaxY(120);

        DecoHelper5050 decoHelper5050 = new DecoHelper5050(birchTrees, smallPine);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(100f);
        decoFallenTree.getDistribution().setNoiseFactor(6f);
        decoFallenTree.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setRandomLogBlocks(new IBlockState[]{Blocks.LOG.getDefaultState(), BlockUtil.getStateLog(EnumType.SPRUCE), BlockUtil.getStateLog(EnumType.BIRCH)});
        decoFallenTree.setMinSize(8);
        decoFallenTree.setMaxSize(12);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setLoopMultiplier(2f);
        this.addDeco(decoShrub);

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setMaxY(80);
        decoBoulder.setChance(12);
        decoBoulder.setStrengthFactor(1f);
        this.addDeco(decoBoulder);
    }

    /**
     * 依据：RWG {@code SupportBOP.java:461-470} 的 {@code landOfLakesMarsh} 条目 ——
     * {@code HOT_BORDER}，地形 {@code new TerrainGrasslandHills(90f, 180f, 13f, 100f, 38f, 260f, 71f)}。
     * <p>
     * <b>近亲推断</b>：RWG 只收了 BOP 的 {@code landOfLakesMarsh}（沼泽变体），
     * rtgc 这个群系对应的是 BOP 的 {@code landOfLakes}。两者同族，取同值。
     */
    public static class TerrainBOPLandOfLakes extends TerrainBase {

        public TerrainBOPLandOfLakes() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            return terrainGrasslandHills(x, y, rtgWorld, river, 90f, 180f, 13f, 100f, 38f, 260f, 71f);
        }
    }

}
