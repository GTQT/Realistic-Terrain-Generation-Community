package rtg.world.biome.realistic.vanilla;

import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.collection.DecoCollectionBirchForestM;
import rtg.api.world.deco.collection.trees.DecoCollectionBirchForestMTrees;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaPapyrifera;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;


public class RealisticBiomeVanillaBirchForestM extends RealisticBiomeBase {

    public static Biome biome = Biomes.MUTATED_BIRCH_FOREST;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaBirchForestM() {

        super(biome);
    }

    @Override
    public boolean allowVanillaTrees() {
    	return false;
    }
    
    @Override
    public void initConfig() {
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaBirchForestM();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 0f, 1.5f, 60f, 65f, 1.5f);
    }

    @Override
    public void initDecos() {
    	
    	this.addDecoCollection(new DecoCollectionBirchForestM(this.getConfig()));

        TreeRTG tallBirch = new TreeRTGBetulaPapyrifera();
        tallBirch.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        tallBirch.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        tallBirch.setMinTrunkSize(16);
        tallBirch.setMaxTrunkSize(23);
        tallBirch.setMinCrownSize(4);
        tallBirch.setMaxCrownSize(11);
        //this.addTree(tallBirch);

        DecoTree superTallBirch = new DecoTree(tallBirch);
        superTallBirch.setStrengthFactorForLoops(8f);
        superTallBirch.setStrengthNoiseFactorForLoops(true);
        superTallBirch.setTreeType(DecoTree.TreeType.RTG_TREE);
        superTallBirch.getDistribution().setNoiseDivisor(80f);
        superTallBirch.getDistribution().setNoiseFactor(60f);
        superTallBirch.getDistribution().setNoiseAddend(-15f);
        superTallBirch.setTreeCondition(DecoTree.TreeCondition.ALWAYS_GENERATE);
        superTallBirch.setMaxY(100);
        //this.addDeco(superTallBirch);

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(20);
        decoFallenTree.setLogBlock(BlockUtil.getStateLog(EnumType.BIRCH));
        decoFallenTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.BIRCH));
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setLoopMultiplier(1f);
        this.addDeco(decoShrub);
        this.treeGenerator = new DecoCollectionBirchForestMTrees(this.getConfig());
    }

//    @Override
//    public void overrideDecorations() {
//        baseBiome().decorator.grassPerChunk = -999;
//        baseBiome().decorator.flowersPerChunk = -999;
//    }

    public static class TerrainVanillaBirchForestM extends TerrainBase {

        public TerrainVanillaBirchForestM() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG 温带 WoodHills -> TerrainHilly(230f,120f,0f)
            return terrainHilly(x, y, rtgWorld, river, 230f, 120f, 0f, 260f, 68f);
        }
    }

}
