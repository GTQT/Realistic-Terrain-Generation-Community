package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.config.BiomeConfig;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.*;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainSnow;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPBorealForest extends RealisticBiomeBase {

    public RealisticBiomeBOPBorealForest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        // 移植上游新树系统 / T5+T6
        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());
        this.treeGenerator = new DecoCollectionBOPBorealForest(this.getConfig());
    }

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


    public static class DecoCollectionBOPBorealForest extends DecoCollectionBase {
        
        private Distribution treeFrequencyDistribution = new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.5f, 5.5f); 
        // the BoP version is pretty crowded
        // the trees are a bit stunted too

    	public DecoCollectionBOPBorealForest(BiomeConfig config) {
    		super(config);     
    		DecoTreeSet treeChooser = new DecoTreeSet();
    		treeChooser.setDistribution(treeFrequencyDistribution);
    		treeChooser.add(evergreenTree(),9);
    		treeChooser.add(yellowAutumnTree(), 9);
    		treeChooser.add(stuntedOakTree(), 2);
    		this.addDeco(treeChooser);
    	}

    	private DecoTree evergreenTree() {
    		DecoVariableTree result = new DecoVariableSpruce();
    		result.changeAverageHeightSqrt(-.5f);
    		return result;
    	}
    	
    	private DecoTree yellowAutumnTree() {
    		DecoVariableTree result = new DecoVariableBirch();
    		result.changeAverageHeightSqrt(-.5f);
    		result.setMaterials(BOPTreeMaterials.yellowAutumn);
    		return result;
    	}
  
    	
    	private DecoTree stuntedOakTree() {
    		DecoVariableTree result = new DecoVariableOak();
    		result.changeAverageHeightSqrt(-1.5f);
    		result.changeHeightNoiseVariability(-.5f);
    		return result;
    	}	
    	
    }

}
