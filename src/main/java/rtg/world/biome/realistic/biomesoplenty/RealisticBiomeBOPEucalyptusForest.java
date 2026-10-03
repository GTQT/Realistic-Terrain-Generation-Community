package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoBoulder;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTree.TreeCondition;
import rtg.api.world.deco.bop.VariableBOPEucalyptusTree;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPEucalyptusForest extends RealisticBiomeBase {

    public RealisticBiomeBOPEucalyptusForest(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPEucalyptusForest(); //(58f, 80f, 36f)
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0.95f);
    }

    @Override
    public void initDecos() {

        // 移植上游新树系统 / T5+T6
        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());
        this.treeGenerator = new DecoCollectionBOPEucalyptusForest(this.getConfig());

        DecoBoulder decoBoulder = new DecoBoulder();
        decoBoulder.setBoulderBlock(Blocks.COBBLESTONE.getDefaultState());
        decoBoulder.setMaxY(80);
        decoBoulder.setChance(12);
        decoBoulder.setStrengthFactor(1f);
        this.addDeco(decoBoulder);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(100f);
        decoFallenTree.getDistribution().setNoiseFactor(6f);
        decoFallenTree.getDistribution().setNoiseAddend(0.8f);
        decoFallenTree.setLogConditionChance(16);
        decoFallenTree.setLogBlock(BlockUtil.getStateLog(EnumType.JUNGLE));
        decoFallenTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.JUNGLE));
        decoFallenTree.setMinSize(8);
        decoFallenTree.setMaxSize(14);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPEucalyptusForest extends TerrainBase {

        private float baseHeight = 76f;
        private float peakyHillWavelength = 40f;
        private float peakyHillStrength = 20f;
        private float smoothHillWavelength = 20f;
        private float smoothHillStrength = 10f;

        public TerrainBOPEucalyptusForest() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  eucalyptusForest -> TerrainSwampMountain(135f, 300f)
            return terrainSwampMountain(x, y, rtgWorld, river, 135f, 300f);
        }
    }


    private class DecoCollectionBOPEucalyptusForest extends DecoCollectionBase {

		public DecoCollectionBOPEucalyptusForest(BiomeConfig config) {
			super(config);       
			float tallMin = -1f;
	        float tallMax = 3f;
	        Distribution treeFrequencyDistribution = new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 1.0f, 2.0f); 
	        
	    	DecoTree trees = new VariableBOPEucalyptusTree();
	    	trees.setStrengthFactorForLoops(6f)
	        .setTreeCondition(TreeCondition.ALWAYS_GENERATE)
	        .setTreeConditionNoise(tallMin)
	        .setTreeConditionNoise2(tallMax)
	        .setTreeConditionChance(1)
	        .setMaxY(120)
	        .setStrengthNoiseFactorForLoops(true)
	        .setStrengthNoiseFactorXForLoops(false);
	    	
	    	trees.setDistribution(treeFrequencyDistribution);
	    	
	        this.addDeco(trees);
	    	
	    	DecoShrub shrubs = new DecoShrub();
	    	shrubs.setLoops(8);
	    	
	    	this.addDeco(shrubs);
	        
		}
    	
    }
}
