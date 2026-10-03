package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockLog.EnumAxis;
import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.gen.feature.tree.bop.TreeBOPMediumEucalyptus;
import rtg.api.world.gen.feature.tree.bop.TreeBOPTropicalRainforest;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaPopulifolia;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPTropicalRainforest extends RealisticBiomeBase {

    public RealisticBiomeBOPTropicalRainforest(final Biome biome) { super(biome); }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTG mahoganyTree = new TreeBOPTropicalRainforest();
        mahoganyTree.setMinCrownSize(6);
        mahoganyTree.setMaxCrownSize(8);
        mahoganyTree.setMinTrunkSize(12);
        mahoganyTree.setMaxTrunkSize(14);
        DecoTree mahoganyDeco = new DecoTree(mahoganyTree);

        TreeRTG jungleTree = new TreeRTGBetulaPopulifolia();
        jungleTree.setLogBlock(BlockUtil.getStateLog(EnumType.JUNGLE));
        jungleTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.JUNGLE));
        jungleTree.setBranchBlock(BlockUtil.getStateLog(EnumType.JUNGLE).withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE));
        jungleTree.setMinCrownSize(6);
        jungleTree.setMaxCrownSize(8);
        jungleTree.setMinTrunkSize(12);
        jungleTree.setMaxTrunkSize(14);
        DecoTree jungleDeco = new DecoTree(jungleTree);

        TreeRTG darkTree = new TreeRTGBetulaPopulifolia();
        darkTree.setLogBlock(BlockUtil.getStateLog(EnumType.DARK_OAK));
        darkTree.setLeavesBlock(BlockUtil.getStateLeaf(EnumType.DARK_OAK));
        darkTree.setBranchBlock(BlockUtil.getStateLog(EnumType.DARK_OAK).withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE));
        darkTree.setMinCrownSize(8);
        darkTree.setMaxCrownSize(10);
        darkTree.setMinTrunkSize(10);
        darkTree.setMaxTrunkSize(14);
        DecoTree darkDeco = new DecoTree(darkTree);

        TreeRTG oakalyptus = new TreeBOPMediumEucalyptus();
        oakalyptus.setLogBlock(BlockUtil.getStateLog(EnumType.OAK));
        oakalyptus.setBranchBlock(BlockUtil.getStateLog(EnumType.OAK).withProperty(BlockLog.LOG_AXIS, EnumAxis.NONE));
        oakalyptus.setMinCrownSize(6);
        oakalyptus.setMaxCrownSize(8);
        oakalyptus.setMinTrunkSize(12);
        oakalyptus.setMaxTrunkSize(14);
        DecoTree oakalyptusDeco = new DecoTree(oakalyptus);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(mahoganyDeco, 5);
        treeCombination.add(jungleDeco, 6);
        treeCombination.add(darkDeco, 4);
        treeCombination.add(oakalyptusDeco, 3);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.5f, 7.5f));

        this.treeGenerator = treeCombination;
    }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_RIVERS.set(false);
        this.getConfig().ALLOW_SCENIC_LAKES.set(false);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPTropicalRainforest(0f, 60f, 68f, 200f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    public static class TerrainBOPTropicalRainforest extends TerrainBase {

        private float start;
        private float height;
        private float width;

        public TerrainBOPTropicalRainforest(float hillStart, float landHeight, float baseHeight, float hillWidth) {

            start = hillStart;
            height = landHeight;
            base = baseHeight;
            width = hillWidth;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  tropicalRainforest -> TerrainHighland(0f, 140f, 68f, 200f)  [由偏离版 terrainHighlandLegacy 拨回忠实版]
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
