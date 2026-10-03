package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockLog.EnumAxis;
import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.bop.TreeBOPPalm;
import rtg.api.world.gen.feature.tree.rtg.*;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPRainforest extends RealisticBiomeBase {

    public RealisticBiomeBOPRainforest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTG coconutTree = new TreeRTGCocosNucifera();
        coconutTree.setMinCrownSize(9);
        coconutTree.setMaxCrownSize(11);
        coconutTree.setMinTrunkSize(9);
        coconutTree.setMaxTrunkSize(9);
        DecoTree coconutDeco = new DecoTree(coconutTree);

        TreeRTG palmTree = new TreeBOPPalm();
        palmTree.setMinCrownSize(9);
        palmTree.setMaxCrownSize(11);
        palmTree.setMinTrunkSize(9);
        palmTree.setMaxTrunkSize(9);
        DecoTree palmDeco = new DecoTree(palmTree);

        TreeRTG jungleTree = new TreeRTGBetulaPopulifolia();
        jungleTree.setLogBlock(BlockUtil.getStateLog(EnumType.JUNGLE));
        jungleTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.JUNGLE));
        jungleTree.setBranchBlock(BlockUtil.getStateLog(EnumType.JUNGLE).withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE));
        jungleTree.setMinCrownSize(8);
        jungleTree.setMaxCrownSize(11);
        jungleTree.setMinTrunkSize(4);
        jungleTree.setMaxTrunkSize(6);
        DecoTree jungleDeco = new DecoTree(jungleTree);

        TreeRTG floweringTree = new TreeRTGBetulaPopulifolia();
        floweringTree.setMaterials(TreeMaterials.Picker.oak);
        floweringTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        floweringTree.setBranchBlock(BlockUtil.getStateLog(EnumType.DARK_OAK).withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE));
        floweringTree.setMinCrownSize(8);
        floweringTree.setMaxCrownSize(11);
        floweringTree.setMinTrunkSize(4);
        floweringTree.setMaxTrunkSize(6);
        floweringTree.setLeafChoice(BOPTreeMaterials.floweringOak());
        DecoTree floweringDeco = new DecoTree(floweringTree);

        TreeRTG oakTree = new TreeRTGQuercusRobur();
        oakTree.setMinCrownSize(9);
        oakTree.setMaxCrownSize(12);
        oakTree.setMinTrunkSize(4);
        oakTree.setMaxTrunkSize(7);
        oakTree.setLeafChoice(BOPTreeMaterials.floweringOak());
        DecoTree oakDeco = new DecoTree(oakTree);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(coconutDeco, 1);
        treeCombination.add(palmDeco, 1);
        treeCombination.add(jungleDeco, 2);
        treeCombination.add(floweringDeco, 3);
        treeCombination.add(oakDeco, 6);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.5f, 6.5f));

        DecoShrub shrubs = new DecoShrub();
        shrubs.setLeafChoice(BOPTreeMaterials.floweringOak());
        shrubs.setLoops(4);

        DecoCollectionBase allDecos = new DecoCollectionBase(getConfig());
        allDecos.addDeco(treeCombination);
        allDecos.addDeco(shrubs);
        this.treeGenerator = allDecos;
    }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPRainforest();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1.3f);
    }

    public static class TerrainBOPRainforest extends TerrainBase {

        public TerrainBOPRainforest() {}

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java:637-641  rainforest -> TerrainSwampMountain(120f, 300f)
            return terrainSwampMountain(x, y, rtgWorld, river, 120f, 300f);
        }
    }

}
