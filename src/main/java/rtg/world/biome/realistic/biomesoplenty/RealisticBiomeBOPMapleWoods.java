package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.biome.BOPBiomes;
import net.minecraft.block.BlockPlanks.EnumType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.BlockUtil;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.bop.TreeBOPMediumFir;
import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusNigra;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;

public class RealisticBiomeBOPMapleWoods extends RealisticBiomeBase {

    public RealisticBiomeBOPMapleWoods(final Biome biome) { super(biome); }

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

        return new TerrainBOPMapleWoods(68f, 80f, 30f);
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTGQuercusNigra fancyMaple = new TreeRTGQuercusNigra();
        fancyMaple.setMaterials(BOPTreeMaterials.maple);
        fancyMaple.setTrunkProportionVariability(.15f);
        fancyMaple.setLowestVariableTrunkProportion(.3f);
        TreeRTGResizable variableMaple = new TreeRTGResizable(fancyMaple);
        variableMaple.changeAverageHeightSqrt(-.5f);
        variableMaple.changeHeightNoiseVariability(-.75f);
        DecoTree mapleDeco = new DecoTree(variableMaple);

        TreeBOPMediumFir scatteredPines = new TreeBOPMediumFir();
        scatteredPines.setTrunkProportionVariability(.15f);
        scatteredPines.setMaterials(TreeMaterials.Picker.spruce);
        scatteredPines.setLowestVariableTrunkProportion(.3f);
        TreeRTGResizable variablePines = new TreeRTGResizable(scatteredPines);
        variablePines.changeHeightNoiseVariability(-.75f);
        DecoTree pineDeco = new DecoTree(variablePines);

        DecoTreeSet trees = new DecoTreeSet();
        trees.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 2.0f, 5.0f));
        trees.add(pineDeco, 3);
        trees.add(mapleDeco, 4);

        this.treeGenerator = trees;

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogConditionChance(1);
        decoFallenTree.setRandomLogBlocks(new IBlockState[]{Blocks.LOG.getDefaultState(), BlockUtil.getStateLog(EnumType.SPRUCE)});
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(6);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPMapleWoods extends TerrainBase {

        private float minHeight;
        private float maxHeight;
        private float hillStrength;

        public TerrainBOPMapleWoods(float minHeight, float maxHeight, float hillStrength) {

            this.minHeight = minHeight;
            this.maxHeight = (maxHeight > rollingHillsMaxHeight) ? rollingHillsMaxHeight : ((maxHeight < this.minHeight) ? rollingHillsMaxHeight : maxHeight);
            this.hillStrength = hillStrength;
        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  mapleWoods -> TerrainHighland(0f, 140f, 68f, 200f)
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f);
        }
    }

}
