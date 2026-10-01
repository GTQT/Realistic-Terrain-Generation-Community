package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;
import biomesoplenty.api.enums.BOPTrees;
import biomesoplenty.api.enums.BOPWoods;
import biomesoplenty.common.block.BlockBOPLeaves;
import biomesoplenty.common.block.BlockBOPLog;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockLog.EnumAxis;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;

import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoMushrooms;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.surface.SurfaceGrassland;


public class RealisticBiomeBOPBayou extends RealisticBiomeBase {

    private static IBlockState mudBlock = BOPBlocks.mud.getDefaultState();
    private static IBlockState logBlock = BlockBOPLog.paging.getVariantState(BOPWoods.WILLOW)
        .withProperty(BlockLog.LOG_AXIS, EnumAxis.Y);
    private static IBlockState leavesBlock = BlockBOPLeaves
        .paging.getVariantState(BOPTrees.WILLOW)
        .withProperty(BlockLeaves.CHECK_DECAY, false)
        .withProperty(BlockLeaves.DECAYABLE, false);
    private double lakeWaterLevel = 0.04;// the lakeStrength below which things should be below water
    private double lakeDepressionLevel = 0.3;// the lakeStrength below which land should start to be lowered

    public RealisticBiomeBOPBayou(final Biome biome) { 
    	super(biome);  
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_WATER_LAKE_MULT.set(0.0f);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPBayou();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

//        TreeRTG myrtilloidesTree = new TreeRTGSalixMyrtilloides();
//        myrtilloidesTree.setLogBlock(logBlock);
//        myrtilloidesTree.setLeavesBlock(leavesBlock);
//        myrtilloidesTree.validGroundBlocks.add(mudBlock);
//        this.addTree(myrtilloidesTree);
//        DecoTree decoTrees = new DecoTree(myrtilloidesTree);
//        decoTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
//        decoTrees.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
//        decoTrees.setTreeConditionChance(4);
//        decoTrees.setLogBlock(logBlock);
//        decoTrees.setLeavesBlock(leavesBlock);
//        decoTrees.setMaxY(90);
//        this.addDeco(decoTrees);

        /*
         * STOP! Don't add anymore trees! BOP seems to generate a batch of its trees every time RTG generates a batch
         * of its trees, even though we're not calling the BOP Bayou's decorate() method.
         */

//        TreeRTG roseaTree = new TreeRTGCeibaRosea(16f, 5, 0.32f, 0.1f);
//        roseaTree.setLogBlock(logBlock);
//        roseaTree.setLeavesBlock(leavesBlock);
//        roseaTree.validGroundBlocks.add(mudBlock);
//        roseaTree.setMinTrunkSize(2);
//        roseaTree.setMaxTrunkSize(3);
//        roseaTree.setMinCrownSize(10);
//        roseaTree.setMaxCrownSize(18);
//        roseaTree.setNoLeaves(false);
//        this.addTree(roseaTree);
//        DecoTree ceibaRoseaTree = new DecoTree(roseaTree);
//        ceibaRoseaTree.setTreeType(DecoTree.TreeType.RTG_TREE);
//        ceibaRoseaTree.setTreeCondition(DecoTree.TreeCondition.RANDOM_CHANCE);
//        ceibaRoseaTree.setTreeConditionChance(4);
//        ceibaRoseaTree.setMaxY(90);
//        this.addDeco(ceibaRoseaTree);

        //decoBaseBiomeDecorations.setNotEqualsZeroChance(4);

        // Shrubs to fill in the blanks.
        DecoShrub decoShrubOak = new DecoShrub();
        decoShrubOak.setMaxY(90);
        decoShrubOak.setLoopMultiplier(3f);
        decoShrubOak.setChance(3);
        this.addDeco(decoShrubOak);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogConditionChance(4);
        decoFallenTree.setLogBlock(logBlock);
        decoFallenTree.setLeavesBlock(leavesBlock);
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoMushrooms decoMushrooms = new DecoMushrooms();
        decoMushrooms.setMaxY(90);
        decoMushrooms.setRandomType(DecoMushrooms.RandomType.ALWAYS_GENERATE);
        this.addDeco(decoMushrooms);
    }

    public static class TerrainBOPBayou extends TerrainBase {

        public TerrainBOPBayou() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  bayou -> TerrainSwampRiver()  （本仓库先前误用了 terrainPlains）
            return terrainSwampRiver(x, y, rtgWorld, river);
        }
    }

}
