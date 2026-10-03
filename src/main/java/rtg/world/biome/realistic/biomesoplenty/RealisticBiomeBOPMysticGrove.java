package rtg.world.biome.realistic.biomesoplenty;


import biomesoplenty.api.block.BOPBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoFallenTree;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.deco.DecoVariableBirch;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.gen.feature.tree.bop.BOPTreeMaterials;
import rtg.api.world.gen.feature.tree.bop.TreeBOPJacaranda;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusRobur;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGrassland;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;

import static rtg.api.world.deco.DecoFallenTree.LogCondition.RANDOM_CHANCE;


public class RealisticBiomeBOPMysticGrove extends RealisticBiomeBase {

    public RealisticBiomeBOPMysticGrove(final Biome biome) { super(biome); }

    @Override
    public Biome preferredBeach() {
        return baseBiome();
    }

    @Override
    public void initConfig() {
        this.getConfig().addProperty(this.getConfig().ALLOW_LOGS).set(true);
        this.getConfig().addProperty(this.getConfig().FALLEN_LOG_DENSITY_MULTIPLIER);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPMysticGrove();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceGrassland(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState());
    }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTG oakTree = new TreeRTGQuercusRobur();
        oakTree.setMinCrownSize(6);
        oakTree.setMaxCrownSize(10);
        oakTree.setMinTrunkSize(6);
        oakTree.setMaxTrunkSize(8);
        oakTree.setLeafChoice(BOPTreeMaterials.floweringOak());
        TreeRTGResizable variableOak = new TreeRTGResizable(oakTree);
        variableOak.changeAverageHeightSqrt(-0.5f);
        variableOak.changeHeightNoiseVariability(-1);
        DecoTree oakDeco = new DecoTree(variableOak);

        DecoVariableBirch mysticDeco = new DecoVariableBirch();
        mysticDeco.changeAverageHeightSqrt(-1);
        mysticDeco.changeHeightNoiseVariability(-1);
        mysticDeco.setMaterials(BOPTreeMaterials.magic);

        TreeRTG jacarandaTree = new TreeBOPJacaranda();
        TreeRTGResizable variableJacaranda = new TreeRTGResizable(jacarandaTree);
        variableJacaranda.changeAverageHeightSqrt(-0.5f);
        variableJacaranda.changeHeightNoiseVariability(-1);
        DecoTree jacarandaDeco = new DecoTree(variableJacaranda);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(oakDeco, 2);
        treeCombination.add(mysticDeco, 1);
        treeCombination.add(jacarandaDeco, 1);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 3f, 6.0f));

        DecoCollectionBase rtgTreeColl = new DecoCollectionBase(getConfig());
        rtgTreeColl.addDeco(treeCombination);

        this.treeGenerator = rtgTreeColl;

        DecoFallenTree decoFallenTree = new DecoFallenTree();
        decoFallenTree.getDistribution().setNoiseDivisor(80f);
        decoFallenTree.getDistribution().setNoiseFactor(60f);
        decoFallenTree.getDistribution().setNoiseAddend(-15f);
        decoFallenTree.setLogCondition(RANDOM_CHANCE);
        decoFallenTree.setLogConditionChance(12);
        decoFallenTree.setRandomLogBlocks(new IBlockState[]{Blocks.LOG.getDefaultState(), BOPBlocks.log_3.getStateFromMeta(4), BOPBlocks.log_1.getStateFromMeta(5)});
        decoFallenTree.setMinSize(3);
        decoFallenTree.setMaxSize(5);
        this.addDeco(decoFallenTree, this.getConfig().ALLOW_LOGS.get());
    }

    public static class TerrainBOPMysticGrove extends TerrainBase {

        public TerrainBOPMysticGrove() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {

            // 推断：RWG 无 mysticGrove；按名字亲缘取 grove 的配方
            return terrainHighland(x, y, rtgWorld, river, 0f, 140f, 68f, 200f, 0.3f);
        }
    }

}
